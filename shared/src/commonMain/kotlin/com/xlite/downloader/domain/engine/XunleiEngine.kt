package com.xlite.downloader.domain.engine

import com.xlite.downloader.core.AppLog
import com.xlite.downloader.data.PlatformPaths
import com.xlite.downloader.data.SettingsRepository
import com.xlite.downloader.data.TaskRepository
import com.xlite.downloader.domain.EngineMeta
import com.xlite.downloader.domain.model.AppSettings
import com.xlite.downloader.domain.model.DesiredState
import com.xlite.downloader.domain.model.DownloadTask
import com.xlite.downloader.domain.model.TaskTraffic
import com.xlite.downloader.domain.model.TaskStatus
import com.xlite.downloader.platform.PlatformActions
import com.xlite.downloader.util.FileNameUtils
import com.xlite.downloader.util.UrlParseResult
import com.xlite.downloader.util.UrlValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import okio.FileSystem
import okio.Path.Companion.toPath

class XunleiEngine(
    private val port: XunleiSdkPort,
    private val auth: AuthManager,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val actions: PlatformActions,
    private val paths: PlatformPaths,
    private val fileSystem: FileSystem,
    private val scope: CoroutineScope,
    private val sdkDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val _meta = MutableStateFlow(EngineMeta())
    val meta: StateFlow<EngineMeta> = _meta.asStateFlow()

    private var initialized = false
    private var pollJob: Job? = null
    private var tokenJob: Job? = null
    private var tick = 0L
    private var engineVersion: String? = null
    private val lastResumeAttempt = mutableMapOf<Long, Long>()
    private val loggedStateErrors = mutableSetOf<Long>()

    suspend fun initialize(): Boolean = withContext(sdkDispatcher) {
        if (initialized) return@withContext true
        _meta.update { it.copy(initializing = true, globalError = null) }
        val settingsValue = settings.settings.value
        val ok = auth.initialize(settingsValue.appIdOverride)
        if (ok) {
            initialized = true
            engineVersion = runCatching { port.getVersion().version }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            applySettingsInternal(settingsValue)?.let { error ->
                _meta.update { it.copy(globalError = error) }
            }
            reconcileInternal()
            startLoops()
            AppLog.info("engine", "下载引擎已启动 sdk=$engineVersion")
        }
        _meta.update { it.copy(initializing = false, engineVersion = engineVersion) }
        ok
    }

    suspend fun updateSettings(newSettings: AppSettings): ActionResult {
        val current = settings.settings.value
        val updated = settings.update { newSettings.copy(appIdOverride = current.appIdOverride) }
        if (!initialized || !auth.isReady()) return ActionResult(true, "设置已保存")
        val error = withContext(sdkDispatcher) { applySettingsInternal(updated) }
        return if (error == null) {
            _meta.update { it.copy(globalError = null) }
            ActionResult(true, "设置已保存并生效")
        } else {
            _meta.update { it.copy(globalError = error) }
            ActionResult(false, error)
        }
    }

    suspend fun testConnection(apiKey: String, appIdOverride: String?): ConnectionTestResult =
        withContext(ioDispatcher) { auth.testConnection(apiKey, appIdOverride) }

    suspend fun testStoredConnection(): ConnectionTestResult {
        val key = auth.apiKey ?: return ConnectionTestResult(
            success = false,
            appId = null,
            tokenExpiresAtEpochSeconds = null,
            message = "尚未保存 API Key",
        )
        return testConnection(key, settings.settings.value.appIdOverride)
    }

    suspend fun reconnect(): ActionResult = withContext(sdkDispatcher) {
        stopLoops()
        if (initialized) {
            stopActiveTasks(preserveDesired = true)
            runCatching { port.uninit() }
            initialized = false
        }
        _meta.update { it.copy(initializing = true, globalError = null) }
        val ok = auth.initialize(settings.settings.value.appIdOverride)
        if (ok) {
            initialized = true
            engineVersion = runCatching { port.getVersion().version }.getOrNull()?.takeIf { it.isNotBlank() }
            applySettingsInternal(settings.settings.value)?.let { error ->
                _meta.update { it.copy(globalError = error) }
            }
            reconcileInternal()
            startLoops()
        }
        _meta.update { it.copy(initializing = false, engineVersion = engineVersion) }
        if (ok) ActionResult(true, "已重新登录") else ActionResult(false, "重新登录失败，请检查 API Key")
    }

    suspend fun saveCredentials(apiKey: String, appIdOverride: String?): ActionResult {
        val normalizedKey = apiKey.trim()
        if (normalizedKey.isEmpty()) return ActionResult(false, "请输入 API Key")
        val normalizedOverride = appIdOverride?.trim()?.takeIf { it.isNotEmpty() }

        val test = withContext(ioDispatcher) { auth.testConnection(normalizedKey, normalizedOverride) }
        if (!test.success) return ActionResult(false, test.message)

        val oldKey = auth.apiKey
        val oldOverride = settings.settings.value.appIdOverride
        val hadEngine = initialized

        stopLoops()
        _meta.update { it.copy(initializing = true, globalError = null) }
        val result = withContext(sdkDispatcher) {
            if (hadEngine) {
                stopActiveTasks(preserveDesired = true)
                runCatching { port.uninit() }
                initialized = false
            }
            val ok = auth.authenticate(normalizedKey, normalizedOverride)
            if (ok) {
                settings.update { it.copy(appIdOverride = normalizedOverride) }
                engineVersion = runCatching { port.getVersion().version }.getOrNull()?.takeIf { it.isNotBlank() }
                initialized = true
                applySettingsInternal(settings.settings.value)
                reconcileInternal()
                startLoops()
                AppLog.info("engine", "凭据已更新并重新登录")
                ActionResult(true, "凭据已保存，下载引擎已重新登录")
            } else {
                val newError = (auth.auth.value as? com.xlite.downloader.domain.model.AuthState.Error)?.message
                    ?: "新凭据验证失败"
                if (oldKey != null) {
                    val restored = auth.authenticate(oldKey, oldOverride)
                    if (restored) {
                        settings.update { it.copy(appIdOverride = oldOverride) }
                        engineVersion = runCatching { port.getVersion().version }.getOrNull()?.takeIf { it.isNotBlank() }
                        initialized = true
                        applySettingsInternal(settings.settings.value)
                        reconcileInternal()
                        startLoops()
                        ActionResult(false, "新凭据验证失败：$newError；已恢复原凭据")
                    } else {
                        ActionResult(false, "新凭据验证失败：$newError；恢复原凭据也失败，请重新配置")
                    }
                } else {
                    ActionResult(false, newError)
                }
            }
        }
        _meta.update { it.copy(initializing = false, engineVersion = engineVersion) }
        return result
    }

    suspend fun clearCredentials(): ActionResult {
        stopLoops()
        withContext(sdkDispatcher) {
            stopActiveTasks(preserveDesired = false)
            if (initialized) {
                runCatching { port.uninit() }
                initialized = false
            }
            auth.clearStoredKey()
        }
        _meta.update { it.copy(engineVersion = null) }
        return ActionResult(true, "已清除 API Key，下载任务已暂停")
    }

    suspend fun addTasks(rawInput: String, saveDir: String, singleName: String?): AddTasksOutcome =
        withContext(sdkDispatcher) {
            if (!auth.isReady()) {
                return@withContext AddTasksOutcome(0, listOf(AddFailure(rawInput, "请先配置有效的 API Key")))
            }
            val entries = UrlValidator.splitInput(rawInput)
            if (entries.isEmpty()) return@withContext AddTasksOutcome(0, emptyList())

            val targetDir = saveDir.trim().ifBlank { settings.settings.value.saveDir }
            writableDirectory(targetDir)?.let { error ->
                return@withContext AddTasksOutcome(0, listOf(AddFailure(targetDir, error)))
            }
            val dirPath = targetDir.toPath()
            val usedNames = tasks.snapshot()
                .filter { it.saveDir == targetDir }
                .map { it.saveName }
                .toMutableSet()
            val failures = mutableListOf<AddFailure>()
            val batchKeys = mutableSetOf<String>()
            var added = 0

            for (entry in entries) {
                val parsed = UrlValidator.parse(entry)
                val url = when (parsed) {
                    is UrlParseResult.Valid -> parsed.url
                    is UrlParseResult.Unsupported -> {
                        failures += AddFailure(entry, parsed.message)
                        continue
                    }
                    is UrlParseResult.Invalid -> {
                        failures += AddFailure(entry, parsed.message)
                        continue
                    }
                    UrlParseResult.Blank -> continue
                }
                val now = nowMillis()
                val fallback = FileNameUtils.fromUrl(url, now)
                val requested = if (singleName != null && entries.size == 1) {
                    FileNameUtils.sanitize(singleName, fallback)
                } else {
                    fallback
                }
                val batchKey = "$url|$targetDir"
                if (!batchKeys.add(batchKey)) {
                    failures += AddFailure(entry, "本次输入中存在重复链接")
                    continue
                }
                if (tasks.snapshot().any { it.url == url && it.saveDir == targetDir }) {
                    failures += AddFailure(entry, "相同链接和保存位置的任务已存在")
                    continue
                }
                val saveName = FileNameUtils.uniqueName(dirPath, requested, fileSystem, usedNames)

                val create = port.createP2spTask(url, targetDir, saveName)
                if (create.result != XunleiCodes.SUCCESS) {
                    failures += AddFailure(entry, ErrorMapper.messageFor(create.result))
                    continue
                }
                val task = DownloadTask(
                    taskId = create.taskId,
                    url = url,
                    saveDir = targetDir,
                    saveName = saveName,
                    createdAt = now,
                    desired = DesiredState.RUNNING,
                    status = TaskStatus.QUEUED,
                )
                usedNames += saveName
                tasks.upsert(task)
                val start = port.startTask(create.taskId)
                if (start == XunleiCodes.SUCCESS || start == XunleiCodes.TASK_ALREADY_RUNNING) {
                    added++
                } else {
                    tasks.update(create.taskId) {
                        it.copy(
                            status = TaskStatus.FAILED,
                            desired = DesiredState.NONE,
                            errorCode = start,
                            errorMessage = ErrorMapper.messageFor(start),
                        )
                    }
                    failures += AddFailure(entry, ErrorMapper.messageFor(start))
                }
            }
            AppLog.info("engine", "批量添加完成 added=$added failed=${failures.size}")
            AddTasksOutcome(added, failures)
        }

    suspend fun pause(taskId: Long): ActionResult = withPending(taskId) {
        pauseInternal(taskId)
    }

    suspend fun resume(taskId: Long): ActionResult = withPending(taskId) {
        resumeInternal(taskId)
    }

    suspend fun retry(taskId: Long): ActionResult = withPending(taskId) {
        retryInternal(taskId)
    }

    suspend fun delete(taskId: Long, deleteFile: Boolean): ActionResult = withPending(taskId) {
        deleteInternal(taskId, deleteFile)
    }

    suspend fun pauseAll(): ActionResult = withContext(sdkDispatcher) {
        val targets = tasks.snapshot().filter { !it.status.isTerminal }
        var okCount = 0
        val failures = mutableListOf<String>()
        for (task in targets) {
            val result = pauseInternal(task.taskId)
            if (result.ok) okCount++ else failures += result.message
        }
        ActionResult(
            failures.isEmpty(),
            if (failures.isEmpty()) "已暂停 $okCount 个任务" else "已暂停 $okCount 个，${failures.size} 个失败",
        )
    }

    suspend fun resumeAll(): ActionResult = withContext(sdkDispatcher) {
        val targets = tasks.snapshot().filter { it.status == TaskStatus.PAUSED || it.status == TaskStatus.FAILED }
        var okCount = 0
        val failures = mutableListOf<String>()
        for (task in targets) {
            val result = if (task.status == TaskStatus.FAILED) retryInternal(task.taskId) else resumeInternal(task.taskId)
            if (result.ok) okCount++ else failures += result.message
        }
        ActionResult(
            failures.isEmpty(),
            if (failures.isEmpty()) "已继续 $okCount 个任务" else "已继续 $okCount 个，${failures.size} 个失败",
        )
    }

    suspend fun shutdown() {
        stopLoops()
        withContext(sdkDispatcher) {
            if (initialized) {
                stopActiveTasks(preserveDesired = true)
                runCatching { port.uninit() }
                    .onFailure { AppLog.warn("engine", "释放 SDK 失败", it) }
                initialized = false
            }
        }
        tasks.flush()
        settings.update { it }
    }

    private suspend fun withPending(taskId: Long, block: suspend () -> ActionResult): ActionResult {
        _meta.update { it.copy(pendingTaskIds = it.pendingTaskIds + taskId) }
        return try {
            withContext(sdkDispatcher) { block() }
        } finally {
            _meta.update { it.copy(pendingTaskIds = it.pendingTaskIds - taskId) }
        }
    }

    private suspend fun pauseInternal(taskId: Long): ActionResult {
        if (!auth.isReady()) return ActionResult(false, "请先配置有效的 API Key")
        val task = tasks.snapshot().firstOrNull { it.taskId == taskId }
            ?: return ActionResult(false, "任务不存在")
        if (task.status.isTerminal) return ActionResult(false, "任务已结束")
        var result = port.stopTask(taskId)
        if (XunleiCodes.isAuthError(result) && auth.refresh(force = true)) {
            result = port.stopTask(taskId)
        }
        if (result == XunleiCodes.SUCCESS || XunleiCodes.isAlreadyStopped(result)) {
            tasks.update(taskId) {
                it.copy(
                    desired = DesiredState.PAUSED,
                    status = if (it.status == TaskStatus.PAUSING) TaskStatus.PAUSED else TaskStatus.PAUSING,
                    speedBps = 0,
                    errorCode = 0,
                    errorMessage = null,
                )
            }
            return ActionResult(true, "任务已暂停")
        }
        return ActionResult(false, ErrorMapper.messageFor(result))
    }

    private suspend fun resumeInternal(taskId: Long): ActionResult {
        if (!auth.isReady()) return ActionResult(false, "请先配置有效的 API Key")
        val task = tasks.snapshot().firstOrNull { it.taskId == taskId }
            ?: return ActionResult(false, "任务不存在")
        if (task.status == TaskStatus.COMPLETED) return ActionResult(false, "任务已完成")
        val result = startWithRetry(taskId)
        return if (result == XunleiCodes.SUCCESS || result == XunleiCodes.TASK_ALREADY_RUNNING) {
            tasks.update(taskId) {
                it.copy(
                    desired = DesiredState.RUNNING,
                    status = TaskStatus.QUEUED,
                    speedBps = 0,
                    errorCode = 0,
                    errorMessage = null,
                )
            }
            ActionResult(true, "任务已继续")
        } else {
            tasks.update(taskId) {
                it.copy(
                    desired = DesiredState.PAUSED,
                    status = TaskStatus.PAUSED,
                    errorCode = result,
                    errorMessage = ErrorMapper.messageFor(result),
                )
            }
            ActionResult(false, ErrorMapper.messageFor(result))
        }
    }

    private suspend fun retryInternal(taskId: Long): ActionResult {
        if (!auth.isReady()) return ActionResult(false, "请先配置有效的 API Key")
        val task = tasks.snapshot().firstOrNull { it.taskId == taskId }
            ?: return ActionResult(false, "任务不存在")
        if (task.status == TaskStatus.COMPLETED) return ActionResult(false, "任务已完成，无需重试")
        val result = startWithRetry(taskId)
        if (result == XunleiCodes.SUCCESS || result == XunleiCodes.TASK_ALREADY_RUNNING) {
            tasks.update(taskId) {
                it.copy(
                    desired = DesiredState.RUNNING,
                    status = TaskStatus.QUEUED,
                    speedBps = 0,
                    errorCode = 0,
                    errorMessage = null,
                )
            }
            return ActionResult(true, "任务已重新开始")
        }
        if (result == XunleiCodes.TASK_NOT_EXIST || result == XunleiCodes.TASK_FINISH || result == XunleiCodes.TASK_STATUS_ERR) {
            return recreateTask(task)
        }
        tasks.update(taskId) {
            it.copy(
                desired = DesiredState.NONE,
                status = TaskStatus.FAILED,
                errorCode = result,
                errorMessage = ErrorMapper.messageFor(result),
            )
        }
        return ActionResult(false, ErrorMapper.messageFor(result))
    }

    private fun recreateTask(task: DownloadTask): ActionResult {
        val create = port.createP2spTask(task.url, task.saveDir, task.saveName)
        if (create.result != XunleiCodes.SUCCESS) {
            return ActionResult(false, ErrorMapper.messageFor(create.result))
        }
        val replacement = task.copy(
            taskId = create.taskId,
            createdAt = nowMillis(),
            desired = DesiredState.RUNNING,
            status = TaskStatus.QUEUED,
            totalBytes = 0,
            downloadedBytes = 0,
            speedBps = 0,
            errorCode = 0,
            errorMessage = null,
            completedAt = null,
            completionNotified = false,
        )
        tasks.remove(task.taskId)
        tasks.upsert(replacement)
        val start = port.startTask(create.taskId)
        if (start != XunleiCodes.SUCCESS && start != XunleiCodes.TASK_ALREADY_RUNNING) {
            tasks.update(create.taskId) {
                it.copy(
                    status = TaskStatus.FAILED,
                    desired = DesiredState.NONE,
                    errorCode = start,
                    errorMessage = ErrorMapper.messageFor(start),
                )
            }
            return ActionResult(false, ErrorMapper.messageFor(start))
        }
        return ActionResult(true, "任务已重新创建并开始")
    }

    private suspend fun deleteInternal(taskId: Long, deleteFile: Boolean): ActionResult {
        val task = tasks.snapshot().firstOrNull { it.taskId == taskId }
            ?: return ActionResult(true, "任务已删除")
        if (task.status.isActive) {
            port.stopTask(taskId)
            delay(300)
        }
        var result = port.deleteTask(taskId, deleteFile)
        if (result == XunleiCodes.TASK_STILL_RUNNING) {
            delay(500)
            result = port.deleteTask(taskId, deleteFile)
        }
        if (result == XunleiCodes.SUCCESS || result == XunleiCodes.TASK_NOT_EXIST) {
            tasks.remove(taskId)
            if (deleteFile) deleteLocalFile(task)
            return ActionResult(true, "任务已删除")
        }
        return ActionResult(false, ErrorMapper.messageFor(result))
    }

    private fun deleteLocalFile(task: DownloadTask) {
        if (task.saveName.contains('/') || task.saveName.contains('\\')) return
        runCatching {
            val file = task.saveDir.toPath() / task.saveName
            if (fileSystem.exists(file)) fileSystem.delete(file)
        }.onFailure { AppLog.warn("engine", "删除本地文件失败", it) }
    }

    private suspend fun startWithRetry(taskId: Long): Int {
        var result = port.startTask(taskId)
        if (XunleiCodes.isAuthError(result) && auth.refresh(force = true)) {
            result = port.startTask(taskId)
        }
        return result
    }

    private fun writableDirectory(dir: String): String? = runCatching {
        val path = dir.toPath()
        fileSystem.createDirectories(path)
        val probe = path / ".xlite-write-test-${nowMillis()}"
        fileSystem.sink(probe).use { }
        fileSystem.delete(probe)
        null
    }.getOrElse { "保存目录不可写：${it.message ?: "未知错误"}" }

    private fun applySettingsInternal(value: AppSettings): String? {
        if (!initialized) return null
        var firstError: String? = null
        fun apply(name: String, call: () -> Int) {
            val result = runCatching(call).getOrElse {
                AppLog.warn("engine", "应用设置失败 $name", it)
                if (firstError == null) firstError = "$name 失败：${it.message ?: "未知错误"}"
                return
            }
            if (result != XunleiCodes.SUCCESS) {
                val message = "$name 失败：${ErrorMapper.messageFor(result)}"
                AppLog.warn("engine", message)
                if (firstError == null) firstError = message
            }
        }
        apply("同时下载数") { port.setConcurrentTaskCount(value.concurrentTasks) }
        apply("下载限速") { port.setDownloadSpeedLimit(if (value.downloadLimitKbps <= 0) -1 else value.downloadLimitKbps) }
        apply("上传开关") { port.setUploadEnabled(value.uploadEnabled) }
        apply("上传限速") { port.setUploadSpeedLimit(if (value.uploadLimitKbps <= 0) -1 else value.uploadLimitKbps) }
        apply("动态链接加速") { port.setDynamicLinkAcceleration(value.dynamicAcceleration) }
        return firstError
    }

    private fun startLoops() {
        stopLoops()
        pollJob = scope.launch(sdkDispatcher) {
            while (isActive && initialized) {
                val active = runCatching { pollOnce() }.getOrElse {
                    AppLog.error("engine", "轮询任务状态失败", it)
                    false
                }
                delay(if (active) 1_000 else 3_000)
            }
        }
        tokenJob = scope.launch(sdkDispatcher) {
            while (isActive && initialized) {
                delay(60_000)
                if (!auth.isReady()) continue
                val remaining = auth.secondsUntilExpiry()
                if (remaining == null || remaining <= REFRESH_MARGIN_SECONDS) {
                    val refreshed = auth.refresh(force = false)
                    if (!refreshed && (remaining == null || remaining <= 0)) {
                        auth.markExpired("登录已过期，请重新登录")
                    }
                }
            }
        }
    }

    private fun stopLoops() {
        pollJob?.cancel()
        pollJob = null
        tokenJob?.cancel()
        tokenJob = null
    }

    private suspend fun pollOnce(): Boolean {
        if (!initialized || !auth.isReady()) return false
        val snapshot = tasks.snapshot()
        var active = false
        var needsRefresh = false
        val notify = settings.settings.value.notifyOnComplete

        for (task in snapshot) {
            if (task.status.isTerminal) continue
            val stateResult = port.getTaskState(task.taskId)
            if (stateResult.result != XunleiCodes.SUCCESS) {
                if (XunleiCodes.isAuthError(stateResult.result)) needsRefresh = true
                if (loggedStateErrors.add(task.taskId)) {
                    AppLog.warn("engine", "查询任务状态失败 task=${task.taskId} code=${stateResult.result}")
                }
                if (XunleiCodes.isTaskMissing(stateResult.result) && nowMillis() - task.createdAt > 30_000) {
                    tasks.update(task.taskId) {
                        it.copy(
                            status = TaskStatus.FAILED,
                            desired = DesiredState.NONE,
                            errorCode = stateResult.result,
                            errorMessage = "任务在下载引擎中不存在",
                        )
                    }
                }
                continue
            }
            loggedStateErrors.remove(task.taskId)
            if (stateResult.tokenErrCode != 0) needsRefresh = true

            val status = TaskStatus.fromSdkState(stateResult.stateCode)
            if (status.isActive) active = true
            if (task.desired == DesiredState.RUNNING && !status.isTerminal) active = true

            var completionNotified = task.completionNotified
            if (status == TaskStatus.COMPLETED && !task.completionNotified) {
                if (notify) {
                    runCatching { actions.notifyCompleted(task.taskId, task.saveName, task.displayPath) }
                        .onFailure { AppLog.warn("engine", "发送完成通知失败", it) }
                }
                completionNotified = true
            }

            val updated = task.copy(
                status = status,
                totalBytes = stateResult.totalBytes,
                downloadedBytes = stateResult.downloadedBytes,
                speedBps = if (status == TaskStatus.PAUSED || status.isTerminal) 0 else stateResult.speedBps,
                errorCode = stateResult.taskErrCode,
                errorMessage = if (status == TaskStatus.FAILED) {
                    ErrorMapper.messageFor(stateResult.taskErrCode)
                } else {
                    null
                },
                desired = when (status) {
                    TaskStatus.COMPLETED, TaskStatus.FAILED -> DesiredState.NONE
                    else -> task.desired
                },
                completedAt = if (status == TaskStatus.COMPLETED) task.completedAt ?: nowMillis() else task.completedAt,
                completionNotified = completionNotified,
                traffic = readTraffic(task.taskId) ?: task.traffic,
            )
            if (updated != task) {
                val persist = updated.status != task.status ||
                    updated.desired != task.desired ||
                    updated.completionNotified != task.completionNotified ||
                    updated.errorCode != task.errorCode
                tasks.upsert(updated, persist = persist)
                if (updated.status != task.status) {
                    AppLog.info("engine", "任务状态 task=${task.taskId} ${task.status} -> ${updated.status}")
                }
            }

            if (status == TaskStatus.PAUSED && updated.desired == DesiredState.RUNNING) {
                maybeResume(task.taskId)
            }
        }

        if (needsRefresh) {
            val remaining = auth.secondsUntilExpiry()
            val refreshed = auth.refresh(force = true)
            if (!refreshed && (remaining == null || remaining <= 0)) {
                auth.markExpired("登录已过期，请重新登录")
            }
        }

        tick++
        if (tick % 10L == 0L) reconcileInternal()
        return active
    }

    private suspend fun maybeResume(taskId: Long) {
        val now = nowMillis()
        val last = lastResumeAttempt[taskId] ?: 0
        if (now - last < 5_000) return
        lastResumeAttempt[taskId] = now
        val result = startWithRetry(taskId)
        if (result == XunleiCodes.SUCCESS || result == XunleiCodes.TASK_ALREADY_RUNNING) {
            tasks.update(taskId) { it.copy(status = TaskStatus.QUEUED, speedBps = 0) }
        }
    }

    private suspend fun reconcileInternal() {
        if (!initialized || !auth.isReady()) return
        val unfinished = port.getUnfinishedTasks()
        val finished = port.getFinishedTasks()
        if (unfinished.result != XunleiCodes.SUCCESS || finished.result != XunleiCodes.SUCCESS) {
            AppLog.warn("engine", "同步任务列表失败 unfinished=${unfinished.result} finished=${finished.result}")
            return
        }
        val unfinishedIds = unfinished.taskIds.toSet()
        val finishedIds = finished.taskIds.toSet()
        val local = tasks.snapshot()
        val localIds = local.map { it.taskId }.toSet()
        val stateById = mutableMapOf<Long, TaskStateResult>()
        val now = nowMillis()

        for (task in local) {
            val traffic = task.traffic ?: readTraffic(task.taskId)
            if (traffic != task.traffic) {
                tasks.update(task.taskId) { it.copy(traffic = traffic) }
            }
            when {
                task.taskId in finishedIds -> {
                    if (task.status != TaskStatus.COMPLETED) {
                        tasks.update(task.taskId) {
                            it.copy(
                                status = TaskStatus.COMPLETED,
                                desired = DesiredState.NONE,
                                speedBps = 0,
                                completedAt = it.completedAt ?: now,
                                completionNotified = true,
                                errorCode = 0,
                                errorMessage = null,
                            )
                        }
                    } else if (!task.completionNotified) {
                        tasks.update(task.taskId) { it.copy(completionNotified = true) }
                    }
                }
                task.taskId in unfinishedIds -> {
                    val state = port.getTaskState(task.taskId)
                    if (state.result != XunleiCodes.SUCCESS) continue
                    stateById[task.taskId] = state
                    val status = TaskStatus.fromSdkState(state.stateCode)
                    val updated = task.copy(
                        status = status,
                        traffic = traffic,
                        totalBytes = state.totalBytes,
                        downloadedBytes = state.downloadedBytes,
                        speedBps = if (status == TaskStatus.PAUSED) 0 else state.speedBps,
                        errorCode = state.taskErrCode,
                        errorMessage = if (status == TaskStatus.FAILED) ErrorMapper.messageFor(state.taskErrCode) else null,
                        desired = if (status == TaskStatus.FAILED) DesiredState.NONE else task.desired,
                    )
                    if (updated != task) {
                        tasks.upsert(updated, persist = updated.status != task.status || updated.desired != task.desired)
                    }
                    when {
                        updated.desired == DesiredState.RUNNING && state.stateCode == 7 -> maybeResume(task.taskId)
                        updated.desired == DesiredState.PAUSED && status.isActive -> {
                            val stop = port.stopTask(task.taskId)
                            if (stop == XunleiCodes.SUCCESS || XunleiCodes.isAlreadyStopped(stop)) {
                                tasks.update(task.taskId) {
                                    it.copy(status = TaskStatus.PAUSED, speedBps = 0)
                                }
                            }
                        }
                    }
                }
                !task.status.isTerminal && now - task.createdAt > 30_000 -> {
                    tasks.update(task.taskId) {
                        it.copy(
                            status = TaskStatus.FAILED,
                            desired = DesiredState.NONE,
                            errorCode = XunleiCodes.TASK_NOT_EXIST,
                            errorMessage = "任务在下载引擎中不存在",
                        )
                    }
                }
            }
        }

        val knowledgeIds = unfinishedIds + finishedIds
        val imports = mutableListOf<DownloadTask>()
        for (id in knowledgeIds) {
            if (id in localIds) continue
            val state = port.getTaskState(id)
            if (state.result != XunleiCodes.SUCCESS) continue
            stateById[id] = state
            val url = taskInfoValue(id, "url")
            val savePath = taskInfoValue(id, "save_path")
            val saveNameRaw = taskInfoValue(id, "save_name")
            val saveName = saveNameRaw.ifBlank { FileNameUtils.fromUrl(url, now) }
            val saveDir = dirFromInfo(savePath, saveName, settings.settings.value.saveDir)
            val status = TaskStatus.fromSdkState(state.stateCode)
            val isFinished = id in finishedIds
            imports += DownloadTask(
                taskId = id,
                url = url,
                saveDir = saveDir,
                saveName = saveName,
                createdAt = now,
                desired = when {
                    isFinished -> DesiredState.NONE
                    status == TaskStatus.PAUSED -> DesiredState.PAUSED
                    else -> DesiredState.RUNNING
                },
                status = status,
                totalBytes = state.totalBytes,
                downloadedBytes = state.downloadedBytes,
                speedBps = if (isFinished || status == TaskStatus.PAUSED) 0 else state.speedBps,
                errorCode = state.taskErrCode,
                errorMessage = if (status == TaskStatus.FAILED) ErrorMapper.messageFor(state.taskErrCode) else null,
                completedAt = if (isFinished) now else null,
                completionNotified = isFinished,
                importedFromSdk = true,
                traffic = readTraffic(id),
            )
        }
        if (imports.isNotEmpty()) {
            tasks.upsertAll(imports)
            AppLog.info("engine", "从 SDK 导入 ${imports.size} 个任务")
        }
        for (task in imports) {
            if (task.desired == DesiredState.RUNNING && task.taskId in unfinishedIds) {
                val state = stateById[task.taskId]
                if (state?.stateCode == 7) maybeResume(task.taskId)
            }
        }
    }

    private fun readTraffic(taskId: Long): TaskTraffic? = runCatching {
        val result = port.getTaskInfo(taskId, "traffic")
        if (result.result == XunleiCodes.SUCCESS) TaskTraffic.parse(result.value) else null
    }.getOrNull()

    private fun taskInfoValue(taskId: Long, name: String): String {
        val raw = port.getTaskInfo(taskId, name).value
        if (raw.isBlank()) return ""
        return runCatching {
            Json.parseToJsonElement(raw).jsonObject[name]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: raw
    }

    private fun dirFromInfo(savePath: String, saveName: String, fallback: String): String {
        if (savePath.isBlank()) return fallback
        val normalized = savePath.replace('\\', '/')
        if (saveName.isNotBlank() && normalized.endsWith("/$saveName")) {
            val dir = normalized.removeSuffix("/$saveName").trimEnd('/')
            if (dir.isNotEmpty() && !dir.endsWith(":")) return dir
        }
        return savePath
    }

    private fun stopActiveTasks(preserveDesired: Boolean) {
        for (task in tasks.snapshot()) {
            if (task.status.isTerminal) continue
            if (!task.status.isActive && task.desired != DesiredState.RUNNING) continue
            val result = port.stopTask(task.taskId)
            if (result == XunleiCodes.SUCCESS || XunleiCodes.isAlreadyStopped(result) || XunleiCodes.isTaskMissing(result)) {
                tasks.update(task.taskId) {
                    it.copy(
                        status = TaskStatus.PAUSED,
                        speedBps = 0,
                        desired = if (preserveDesired) it.desired else DesiredState.PAUSED,
                    )
                }
            }
        }
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val REFRESH_MARGIN_SECONDS = 30L * 60L
    }
}
