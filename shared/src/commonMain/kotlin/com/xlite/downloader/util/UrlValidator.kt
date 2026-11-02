package com.xlite.downloader.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

sealed interface UrlParseResult {
    data class Valid(val url: String, val decodedThunder: Boolean = false) : UrlParseResult
    data class Unsupported(val message: String) : UrlParseResult
    data class Invalid(val message: String) : UrlParseResult
    data object Blank : UrlParseResult
}

object UrlValidator {
    private val httpUrl = Regex("""^https?://[^\s/]+(\S*)$""", RegexOption.IGNORE_CASE)

    fun splitInput(text: String): List<String> = text
        .split(Regex("""[\r\n\t ]+"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    fun parse(raw: String): UrlParseResult {
        val text = raw.trim()
        if (text.isEmpty()) return UrlParseResult.Blank

        if (text.startsWith("thunder://", ignoreCase = true)) {
            val decoded = decodeThunder(text)
                ?: return UrlParseResult.Invalid("无法解析该 thunder:// 链接")
            return if (isCleanHttp(decoded)) {
                UrlParseResult.Valid(decoded, decodedThunder = true)
            } else {
                UrlParseResult.Invalid("thunder:// 解码后不是 HTTP/HTTPS 链接")
            }
        }

        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            return if (isCleanHttp(text)) {
                UrlParseResult.Valid(text)
            } else {
                UrlParseResult.Invalid("链接格式不正确")
            }
        }

        val scheme = text.substringBefore(':').lowercase()
        val reason = when (scheme) {
            "magnet" -> "迅雷下载 SDK 不支持磁力链，仅支持 HTTP/HTTPS"
            "ftp", "ftps", "sftp" -> "不支持 FTP/FTPS/SFTP，仅支持 HTTP/HTTPS"
            "ed2k" -> "迅雷下载 SDK 不支持 ed2k，仅支持 HTTP/HTTPS"
            "file" -> "不支持本地文件链接，仅支持 HTTP/HTTPS"
            else -> "仅支持 HTTP/HTTPS 链接"
        }
        return UrlParseResult.Unsupported(reason)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun isCleanHttp(value: String): Boolean =
        httpUrl.matches(value) && value.none { it.code < 0x20 || it.code == 0x7F }

    private fun decodeThunder(raw: String): String? {
        val payload = raw.removePrefix("thunder://").removePrefix("THUNDER://")
        val decoded = runCatching {
            Base64.Default.decode(payload).decodeToString()
        }.getOrNull() ?: return null
        val trimmed = when {
            decoded.startsWith("AA") && decoded.endsWith("ZZ") && decoded.length > 4 ->
                decoded.substring(2, decoded.length - 2)
            else -> decoded
        }
        return trimmed.takeIf { it.isNotBlank() }
    }
}
