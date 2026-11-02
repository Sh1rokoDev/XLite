package com.xlite.downloader.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

class SingleInstance private constructor(
    private val file: RandomAccessFile,
    private val channel: FileChannel,
    private val lock: FileLock,
) {
    fun close() {
        runCatching { lock.release() }
        runCatching { channel.close() }
        runCatching { file.close() }
    }

    companion object {
        fun acquire(dataDir: String): SingleInstance? {
            val lockFile = File(dataDir, "instance.lock")
            lockFile.parentFile?.mkdirs()
            return try {
                val file = RandomAccessFile(lockFile, "rw")
                val channel = file.channel
                val lock = channel.tryLock()
                if (lock == null) {
                    channel.close()
                    file.close()
                    null
                } else {
                    SingleInstance(file, channel, lock)
                }
            } catch (_: OverlappingFileLockException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }
}
