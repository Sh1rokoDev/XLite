package com.xlite.downloader.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeedLimitsTest {
    @Test
    fun supportsAllUnitsAndDecimalInput() {
        assertEquals(32, SpeedLimits.parse("32", SpeedUnit.KB))
        assertEquals(1536, SpeedLimits.parse("1.5", SpeedUnit.MB))
        assertEquals(1048576, SpeedLimits.parse("1", SpeedUnit.GB))
        assertEquals(103, SpeedLimits.parse("0.1", SpeedUnit.MB))
        assertEquals(0, SpeedLimits.parse("0", SpeedUnit.GB))
    }

    @Test
    fun rejectsInvalidAndOverflowingLimits() {
        listOf("", "-1", "NaN", "Infinity", "1.2.3", "9999999999999999999999999").forEach {
            assertNull(SpeedLimits.parse(it, SpeedUnit.KB))
        }
        assertNull(SpeedLimits.parse("2048", SpeedUnit.GB))
    }

    @Test
    fun switchingUnitsPreservesExactStoredLimit() {
        listOf(0, 1, 32, 1536, Int.MAX_VALUE).forEach { value ->
            SpeedUnit.entries.forEach { unit ->
                assertEquals(value, SpeedLimits.parse(SpeedLimits.format(value, unit), unit))
            }
        }
    }
}
