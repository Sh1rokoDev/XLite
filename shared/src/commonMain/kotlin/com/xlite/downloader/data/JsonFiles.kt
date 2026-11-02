package com.xlite.downloader.data

import okio.FileSystem
import okio.buffer
import okio.Path

internal object JsonFiles {
    fun writeAtomic(fileSystem: FileSystem, file: Path, content: String) {
        val parent = requireNotNull(file.parent) { "file has no parent: $file" }
        fileSystem.createDirectories(parent)
        val temp = parent / "${file.name}.tmp"
        fileSystem.sink(temp).buffer().use { it.writeUtf8(content) }
        runCatching {
            fileSystem.atomicMove(temp, file)
        }.onFailure {
            fileSystem.delete(file, mustExist = false)
            fileSystem.atomicMove(temp, file)
        }
    }

    fun backupCorrupt(fileSystem: FileSystem, file: Path, timestamp: Long) {
        if (!fileSystem.exists(file)) return
        runCatching {
            fileSystem.atomicMove(file, file.parent!! / "${file.name}.corrupt-$timestamp")
        }
    }
}
