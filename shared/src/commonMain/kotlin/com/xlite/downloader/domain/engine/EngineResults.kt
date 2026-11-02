package com.xlite.downloader.domain.engine

data class ActionResult(
    val ok: Boolean,
    val message: String,
)

data class AddFailure(
    val url: String,
    val reason: String,
)

data class AddTasksOutcome(
    val added: Int,
    val failures: List<AddFailure>,
) {
    val message: String
        get() = buildString {
            if (added > 0) append("已添加 $added 个任务")
            if (failures.isNotEmpty()) {
                if (isNotEmpty()) append("；")
                append("${failures.size} 个失败")
            }
            if (isEmpty()) append("没有可添加的任务")
        }
}
