package com.xlite.downloader.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.xlite.downloader.App
import com.xlite.downloader.AppContainer
import com.xlite.downloader.AppPlatform
import com.xlite.downloader.DesktopPaths
import com.xlite.downloader.DesktopPlatformActions
import com.xlite.downloader.DesktopSecretStore
import com.xlite.downloader.DesktopXunleiSdkPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

fun main() {
    val paths = DesktopPaths()
    val instance = SingleInstance.acquire(paths.dataDir.toString())
    if (instance == null) {
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(
                null,
                "XLite 已在运行。请从任务栏托盘打开主界面。",
                "XLite",
                JOptionPane.INFORMATION_MESSAGE,
            )
        }
        return
    }

    val actions = DesktopPlatformActions()
    val container = AppContainer(
        paths = paths,
        secretStore = DesktopSecretStore(paths),
        port = DesktopXunleiSdkPort(),
        actions = actions,
        appVersion = System.getProperty("jpackage.app-version", "0.1.0"),
    )
    val exitScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val windowVisible = mutableStateOf(true)
    val themeOverride = when (System.getenv("XLITE_THEME")?.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> null
    }
    var tray: TrayController? = null

    fun requestExit() {
        tray?.uninstall()
        exitScope.launch {
            runCatching { container.shutdown() }
            exitProcess(0)
        }
    }

    tray = TrayController(
        onShow = { windowVisible.value = true },
        onPauseAll = { exitScope.launch { container.engine.pauseAll() } },
        onResumeAll = { exitScope.launch { container.engine.resumeAll() } },
        onExit = { requestExit() },
    )
    val trayAvailable = tray.install()
    actions.onCompleted = { _, name, _ ->
        SwingUtilities.invokeLater { tray?.notify("下载完成", name) }
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            runBlocking { runCatching { container.shutdown() } }
            tray?.uninstall()
            instance.close()
        },
    )

    application {
        LaunchedEffect(Unit) { container.start() }
        val windowState = rememberWindowState(width = 1100.dp, height = 720.dp)
        val windowIcon = remember {
            BitmapPainter(
                loadImageBitmap(
                    Thread.currentThread().contextClassLoader.getResourceAsStream("xlite.png"),
                ),
            )
        }
        Window(
            icon = windowIcon,
            onCloseRequest = {
                if (trayAvailable && container.settings.settings.value.closeToTray) {
                    windowVisible.value = false
                } else {
                    requestExit()
                }
            },
            state = windowState,
            visible = windowVisible.value,
            title = "XLite",
        ) {
            SideEffect {
                window.minimumSize = Dimension(900, 600)
            }
            App(container = container, platform = AppPlatform.Desktop, themeOverride = themeOverride)
        }
    }
}
