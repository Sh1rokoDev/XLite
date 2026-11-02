package com.xlite.downloader.platform

interface PlatformActions {
    val canPickDirectory: Boolean
    val canOpenFolder: Boolean
    val canShareFile: Boolean

    suspend fun pickDirectory(current: String): String?
    suspend fun openFolder(path: String): Boolean
    suspend fun openFile(path: String): Boolean
    suspend fun shareFile(path: String): Boolean
    suspend fun openUrl(url: String): Boolean
    suspend fun notifyCompleted(taskId: Long, name: String, path: String)
}
