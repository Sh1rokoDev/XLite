package com.xlite.downloader.domain

import com.xlite.downloader.domain.engine.ErrorMapper
import com.xlite.downloader.domain.engine.XunleiCodes
import com.xlite.downloader.domain.model.AppSettings
import com.xlite.downloader.domain.model.DesiredState
import com.xlite.downloader.domain.model.DownloadTask
import com.xlite.downloader.domain.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskStatusTest {
    @Test
    fun mapsEverySdkStateCode() {
        val expected = mapOf(
            0 to TaskStatus.UNKNOWN,
            3 to TaskStatus.QUEUED,
            4 to TaskStatus.CONNECTING,
            5 to TaskStatus.DOWNLOADING,
            6 to TaskStatus.PAUSING,
            7 to TaskStatus.PAUSED,
            8 to TaskStatus.COMPLETED,
            9 to TaskStatus.FAILED,
            99 to TaskStatus.UNKNOWN,
        )
        for ((code, status) in expected) {
            assertEquals(status, TaskStatus.fromSdkState(code), "stateCode=$code")
        }
    }

    @Test
    fun terminalAndActiveFlagsAreConsistent() {
        assertTrue(TaskStatus.DOWNLOADING.isActive)
        assertTrue(TaskStatus.PAUSING.isActive)
        assertFalse(TaskStatus.PAUSED.isActive)
        assertTrue(TaskStatus.COMPLETED.isTerminal)
        assertTrue(TaskStatus.FAILED.isTerminal)
        assertFalse(TaskStatus.DOWNLOADING.isTerminal)
    }
}

class AppSettingsTest {
    @Test
    fun normalizesConcurrencyAndLimits() {
        val normalized = AppSettings(
            saveDir = " /tmp ",
            appIdOverride = "  ",
            concurrentTasks = 99,
            downloadLimitKbps = 5,
            uploadLimitKbps = 3,
        ).normalized()
        assertEquals(10, normalized.concurrentTasks)
        assertEquals(32, normalized.downloadLimitKbps)
        assertEquals(16, normalized.uploadLimitKbps)
        assertNull(normalized.appIdOverride)
        assertEquals(" /tmp ", normalized.saveDir)

        val minimum = AppSettings(saveDir = "/x", concurrentTasks = 0, downloadLimitKbps = -5, uploadLimitKbps = 0).normalized()
        assertEquals(1, minimum.concurrentTasks)
        assertEquals(0, minimum.downloadLimitKbps)
        assertEquals(0, minimum.uploadLimitKbps)
    }

    @Test
    fun keepsValidValues() {
        val value = AppSettings(saveDir = "/x", concurrentTasks = 4, downloadLimitKbps = 256, uploadLimitKbps = 64).normalized()
        assertEquals(4, value.concurrentTasks)
        assertEquals(256, value.downloadLimitKbps)
        assertEquals(64, value.uploadLimitKbps)
    }
}

class DownloadTaskTest {
    private fun task(total: Long, downloaded: Long, dir: String = "/downloads/") = DownloadTask(
        taskId = 1,
        url = "https://example.com/a.zip",
        saveDir = dir,
        saveName = "a.zip",
        createdAt = 0,
        totalBytes = total,
        downloadedBytes = downloaded,
    )

    @Test
    fun progressIsClampedAndNullWhenSizeUnknown() {
        assertEquals(0.5f, task(100, 50).progress)
        assertEquals(1f, task(100, 200).progress)
        assertNull(task(0, 0).progress)
    }

    @Test
    fun displayPathJoinsDirectoryAndName() {
        assertEquals("/downloads/a.zip", task(1, 0).displayPath)
        assertEquals("C:\\dl/a.zip", task(1, 0, "C:\\dl").displayPath)
    }
}

class ErrorMapperTest {
    @Test
    fun mapsKnownBusinessCodes() {
        assertTrue(ErrorMapper.messageFor(XunleiCodes.SUCCESS).contains("成功"))
        assertTrue(ErrorMapper.messageFor(XunleiCodes.APPNAME_APPKEY_ERROR).contains("API Key"))
        assertTrue(ErrorMapper.messageFor(XunleiCodes.TASK_FILENAME_CONFLICT).contains("文件名"))
        assertTrue(ErrorMapper.messageFor(XunleiCodes.NO_DATA_PIPE).contains("网络"))
        assertTrue(ErrorMapper.messageFor(XunleiCodes.COPYRIGHT_BLOCKING).contains("版权"))
        assertTrue(ErrorMapper.messageFor(XunleiCodes.SPEED_LIMIT_TO_SMALL).contains("32"))
    }

    @Test
    fun fallsBackToNumericCode() {
        val message = ErrorMapper.messageFor(424242)
        assertTrue(message.contains("424242"))
    }

    @Test
    fun classifiesAuthAndStopCodes() {
        assertTrue(XunleiCodes.isAuthError(XunleiCodes.APPNAME_APPKEY_ERROR))
        assertTrue(XunleiCodes.isAuthError(XunleiCodes.AUTH_SESSION_ID_EXPIRED))
        assertFalse(XunleiCodes.isAuthError(XunleiCodes.DISK_FULL))
        assertTrue(XunleiCodes.isAlreadyStopped(XunleiCodes.TASK_ALREADY_STOPPED))
        assertTrue(XunleiCodes.isAlreadyStopped(XunleiCodes.TASK_NOT_RUNNING))
        assertFalse(XunleiCodes.isAlreadyStopped(XunleiCodes.TASK_STILL_RUNNING))
        assertTrue(XunleiCodes.isTaskMissing(XunleiCodes.TASK_NOT_EXIST))
    }
}

class DesiredStateTest {
    @Test
    fun hasExpectedValues() {
        assertEquals(
            listOf("RUNNING", "PAUSED", "NONE"),
            DesiredState.entries.map { it.name },
        )
    }
}
