package com.xlite.downloader

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DesktopPathsTest {
    @Test
    fun portableDataAndDownloadsStayInsideThePortableFolder() {
        val root = Files.createTempDirectory("xlite-portable").resolve("中文 portable folder").toFile()
        val paths = DesktopPaths(applicationRoot = root)
        assertEquals(File(root, "data").absolutePath, paths.dataDir.toString())
        assertEquals(File(root, "downloads").absolutePath, paths.downloadDir)
        assertEquals(File(root, "data/tasks.json").absolutePath, paths.tasksFile.toString())
    }

    @Test
    fun installedAndPortableDirectoriesDoNotShareUserData() {
        val root = Files.createTempDirectory("xlite-portable").toFile()
        val installed = DesktopPaths(applicationRoot = File(root, "Program Files/XLite"))
        val portable = DesktopPaths(applicationRoot = File(root, "USB/XLite"))
        assertNotEquals(installed.dataDir, portable.dataDir)
        assertEquals(File(root, "Program Files/XLite/downloads").absolutePath, installed.downloadDir)
    }
}
