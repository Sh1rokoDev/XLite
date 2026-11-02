package com.xlite.downloader

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.xlite.downloader.core.AppLog
import com.xlite.downloader.data.PlatformPaths
import com.xlite.downloader.data.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSecretStore(
    paths: PlatformPaths,
) : SecretStore {
    private val file = File(paths.secretFile.toString())
    private var warnedFallback = false

    override suspend fun readApiKey(): String? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        val bytes = file.readBytes()
        when {
            bytes.startsWith(HEADER_KEYSTORE) -> runCatching { decrypt(bytes) }
                .onFailure { AppLog.warn("secret", "解密本机 API Key 失败", it) }
                .getOrNull()
            bytes.startsWith(HEADER_PLAIN) ->
                bytes.copyOfRange(HEADER_PLAIN.size, bytes.size).toString(Charsets.UTF_8)
            else -> null
        }
    }

    override suspend fun writeApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val encoded = runCatching { encrypt(apiKey) }
            .onFailure { warnFallback(it) }
            .getOrNull()
            ?: (HEADER_PLAIN + apiKey.toByteArray(Charsets.UTF_8))
        file.writeBytes(encoded)
        Unit
    }

    override suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }

    private fun encrypt(apiKey: String): ByteArray {
        val key = obtainKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        return HEADER_KEYSTORE + byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    private fun decrypt(bytes: ByteArray): String? {
        var offset = HEADER_KEYSTORE.size
        if (bytes.size <= offset) return null
        val ivLength = bytes[offset].toInt() and 0xFF
        offset++
        if (bytes.size < offset + ivLength) return null
        val iv = bytes.copyOfRange(offset, offset + ivLength)
        val ciphertext = bytes.copyOfRange(offset + ivLength, bytes.size)
        val key = obtainKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun obtainKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun warnFallback(error: Throwable) {
        if (warnedFallback) return
        warnedFallback = true
        AppLog.warn("secret", "Android Keystore 不可用，已降级为应用私有目录存储", error)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "xlite_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val HEADER_KEYSTORE = "XLAK".toByteArray(Charsets.US_ASCII)
        val HEADER_PLAIN = "XLPL".toByteArray(Charsets.US_ASCII)
    }
}
