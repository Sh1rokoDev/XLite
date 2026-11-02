package com.xlite.downloader

import com.xlite.downloader.domain.engine.CreateTaskResult
import com.xlite.downloader.domain.engine.LoginResult
import com.xlite.downloader.domain.engine.LoginTokenResult
import com.xlite.downloader.domain.engine.TaskInfoResult
import com.xlite.downloader.domain.engine.TaskListResult
import com.xlite.downloader.domain.engine.TaskStateResult
import com.xlite.downloader.domain.engine.VersionResult
import com.xlite.downloader.domain.engine.XunleiSdkPort
import com.xunlei.open.dlsdk.XLDownloadAPI

class DesktopXunleiSdkPort : XunleiSdkPort {
    override fun init(appId: String, appVersion: String, cfgPath: String, saveTasks: Boolean): Int =
        XLDownloadAPI.init(appId, appVersion, cfgPath, saveTasks)

    override fun uninit(): Int = XLDownloadAPI.uninit()

    override fun login(token: String): LoginResult {
        val result = XLDownloadAPI.login(token)
        return LoginResult(result.result, result.sessionId.orEmpty())
    }

    override fun getLoginToken(apiKey: String, expiresInSeconds: Int, scopes: List<String>): LoginTokenResult {
        val result = XLDownloadAPI.getLoginToken(apiKey, expiresInSeconds, scopes.toTypedArray())
        return LoginTokenResult(
            code = result.code,
            token = result.token,
            expiresIn = result.expiresIn,
            message = result.message.orEmpty(),
        )
    }

    override fun createP2spTask(url: String, savePath: String, saveName: String): CreateTaskResult {
        val result = XLDownloadAPI.createP2spTask(url, savePath, saveName)
        return CreateTaskResult(result.result, result.taskId)
    }

    override fun startTask(taskId: Long): Int = XLDownloadAPI.startTask(taskId)

    override fun stopTask(taskId: Long): Int = XLDownloadAPI.stopTask(taskId)

    override fun deleteTask(taskId: Long, deleteFile: Boolean): Int = XLDownloadAPI.deleteTask(taskId, deleteFile)

    override fun getTaskState(taskId: Long): TaskStateResult {
        val result = XLDownloadAPI.getTaskState(taskId)
        val state = result.state
        return TaskStateResult(
            result = result.result,
            stateCode = state?.stateCode ?: 0,
            speedBps = state?.speed ?: 0L,
            totalBytes = state?.totalSize ?: 0L,
            downloadedBytes = state?.downloadedSize ?: 0L,
            taskErrCode = state?.taskErrCode ?: 0,
            tokenErrCode = state?.taskTokenErr ?: 0,
        )
    }

    override fun getTaskInfo(taskId: Long, infoName: String): TaskInfoResult {
        val result = XLDownloadAPI.getTaskInfo(taskId, infoName)
        return TaskInfoResult(result.result, result.value.orEmpty())
    }

    override fun getUnfinishedTasks(): TaskListResult {
        val result = XLDownloadAPI.getUnfinishedTasks()
        return TaskListResult(result.result, result.taskIds?.toList().orEmpty())
    }

    override fun getFinishedTasks(): TaskListResult {
        val result = XLDownloadAPI.getFinishedTasks()
        return TaskListResult(result.result, result.taskIds?.toList().orEmpty())
    }

    override fun setConcurrentTaskCount(count: Int): Int = XLDownloadAPI.setConcurrentTaskCount(count)

    override fun setDownloadSpeedLimit(kbps: Int): Int = XLDownloadAPI.setDownloadSpeedLimit(kbps)

    override fun setUploadSpeedLimit(kbps: Int): Int = XLDownloadAPI.setUploadSpeedLimit(kbps)

    override fun setUploadEnabled(enabled: Boolean): Int = XLDownloadAPI.setUploadEnabled(enabled)

    override fun setDynamicLinkAcceleration(enabled: Boolean): Int =
        XLDownloadAPI.setDynamicLinkAcceleration(enabled)

    override fun getVersion(): VersionResult {
        val result = XLDownloadAPI.getVersion()
        return VersionResult(result.result, result.value.orEmpty())
    }
}
