package com.xlite.downloader.domain.engine

import com.xlite.downloader.auth.AppIdResolver
import com.xlite.downloader.auth.JwtParser
import com.xlite.downloader.core.AppLog
import com.xlite.downloader.data.PlatformPaths
import com.xlite.downloader.data.SecretStore
import com.xlite.downloader.domain.model.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import okio.FileSystem

class AuthManager(
    private val port: XunleiSdkPort,
    private val secretStore: SecretStore,
    private val paths: PlatformPaths,
    private val fileSystem: FileSystem,
    private val appVersion: String,
) {
    private val mutex = Mutex()
    private val _auth = MutableStateFlow<AuthState>(AuthState.NotConfigured)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    var apiKey: String? = null
        private set
    var appId: String? = null
        private set
    var token: String? = null
        private set
    var tokenExpiresAtEpochSeconds: Long? = null
        private set

    fun isReady(): Boolean = _auth.value is AuthState.Ready && token != null && appId != null

    suspend fun initialize(appIdOverride: String?): Boolean = mutex.withLock {
        val key = runCatching { secretStore.readApiKey() }
            .getOrElse {
                _auth.value = AuthState.Error("读取本机 API Key 失败：${it.message ?: "未知错误"}")
                return@withLock false
            }
            ?.trim()
            .orEmpty()
        if (key.isEmpty()) {
            _auth.value = AuthState.NotConfigured
            return@withLock false
        }
        authenticateLocked(key, appIdOverride)
    }

    suspend fun authenticate(apiKey: String, appIdOverride: String?): Boolean = mutex.withLock {
        val normalized = apiKey.trim()
        if (normalized.isEmpty()) {
            _auth.value = AuthState.Error("请输入 API Key")
            return@withLock false
        }
        authenticateLocked(normalized, appIdOverride)
    }

    suspend fun testConnection(apiKey: String, appIdOverride: String?): ConnectionTestResult = mutex.withLock {
        val normalized = apiKey.trim()
        if (normalized.isEmpty()) {
            return@withLock ConnectionTestResult(false, null, null, "请输入 API Key")
        }
        val tokenResult = runCatching {
            port.getLoginToken(normalized, TOKEN_LIFETIME_SECONDS, SCOPES)
        }.getOrElse {
            return@withLock ConnectionTestResult(false, null, null, "无法连接登录服务：${it.message ?: "网络错误"}")
        }
        if (tokenResult.code != 0 || tokenResult.token.isNullOrBlank()) {
            val message = tokenResult.message.ifBlank { "API Key 无效或登录服务不可用" }
            return@withLock ConnectionTestResult(false, null, null, message)
        }
        val jwt = JwtParser.parse(tokenResult.token)
        val derived = AppIdResolver.fromAudience(jwt?.audience)
        val resolved = appIdOverride?.trim()?.takeIf { it.isNotEmpty() } ?: derived
        if (resolved.isNullOrBlank()) {
            return@withLock ConnectionTestResult(
                false,
                null,
                null,
                "已获取 token，但无法自动识别 App ID，请展开高级选项手动填写",
            )
        }
        val expiry = jwt?.expiresAtEpochSeconds
            ?: (nowSeconds() + tokenResult.expiresIn.takeIf { it > 0 }?.toLong().let { it ?: TOKEN_LIFETIME_SECONDS.toLong() })
        return@withLock ConnectionTestResult(true, resolved, expiry, "连接成功，App ID：$resolved")
    }

    suspend fun refresh(force: Boolean): Boolean = mutex.withLock {
        val key = apiKey ?: return@withLock false
        val expiresAt = tokenExpiresAtEpochSeconds ?: 0L
        if (!force && expiresAt - nowSeconds() > REFRESH_MARGIN_SECONDS) return@withLock true

        val tokenResult = runCatching {
            port.getLoginToken(key, TOKEN_LIFETIME_SECONDS, SCOPES)
        }.getOrElse {
            AppLog.warn("auth", "刷新 token 失败", it)
            return@withLock false
        }
        if (tokenResult.code != 0 || tokenResult.token.isNullOrBlank()) {
            AppLog.warn("auth", "刷新 token 被拒绝：${tokenResult.message}")
            return@withLock false
        }
        val login = runCatching { port.login(tokenResult.token) }.getOrElse {
            AppLog.warn("auth", "重新登录失败", it)
            return@withLock false
        }
        if (login.result != 0) {
            AppLog.warn("auth", "重新登录被拒绝：${ErrorMapper.messageFor(login.result)}")
            return@withLock false
        }
        val jwt = JwtParser.parse(tokenResult.token)
        token = tokenResult.token
        tokenExpiresAtEpochSeconds = jwt?.expiresAtEpochSeconds
            ?: (nowSeconds() + tokenResult.expiresIn.takeIf { it > 0 }?.toLong().let { it ?: TOKEN_LIFETIME_SECONDS.toLong() })
        _auth.value = AuthState.Ready(appId.orEmpty(), tokenExpiresAtEpochSeconds ?: 0L)
        AppLog.info("auth", "token 已刷新")
        return@withLock true
    }

    suspend fun clearStoredKey() {
        mutex.withLock {
            runCatching { secretStore.clearApiKey() }
                .onFailure { AppLog.warn("auth", "清除本机 API Key 失败", it) }
            apiKey = null
            appId = null
            token = null
            tokenExpiresAtEpochSeconds = null
            _auth.value = AuthState.NotConfigured
        }
    }

    fun markExpired(message: String) {
        _auth.value = AuthState.Expired(message)
    }

    fun secondsUntilExpiry(): Long? = tokenExpiresAtEpochSeconds?.let { it - nowSeconds() }

    private suspend fun authenticateLocked(key: String, appIdOverride: String?): Boolean {
        _auth.value = AuthState.Validating
        fileSystem.createDirectories(paths.cfgDir)

        val tokenResult = runCatching {
            port.getLoginToken(key, TOKEN_LIFETIME_SECONDS, SCOPES)
        }.getOrElse {
            _auth.value = AuthState.Error("获取登录 token 失败：${it.message ?: "网络错误"}")
            return false
        }
        if (tokenResult.code != 0) {
            _auth.value = AuthState.Error(tokenResult.message.ifBlank { "API Key 校验失败" })
            return false
        }
        val loginToken = tokenResult.token
        if (loginToken.isNullOrBlank()) {
            _auth.value = AuthState.Error("登录服务未返回 token")
            return false
        }

        val jwt = JwtParser.parse(loginToken)
        val derivedAppId = AppIdResolver.fromAudience(jwt?.audience)
        val resolvedAppId = appIdOverride?.trim()?.takeIf { it.isNotEmpty() } ?: derivedAppId
        if (resolvedAppId.isNullOrBlank()) {
            _auth.value = AuthState.Error("无法自动识别 App ID，请在设置的高级选项中填写")
            return false
        }

        val initResult = runCatching {
            port.init(resolvedAppId, appVersion, paths.cfgDir.toString(), true)
        }.getOrElse {
            _auth.value = AuthState.Error("初始化下载引擎失败：${it.message ?: "未知错误"}")
            return false
        }
        if (initResult != XunleiCodes.SUCCESS && initResult != XunleiCodes.ALREADY_INIT) {
            _auth.value = AuthState.Error(ErrorMapper.messageFor(initResult))
            return false
        }

        val loginResult = runCatching { port.login(loginToken) }.getOrElse {
            _auth.value = AuthState.Error("登录下载引擎失败：${it.message ?: "未知错误"}")
            return false
        }
        if (loginResult.result != XunleiCodes.SUCCESS) {
            _auth.value = AuthState.Error(ErrorMapper.messageFor(loginResult.result))
            return false
        }

        val expiry = jwt?.expiresAtEpochSeconds
            ?: (nowSeconds() + tokenResult.expiresIn.takeIf { it > 0 }?.toLong().let { it ?: TOKEN_LIFETIME_SECONDS.toLong() })
        apiKey = key
        appId = resolvedAppId
        token = loginToken
        tokenExpiresAtEpochSeconds = expiry
        withContext(NonCancellable) {
            runCatching { secretStore.writeApiKey(key) }
                .onFailure { AppLog.warn("auth", "保存本机 API Key 失败", it) }
        }
        _auth.value = AuthState.Ready(resolvedAppId, expiry)
        AppLog.info("auth", "登录成功")
        return true
    }

    private fun nowSeconds(): Long = Clock.System.now().epochSeconds

    companion object {
        private const val TOKEN_LIFETIME_SECONDS = 86_400
        private const val REFRESH_MARGIN_SECONDS = 30L * 60L
        private val SCOPES = listOf("xtask.p2sp", "xindex.query")
    }
}
