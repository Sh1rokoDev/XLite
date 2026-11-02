package com.xlite.downloader

import com.xlite.downloader.data.PlatformPaths
import okio.Path
import okio.Path.Companion.toPath
import java.io.File

class DesktopPaths(
    private val applicationRoot: File? = System.getProperty("xlite.home")
        ?.takeIf { it.isNotBlank() }?.let { File(it).canonicalFile },
) : PlatformPaths {
    private val osName = System.getProperty("os.name").orEmpty().lowercase()
    private val isWindows = osName.contains("windows")

    private val windowsRoot = applicationRoot ?: File(System.getProperty("user.dir"))

    override val dataDir: Path = if (applicationRoot != null) {
        File(applicationRoot, "data").absolutePath.toPath()
    } else if (isWindows) {
        File(windowsRoot, "data").absolutePath.toPath()
    } else {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: File(System.getProperty("user.home"), ".local/share").path
        File(base, "XLite").absolutePath.toPath()
    }

    override val downloadDir: String = if (applicationRoot != null || isWindows) {
        File(windowsRoot, "downloads").absolutePath
    } else {
        File(System.getProperty("user.home"), "Downloads/XLite").absolutePath
    }

    fun toNativePath(path: String): String = File(path).absolutePath
}
