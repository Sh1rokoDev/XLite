package com.xlite.downloader

import com.xlite.downloader.core.AppLog
import com.xlite.downloader.data.PlatformPaths
import okio.FileSystem
import okio.Path
import okio.buffer

class FileLogSink(
    private val fileSystem: FileSystem,
    private val logsDir: Path,
) {
    private val lock = Any()
    private val logFile = logsDir / "xlite.log"

    fun install() {
        fileSystem.createDirectories(logsDir)
        AppLog.sink = { _, line -> append(line) }
    }

    private fun append(line: String) {
        synchronized(lock) {
            runCatching {
                rotateIfNeeded()
                fileSystem.appendingSink(logFile).buffer().use {
                    it.writeUtf8(line)
                    it.writeUtf8("\n")
                }
            }.onFailure { println(line) }
        }
    }

    private fun rotateIfNeeded() {
        val size = runCatching { fileSystem.metadata(logFile).size }.getOrNull() ?: 0L
        if (size < MAX_BYTES) return
        fileSystem.delete(backup(MAX_BACKUPS), mustExist = false)
        for (index in MAX_BACKUPS - 1 downTo 1) {
            val from = backup(index)
            if (fileSystem.exists(from)) fileSystem.atomicMove(from, backup(index + 1))
        }
        if (fileSystem.exists(logFile)) fileSystem.atomicMove(logFile, backup(1))
    }

    private fun backup(index: Int): Path = logsDir / "xlite.$index.log"

    private companion object {
        const val MAX_BYTES = 2L * 1024 * 1024
        const val MAX_BACKUPS = 3
    }
}
