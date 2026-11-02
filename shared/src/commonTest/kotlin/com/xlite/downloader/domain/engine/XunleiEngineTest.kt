package com.xlite.downloader.domain.engine

import com.xlite.downloader.data.MemorySecretStore
import com.xlite.downloader.data.SettingsRepository
import com.xlite.downloader.data.TaskRepository
import com.xlite.downloader.domain.model.AppSettings
import com.xlite.downloader.domain.model.DesiredState
import com.xlite.downloader.domain.model.DownloadTask
import com.xlite.downloader.domain.model.TaskStatus
import com.xlite.downloader.testutil.FakePlatformActions
import com.xlite.downloader.testutil.FakePlatformPaths
import com.xlite.downloader.testutil.FakeXunleiSdkPort
import com.xlite.downloader.testutil.dataRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import okio.fakefilesystem.FakeFileSystem

@OptIn(ExperimentalCoroutinesApi::class)
class XunleiEngineTest {
    private class Fixture(
        val port: FakeXunleiSdkPort,
        val tasks: TaskRepository,
        val engine: XunleiEngine,
        val actions: FakePlatformActions,
    )

    private suspend fun TestScope.fixture(
        port: FakeXunleiSdkPort = FakeXunleiSdkPort(),
        withAuth: Boolean = true,
    ): Fixture {
        val fs = FakeFileSystem()
        val paths = FakePlatformPaths(dataRoot())
        val settings = SettingsRepository(fs, paths.settingsFile, AppSettings(saveDir = "/downloads"))
        val tasks = TaskRepository(fs, paths.tasksFile, backgroundScope)
        val store = MemorySecretStore(if (withAuth) "xl_testkey_1234567890" else null)
        val auth = AuthManager(port, store, paths, fs, "test")
        if (withAuth) {
            check(auth.authenticate("xl_testkey_1234567890", null))
        }
        val actions = FakePlatformActions()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = XunleiEngine(
            port = port,
            auth = auth,
            tasks = tasks,
            settings = settings,
            actions = actions,
            paths = paths,
            fileSystem = fs,
            scope = backgroundScope,
            sdkDispatcher = dispatcher,
            ioDispatcher = dispatcher,
        )
        return Fixture(port, tasks, engine, actions)
    }

    @Test
    fun addTasksCreatesStartsAndPersists() = runTest {
        val f = fixture()
        val outcome = f.engine.addTasks("https://example.com/a.zip", "/downloads", null)
        assertEquals(1, outcome.added)
        assertTrue(outcome.failures.isEmpty())
        val task = f.tasks.snapshot().single()
        assertEquals(100, task.taskId)
        assertEquals(TaskStatus.QUEUED, task.status)
        assertEquals(DesiredState.RUNNING, task.desired)
        assertEquals(listOf(100L), f.port.startedTasks)
        assertEquals(
            Triple("https://example.com/a.zip", "/downloads", "a.zip"),
            f.port.createdRequests.single(),
        )
    }

    @Test
    fun addTasksRejectsUnsupportedAndDuplicateInputs() = runTest {
        val f = fixture()
        val outcome = f.engine.addTasks(
            """
            https://example.com/a.zip
            https://example.com/a.zip
            ftp://example.com/b.zip
            magnet:?xt=urn:btih:abc
            not-a-url
            """.trimIndent(),
            "/downloads",
            null,
        )
        assertEquals(1, outcome.added)
        assertEquals(4, outcome.failures.size)
        assertTrue(outcome.failures.any { it.reason.contains("重复") })
        assertTrue(outcome.failures.any { it.reason.contains("HTTP") })
        assertEquals(1, f.port.startedTasks.size)
    }

    @Test
    fun addTasksRejectsSameUrlAndDirectoryAcrossCalls() = runTest {
        val f = fixture()
        assertEquals(1, f.engine.addTasks("https://example.com/a.zip", "/downloads", null).added)
        val again = f.engine.addTasks("https://example.com/a.zip", "/downloads", null)
        assertEquals(0, again.added)
        assertTrue(again.failures.single().reason.contains("已存在"))
        assertEquals(1, f.tasks.snapshot().size)
    }

    @Test
    fun differentUrlsWithSameFileNameGetUniqueNames() = runTest {
        val f = fixture()
        val outcome = f.engine.addTasks(
            "https://a.example.com/report.pdf\nhttps://b.example.com/report.pdf",
            "/downloads",
            null,
        )
        assertEquals(2, outcome.added)
        assertEquals(listOf("report (1).pdf", "report.pdf"), f.tasks.snapshot().map { it.saveName }.sorted())
    }

    @Test
    fun addTasksFailsWhenAuthIsMissing() = runTest {
        val f = fixture(withAuth = false)
        val outcome = f.engine.addTasks("https://example.com/a.zip", "/downloads", null)
        assertEquals(0, outcome.added)
        assertTrue(outcome.failures.single().reason.contains("API Key"))
    }

    @Test
    fun createFailureIsReportedWithMappedMessage() = runTest {
        val port = FakeXunleiSdkPort().apply { createResult = XunleiCodes.TASK_FILENAME_CONFLICT }
        val f = fixture(port = port)
        val outcome = f.engine.addTasks("https://example.com/a.zip", "/downloads", null)
        assertEquals(0, outcome.added)
        assertTrue(outcome.failures.single().reason.contains("文件名"))
        assertTrue(f.tasks.snapshot().isEmpty())
    }

    @Test
    fun startFailureMarksTaskFailed() = runTest {
        val port = FakeXunleiSdkPort().apply { startResult = XunleiCodes.DISK_FULL }
        val f = fixture(port = port)
        val outcome = f.engine.addTasks("https://example.com/a.zip", "/downloads", null)
        assertEquals(0, outcome.added)
        val task = f.tasks.snapshot().single()
        assertEquals(TaskStatus.FAILED, task.status)
        assertEquals(DesiredState.NONE, task.desired)
        assertEquals(XunleiCodes.DISK_FULL, task.errorCode)
    }

    @Test
    fun pauseResumeAndDeleteDriveThePort() = runTest {
        val f = fixture()
        f.engine.addTasks("https://example.com/a.zip", "/downloads", null)
        val id = f.tasks.snapshot().single().taskId

        val paused = f.engine.pause(id)
        assertTrue(paused.ok)
        assertEquals(listOf(id), f.port.stoppedTasks)
        assertEquals(DesiredState.PAUSED, f.tasks.snapshot().single().desired)
        assertEquals(TaskStatus.PAUSING, f.tasks.snapshot().single().status)

        val resumed = f.engine.resume(id)
        assertTrue(resumed.ok)
        assertEquals(DesiredState.RUNNING, f.tasks.snapshot().single().desired)
        assertEquals(TaskStatus.QUEUED, f.tasks.snapshot().single().status)

        val deleted = f.engine.delete(id, deleteFile = false)
        assertTrue(deleted.ok)
        assertTrue(f.tasks.snapshot().isEmpty())
        assertEquals(listOf(id), f.port.deletedTasks)
    }

    @Test
    fun pauseRejectsTerminalTask() = runTest {
        val f = fixture()
        f.engine.addTasks("https://example.com/a.zip", "/downloads", null)
        val id = f.tasks.snapshot().single().taskId
        f.tasks.update(id) { it.copy(status = TaskStatus.COMPLETED) }
        val result = f.engine.pause(id)
        assertEquals(false, result.ok)
        assertTrue(result.message.contains("结束"))
    }

    @Test
    fun retryRestartsFailedTask() = runTest {
        val f = fixture()
        f.engine.addTasks("https://example.com/a.zip", "/downloads", null)
        val id = f.tasks.snapshot().single().taskId
        f.tasks.update(id) {
            it.copy(status = TaskStatus.FAILED, desired = DesiredState.NONE, errorCode = 1)
        }
        val result = f.engine.retry(id)
        assertTrue(result.ok)
        assertEquals(TaskStatus.QUEUED, f.tasks.snapshot().single().status)
        assertEquals(DesiredState.RUNNING, f.tasks.snapshot().single().desired)
    }

    @Test
    fun retryRecreatesTaskWhenSdkLostIt() = runTest {
        val port = FakeXunleiSdkPort()
        val f = fixture(port = port)
        f.engine.addTasks("https://example.com/a.zip", "/downloads", null)
        val original = f.tasks.snapshot().single()
        f.tasks.update(original.taskId) {
            it.copy(status = TaskStatus.FAILED, desired = DesiredState.NONE)
        }
        port.startResultByTask[original.taskId] = XunleiCodes.TASK_NOT_EXIST
        val result = f.engine.retry(original.taskId)
        assertTrue(result.ok)
        val replacement = f.tasks.snapshot().single()
        assertNotEquals(original.taskId, replacement.taskId)
        assertEquals(TaskStatus.QUEUED, replacement.status)
        assertEquals(DesiredState.RUNNING, replacement.desired)
        assertEquals(listOf(replacement.taskId), f.port.startedTasks.filter { it != original.taskId })
    }

    @Test
    fun startupImportsTasksThatExistOnlyInSdk() = runTest {
        val port = FakeXunleiSdkPort()
        port.unfinishedIds = listOf(800)
        port.states[800] = TaskStateResult(XunleiCodes.SUCCESS, 5, 512, 1000, 400, 0, 0)
        port.infos[800] = mutableMapOf(
            "url" to "https://example.com/sdk.zip",
            "save_path" to "/downloads/sdk.zip",
            "save_name" to "sdk.zip",
        )
        val f = fixture(port = port)
        assertTrue(f.engine.initialize())
        val imported = f.tasks.snapshot().single()
        assertEquals(800, imported.taskId)
        assertTrue(imported.importedFromSdk)
        assertEquals("sdk.zip", imported.saveName)
        assertEquals("/downloads", imported.saveDir)
        assertEquals(TaskStatus.DOWNLOADING, imported.status)
        assertEquals(DesiredState.RUNNING, imported.desired)
    }

    @Test
    fun startupMarksMissingActiveTaskAsFailed() = runTest {
        val f = fixture()
        f.tasks.upsert(
            DownloadTask(
                taskId = 700,
                url = "https://example.com/gone.zip",
                saveDir = "/downloads",
                saveName = "gone.zip",
                createdAt = kotlin.time.Clock.System.now().toEpochMilliseconds() - 60_000,
                desired = DesiredState.RUNNING,
                status = TaskStatus.QUEUED,
            ),
        )
        assertTrue(f.engine.initialize())
        val task = f.tasks.snapshot().single()
        assertEquals(TaskStatus.FAILED, task.status)
        assertEquals(DesiredState.NONE, task.desired)
        assertTrue(task.errorMessage?.contains("引擎") == true)
    }

    @Test
    fun completionNotificationIsSentOnlyOnce() = runTest {
        val port = FakeXunleiSdkPort()
        port.unfinishedIds = listOf(900)
        port.states[900] = TaskStateResult(XunleiCodes.SUCCESS, 5, 128, 100, 10, 0, 0)
        val f = fixture(port = port)
        f.tasks.upsert(
            DownloadTask(
                taskId = 900,
                url = "https://example.com/done.zip",
                saveDir = "/downloads",
                saveName = "done.zip",
                createdAt = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                desired = DesiredState.RUNNING,
                status = TaskStatus.DOWNLOADING,
            ),
        )
        assertTrue(f.engine.initialize())
        port.states[900] = TaskStateResult(XunleiCodes.SUCCESS, 8, 0, 100, 100, 0, 0)
        testScheduler.advanceTimeBy(1_100)
        testScheduler.runCurrent()
        assertEquals(1, f.actions.notifications.size)
        assertTrue(f.tasks.snapshot().single().completionNotified)
        assertEquals(TaskStatus.COMPLETED, f.tasks.snapshot().single().status)

        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()
        assertEquals(1, f.actions.notifications.size)
    }
    @Test
    fun trafficUpdatesAtCompletionAndSurvivesAnUnavailableResponse() = runTest {
        val port = FakeXunleiSdkPort()
        port.unfinishedIds = listOf(900)
        port.trafficInfos[900] = """{"origin_size":100,"p2p_size":50,"p2s_size":0}"""
        val f = fixture(port)
        f.tasks.upsert(DownloadTask(900, "https://example.com/file", "/downloads", "file",
            kotlin.time.Clock.System.now().toEpochMilliseconds()))
        assertTrue(f.engine.initialize())
        assertEquals(50L, f.tasks.snapshot().single().traffic?.p2pBytes)
        port.trafficInfos[900] = """{"origin_size":200,"p2p_size":100,"p2s_size":50}"""
        port.states[900] = TaskStateResult(0, 8, 0, 350, 350, 0, 0)
        testScheduler.advanceTimeBy(1_100)
        testScheduler.runCurrent()
        val task = f.tasks.snapshot().single()
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertEquals(200L, task.traffic?.originBytes)
        assertEquals(100L, task.traffic?.p2pBytes)
        assertEquals(50L, task.traffic?.p2sBytes)
        port.trafficInfos[900] = ""
        port.finishedIds = listOf(900)
        port.unfinishedIds = emptyList()
        testScheduler.advanceTimeBy(35_000)
        testScheduler.runCurrent()
        assertEquals(task.traffic, f.tasks.snapshot().single().traffic)
    }

}
