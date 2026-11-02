package com.xlite.downloader

import com.xlite.downloader.core.AppLog
import com.xlite.downloader.platform.PlatformActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.ShellAPI
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinUser

class DesktopPlatformActions(
    private val pathLauncher: (File) -> Unit = ::openWithPlatformShell,
) : PlatformActions {
    var onCompleted: ((taskId: Long, name: String, path: String) -> Unit)? = null

    override val canPickDirectory: Boolean = true
    override val canOpenFolder: Boolean = true
    override val canShareFile: Boolean = false

    override suspend fun pickDirectory(current: String): String? = withContext(Dispatchers.Swing) {
        val chooser = JFileChooser(current.takeIf { File(it).isDirectory })
        chooser.dialogTitle = "选择下载目录"
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isMultiSelectionEnabled = false
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.absolutePath
        } else {
            null
        }
    }

    override suspend fun openFolder(path: String): Boolean = open(File(path))

    override suspend fun openFile(path: String): Boolean = open(File(path))

    override suspend fun shareFile(path: String): Boolean = false

    override suspend fun openUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            require(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            Desktop.getDesktop().browse(java.net.URI(url))
            true
        }.getOrElse {
            AppLog.warn("platform", "打开链接失败", it)
            false
        }
    }

    override suspend fun notifyCompleted(taskId: Long, name: String, path: String) {
        onCompleted?.invoke(taskId, name, path)
    }

    private suspend fun open(file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = file.canonicalFile
            require(target.exists()) { "文件不存在" }
            pathLauncher(target)
            true
        }.getOrElse {
            AppLog.warn("platform", "打开路径失败", it)
            false
        }
    }
}

private fun openWithPlatformShell(file: File) {
    if (!System.getProperty("os.name").lowercase().contains("windows")) {
        require(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
        Desktop.getDesktop().open(file)
        return
    }
    // Use the same shell operation as Explorer, including executable manifests
    // and document associations. Keep paths as Unicode strings, never commands.
    val ole = Ole32.INSTANCE
    val initialized = ole.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED or Ole32.COINIT_DISABLE_OLE1DDE).toInt()
    try {
        val info = ShellAPI.SHELLEXECUTEINFO().apply {
            fMask = 0x00000100 // SEE_MASK_NOASYNC (not exposed by this JNA version)
            lpVerb = "open"
            lpFile = file.absolutePath
            lpDirectory = file.parentFile?.absolutePath
            nShow = WinUser.SW_SHOWNORMAL
        }
        if (!Shell32.INSTANCE.ShellExecuteEx(info)) {
            val error = Kernel32.INSTANCE.GetLastError()
            if (error != 740) throw Win32Exception(error) // ERROR_ELEVATION_REQUIRED
            info.lpVerb = "runas"
            if (!Shell32.INSTANCE.ShellExecuteEx(info)) throw Win32Exception(Kernel32.INSTANCE.GetLastError())
        }
    } finally {
        if (initialized == 0 || initialized == 1) ole.CoUninitialize()
    }
}
