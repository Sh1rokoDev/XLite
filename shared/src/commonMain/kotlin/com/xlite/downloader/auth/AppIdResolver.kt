package com.xlite.downloader.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The SDK's init() expects the JWT `aud` claim verbatim: it is itself a Base64
 * string that decodes to the printable application id followed by binary data.
 */
object AppIdResolver {
    @OptIn(ExperimentalEncodingApi::class)
    fun fromAudience(audience: String?): String? {
        if (audience.isNullOrBlank()) return null
        val normalized = audience.trim().replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        val bytes = runCatching { Base64.Default.decode(padded) }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        val prefixBytes = bytes.copyOfRange(0, bytes.indexOfFirst { it == 0.toByte() }.let { if (it >= 0) it else bytes.size })
        if (prefixBytes.isEmpty()) return null
        if (prefixBytes.any { it.toInt() < 0x20 || it.toInt() > 0x7e }) return null
        return Base64.Default.encode(bytes)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun displayName(appId: String): String? {
        val padded = appId.trim().let { it + "=".repeat((4 - it.length % 4) % 4) }
        val bytes = runCatching { Base64.Default.decode(padded) }.getOrNull() ?: return null
        val end = bytes.indexOfFirst { it == 0.toByte() }.let { if (it >= 0) it else bytes.size }
        if (end <= 0) return null
        return bytes.copyOfRange(0, end).decodeToString().takeIf { it.isNotBlank() }
    }
}
