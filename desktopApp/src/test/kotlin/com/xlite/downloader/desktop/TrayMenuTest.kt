package com.xlite.downloader.desktop

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.util.concurrent.FutureTask
import javax.swing.JDialog
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assume.assumeFalse

class TrayMenuTest {
    private fun onEdt(test: () -> Unit) {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val task = FutureTask { test() }
        SwingUtilities.invokeAndWait(task)
        task.get()
    }

    @Test
    fun menuDispatchesEachActionOnceAndDismissesBeforeCallback() = onEdt {
        val calls = mutableListOf<Int>()
        fun action(index: Int): () -> Unit = {
            assertTrue(MenuSelectionManager.defaultManager().selectedPath.isEmpty())
            assertFalse(menuOwner().isVisible)
            calls += index
        }
        val menu = TrayMenu(Font(Font.DIALOG, Font.PLAIN, 12), action(0), action(1), action(2), action(3))
        try {
            val labels = listOf("显示主界面", "暂停全部", "继续全部", "退出")
            labels.forEachIndexed { index, label ->
                menu.show(Point(100, 100))
                val popup = MenuSelectionManager.defaultManager().selectedPath.filterIsInstance<JPopupMenu>().single()
                val items = popup.components.filterIsInstance<JMenuItem>()
                assertEquals(labels, items.map { it.text })
                items.single { it.text == label }.doClick(0)
                assertEquals((0..index).toList(), calls)
            }
        } finally {
            menu.dispose()
        }
        assertFalse(menuOwnerOrNull()?.isDisplayable == true)
    }

    @Test
    fun popupFitsWorkAreaAndCanReopenAfterCancellation() = onEdt {
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val bounds = screen.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(screen)
        val menu = TrayMenu(Font(Font.DIALOG, Font.PLAIN, 12), {}, {}, {}, {})
        try {
            repeat(2) {
                menu.show(Point(bounds.x + bounds.width - 1, bounds.y + bounds.height - 1))
                val popup = MenuSelectionManager.defaultManager().selectedPath.filterIsInstance<JPopupMenu>().single()
                val location = popup.locationOnScreen
                assertTrue(location.x >= bounds.x + insets.left)
                assertTrue(location.y >= bounds.y + insets.top)
                assertTrue(location.x + popup.width <= bounds.x + bounds.width - insets.right)
                assertTrue(location.y + popup.height <= bounds.y + bounds.height - insets.bottom)
                MenuSelectionManager.defaultManager().clearSelectedPath()
                assertFalse(menuOwner().isVisible)
            }
        } finally {
            menu.dispose()
        }
    }

    private fun menuOwnerOrNull() = Window.getWindows().filterIsInstance<JDialog>()
        .lastOrNull { it.title == "XLite 托盘菜单" && it.isDisplayable }

    private fun menuOwner() = requireNotNull(menuOwnerOrNull())
}
