package com.xlite.downloader.data

import okio.FileSystem
import okio.Path

interface PlatformPaths {
    val dataDir: Path
    val downloadDir: String

    val cfgDir: Path get() = dataDir / "xunlei"
    val logsDir: Path get() = dataDir / "logs"
    val settingsFile: Path get() = dataDir / "settings.json"
    val tasksFile: Path get() = dataDir / "tasks.json"
    val secretFile: Path get() = dataDir / "secret.bin"

    fun ensureDirectories(fileSystem: FileSystem) {
        fileSystem.createDirectories(dataDir)
        fileSystem.createDirectories(cfgDir)
        fileSystem.createDirectories(logsDir)
    }
}
