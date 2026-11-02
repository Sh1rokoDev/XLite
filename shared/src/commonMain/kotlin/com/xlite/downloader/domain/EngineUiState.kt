package com.xlite.downloader.domain

import com.xlite.downloader.domain.model.AppSettings
import com.xlite.downloader.domain.model.AuthState
import com.xlite.downloader.domain.model.DownloadTask
import com.xlite.downloader.domain.model.TaskStatus

data class EngineMeta(
    val engineVersion: String? = null,
    val initializing: Boolean = false,
    val globalError: String? = null,
    val pendingTaskIds: Set<Long> = emptySet(),
)

data class AppUiState(
    val auth: AuthState,
    val tasks: List<DownloadTask>,
    val settings: AppSettings,
    val meta: EngineMeta,
) {
    val totalSpeedBps: Long = tasks.sumOf { task ->
        if (task.status == TaskStatus.DOWNLOADING || task.status == TaskStatus.CONNECTING) task.speedBps else 0L
    }
    val activeCount: Int = tasks.count { it.status.isActive }
    val completedCount: Int = tasks.count { it.status == TaskStatus.COMPLETED }
    val failedCount: Int = tasks.count { it.status == TaskStatus.FAILED }

    val isReady: Boolean = auth is AuthState.Ready
}
