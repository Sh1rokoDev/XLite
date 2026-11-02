package com.xlite.downloader.util

object Redactor {
    private val apiKey = Regex("""xl_[A-Za-z0-9]{12,}""")
    private val jwt = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*""")
    private val secretParam = Regex(
        """(?i)\b(token|key|api_?key|sign|signature|password|authorization)=([^&\s]+)""",
    )

    fun redact(text: String): String = text
        .replace(apiKey, "[REDACTED_KEY]")
        .replace(jwt, "[REDACTED_TOKEN]")
        .replace(secretParam) { match -> "${match.groupValues[1]}=[REDACTED]" }

    fun hostAndPath(url: String): String {
        val schemeEnd = url.indexOf("://")
        val prefix = if (schemeEnd >= 0) url.substring(0, schemeEnd + 3) else ""
        val rest = if (schemeEnd >= 0) url.substring(schemeEnd + 3) else url
        val hostEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
        var host = if (hostEnd >= 0) rest.substring(0, hostEnd) else rest
        val at = host.lastIndexOf('@')
        if (at >= 0) host = host.substring(at + 1)
        val tail = if (hostEnd >= 0) rest.substring(hostEnd) else ""
        val path = tail.substringBefore('?').substringBefore('#')
        return "$prefix$host$path"
    }
}
