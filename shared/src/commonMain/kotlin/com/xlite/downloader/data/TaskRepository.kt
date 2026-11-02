package com.xlite.downloader.data

import com.xlite.downloader.core.AppLog
import com.xlite.downloader.domain.model.DownloadTask
import com.xlite.downloader.domain.model.TaskStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.buffer
import okio.Path

class TaskRepository(
    private val fileSystem: FileSystem,
    private val file: Path,
    private val scope: CoroutineScope,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val mutex = Mutex()
    private val saveSignals = Channel<Unit>(Channel.CONFLATED)
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    init {
        scope.launch {
            for (ignored in saveSignals) {
                delay(500)
                runCatching { persistNow() }
                    .onFailure { AppLog.error("tasks", "保存任务列表失败", it) }
            }
        }
    }

    suspend fun load() {
        if (!fileSystem.exists(file)) return
        runCatching {
            val text = fileSystem.source(file).buffer().use { it.readUtf8() }
            json.decodeFromString<TaskStoreFile>(text).tasks
        }.onSuccess {
            _tasks.value = it
        }.onFailure {
            AppLog.warn("tasks", "读取任务列表失败，已备份并重建", it)
            JsonFiles.backupCorrupt(fileSystem, file, Clock.System.now().toEpochMilliseconds())
        }
    }

    fun snapshot(): List<DownloadTask> = _tasks.value

    fun upsert(task: DownloadTask, persist: Boolean = true) {
        val current = _tasks.value
        val index = current.indexOfFirst { it.taskId == task.taskId }
        _tasks.value = if (index >= 0) {
            current.toMutableList().also { it[index] = task }
        } else {
            listOf(task) + current
        }
        if (persist) requestSave()
    }

    fun upsertAll(tasks: List<DownloadTask>, persist: Boolean = true) {
        if (tasks.isEmpty()) return
        val byId = tasks.associateBy { it.taskId }
        val existingIds = _tasks.value.map { it.taskId }.toSet()
        val merged = _tasks.value.map { byId[it.taskId] ?: it } + tasks.filter { it.taskId !in existingIds }
        _tasks.value = merged
        if (persist) requestSave()
    }

    fun update(taskId: Long, persist: Boolean = true, transform: (DownloadTask) -> DownloadTask): DownloadTask? {
        val current = _tasks.value
        val index = current.indexOfFirst { it.taskId == taskId }
        if (index < 0) return null
        val updated = transform(current[index])
        _tasks.value = current.toMutableList().also { it[index] = updated }
        if (persist) requestSave()
        return updated
    }

    fun remove(taskId: Long, persist: Boolean = true) {
        _tasks.value = _tasks.value.filterNot { it.taskId == taskId }
        if (persist) requestSave()
    }

    fun replaceAll(tasks: List<DownloadTask>, persist: Boolean = true) {
        _tasks.value = tasks
        if (persist) requestSave()
    }

    suspend fun flush() {
        runCatching { persistNow() }
            .onFailure { AppLog.error("tasks", "刷新任务列表失败", it) }
    }

    private fun requestSave() {
        saveSignals.trySend(Unit)
    }

    private suspend fun persistNow() = mutex.withLock {
        withContext(NonCancellable) {
            val text = json.encodeToString(TaskStoreFile(tasks = _tasks.value))
            JsonFiles.writeAtomic(fileSystem, file, text)
        }
    }
}
