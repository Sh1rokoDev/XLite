package com.xlite.downloader.util

import okio.FileSystem
import okio.Path

object FileNameUtils {
    private val invalidChars = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val reservedNames = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )
    private const val MAX_NAME_LENGTH = 180

    fun fromUrl(url: String, fallbackTimestamp: Long): String {
        val clean = url.substringBefore('#').substringBefore('?')
        val rawName = percentDecode(clean.substringAfterLast('/'))
        return sanitize(rawName, "download-$fallbackTimestamp")
    }

    fun sanitize(raw: String, fallback: String): String {
        var name = raw.replace(invalidChars, "_").trim().trimEnd('.')
        if (name.isBlank()) name = fallback
        val base = name.substringBeforeLast('.').uppercase()
        if (base in reservedNames) name = "_$name"
        if (name.length > MAX_NAME_LENGTH) {
            val dot = name.lastIndexOf('.')
            val extension = if (dot > 0 && name.length - dot <= 12) name.substring(dot) else ""
            name = name.substring(0, MAX_NAME_LENGTH - extension.length) + extension
        }
        return name.ifBlank { fallback }
    }

    fun uniqueName(
        dir: Path,
        requested: String,
        fileSystem: FileSystem,
        taken: Set<String> = emptySet(),
    ): String {
        if (requested !in taken && !fileSystem.exists(dir / requested)) return requested
        val dot = requested.lastIndexOf('.')
        val extension = if (dot > 0) requested.substring(dot) else ""
        val base = if (dot > 0) requested.substring(0, dot) else requested
        for (index in 1..999) {
            val candidate = "$base ($index)$extension"
            if (candidate !in taken && !fileSystem.exists(dir / candidate)) return candidate
        }
        return requested
    }

    fun percentDecode(value: String): String {
        if (!value.contains('%')) return value
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '%' && index + 2 < value.length) {
                val bytes = ArrayList<Byte>()
                var cursor = index
                while (cursor + 2 < value.length && value[cursor] == '%') {
                    val hex = value.substring(cursor + 1, cursor + 3)
                    val byte = hex.toIntOrNull(16)
                    if (byte == null) break
                    bytes.add(byte.toByte())
                    cursor += 3
                }
                if (bytes.isNotEmpty()) {
                    result.append(bytes.toByteArray().decodeToString())
                    index = cursor
                    continue
                }
            }
            result.append(value[index])
            index++
        }
        return result.toString()
    }
}
