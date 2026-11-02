package com.xlite.downloader

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPlatformActionsTest {
    @Test
    fun existingUnicodeFileIsPassedAsOneCanonicalPath() = runBlocking {
        val directory = Files.createTempDirectory("xlite-open").toFile()
        val file = File(directory, "中文 file #1 & 100%.exe").apply { writeText("fixture only") }
        val launched = mutableListOf<File>()
        val actions = DesktopPlatformActions { launched += it }
        assertTrue(actions.openFile(file.absolutePath.replace('\\', '/')))
        assertEquals(listOf(file.canonicalFile), launched)
    }

    @Test
    fun openingFolderUsesItsActualPath() = runBlocking {
        val directory = Files.createTempDirectory("xlite-folder").toFile()
        val launched = mutableListOf<File>()
        assertTrue(DesktopPlatformActions { launched += it }.openFolder(directory.absolutePath))
        assertEquals(listOf(directory.canonicalFile), launched)
    }

    @Test
    fun missingFileDoesNotReachSystemLauncher() = runBlocking {
        var launched = false
        val missing = Files.createTempDirectory("xlite-missing").resolve("missing.exe")
        assertFalse(DesktopPlatformActions { launched = true }.openFile(missing.toString()))
        assertFalse(launched)
    }

    @Test
    fun shellFailureIsReportedWithoutTreatingTheExistingFileAsMissing() = runBlocking {
        val file = Files.createTempFile("xlite-denied", ".exe").toFile()
        assertFalse(DesktopPlatformActions { error("Access denied") }.openFile(file.absolutePath))
        assertTrue(file.exists())
    }
}
