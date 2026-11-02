package com.xlite.downloader

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XLiteApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val paths = AndroidPaths(this)
        val actions = AndroidPlatformActions(this)
        actions.ensureChannel()
        container = AppContainer(
            paths = paths,
            secretStore = AndroidSecretStore(paths),
            port = AndroidXunleiSdkPort(),
            actions = actions,
            appVersion = appVersion(),
        )
        appScope.launch { container.start() }
        appScope.launch {
            var lastActive = false
            container.state.collect { state ->
                val active = state.activeCount > 0
                if (active && !lastActive) DownloadForegroundService.start(this@XLiteApplication)
                lastActive = active
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "0.1.0" }
}
