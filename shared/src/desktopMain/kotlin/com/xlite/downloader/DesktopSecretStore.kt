package com.xlite.downloader

import com.xlite.downloader.core.AppLog
import com.xlite.downloader.data.PlatformPaths
import com.xlite.downloader.data.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class DesktopSecretStore(
    private val paths: PlatformPaths,
) : SecretStore {
    private val file = File(paths.secretFile.toString())
    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("windows")
    private var warnedFallback = false

    override suspend fun readApiKey(): String? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        val bytes = file.readBytes()
        decode(bytes)
    }

    override suspend fun writeApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val encoded = encode(apiKey)
        file.writeBytes(encoded)
        restrictPermissions()
        Unit
    }

    override suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }

    private fun encode(apiKey: String): ByteArray {
        if (isWindows) {
            val protected = runCatching { protect(apiKey.toByteArray(Charsets.UTF_8)) }
                .onFailure { warnFallback(it) }
                .getOrNull()
            if (protected != null) return HEADER_DPAPI + protected
        }
        return HEADER_PLAIN + apiKey.toByteArray(Charsets.UTF_8)
    }

    private fun decode(bytes: ByteArray): String? = when {
        bytes.startsWith(HEADER_DPAPI) -> runCatching {
            unprotect(bytes.copyOfRange(HEADER_DPAPI.size, bytes.size)).toString(Charsets.UTF_8)
        }.onFailure { AppLog.warn("secret", "解密本机 API Key 失败", it) }.getOrNull()
        bytes.startsWith(HEADER_PLAIN) ->
            bytes.copyOfRange(HEADER_PLAIN.size, bytes.size).toString(Charsets.UTF_8)
        else -> runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
    }

    private fun protect(data: ByteArray): ByteArray =
        com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(data, null, 0, null, null)

    private fun unprotect(data: ByteArray): ByteArray =
        com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(data, null, 0, null)

    private fun restrictPermissions() {
        if (isWindows) return
        runCatching {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }.onFailure { AppLog.warn("secret", "设置密钥文件权限失败", it) }
    }

    private fun warnFallback(error: Throwable) {
        if (warnedFallback) return
        warnedFallback = true
        AppLog.warn("secret", "Windows 凭据加密不可用，已降级为仅权限保护的本地文件", error)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }

    private companion object {
        val HEADER_DPAPI = "XLDP".toByteArray(Charsets.US_ASCII)
        val HEADER_PLAIN = "XLPL".toByteArray(Charsets.US_ASCII)
    }
}
