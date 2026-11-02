package com.xlite.downloader.testutil

import com.xlite.downloader.data.MemorySecretStore
import com.xlite.downloader.data.PlatformPaths
import com.xlite.downloader.domain.engine.CreateTaskResult
import com.xlite.downloader.domain.engine.LoginResult
import com.xlite.downloader.domain.engine.LoginTokenResult
import com.xlite.downloader.domain.engine.TaskInfoResult
import com.xlite.downloader.domain.engine.TaskListResult
import com.xlite.downloader.domain.engine.TaskStateResult
import com.xlite.downloader.domain.engine.VersionResult
import com.xlite.downloader.domain.engine.XunleiCodes
import com.xlite.downloader.domain.engine.XunleiSdkPort
import com.xlite.downloader.platform.PlatformActions
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import okio.Path
import okio.Path.Companion.toPath

object TestJwt {
    @OptIn(ExperimentalEncodingApi::class)
    fun create(audience: String?, expiresAt: Long): String {
        val payload = buildString {
            append("{\"exp\":").append(expiresAt)
            if (audience != null) append(",\"aud\":\"").append(audience).append('"')
            append('}')
        }
        val body = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode(payload.encodeToByteArray())
        return "eyJhbGciOiJIUzI1NiJ9.$body.signature"
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun audienceFor(appId: String): String {
        val bytes = appId.encodeToByteArray() + byteArrayOf(0, 0, 0, 1, 2, 3)
        return Base64.Default.encode(bytes)
    }
}

class FakePlatformPaths(override val dataDir: Path) : PlatformPaths {
    override val downloadDir: String = "/downloads"
}

class FakePlatformActions : PlatformActions {
    override val canPickDirectory: Boolean = true
    override val canOpenFolder: Boolean = true
    override val canShareFile: Boolean = false
    val notifications = mutableListOf<Pair<Long, String>>()
    val openedFiles = mutableListOf<String>()
    val openedFolders = mutableListOf<String>()

    override suspend fun pickDirectory(current: String): String? = current
    override suspend fun openFolder(path: String): Boolean { openedFolders += path; return true }
    override suspend fun openFile(path: String): Boolean { openedFiles += path; return true }
    override suspend fun shareFile(path: String): Boolean = true
    override suspend fun openUrl(url: String): Boolean = true
    override suspend fun notifyCompleted(taskId: Long, name: String, path: String) {
        notifications += taskId to name
    }
}

class FakeXunleiSdkPort(
    private val tokenFor: (String) -> String? = { key -> TestJwt.create(TestJwt.audienceFor("xl-test"), nowSeconds() + 86_400) },
) : XunleiSdkPort {
    var loginTokenCode: Int = 0
    var initResult: Int = XunleiCodes.SUCCESS
    var loginResult: Int = XunleiCodes.SUCCESS
    var createResult: Int = XunleiCodes.SUCCESS
    var startResult: Int = XunleiCodes.SUCCESS
    val startResultByTask: MutableMap<Long, Int> = mutableMapOf()
    var stopResult: Int = XunleiCodes.SUCCESS
    var deleteResult: Int = XunleiCodes.SUCCESS
    var version: String = "12.0.0-test"

    var initArgs: Triple<String, String, String>? = null
    var lastApiKey: String? = null
    val startedTasks = mutableListOf<Long>()
    val stoppedTasks = mutableListOf<Long>()
    val deletedTasks = mutableListOf<Long>()
    val createdTasks = mutableListOf<CreateTaskResult>()
    val createdRequests = mutableListOf<Triple<String, String, String>>()
    val states = mutableMapOf<Long, TaskStateResult>()
    val trafficInfos = mutableMapOf<Long, String>()
    val infos = mutableMapOf<Long, MutableMap<String, String>>()
    var unfinishedIds: List<Long> = emptyList()
    var finishedIds: List<Long> = emptyList()

    private var nextTaskId = 100L

    override fun init(appId: String, appVersion: String, cfgPath: String, saveTasks: Boolean): Int {
        initArgs = Triple(appId, appVersion, cfgPath)
        return initResult
    }

    override fun uninit(): Int = XunleiCodes.SUCCESS

    override fun login(token: String): LoginResult = LoginResult(loginResult, "session")

    override fun getLoginToken(apiKey: String, expiresInSeconds: Int, scopes: List<String>): LoginTokenResult {
        lastApiKey = apiKey
        if (loginTokenCode != 0) {
            return LoginTokenResult(loginTokenCode, null, 0, "API Key 无效")
        }
        val token = tokenFor(apiKey) ?: return LoginTokenResult(0, null, 0, "")
        return LoginTokenResult(0, token, expiresInSeconds, "ok")
    }

    override fun createP2spTask(url: String, savePath: String, saveName: String): CreateTaskResult {
        createdRequests += Triple(url, savePath, saveName)
        if (createResult != XunleiCodes.SUCCESS) return CreateTaskResult(createResult, 0)
        val result = CreateTaskResult(XunleiCodes.SUCCESS, nextTaskId++)
        createdTasks += result
        return result
    }

    override fun startTask(taskId: Long): Int {
        startedTasks += taskId
        return startResultByTask[taskId] ?: startResult
    }

    override fun stopTask(taskId: Long): Int {
        stoppedTasks += taskId
        return stopResult
    }

    override fun deleteTask(taskId: Long, deleteFile: Boolean): Int {
        deletedTasks += taskId
        return deleteResult
    }

    override fun getTaskState(taskId: Long): TaskStateResult = states[taskId]
        ?: TaskStateResult(XunleiCodes.SUCCESS, 5, 1024, 8192, 4096, 0, 0)

    override fun getTaskInfo(taskId: Long, infoName: String): TaskInfoResult {
        if (infoName == "traffic") return TaskInfoResult(XunleiCodes.SUCCESS, trafficInfos[taskId].orEmpty())
        val value = infos[taskId]?.get(infoName)
        return if (value == null) TaskInfoResult(XunleiCodes.SUCCESS, "") else {
            TaskInfoResult(XunleiCodes.SUCCESS, "{\"$infoName\":\"$value\"}")
        }
    }

    override fun getUnfinishedTasks(): TaskListResult = TaskListResult(XunleiCodes.SUCCESS, unfinishedIds)

    override fun getFinishedTasks(): TaskListResult = TaskListResult(XunleiCodes.SUCCESS, finishedIds)

    override fun setConcurrentTaskCount(count: Int): Int = XunleiCodes.SUCCESS
    override fun setDownloadSpeedLimit(kbps: Int): Int = XunleiCodes.SUCCESS
    override fun setUploadSpeedLimit(kbps: Int): Int = XunleiCodes.SUCCESS
    override fun setUploadEnabled(enabled: Boolean): Int = XunleiCodes.SUCCESS
    override fun setDynamicLinkAcceleration(enabled: Boolean): Int = XunleiCodes.SUCCESS
    override fun getVersion(): VersionResult = VersionResult(XunleiCodes.SUCCESS, version)
}

internal fun nowSeconds(): Long = kotlin.time.Clock.System.now().epochSeconds

internal fun dataRoot(): Path = "/data".toPath()

internal fun memoryStore(key: String = "xl_testkey_1234567890") = MemorySecretStore(key)
