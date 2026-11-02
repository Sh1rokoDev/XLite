package com.xlite.downloader.util

import com.xlite.downloader.domain.model.DownloadTask
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant

object Formatters {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    fun bytes(value: Long): String {
        if (value <= 0L) return "0 B"
        var size = value.toDouble()
        var unit = 0
        while (size >= 1024.0 && unit < units.lastIndex) {
            size /= 1024.0
            unit++
        }
        return if (unit == 0 || size >= 100.0) {
            "${size.roundToInt()} ${units[unit]}"
        } else {
            "${oneDecimal(size)} ${units[unit]}"
        }
    }

    fun speed(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0L) "--" else "${bytes(bytesPerSecond)}/s"

    fun percent(task: DownloadTask): String {
        val progress = task.progress ?: return if (task.status == com.xlite.downloader.domain.model.TaskStatus.COMPLETED) "100%" else "--"
        return "${(progress * 100).roundToInt()}%"
    }

    fun eta(task: DownloadTask): String? {
        if (task.totalBytes <= 0L) return null
        val remaining = task.totalBytes - task.downloadedBytes
        if (remaining <= 0L) return null
        if (task.speedBps <= 0L) return null
        return duration(remaining / task.speedBps)
    }

    fun duration(seconds: Long): String {
        if (seconds <= 0L) return "0 秒"
        if (seconds > 359_999L) return "超过 99 小时"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> "${hours} 小时 ${minutes} 分"
            minutes > 0 -> "${minutes} 分 ${secs} 秒"
            else -> "${secs} 秒"
        }
    }

    fun dateTime(epochMillis: Long): String {
        val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${local.year}-${pad(local.monthNumber)}-${pad(local.dayOfMonth)} ${pad(local.hour)}:${pad(local.minute)}"
    }

    fun tokenExpiry(epochSeconds: Long): String {
        val local = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${local.year}-${pad(local.monthNumber)}-${pad(local.dayOfMonth)} ${pad(local.hour)}:${pad(local.minute)}"
    }

    fun remainingUntil(epochSeconds: Long, nowEpochSeconds: Long): String {
        val diff = epochSeconds - nowEpochSeconds
        if (diff <= 0) return "已过期"
        return duration(diff)
    }

    fun host(url: String): String = runCatching {
        val withoutScheme = url.substringAfter("://", url)
        withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfter('@')
            .takeIf { it.isNotBlank() } ?: url
    }.getOrDefault(url)

    fun signedBytes(value: Long): String = if (value < 0) "-${bytes(abs(value))}" else bytes(value)

    private fun pad(value: Int): String = value.toString().padStart(2, '0')

    private fun oneDecimal(value: Double): String {
        val tenths = (value * 10).roundToInt()
        val whole = tenths / 10
        val fraction = abs(tenths % 10)
        return if (fraction == 0) whole.toString() else "$whole.$fraction"
    }
}
