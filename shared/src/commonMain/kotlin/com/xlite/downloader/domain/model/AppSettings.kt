package com.xlite.downloader.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val saveDir: String,
    val appIdOverride: String? = null,
    val concurrentTasks: Int = DEFAULT_CONCURRENT_TASKS,
    val downloadLimitKbps: Int = UNLIMITED,
    val uploadEnabled: Boolean = false,
    val uploadLimitKbps: Int = UNLIMITED,
    val dynamicAcceleration: Boolean = true,
    val dynamicAccelerationAcknowledged: Boolean = false,
    val closeToTray: Boolean = true,
    val notifyOnComplete: Boolean = true,
) {
    fun normalized(): AppSettings = copy(
        appIdOverride = appIdOverride?.trim()?.takeIf { it.isNotEmpty() },
        concurrentTasks = concurrentTasks.coerceIn(MIN_CONCURRENT_TASKS, MAX_CONCURRENT_TASKS),
        downloadLimitKbps = if (downloadLimitKbps <= 0) UNLIMITED else downloadLimitKbps.coerceAtLeast(MIN_DOWNLOAD_LIMIT_KBPS),
        uploadLimitKbps = if (uploadLimitKbps <= 0) UNLIMITED else uploadLimitKbps.coerceAtLeast(MIN_UPLOAD_LIMIT_KBPS),
    )

    companion object {
        const val UNLIMITED = 0
        const val MIN_CONCURRENT_TASKS = 1
        const val MAX_CONCURRENT_TASKS = 10
        const val DEFAULT_CONCURRENT_TASKS = 3
        const val MIN_DOWNLOAD_LIMIT_KBPS = 32
        const val MIN_UPLOAD_LIMIT_KBPS = 16
    }
}

@Serializable
data class SettingsFile(
    val version: Int = 1,
    val settings: AppSettings,
)
