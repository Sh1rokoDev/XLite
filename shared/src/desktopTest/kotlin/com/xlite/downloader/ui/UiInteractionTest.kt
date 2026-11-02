package com.xlite.downloader.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.xlite.downloader.App
import com.xlite.downloader.AppContainer
import com.xlite.downloader.AppPlatform
import com.xlite.downloader.data.MemorySecretStore
import com.xlite.downloader.data.PlatformPaths
import com.xlite.downloader.domain.engine.ActionResult
import com.xlite.downloader.domain.engine.ConnectionTestResult
import com.xlite.downloader.domain.model.AuthState
import com.xlite.downloader.domain.model.DesiredState
import com.xlite.downloader.domain.model.DownloadTask
import com.xlite.downloader.domain.model.TaskStatus
import com.xlite.downloader.testutil.FakePlatformActions
import com.xlite.downloader.testutil.FakeXunleiSdkPort
import com.xlite.downloader.ui.screens.AddTaskDialog
import com.xlite.downloader.ui.screens.AuthScreen
import com.xlite.downloader.ui.screens.TasksScreen
import com.xlite.downloader.ui.theme.XLiteTheme
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okio.Path
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    private var container: AppContainer? = null

    @After
    fun closeContainer() {
        container?.let { runBlocking { it.shutdown() } }
    }

    @Test
    fun authenticationRequiresKeyAndShowsTestFailure() {
        val calls = AtomicInteger()
        compose.setContent {
            XLiteTheme(darkTheme = false) {
                AuthScreen(
                    auth = AuthState.NotConfigured,
                    appIdOverride = null,
                    engineVersion = null,
                    onTest = { _, _ ->
                        calls.incrementAndGet()
                        ConnectionTestResult(false, null, null, "测试密钥无效")
                    },
                    onSave = { _, _ -> ActionResult(true, "已保存") },
                    onReconnect = { ActionResult(true, "已连接") },
                )
            }
        }

        compose.onNodeWithText("保存并登录").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("test-key")
        compose.onNodeWithText("测试连接").performClick()
        compose.onNodeWithText("连接失败").assertIsDisplayed()
        compose.onNodeWithText("测试密钥无效").assertIsDisplayed()
        assertEquals(1, calls.get())
    }

    @Test
    fun filteringAndBatchDeleteUseSelectedTasks() {
        val port = FakeXunleiSdkPort()
        val app = newContainer(port)
        app.tasks.upsertAll(
            listOf(
                task(1, "alpha.zip", TaskStatus.PAUSED),
                task(2, "beta.pdf", TaskStatus.COMPLETED),
            ),
        )
        compose.setContent {
            val state by app.state.collectAsState()
            XLiteTheme(darkTheme = false) {
                TasksScreen(
                    container = app,
                    state = state,
                    compact = false,
                    authBlocked = false,
                    onAdd = {},
                    onOpenSettings = {},
                    showMessage = {},
                )
            }
        }
        compose.waitUntil(5_000) { app.state.value.tasks.size == 2 }
        compose.onNodeWithTag("task-filter-done").performClick()
        compose.onNodeWithText("beta.pdf").assertIsDisplayed()
        compose.onNodeWithText("alpha.zip").assertDoesNotExist()
        compose.onNodeWithTag("task-filter-all").performClick()
        compose.onNodeWithContentDescription("搜索任务").performClick()
        compose.onNodeWithContentDescription("搜索任务").performTextInput("alpha")
        compose.onNodeWithText("alpha.zip").assertIsDisplayed()
        compose.onNodeWithText("beta.pdf").assertDoesNotExist()

        compose.onNodeWithContentDescription("搜索任务").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithContentDescription("多选").performClick()
        app.tasks.update(1, persist = false) { it.copy(speedBps = 1) }
        compose.waitUntil(5_000) { app.state.value.tasks.first { it.taskId == 1L }.speedBps == 1L }
        compose.onNodeWithContentDescription("选择 alpha.zip").performClick()
        compose.onNodeWithText("已选 1 项").assertIsDisplayed()
        compose.onNode(hasClickAction() and androidx.compose.ui.test.hasText("删除")).performClick()
        compose.onNodeWithText("批量删除").assertIsDisplayed()
        compose.onAllNodesWithText("删除").onLast().performClick()
        compose.waitUntil(5_000) { port.deletedTasks.contains(1) }
        assertEquals(listOf(1L), port.deletedTasks)
        assertTrue(app.tasks.snapshot().any { it.taskId == 2L })
    }

    @Test
    fun batchAddKeepsDialogOpenAndShowsPartialFailures() {
        val app = newContainer(FakeXunleiSdkPort())
        val added = AtomicInteger()
        val dismissed = AtomicInteger()
        compose.setContent {
            XLiteTheme(darkTheme = false) {
                AddTaskDialog(
                    container = app,
                    defaultDir = app.paths.downloadDir,
                    isDesktop = true,
                    initialText = "https://example.com/a.zip\nmagnet:?xt=urn:btih:invalid",
                    onDismiss = { dismissed.incrementAndGet() },
                    onAdded = { added.set(it) },
                    showMessage = {},
                )
            }
        }
        compose.onNodeWithText("开始下载 (2)").performClick()
        compose.waitUntil(5_000) { added.get() == 1 }
        compose.onNodeWithText("1 条未能添加").assertIsDisplayed()
        assertEquals(0, dismissed.get())
        assertEquals(1, app.tasks.snapshot().size)
    }

    @Test
    fun resizingKeepsSearchAndSettingsReturnToTasks() {
        val app = newContainer(FakeXunleiSdkPort())
        app.tasks.upsertAll(listOf(task(1, "alpha.zip", TaskStatus.PAUSED), task(2, "beta.pdf", TaskStatus.COMPLETED)))
        val width = mutableStateOf(320.dp)
        compose.setContent {
            Box(Modifier.size(width.value, 680.dp)) {
                App(container = app, platform = AppPlatform.Desktop, themeOverride = false)
            }
        }
        compose.waitUntil(5_000) { app.state.value.tasks.size == 2 }
        compose.onNodeWithContentDescription("搜索任务").performClick()
        compose.onNodeWithContentDescription("搜索任务").performTextInput("alpha")
        compose.onNodeWithText("alpha.zip").assertIsDisplayed()
        compose.onNodeWithText("beta.pdf").assertDoesNotExist()
        compose.runOnIdle { width.value = 700.dp }
        compose.onNodeWithText("alpha.zip").assertIsDisplayed()
        compose.onNodeWithText("beta.pdf").assertDoesNotExist()
        compose.runOnIdle { width.value = 900.dp }
        compose.onNodeWithText("alpha.zip").assertIsDisplayed()
        compose.onNodeWithText("beta.pdf").assertDoesNotExist()
        compose.onNodeWithContentDescription("关闭搜索").performClick()
        compose.onNodeWithText("beta.pdf").assertIsDisplayed()
        compose.onNodeWithContentDescription("搜索任务").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithContentDescription("返回下载任务").performClick()
        compose.onNodeWithContentDescription("搜索任务").assertIsDisplayed()
    }

    @Test
    fun compactDarkSettingsWorkWithLargerText() {
        val app = newContainer(FakeXunleiSdkPort())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                Box(Modifier.size(320.dp, 680.dp)) {
                    App(container = app, platform = AppPlatform.Desktop, themeOverride = true)
                }
            }
        }
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithContentDescription("增加同时下载数").performScrollTo().performClick()
        compose.waitUntil(5_000) { app.settings.settings.value.concurrentTasks == 4 }
        assertEquals(4, app.settings.settings.value.concurrentTasks)
    }

    @Test
    fun unknownSizeDoesNotShowZeroPercentAndDetailsRemainAvailable() {
        val app = newContainer(FakeXunleiSdkPort())
        app.tasks.upsert(task(1, "unknown.bin", TaskStatus.PAUSED).copy(totalBytes = 0, downloadedBytes = 0))
        compose.setContent {
            val state by app.state.collectAsState()
            XLiteTheme(darkTheme = true) {
                TasksScreen(
                    container = app,
                    state = state,
                    compact = false,
                    authBlocked = false,
                    onAdd = {},
                    onOpenSettings = {},
                    showMessage = {},
                )
            }
        }
        compose.waitUntil(5_000) { app.state.value.tasks.size == 1 }
        compose.onNodeWithText("大小未知").assertIsDisplayed()
        compose.onAllNodesWithText("0%").assertCountEquals(0)
        compose.onNodeWithText("unknown.bin").performClick()
        compose.onNodeWithText("暂无来源统计").assertIsDisplayed()
    }

    @Test
    fun selectAllAndInvertRespectCurrentFilter() {
        val port = FakeXunleiSdkPort()
        val app = newContainer(port)
        app.tasks.upsertAll(listOf(task(1, "alpha.zip", TaskStatus.PAUSED),
            task(2, "beta.pdf", TaskStatus.COMPLETED), task(3, "gamma.exe", TaskStatus.COMPLETED)))
        compose.setContent { App(app, AppPlatform.Desktop, themeOverride = false) }
        compose.waitUntil(5_000) { app.state.value.tasks.size == 3 }
        compose.onNodeWithTag("task-filter-done").performClick()
        compose.onNodeWithContentDescription("多选").performClick()
        compose.onNodeWithText("全选").performClick()
        compose.onNodeWithText("已选 2 项").assertIsDisplayed()
        compose.onNodeWithText("反选").performClick()
        compose.onNodeWithText("已选 0 项").assertIsDisplayed()
        compose.onNodeWithContentDescription("选择 gamma.exe").performClick()
        compose.onNodeWithTag("task-filter-all").performClick()
        compose.onNodeWithText("反选").performClick()
        compose.onNodeWithText("已选 2 项").assertIsDisplayed()
        compose.onNodeWithText("删除").performClick()
        compose.onAllNodesWithText("删除").onLast().performClick()
        compose.waitUntil(5_000) { port.deletedTasks.size == 2 }
        assertEquals(setOf(1L, 2L), port.deletedTasks.toSet())
        assertTrue(app.tasks.snapshot().any { it.taskId == 3L })
    }

    @Test
    fun fileAndFolderButtonsAreVisibleAndNotDuplicatedInMenu() {
        val app = newContainer(FakeXunleiSdkPort())
        val completed = task(1, "中文 file.exe", TaskStatus.COMPLETED)
        app.tasks.upsert(completed)
        compose.setContent { App(app, AppPlatform.Desktop, themeOverride = false) }
        compose.waitUntil(5_000) { app.state.value.tasks.size == 1 }
        compose.onNodeWithText("打开文件").performClick()
        compose.onNodeWithText("打开所在文件夹").performClick()
        val actions = app.actions as FakePlatformActions
        compose.waitUntil(5_000) { actions.openedFiles.size == 1 && actions.openedFolders.size == 1 }
        assertEquals(listOf(completed.displayPath), actions.openedFiles)
        assertEquals(listOf(completed.saveDir), actions.openedFolders)
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onAllNodesWithText("打开文件").assertCountEquals(1)
        compose.onAllNodesWithText("打开所在文件夹").assertCountEquals(1)
        compose.onNodeWithText("复制链接").assertIsDisplayed()
    }

    @Test
    fun speedUnitSelectionsApplyConvertedDownloadAndUploadLimits() {
        val app = newContainer(FakeXunleiSdkPort())
        compose.setContent { App(app, AppPlatform.Desktop, themeOverride = false) }
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithContentDescription("下载限速单位").performScrollTo().performClick()
        compose.onNodeWithText("MB/s").performClick()
        compose.onNodeWithContentDescription("下载限速").performTextReplacement("1.5")
        compose.onNodeWithContentDescription("下载限速应用").performClick()
        compose.waitUntil(5_000) { app.settings.settings.value.downloadLimitKbps == 1536 }
        compose.onNodeWithContentDescription("下载限速单位").performClick()
        compose.onNodeWithText("GB/s").performClick()
        compose.onNodeWithContentDescription("下载限速").performTextReplacement("1")
        compose.onNodeWithContentDescription("下载限速应用").performClick()
        compose.waitUntil(5_000) { app.settings.settings.value.downloadLimitKbps == 1048576 }
        compose.onNodeWithContentDescription("上传").performScrollTo().performClick()
        compose.onNodeWithContentDescription("上传限速单位").performScrollTo().performClick()
        compose.onNodeWithText("MB/s").performClick()
        compose.onNodeWithContentDescription("上传限速").performTextReplacement("2")
        compose.onNodeWithContentDescription("上传限速应用").performClick()
        compose.waitUntil(5_000) { app.settings.settings.value.uploadLimitKbps == 2048 }
    }

    @Test
    fun primaryActionsAreExclusiveAndProgressBarsStayAligned() {
        val app = newContainer(FakeXunleiSdkPort())
        app.tasks.upsertAll(listOf(task(1, "done.exe", TaskStatus.COMPLETED),
            task(2, "paused.zip", TaskStatus.PAUSED), task(3, "failed.bin", TaskStatus.FAILED),
            task(4, "active.zip", TaskStatus.DOWNLOADING)))
        compose.setContent {
            Box(Modifier.size(1000.dp, 740.dp)) { App(app, AppPlatform.Desktop, themeOverride = false) }
        }
        compose.waitUntil(5_000) { app.state.value.tasks.size == 4 }
        compose.onAllNodesWithText("打开文件").assertCountEquals(1)
        compose.onNodeWithText("继续").assertIsDisplayed()
        compose.onNodeWithText("重试").assertIsDisplayed()
        compose.onNodeWithContentDescription("任务操作").assertDoesNotExist()
        val widths = (1..4).map { compose.onNodeWithTag("task-progress-$it").fetchSemanticsNode().boundsInRoot.width }
        assertTrue(widths.all { kotlin.math.abs(it - widths.first()) < 1f }, "Progress widths differ: $widths")
    }

    @Test
    fun floatingSearchKeepsTitleAndFiltersInPlace() {
        val app = newContainer(FakeXunleiSdkPort())
        app.tasks.upsertAll(listOf(task(1, "alpha.zip", TaskStatus.PAUSED), task(2, "beta.pdf", TaskStatus.COMPLETED)))
        compose.setContent { Box(Modifier.size(1000.dp, 740.dp)) { App(app, AppPlatform.Desktop, themeOverride = false) } }
        compose.waitUntil(5_000) { app.state.value.tasks.size == 2 }
        val title = compose.onNodeWithText("下载任务").fetchSemanticsNode().boundsInRoot
        val filters = compose.onNodeWithTag("task-filter-all").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("搜索任务").performClick()
        compose.onNodeWithContentDescription("搜索任务").performTextInput("alpha")
        assertEquals(title, compose.onNodeWithText("下载任务").fetchSemanticsNode().boundsInRoot)
        assertEquals(filters, compose.onNodeWithTag("task-filter-all").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("搜索任务").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithContentDescription("关闭搜索").assertDoesNotExist()
        compose.onNodeWithText("alpha.zip").assertIsDisplayed()
        compose.onNodeWithText("beta.pdf").assertDoesNotExist()
    }

    private fun newContainer(port: FakeXunleiSdkPort): AppContainer {
        val root = Files.createTempDirectory("xlite-ui-test")
        val paths = object : PlatformPaths {
            override val dataDir: Path = root.resolve("data").toString().toPath()
            override val downloadDir: String = root.resolve("downloads").toString()
        }
        return AppContainer(
            paths = paths,
            secretStore = MemorySecretStore("test-key"),
            port = port,
            actions = FakePlatformActions(),
            appVersion = "test",
        ).also {
            container = it
            runBlocking { it.start() }
        }
    }

    private fun task(id: Long, name: String, status: TaskStatus) = DownloadTask(
        taskId = id,
        url = "https://example.com/" + name,
        saveDir = "unused",
        saveName = name,
        createdAt = id,
        desired = if (status == TaskStatus.PAUSED) DesiredState.PAUSED else DesiredState.NONE,
        status = status,
        totalBytes = 1024,
        downloadedBytes = if (status == TaskStatus.COMPLETED) 1024 else 512,
    )
}
