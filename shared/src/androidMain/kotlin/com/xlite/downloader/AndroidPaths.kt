package com.xlite.downloader

import android.content.Context
import com.xlite.downloader.data.PlatformPaths
import okio.Path
import okio.Path.Companion.toPath
import java.io.File

class AndroidPaths(context: Context) : PlatformPaths {
    private val appContext = context.applicationContext

    override val dataDir: Path = appContext.filesDir.absolutePath.toPath()

    override val downloadDir: String = (
        appContext.getExternalFilesDir("downloads")
            ?: File(appContext.filesDir, "downloads")
        ).absolutePath

    fun toNativePath(path: String): String = File(path).absolutePath
}
