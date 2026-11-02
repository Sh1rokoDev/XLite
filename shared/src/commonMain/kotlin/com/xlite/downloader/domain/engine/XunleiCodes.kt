package com.xlite.downloader.domain.engine

object XunleiCodes {
    const val SUCCESS = 0
    const val FAILED = 1
    const val ALREADY_INIT = 9101
    const val SDK_NOT_INIT = 9102
    const val TASK_ALREADY_EXIST = 9103
    const val TASK_NOT_EXIST = 9104
    const val TASK_ALREADY_STOPPED = 9105
    const val TASK_ALREADY_RUNNING = 9106
    const val TASK_NOT_START = 9107
    const val TASK_STILL_RUNNING = 9108
    const val FILE_EXISTED = 9109
    const val DISK_FULL = 9110
    const val TOO_MUCH_TASK = 9111
    const val SCHEMA_NOT_SUPPORT = 9113
    const val APPNAME_APPKEY_ERROR = 9116
    const val TASK_FINISH = 9118
    const val TASK_FILENAME_CONFLICT = 9403
    const val TASK_NOT_RUNNING = 9119
    const val TASK_TYPE_NOT_SUPPORT = 9121
    const val FILE_NAME_TOO_LONG = 9125
    const val FULL_PATH_NAME_TOO_LONG = 9127
    const val NOT_WIFI_MODE = 9130
    const val SPEED_LIMIT_TO_SMALL = 9131
    const val AUTH_TOKEN_VERIFY_FAILED = 9602
    const val AUTH_SCOPE_VERIFY_FAILED = 9603
    const val AUTH_SESSION_ID_VERIFY_FAILED = 9604
    const val AUTH_SESSION_ID_EXPIRED = 9605
    const val AUTH_RES_HAS_NO_QUOTA = 9606
    const val INSUFFICIENT_DISK_SPACE = 111085
    const val OPEN_FILE_ERROR = 111128
    const val NO_DATA_PIPE = 111136
    const val RESTRICTION = 111151
    const val ACCOUNT_EXCEPTION = 111152
    const val RESTRICTION_AREA = 111153
    const val COPYRIGHT_BLOCKING = 111154
    const val TYPE2_BLOCKING = 111155
    const val TYPE3_BLOCKING = 111156
    const val LONG_TIME_NO_DATA = 111176
    const val TIME_OUT = 119212
    const val TASK_STATUS_ERR = 999999

    fun isAuthError(code: Int): Boolean = code == APPNAME_APPKEY_ERROR ||
        code == AUTH_TOKEN_VERIFY_FAILED ||
        code == AUTH_SCOPE_VERIFY_FAILED ||
        code == AUTH_SESSION_ID_VERIFY_FAILED ||
        code == AUTH_SESSION_ID_EXPIRED

    fun isAlreadyStopped(code: Int): Boolean = code == TASK_ALREADY_STOPPED ||
        code == TASK_NOT_START ||
        code == TASK_NOT_RUNNING

    fun isTaskMissing(code: Int): Boolean = code == TASK_NOT_EXIST
}
