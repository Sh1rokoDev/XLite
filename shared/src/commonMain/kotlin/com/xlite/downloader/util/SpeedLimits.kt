package com.xlite.downloader.util

import kotlin.math.ceil

enum class SpeedUnit(val label: String, val kilobytes: Int) {
    KB("KB/s", 1),
    MB("MB/s", 1024),
    GB("GB/s", 1024 * 1024),
}

object SpeedLimits {
    fun parse(text: String, unit: SpeedUnit): Int? {
        val value = text.trim()
        if (!value.matches(Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) return null
        val kb = (value.toDoubleOrNull() ?: return null) * unit.kilobytes
        if (!kb.isFinite() || kb < 0 || kb > Int.MAX_VALUE) return null
        return ceil(kb).toInt()
    }

    fun preferredUnit(kbps: Int): SpeedUnit = SpeedUnit.entries.reversed()
        .firstOrNull { kbps > 0 && kbps % it.kilobytes == 0 } ?: SpeedUnit.KB

    // Exact decimal division keeps small limits unchanged when switching units.
    fun format(kbps: Int, unit: SpeedUnit): String = buildString {
        val divisor = unit.kilobytes.toLong()
        append(kbps / divisor)
        var remainder = kbps % divisor
        if (remainder != 0L) append('.')
        while (remainder != 0L) {
            remainder *= 10
            append(remainder / divisor)
            remainder %= divisor
        }
    }
}
