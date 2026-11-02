package com.xlite.downloader.data

import com.xlite.downloader.core.AppLog
import com.xlite.downloader.domain.model.AppSettings
import com.xlite.downloader.domain.model.SettingsFile
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.buffer
import okio.Path

class SettingsRepository(
    private val fileSystem: FileSystem,
    private val file: Path,
    defaults: AppSettings,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val mutex = Mutex()
    private val _settings = MutableStateFlow(defaults.normalized())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    suspend fun load() {
        if (!fileSystem.exists(file)) return
        runCatching {
            val text = fileSystem.source(file).buffer().use { it.readUtf8() }
            json.decodeFromString<SettingsFile>(text).settings.normalized()
        }.onSuccess {
            _settings.value = it
        }.onFailure {
            AppLog.warn("settings", "读取设置失败，使用默认值", it)
            JsonFiles.backupCorrupt(fileSystem, file, Clock.System.now().toEpochMilliseconds())
        }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings = mutex.withLock {
        val updated = transform(_settings.value).normalized()
        _settings.value = updated
        withContext(NonCancellable) { persist(updated) }
        updated
    }

    private fun persist(value: AppSettings) {
        val text = json.encodeToString(SettingsFile(settings = value))
        JsonFiles.writeAtomic(fileSystem, file, text)
    }
}
