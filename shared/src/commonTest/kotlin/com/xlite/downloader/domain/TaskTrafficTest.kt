package com.xlite.downloader.domain

import com.xlite.downloader.domain.model.TaskTraffic
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class TaskTrafficTest {
    @Test fun parsesNativeFieldsAndCalculatesSourceShares() {
        val data = assertNotNull(TaskTraffic.parse("""{"origin_size":600,"p2p_size":300,"p2s_size":100,"dcdn_size":0}"""))
        assertEquals(0.6f, data.fraction(data.originBytes))
        assertEquals(0.3f, data.fraction(data.p2pBytes))
        assertEquals(0.1f, data.fraction(data.p2sBytes))
    }
    @Test fun supportsWrappedJavaFields() {
        assertEquals(TaskTraffic(1, 2, 3), TaskTraffic.parse("""{"traffic":{"originSize":1,"p2pSize":2,"p2sSize":3}}"""))
    }
    @Test fun unavailableOrInvalidStatisticsAreNotShownAsZeroPercent() {
        for (raw in listOf("", "{}", "not json", """{"origin_size":-1,"p2p_size":0,"p2s_size":0}""")) {
            assertNull(TaskTraffic.parse(raw))
        }
        assertEquals(0f, TaskTraffic(0, 0, 0).fraction(0))
    }
    @Test fun largeCountsDoNotOverflowAndSurvivePersistence() {
        val data = TaskTraffic(Long.MAX_VALUE, Long.MAX_VALUE, 0)
        assertEquals(0.5f, data.fraction(data.originBytes))
        assertEquals(data, Json.decodeFromString<TaskTraffic>(Json.encodeToString(data)))
    }
}
