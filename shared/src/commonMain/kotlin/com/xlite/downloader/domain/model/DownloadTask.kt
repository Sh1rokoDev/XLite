package com.xlite.downloader.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DownloadTask(
    val taskId: Long,
    val url: String,
    val saveDir: String,
    val saveName: String,
    val createdAt: Long,
    val desired: DesiredState = DesiredState.RUNNING,
    val status: TaskStatus = TaskStatus.QUEUED,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val speedBps: Long = 0,
    val errorCode: Int = 0,
    val errorMessage: String? = null,
    val completedAt: Long? = null,
    val completionNotified: Boolean = false,
    val importedFromSdk: Boolean = false,
    val traffic: TaskTraffic? = null,
) {
    val id: String get() = taskId.toString()

    val progress: Float?
        get() = if (totalBytes > 0) {
            (downloadedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
        } else {
            null
        }

    val displayPath: String
        get() = "${saveDir.trimEnd('/', '\\')}/$saveName"
}

@Serializable
data class TaskStoreFile(
    val version: Int = 1,
    val tasks: List<DownloadTask> = emptyList(),
)
