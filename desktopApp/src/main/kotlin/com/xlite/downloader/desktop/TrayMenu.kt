package com.xlite.downloader.desktop

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/** A focusable owner lets a tray popup support Escape, keyboard navigation and outside clicks. */
internal class TrayMenu(
    font: Font,
    onShow: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onExit: () -> Unit,
) {
    private val owner = JDialog().apply {
        title = "XLite 托盘菜单"
        isUndecorated = true
        isAlwaysOnTop = true
        type = java.awt.Window.Type.POPUP
    }
    private val popup = JPopupMenu().apply {
        this.font = font
        fun item(label: String, action: () -> Unit) {
            add(JMenuItem(label).apply {
                this.font = font
                addActionListener {
                    dismiss()
                    action()
                }
            })
        }
        item("显示主界面", onShow)
        addSeparator()
        item("暂停全部", onPauseAll)
        item("继续全部", onResumeAll)
        addSeparator()
        item("退出", onExit)
    }

    init {
        owner.addWindowFocusListener(object : WindowAdapter() {
            override fun windowLostFocus(event: WindowEvent) = dismiss()
        })
        popup.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) = Unit
            override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) {
                owner.isVisible = false
            }
            override fun popupMenuCanceled(event: PopupMenuEvent) = dismiss()
        })
    }

    fun show(point: Point) {
        if (popup.isVisible) return
        val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        val screen = screens.firstOrNull { it.defaultConfiguration.bounds.contains(point) }
            ?.defaultConfiguration ?: owner.graphicsConfiguration
        val usable = Rectangle(screen.bounds)
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(screen)
        usable.x += insets.left
        usable.y += insets.top
        usable.width -= insets.left + insets.right
        usable.height -= insets.top + insets.bottom
        val size = popup.preferredSize
        val x = point.x.coerceIn(usable.x, (usable.x + usable.width - size.width).coerceAtLeast(usable.x))
        val y = point.y.coerceIn(usable.y, (usable.y + usable.height - size.height).coerceAtLeast(usable.y))
        owner.setLocation(x, y)
        owner.size = size
        owner.isVisible = true
        owner.toFront()
        popup.show(owner.contentPane, 0, 0)
        owner.requestFocus()
    }

    private fun dismiss() {
        popup.isVisible = false
        owner.isVisible = false
    }

    fun dispose() {
        dismiss()
        owner.dispose()
    }
}
