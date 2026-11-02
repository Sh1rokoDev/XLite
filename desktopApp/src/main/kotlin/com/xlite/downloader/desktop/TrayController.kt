package com.xlite.downloader.desktop

import com.xlite.downloader.core.AppLog
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.FutureTask
import javax.swing.SwingUtilities

class TrayController(
    private val onShow: () -> Unit,
    private val onPauseAll: () -> Unit,
    private val onResumeAll: () -> Unit,
    private val onExit: () -> Unit,
) {
    private var trayIcon: TrayIcon? = null
    private var trayMenu: TrayMenu? = null

    val available: Boolean
        get() = trayIcon != null

    fun install(): Boolean = onEdt {
        if (trayIcon != null) return@onEdt true
        if (!SystemTray.isSupported()) return@onEdt false
        runCatching {
            val menuFont = trayMenuFont()
            val menu = TrayMenu(menuFont, onShow, onPauseAll, onResumeAll, onExit)
            trayMenu = menu
            val icon = TrayIcon(createTrayImage(32), "XLite - 迅雷加速下载器").apply {
                isImageAutoSize = true
                // AWT's Windows menu peer remaps physical fonts; canDisplayUpTo
                // does not describe that native rendering path. Use Java2D text.
                addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(event: MouseEvent) = showPopup(event)
                    override fun mouseReleased(event: MouseEvent) = showPopup(event)
                    private fun showPopup(event: MouseEvent) {
                        if (event.isPopupTrigger) menu.show(event.locationOnScreen)
                    }
                })
                addActionListener { onShow() }
            }
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
            true
        }.onFailure {
            trayMenu?.dispose()
            trayMenu = null
            AppLog.warn("tray", "系统托盘不可用", it)
        }.getOrDefault(false)
    }

    fun notify(title: String, body: String) {
        val icon = trayIcon ?: return
        runCatching {
            icon.displayMessage(title, body, TrayIcon.MessageType.INFO)
        }.onFailure { AppLog.warn("tray", "显示通知失败", it) }
    }

    fun uninstall() = onEdt {
        val icon = trayIcon
        trayIcon = null
        if (icon != null) runCatching { SystemTray.getSystemTray().remove(icon) }
        trayMenu?.dispose()
        trayMenu = null
    }

    private fun trayMenuFont(): Font {
        val systemFont = Toolkit.getDefaultToolkit().getDesktopProperty("win.menu.font") as? Font
            ?: Font(Font.DIALOG, Font.PLAIN, 12)
        val families = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val labels = "显示主界面暂停全部继续退出"
        // Swing/Java2D honors this physical font and its glyph coverage.
        val preferred = listOf("Microsoft YaHei UI", "Microsoft YaHei", "SimSun", "Noto Sans CJK SC", "WenQuanYi Micro Hei")
        return (preferred.asSequence().filter { it in families } + families.asSequence())
            .map { Font(it, Font.PLAIN, systemFont.size).deriveFont(systemFont.size2D) }
            .firstOrNull { it.canDisplayUpTo(labels) == -1 }
            ?: systemFont
    }

    private fun <T> onEdt(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return action()
        val task = FutureTask(action)
        SwingUtilities.invokeAndWait(task)
        return task.get()
    }

    private fun createTrayImage(size: Int): BufferedImage {
        val source = javax.imageio.ImageIO.read(javaClass.getResource("/xlite.png"))
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.drawImage(source, 0, 0, size, size, null)
        } finally {
            graphics.dispose()
        }
        return image
    }
}
