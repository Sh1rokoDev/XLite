package com.xlite.downloader

import com.xlite.downloader.core.AppLog
import com.xlite.downloader.data.PlatformPaths
import com.xlite.downloader.data.SecretStore
import com.xlite.downloader.data.SettingsRepository
import com.xlite.downloader.data.TaskRepository
import com.xlite.downloader.domain.AppUiState
import com.xlite.downloader.domain.EngineMeta
import com.xlite.downloader.domain.engine.AuthManager
import com.xlite.downloader.domain.engine.XunleiEngine
import com.xlite.downloader.domain.engine.XunleiSdkPort
import com.xlite.downloader.domain.model.AppSettings
import com.xlite.downloader.domain.model.AuthState
import com.xlite.downloader.platform.PlatformActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

class AppContainer(
    val paths: PlatformPaths,
    val secretStore: SecretStore,
    val port: XunleiSdkPort,
    val actions: PlatformActions,
    val appVersion: String,
) {
    val fileSystem: FileSystem = FileSystem.SYSTEM
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val sdkDispatcher = Dispatchers.Default.limitedParallelism(1, "xlite-sdk")
    private val ioDispatcher = Dispatchers.Default.limitedParallelism(2, "xlite-io")

    val settings = SettingsRepository(
        fileSystem = fileSystem,
        file = paths.settingsFile,
        defaults = AppSettings(saveDir = paths.downloadDir),
    )
    val tasks = TaskRepository(fileSystem, paths.tasksFile, scope)
    val auth = AuthManager(port, secretStore, paths, fileSystem, appVersion)
    val engine = XunleiEngine(
        port = port,
        auth = auth,
        tasks = tasks,
        settings = settings,
        actions = actions,
        paths = paths,
        fileSystem = fileSystem,
        scope = scope,
        sdkDispatcher = sdkDispatcher,
        ioDispatcher = ioDispatcher,
    )

    val state: StateFlow<AppUiState> = combine(
        auth.auth,
        tasks.tasks,
        settings.settings,
        engine.meta,
    ) { authState, taskList, settingsValue, meta ->
        AppUiState(authState, taskList, settingsValue, meta)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AppUiState(
            auth = auth.auth.value,
            tasks = emptyList(),
            settings = settings.settings.value,
            meta = EngineMeta(initializing = true),
        ),
    )

    val logFile: Path get() = paths.logsDir / "xlite.log"

    @Volatile
    private var started = false

    @Volatile
    private var stopped = false

    init {
        paths.ensureDirectories(fileSystem)
        FileLogSink(fileSystem, paths.logsDir).install()
        AppLog.info("app", "XLite $appVersion 启动")
    }

    suspend fun start() {
        if (started) return
        started = true
        settings.load()
        tasks.load()
        withContext(ioDispatcher) {
            engine.initialize()
        }
    }

    suspend fun shutdown() {
        if (stopped) return
        stopped = true
        runCatching { engine.shutdown() }
            .onFailure { AppLog.warn("app", "关闭引擎失败", it) }
        runCatching { tasks.flush() }
        AppLog.info("app", "XLite 已退出")
        scope.cancel()
    }
}
