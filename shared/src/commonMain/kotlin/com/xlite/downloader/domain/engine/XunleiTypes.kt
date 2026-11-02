package com.xlite.downloader.domain.engine

data class TaskStateResult(
    val result: Int,
    val stateCode: Int,
    val speedBps: Long,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val taskErrCode: Int,
    val tokenErrCode: Int,
)

data class TaskInfoResult(
    val result: Int,
    val value: String,
)

data class CreateTaskResult(
    val result: Int,
    val taskId: Long,
)

data class LoginResult(
    val result: Int,
    val sessionId: String,
)

data class LoginTokenResult(
    val code: Int,
    val token: String?,
    val expiresIn: Int,
    val message: String,
)

data class TaskListResult(
    val result: Int,
    val taskIds: List<Long>,
)

data class VersionResult(
    val result: Int,
    val version: String,
)

data class ConnectionTestResult(
    val success: Boolean,
    val appId: String?,
    val tokenExpiresAtEpochSeconds: Long?,
    val message: String,
)
