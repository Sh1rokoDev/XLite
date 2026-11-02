package com.xlite.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.xlite.downloader.core.AppLog
import com.xlite.downloader.domain.AppUiState
import com.xlite.downloader.domain.model.TaskStatus
import com.xlite.downloader.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        when (intent?.action) {
            ACTION_PAUSE_ALL -> scope.launch { container().engine.pauseAll() }
            ACTION_RESUME_ALL -> scope.launch { container().engine.resumeAll() }
        }
        if (watcher == null) {
            watcher = scope.launch {
                container().state.collectLatest { state ->
                    updateNotification(state)
                    if (state.activeCount > 0) {
                        acquireWakeLock()
                    } else {
                        releaseWakeLock()
                        delay(2_500)
                        if (container().state.value.activeCount == 0) stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun container(): AppContainer = (application as XLiteApplication).container

    private fun ensureForeground() {
        val notification = buildNotification(container().state.value)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun updateNotification(state: AppUiState) {
        if (state.activeCount == 0) return
        val notification = buildNotification(state)
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }.onFailure { AppLog.warn("service", "更新前台通知失败", it) }
    }

    private fun buildNotification(state: AppUiState): android.app.Notification {
        val activeTasks = state.tasks.filter {
            it.status.isActive || (it.desired == com.xlite.downloader.domain.model.DesiredState.RUNNING && !it.status.isTerminal)
        }
        val totalBytes = activeTasks.sumOf { if (it.totalBytes > 0) it.totalBytes else 0L }
        val downloaded = activeTasks.sumOf { it.downloadedBytes }
        val knownProgress = totalBytes > 0
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadForegroundService::class.java).setAction(ACTION_PAUSE_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val resumeIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, DownloadForegroundService::class.java).setAction(ACTION_RESUME_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = resources.getIdentifier("ic_stat_xlite", "drawable", packageName)
            .takeIf { it != 0 }
            ?: android.R.drawable.stat_sys_download
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("XLite 正在下载")
            .setContentText(
                if (activeTasks.isEmpty()) {
                    "正在准备下载"
                } else {
                    "${activeTasks.size} 个任务 · ${Formatters.speed(state.totalSpeedBps)} · ${Formatters.bytes(downloaded)}"
                },
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "全部暂停", pauseIntent)
            .addAction(0, "全部继续", resumeIntent)
        if (knownProgress) {
            builder.setProgress(1000, ((downloaded.toDouble() / totalBytes) * 1000).toInt().coerceIn(0, 1000), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "下载进度",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "下载任务运行时的常驻进度通知"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    @Suppress("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "XLite:download").apply {
            setReferenceCounted(false)
            runCatching { acquire() }.onFailure { AppLog.warn("service", "获取唤醒锁失败", it) }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "download_progress"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE_ALL = "com.xlite.downloader.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.xlite.downloader.RESUME_ALL"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DownloadForegroundService::class.java),
                )
            }.onFailure { AppLog.warn("service", "启动前台服务失败", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DownloadForegroundService::class.java)) }
        }
    }
}
