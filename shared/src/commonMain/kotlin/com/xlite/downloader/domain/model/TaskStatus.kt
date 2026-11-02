package com.xlite.downloader.domain.model

enum class TaskStatus(val label: String) {
    UNKNOWN("未知"),
    QUEUED("等待中"),
    CONNECTING("连接中"),
    DOWNLOADING("下载中"),
    PAUSING("暂停中"),
    PAUSED("已暂停"),
    COMPLETED("已完成"),
    FAILED("失败"),
    ;

    val isActive: Boolean
        get() = this == QUEUED || this == CONNECTING || this == DOWNLOADING || this == PAUSING

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED

    companion object {
        fun fromSdkState(code: Int): TaskStatus = when (code) {
            3 -> QUEUED
            4 -> CONNECTING
            5 -> DOWNLOADING
            6 -> PAUSING
            7 -> PAUSED
            8 -> COMPLETED
            9 -> FAILED
            else -> UNKNOWN
        }
    }
}

enum class DesiredState {
    RUNNING,
    PAUSED,
    NONE,
}
