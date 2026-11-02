package com.xlite.downloader.data

import com.xlite.downloader.domain.model.AppSettings
import com.xlite.downloader.domain.model.DesiredState
import com.xlite.downloader.domain.model.DownloadTask
import com.xlite.downloader.domain.model.TaskStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class TaskRepositoryTest {
    private fun task(id: Long, name: String = "file$id.zip") = DownloadTask(
        taskId = id,
        url = "https://example.com/$name",
        saveDir = "/downloads",
        saveName = name,
        createdAt = id,
        desired = DesiredState.RUNNING,
        status = TaskStatus.QUEUED,
    )

    @Test
    fun persistsAndReloadsTasks() = runTest {
        val fs = FakeFileSystem()
        val file = "/data/tasks.json".toPath()
        val first = TaskRepository(fs, file, backgroundScope)
        first.upsert(task(1))
        first.upsert(task(2))
        first.update(1) { it.copy(downloadedBytes = 42, status = TaskStatus.DOWNLOADING) }
        first.flush()

        val second = TaskRepository(fs, file, backgroundScope)
        second.load()
        val loaded = second.snapshot()
        assertEquals(setOf(1L, 2L), loaded.map { it.taskId }.toSet())
        assertEquals(42, loaded.first { it.taskId == 1L }.downloadedBytes)
        assertEquals(TaskStatus.DOWNLOADING, loaded.first { it.taskId == 1L }.status)
    }

    @Test
    fun upsertKeepsNewestTaskFirstAndReplacesById() = runTest {
        val fs = FakeFileSystem()
        val repo = TaskRepository(fs, "/data/tasks.json".toPath(), backgroundScope)
        repo.upsert(task(1))
        repo.upsert(task(2))
        assertEquals(listOf(2L, 1L), repo.snapshot().map { it.taskId })
        repo.upsert(task(1, "renamed.zip"))
        assertEquals(listOf(2L, 1L), repo.snapshot().map { it.taskId })
        assertEquals("renamed.zip", repo.snapshot().last().saveName)
    }

    @Test
    fun removeAndReloadDropsTask() = runTest {
        val fs = FakeFileSystem()
        val file = "/data/tasks.json".toPath()
        val repo = TaskRepository(fs, file, backgroundScope)
        repo.upsert(task(1))
        repo.upsert(task(2))
        repo.remove(1)
        repo.flush()
        val reloaded = TaskRepository(fs, file, backgroundScope)
        reloaded.load()
        assertEquals(listOf(2L), reloaded.snapshot().map { it.taskId })
    }

    @Test
    fun corruptFileIsBackedUpAndRepositoryStartsEmpty() = runTest {
        val fs = FakeFileSystem()
        val file = "/data/tasks.json".toPath()
        fs.createDirectories(file.parent!!)
        fs.write(file) { writeUtf8("{not valid json") }
        val repo = TaskRepository(fs, file, backgroundScope)
        repo.load()
        assertTrue(repo.snapshot().isEmpty())
        assertFalse(fs.exists(file))
        assertTrue(fs.list(file.parent!!).any { it.name.startsWith("tasks.json.corrupt-") })
    }
}

class SettingsRepositoryTest {
    private val defaults = AppSettings(saveDir = "/downloads")

    @Test
    fun startsWithNormalizedDefaults() = runTest {
        val fs = FakeFileSystem()
        val repo = SettingsRepository(fs, "/data/settings.json".toPath(), defaults.copy(concurrentTasks = 50))
        assertEquals(10, repo.settings.value.concurrentTasks)
    }

    @Test
    fun updateNormalizesAndPersists() = runTest {
        val fs = FakeFileSystem()
        val file = "/data/settings.json".toPath()
        val repo = SettingsRepository(fs, file, defaults)
        val updated = repo.update {
            it.copy(
                concurrentTasks = 0,
                downloadLimitKbps = 10,
                uploadEnabled = true,
                uploadLimitKbps = 4,
                dynamicAcceleration = false,
            )
        }
        assertEquals(1, updated.concurrentTasks)
        assertEquals(32, updated.downloadLimitKbps)
        assertEquals(16, updated.uploadLimitKbps)
        assertTrue(updated.uploadEnabled)
        assertFalse(updated.dynamicAcceleration)

        val reloaded = SettingsRepository(fs, file, defaults)
        reloaded.load()
        assertEquals(updated, reloaded.settings.value)
    }

    @Test
    fun unlimitedZeroStaysZero() = runTest {
        val fs = FakeFileSystem()
        val repo = SettingsRepository(fs, "/data/settings.json".toPath(), defaults)
        val updated = repo.update { it.copy(downloadLimitKbps = 0, uploadLimitKbps = -1) }
        assertEquals(0, updated.downloadLimitKbps)
        assertEquals(0, updated.uploadLimitKbps)
    }

    @Test
    fun corruptSettingsFallBackToDefaults() = runTest {
        val fs = FakeFileSystem()
        val file = "/data/settings.json".toPath()
        fs.createDirectories(file.parent!!)
        fs.write(file) { writeUtf8("oops") }
        val repo = SettingsRepository(fs, file, defaults)
        repo.load()
        assertEquals(defaults, repo.settings.value)
        assertTrue(fs.list(file.parent!!).any { it.name.startsWith("settings.json.corrupt-") })
    }

    @Test
    fun unknownKeysAreIgnoredForForwardCompatibility() = runTest {
        val fs = FakeFileSystem()
        val file = "/data/settings.json".toPath()
        fs.createDirectories(file.parent!!)
        fs.write(file) {
            writeUtf8("""{"version":1,"settings":{"saveDir":"/x","concurrentTasks":4,"futureFlag":true}}""")
        }
        val repo = SettingsRepository(fs, file, defaults)
        repo.load()
        assertEquals("/x", repo.settings.value.saveDir)
        assertEquals(4, repo.settings.value.concurrentTasks)
    }
}
