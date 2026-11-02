package com.xlite.downloader

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.xlite.downloader.core.AppLog
import com.xlite.downloader.platform.PlatformActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidPlatformActions(
    context: Context,
) : PlatformActions {
    private val appContext = context.applicationContext

    override val canPickDirectory: Boolean = false
    override val canOpenFolder: Boolean = false
    override val canShareFile: Boolean = true

    override suspend fun pickDirectory(current: String): String? = null

    override suspend fun openFolder(path: String): Boolean = false

    override suspend fun openFile(path: String): Boolean = withContext(Dispatchers.Main) {
        runCatching {
            val file = File(path)
            require(file.isFile) { "文件不存在" }
            val uri = fileUri(file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeTypeOf(file.name))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        }.getOrElse {
            AppLog.warn("platform", "打开文件失败", it)
            false
        }
    }

    override suspend fun shareFile(path: String): Boolean = withContext(Dispatchers.Main) {
        runCatching {
            val file = File(path)
            require(file.isFile) { "文件不存在" }
            val uri = fileUri(file)
            val intent = Intent(Intent.ACTION_SEND)
                .setType(mimeTypeOf(file.name))
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            appContext.startActivity(Intent.createChooser(intent, "分享文件").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrElse {
            AppLog.warn("platform", "分享文件失败", it)
            false
        }
    }

    override suspend fun openUrl(url: String): Boolean = withContext(Dispatchers.Main) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        }.getOrElse {
            AppLog.warn("platform", "打开链接失败", it)
            false
        }
    }

    override suspend fun notifyCompleted(taskId: Long, name: String, path: String) {
        if (!canNotify()) return
        ensureChannel()
        val contentIntent = runCatching {
            val file = File(path)
            val intent = if (file.isFile) {
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(fileUri(file), mimeTypeOf(file.name))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                Intent(appContext, MainActivity::class.java)
            }
            PendingIntent.getActivity(
                appContext,
                taskId.hashCode(),
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }.getOrNull()

        val icon = appContext.resources.getIdentifier("ic_stat_xlite", "drawable", appContext.packageName)
            .takeIf { it != 0 }
            ?: android.R.drawable.stat_sys_download
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("下载完成")
            .setContentText(name)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        runCatching {
            NotificationManagerCompat.from(appContext).notify(taskId.hashCode(), builder.build())
        }.onFailure { AppLog.warn("platform", "发送完成通知失败", it) }
    }

    fun ensureChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "下载完成",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "下载任务完成时提醒"
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    private fun fileUri(file: File): Uri =
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)

    private fun mimeTypeOf(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    companion object {
        const val CHANNEL_ID = "downloads"
    }
}
