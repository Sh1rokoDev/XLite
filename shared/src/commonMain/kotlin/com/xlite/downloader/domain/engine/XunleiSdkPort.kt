package com.xlite.downloader.domain.engine

interface XunleiSdkPort {
    fun init(appId: String, appVersion: String, cfgPath: String, saveTasks: Boolean): Int
    fun uninit(): Int
    fun login(token: String): LoginResult
    fun getLoginToken(apiKey: String, expiresInSeconds: Int, scopes: List<String>): LoginTokenResult
    fun createP2spTask(url: String, savePath: String, saveName: String): CreateTaskResult
    fun startTask(taskId: Long): Int
    fun stopTask(taskId: Long): Int
    fun deleteTask(taskId: Long, deleteFile: Boolean): Int
    fun getTaskState(taskId: Long): TaskStateResult
    fun getTaskInfo(taskId: Long, infoName: String): TaskInfoResult
    fun getUnfinishedTasks(): TaskListResult
    fun getFinishedTasks(): TaskListResult
    fun setConcurrentTaskCount(count: Int): Int
    fun setDownloadSpeedLimit(kbps: Int): Int
    fun setUploadSpeedLimit(kbps: Int): Int
    fun setUploadEnabled(enabled: Boolean): Int
    fun setDynamicLinkAcceleration(enabled: Boolean): Int
    fun getVersion(): VersionResult
}
