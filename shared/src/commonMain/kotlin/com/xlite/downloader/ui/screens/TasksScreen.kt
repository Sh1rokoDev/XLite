@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xlite.downloader.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xlite.downloader.ui.components.SecondaryButton
import com.xlite.downloader.ui.components.PrimaryButton
import com.xlite.downloader.ui.components.ResponsiveDialog
import com.xlite.downloader.ui.components.ActionRow
import com.xlite.downloader.AppContainer
import com.xlite.downloader.domain.AppUiState
import com.xlite.downloader.domain.engine.ActionResult
import com.xlite.downloader.domain.model.DesiredState
import com.xlite.downloader.domain.model.DownloadTask
import com.xlite.downloader.domain.model.TaskStatus
import com.xlite.downloader.platform.PlatformBackHandler
import com.xlite.downloader.ui.components.EmptyState
import com.xlite.downloader.ui.components.FilterChipRow
import com.xlite.downloader.ui.components.InfoRow
import com.xlite.downloader.ui.components.SearchBox
import com.xlite.downloader.ui.components.StatusBadge
import com.xlite.downloader.ui.components.statusColor
import com.xlite.downloader.ui.icons.XLIcons
import com.xlite.downloader.ui.theme.XLiteColors
import com.xlite.downloader.util.Formatters
import kotlinx.coroutines.launch

private data class FilterOption(val key: String, val label: String)

private val taskFilters = listOf(
    FilterOption("all", "全部"),
    FilterOption("active", "进行中"),
    FilterOption("done", "已完成"),
    FilterOption("failed", "失败"),
)

@Composable
fun TasksScreen(
    container: AppContainer,
    state: AppUiState,
    compact: Boolean,
    authBlocked: Boolean,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var detailTaskId by remember { mutableStateOf<Long?>(null) }
    var deleteTarget by remember { mutableStateOf<DownloadTask?>(null) }
    var deleteFileToo by remember { mutableStateOf(false) }
    var batchDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.tasks) {
        val alive = state.tasks.map { it.taskId }.toSet()
        selectedIds = selectedIds.intersect(alive)
        if (detailTaskId != null && detailTaskId !in alive) detailTaskId = null
    }

    PlatformBackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    val filtered = remember(state.tasks, query, filter) {
        val search = query.trim()
        state.tasks
            .asSequence()
            .filter { task ->
                search.isEmpty() ||
                    task.saveName.contains(search, ignoreCase = true) ||
                    task.url.contains(search, ignoreCase = true)
            }
            .filter { task ->
                when (filter) {
                    "active" -> task.status.isActive || task.desired == DesiredState.RUNNING && !task.status.isTerminal
                    "done" -> task.status == TaskStatus.COMPLETED
                    "failed" -> task.status == TaskStatus.FAILED
                    else -> true
                }
            }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    fun run(action: suspend () -> ActionResult) {
        scope.launch { showMessage(action().message) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TaskHeader(
            state = state,
            compact = compact,
            authBlocked = authBlocked,
            query = query,
            onQueryChange = { query = it },
            filter = filter,
            onFilterChange = { filter = it },
            onAdd = onAdd,
            selectionMode = selectionMode,
            onToggleSelection = {
                selectionMode = !selectionMode
                if (!selectionMode) selectedIds = emptySet()
            },
            onOpenSettings = onOpenSettings,
        )

        if (selectionMode || selectedIds.isNotEmpty()) {
            BatchBar(
                count = selectedIds.size,
                compact = compact,
                canSelect = filtered.isNotEmpty(),
                onSelectAll = { selectedIds = selectedIds + filtered.map { it.taskId } },
                onInvert = {
                    val visible = filtered.map { it.taskId }.toSet()
                    selectedIds = (selectedIds - visible) + (visible - selectedIds)
                },
                enabled = !authBlocked,
                onPause = {
                    selectedIds.forEach { id ->
                        scope.launch {
                            val result = container.engine.pause(id)
                            if (!result.ok) showMessage(result.message)
                        }
                    }
                },
                onResume = {
                    selectedIds.forEach { id ->
                        scope.launch {
                            val result = container.engine.resume(id)
                            if (!result.ok) showMessage(result.message)
                        }
                    }
                },
                onDelete = { batchDelete = true },
                onCancel = {
                    selectedIds = emptySet()
                    selectionMode = false
                },
            )
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.tasks.isEmpty()) {
                    EmptyState(
                        icon = XLIcons.Download,
                        title = if (authBlocked) "登录后即可下载" else "暂无下载任务",
                        description = if (authBlocked) {
                            "请在设置中恢复 API Key 后继续使用。"
                        } else {
                            "添加链接开始下载。"
                        },
                        action = if (compact && !authBlocked) null else {
                            {
                                if (authBlocked) {
                                    PrimaryButton(onClick = onOpenSettings) { Text("打开设置") }
                                } else {
                                    PrimaryButton(onClick = onAdd) {
                                        Icon(XLIcons.Add, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("添加下载")
                                    }
                                }
                            }
                        },
                    )
                } else {
                    EmptyState(
                        icon = XLIcons.CheckCircle,
                        title = "没有匹配的任务",
                        description = "试试其他关键词或筛选条件。",
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = if (compact) 16.dp else 28.dp,
                    end = if (compact) 16.dp else 28.dp,
                    top = 12.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = filtered, key = { it.taskId }) { task ->
                    TaskCard(
                        task = task,
                        pending = state.meta.pendingTaskIds.contains(task.taskId),
                        authBlocked = authBlocked,
                        selected = task.taskId in selectedIds,
                        selectionMode = selectionMode,
                        canOpenFile = container.actions.canOpenFolder || container.actions.canShareFile,
                        canOpenFolder = container.actions.canOpenFolder,
                        canShare = container.actions.canShareFile,
                        onToggleSelected = {
                            selectedIds = if (task.taskId in selectedIds) {
                                selectedIds - task.taskId
                            } else {
                                selectedIds + task.taskId
                            }
                        },
                        onOpenDetail = { detailTaskId = task.taskId },
                        onPause = { run { container.engine.pause(task.taskId) } },
                        onResume = { run { container.engine.resume(task.taskId) } },
                        onRetry = { run { container.engine.retry(task.taskId) } },
                        onOpenFile = {
                            scope.launch {
                                if (!container.actions.openFile(task.displayPath)) {
                                    showMessage("无法打开文件，请检查文件位置、访问权限或默认打开方式")
                                }
                            }
                        },
                        onOpenFolder = {
                            scope.launch {
                                if (!container.actions.openFolder(task.saveDir)) {
                                    showMessage("无法打开下载目录")
                                }
                            }
                        },
                        onShare = {
                            scope.launch {
                                if (!container.actions.shareFile(task.displayPath)) {
                                    showMessage("分享失败，文件可能不存在")
                                }
                            }
                        },
                        onCopyLink = {
                            clipboard.setText(AnnotatedString(task.url))
                            showMessage("链接已复制")
                        },
                        onDelete = {
                            deleteTarget = task
                            deleteFileToo = false
                        },
                    )
                }
            }
        }
    }

    detailTaskId?.let { id ->
        val task = state.tasks.firstOrNull { it.taskId == id }
        if (task != null) {
            TaskDetailDialog(
                task = task,
                pending = state.meta.pendingTaskIds.contains(task.taskId),
                authBlocked = authBlocked,
                canOpenFile = container.actions.canOpenFolder || container.actions.canShareFile,
                canOpenFolder = container.actions.canOpenFolder,
                canShare = container.actions.canShareFile,
                onDismiss = { detailTaskId = null },
                onPause = { run { container.engine.pause(task.taskId) } },
                onResume = { run { container.engine.resume(task.taskId) } },
                onRetry = { run { container.engine.retry(task.taskId) } },
                onOpenFile = {
                    scope.launch {
                        if (!container.actions.openFile(task.displayPath)) showMessage("无法打开文件")
                    }
                },
                onOpenFolder = {
                    scope.launch {
                        if (!container.actions.openFolder(task.saveDir)) showMessage("无法打开下载目录")
                    }
                },
                onShare = {
                    scope.launch {
                        if (!container.actions.shareFile(task.displayPath)) showMessage("分享失败")
                    }
                },
                onCopyLink = {
                    clipboard.setText(AnnotatedString(task.url))
                    showMessage("链接已复制")
                },
                onDelete = {
                    detailTaskId = null
                    deleteTarget = task
                    deleteFileToo = false
                },
            )
        }
    }

    deleteTarget?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除任务") },
            text = {
                Column {
                    Text("确定删除“${task.saveName}”吗？")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deleteFileToo = !deleteFileToo },
                    ) {
                        Checkbox(checked = deleteFileToo, onCheckedChange = { deleteFileToo = it })
                        Text("同时删除已下载的文件", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                SecondaryButton(destructive = true,
                    onClick = {
                        val target = task
                        deleteTarget = null
                        run { container.engine.delete(target.taskId, deleteFileToo) }
                    },
                ) {
                    Text("删除", color = XLiteColors.Danger)
                }
            },
            dismissButton = {
                SecondaryButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    if (batchDelete) {
        AlertDialog(
            onDismissRequest = { batchDelete = false },
            title = { Text("批量删除") },
            text = { Text("确定删除选中的 ${selectedIds.size} 个任务吗？已下载的文件会保留。") },
            confirmButton = {
                SecondaryButton(destructive = true,
                    onClick = {
                        val ids = selectedIds
                        batchDelete = false
                        selectedIds = emptySet()
                        selectionMode = false
                        scope.launch {
                            var failed = 0
                            ids.forEach { id ->
                                if (!container.engine.delete(id, deleteFile = false).ok) failed++
                            }
                            showMessage(if (failed == 0) "已删除 ${ids.size} 个任务" else "已删除 ${ids.size - failed} 个，$failed 个失败")
                        }
                    },
                ) {
                    Text("删除", color = XLiteColors.Danger)
                }
            },
            dismissButton = {
                SecondaryButton(onClick = { batchDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TaskHeader(
    state: AppUiState,
    compact: Boolean,
    authBlocked: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: String,
    onFilterChange: (String) -> Unit,
    onAdd: () -> Unit,
    selectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var searchExpanded by remember { mutableStateOf(query.isNotEmpty()) }
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    fun closeSearch() {
        onQueryChange("")
        searchExpanded = false
        focusManager.clearFocus()
    }
    @Composable
    fun Filters(modifier: Modifier = Modifier) {
        FilterChipRow(
            options = taskFilters.map { it.key to it.label },
            selectedKey = filter,
            onSelect = onFilterChange,
            modifier = modifier,
        )
    }
    Column(
        Modifier.fillMaxWidth().padding(
            horizontal = if (compact) 16.dp else 28.dp,
            vertical = if (compact) 12.dp else 24.dp,
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val inlineFilters = maxWidth >= 820.dp
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("下载任务", style = MaterialTheme.typography.headlineSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (inlineFilters && state.tasks.isNotEmpty()) {
                                Spacer(Modifier.width(18.dp))
                                Filters(Modifier.weight(1f))
                            }
                        }
                        if (state.activeCount > 0) {
                            Text(
                                "${state.activeCount} 个进行中 · ${Formatters.speed(state.totalSpeedBps)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(XLIcons.Settings, contentDescription = "设置")
                    }
                    if (state.tasks.isNotEmpty()) {
                        IconButton(onClick = onToggleSelection) {
                            Icon(
                                XLIcons.MultiSelect,
                                contentDescription = if (selectionMode) "退出多选" else "多选",
                                tint = if (selectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Box {
                            IconButton(onClick = { searchExpanded = !searchExpanded }) {
                                Icon(
                                    XLIcons.Search,
                                    contentDescription = if (searchExpanded) "搜索已展开" else "搜索任务",
                                    tint = if (query.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            DropdownMenu(
                                expanded = searchExpanded,
                                onDismissRequest = { searchExpanded = false },
                                properties = PopupProperties(focusable = true),
                            ) {
                                SearchBox(
                                    value = query, onValueChange = onQueryChange,
                                    modifier = Modifier.padding(horizontal = 8.dp).width(360.dp),
                                    fieldModifier = Modifier.focusRequester(searchFocus).onPreviewKeyEvent {
                                        if (it.key == Key.Escape) { searchExpanded = false; true } else false
                                    },
                                    onClose = ::closeSearch,
                                )
                                LaunchedEffect(Unit) { searchFocus.requestFocus() }
                            }
                        }
                    }
                    if (compact) {
                        IconButton(onClick = onAdd, enabled = !authBlocked) {
                            Icon(XLIcons.Add, contentDescription = "添加下载")
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton(onClick = onAdd, enabled = !authBlocked) {
                            Icon(XLIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("添加下载")
                        }
                    }
                }
                if (!inlineFilters && state.tasks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Filters(Modifier.fillMaxWidth())
                }
            }
        }
        if (authBlocked) {
            Spacer(Modifier.height(12.dp))
            AuthBanner(onOpenSettings)
        }
    }
}

@Composable
private fun AuthBanner(onOpenSettings: () -> Unit) {
    Surface(
        color = XLiteColors.Warning.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(XLIcons.Warning, contentDescription = null, tint = XLiteColors.Warning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "下载引擎未登录，任务已暂停操作。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(onClick = onOpenSettings) { Text("去设置") }
        }
    }
}

@Composable
private fun BatchBar(
    count: Int,
    compact: Boolean,
    canSelect: Boolean,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    enabled: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 28.dp),
    ) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已选 $count 项", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
            SecondaryButton(onClick = onSelectAll, enabled = canSelect, compact = true) { Text("全选") }
            SecondaryButton(onClick = onInvert, enabled = canSelect, compact = true) { Text("反选") }
            SecondaryButton(onClick = onPause, enabled = enabled && count > 0, compact = true) { Text("暂停") }
            SecondaryButton(onClick = onResume, enabled = enabled && count > 0, compact = true) { Text("继续") }
            SecondaryButton(destructive = true, onClick = onDelete, enabled = enabled && count > 0, compact = true) { Text("删除") }
            SecondaryButton(onClick = onCancel, compact = true) { Text("取消") }
        }
    }
}

@Composable
private fun TaskCard(
    task: DownloadTask,
    pending: Boolean,
    authBlocked: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    canOpenFile: Boolean,
    canOpenFolder: Boolean,
    canShare: Boolean,
    onToggleSelected: () -> Unit,
    onOpenDetail: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = task.progress
    val animatedProgress by animateFloatAsState(progress ?: 0f, label = "task-progress")
    val color = statusColor(task.status)
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val cardColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            pressed -> lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, 0.08f)
            focused -> lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, 0.06f)
            hovered -> lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, 0.035f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "task-card-color",
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val inlineActions = maxWidth >= 900.dp
        @Composable
        fun Actions() {
            TaskRowActions(
                task = task,
                pending = pending,
                enabled = !authBlocked && !pending,
                canOpenFile = canOpenFile,
                canOpenFolder = canOpenFolder,
                canShare = canShare,
                onOpenDetail = onOpenDetail,
                onPause = onPause,
                onResume = onResume,
                onRetry = onRetry,
                onOpenFile = onOpenFile,
                onOpenFolder = onOpenFolder,
                onShare = onShare,
                onCopyLink = onCopyLink,
                onDelete = onDelete,
            )
        }
        Surface(
            color = cardColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(
                1.dp,
                if (selected || focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                .clickable(
                    interactionSource = interactions,
                    indication = null,
                    onClickLabel = if (selectionMode) "选择任务" else "查看详情",
                ) { if (selectionMode) onToggleSelected() else onOpenDetail() },
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Box(
                        modifier = Modifier.width(60.dp).height(64.dp)
                            .semantics { contentDescription = "选择 " + task.saveName }
                            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggleSelected() }),
                        contentAlignment = Alignment.Center,
                    ) { Checkbox(checked = selected, onCheckedChange = null) }
                } else {
                    Box(
                        modifier = Modifier.width(60.dp).height(64.dp).background(color.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = when (task.status) {
                                TaskStatus.COMPLETED -> XLIcons.CheckCircle
                                TaskStatus.FAILED -> XLIcons.Warning
                                TaskStatus.PAUSED, TaskStatus.PAUSING -> XLIcons.Pause
                                else -> XLIcons.Download
                            },
                            contentDescription = task.status.label,
                            tint = color,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                task.saveName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (task.status == TaskStatus.DOWNLOADING || task.status == TaskStatus.CONNECTING) {
                                Text(
                                    Formatters.speed(task.speedBps) + (Formatters.eta(task)?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                task.url,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when {
                                    task.totalBytes > 0 -> "${Formatters.bytes(task.downloadedBytes)} / ${Formatters.bytes(task.totalBytes)}"
                                    task.status == TaskStatus.COMPLETED -> Formatters.bytes(task.downloadedBytes)
                                    else -> "大小未知"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(Formatters.percent(task), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
                        }
                        Spacer(Modifier.height(9.dp))
                        when {
                            progress != null || task.status == TaskStatus.COMPLETED -> LinearProgressIndicator(
                                progress = { if (task.status == TaskStatus.COMPLETED) 1f else animatedProgress },
                                modifier = Modifier.fillMaxWidth().height(5.dp).testTag("task-progress-${task.taskId}"),
                                color = color,
                                trackColor = color.copy(alpha = 0.14f),
                            )
                            task.status.isActive -> LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(5.dp).testTag("task-progress-${task.taskId}"),
                                color = color,
                                trackColor = color.copy(alpha = 0.14f),
                            )
                            else -> Box(
                                Modifier.fillMaxWidth().height(5.dp).testTag("task-progress-${task.taskId}")
                                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)),
                            )
                        }
                        if (task.status == TaskStatus.FAILED && !task.errorMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                task.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = XLiteColors.Danger,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!inlineActions && !selectionMode) {
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Actions() }
                    }
                }
                if (inlineActions && !selectionMode) {
                    Spacer(Modifier.width(12.dp))
                    Actions()
                }
            }
        }
    }
}

@Composable
private fun TaskRowActions(
    task: DownloadTask,
    pending: Boolean,
    enabled: Boolean,
    canOpenFile: Boolean,
    canOpenFolder: Boolean,
    canShare: Boolean,
    onOpenDetail: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val primaryActionWidth = with(LocalDensity.current) {
        (MaterialTheme.typography.labelLarge.fontSize * 4).toDp()
    } + 56.dp
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(primaryActionWidth), contentAlignment = Alignment.CenterEnd) {
            if (task.status == TaskStatus.COMPLETED && canOpenFile && !pending) {
                SecondaryButton(onClick = onOpenFile, modifier = Modifier.fillMaxWidth()) {
                    Icon(XLIcons.FileOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("打开文件")
                }
            } else {
                TaskPrimaryAction(task, pending, enabled, onPause, onResume, onRetry, Modifier.fillMaxWidth())
            }
        }
        if (canOpenFolder) SecondaryButton(onClick = onOpenFolder) {
            Icon(XLIcons.Folder, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("打开所在文件夹")
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(XLIcons.MoreVert, contentDescription = "更多操作")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("查看详情") }, onClick = { menuOpen = false; onOpenDetail() })
                DropdownMenuItem(text = { Text("复制链接") }, onClick = { menuOpen = false; onCopyLink() })
                if (canShare) {
                    DropdownMenuItem(text = { Text("分享文件") }, onClick = { menuOpen = false; onShare() })
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("删除任务", color = XLiteColors.Danger) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun TaskPrimaryAction(
    task: DownloadTask,
    pending: Boolean,
    enabled: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pending) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("处理中", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    when (task.status) {
        TaskStatus.DOWNLOADING, TaskStatus.CONNECTING, TaskStatus.QUEUED -> {
            SecondaryButton(onClick = onPause, enabled = enabled, modifier = modifier) {
                Icon(XLIcons.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("暂停")
            }
        }
        TaskStatus.PAUSING -> {
            SecondaryButton(onClick = {}, enabled = false, modifier = modifier) {
                Text("暂停中")
            }
        }
        TaskStatus.PAUSED, TaskStatus.UNKNOWN -> {
            PrimaryButton(onClick = onResume, enabled = enabled, modifier = modifier) {
                Icon(XLIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("继续")
            }
        }
        TaskStatus.FAILED -> {
            PrimaryButton(onClick = onRetry, enabled = enabled, modifier = modifier) {
                Icon(XLIcons.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("重试")
            }
        }
        TaskStatus.COMPLETED -> Unit
    }
}

@Composable
private fun TaskDetailDialog(
    task: DownloadTask,
    pending: Boolean,
    authBlocked: Boolean,
    canOpenFile: Boolean,
    canOpenFolder: Boolean,
    canShare: Boolean,
    onDismiss: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit,
) {
    ResponsiveDialog(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusBadge(task.status)
                        Text(
                            task.saveName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        task.url,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDismiss) {
                    Icon(XLIcons.Close, contentDescription = "关闭")
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                task.progress != null || task.status == TaskStatus.COMPLETED -> LinearProgressIndicator(
                    progress = { task.progress ?: 1f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = statusColor(task.status),
                    trackColor = statusColor(task.status).copy(alpha = 0.12f),
                )
                task.status.isActive -> LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = statusColor(task.status),
                    trackColor = statusColor(task.status).copy(alpha = 0.12f),
                )
                else -> Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)))
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    if (task.totalBytes > 0) {
                        "${Formatters.bytes(task.downloadedBytes)} / ${Formatters.bytes(task.totalBytes)}"
                    } else {
                        Formatters.bytes(task.downloadedBytes)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(Formatters.percent(task), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(18.dp))
            @Composable
            fun Metadata() {
                if (task.status.isActive) InfoRow("速度", Formatters.speed(task.speedBps))
                InfoRow("保存位置", task.saveDir)
                InfoRow("文件名", task.saveName)
                InfoRow("创建时间", Formatters.dateTime(task.createdAt))
                task.completedAt?.let { InfoRow("完成时间", Formatters.dateTime(it)) }
                task.errorMessage?.let { InfoRow("错误信息", it, XLiteColors.Danger) }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 520.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(0.48f)) { TrafficDetails(task.traffic) }
                        Column(Modifier.weight(0.52f)) { Metadata() }
                    }
                } else {
                    Column {
                        TrafficDetails(task.traffic)
                        Spacer(Modifier.height(16.dp))
                        Metadata()
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            ActionRow {
                when (task.status) {
                    TaskStatus.DOWNLOADING, TaskStatus.CONNECTING, TaskStatus.QUEUED ->
                        SecondaryButton(onClick = onPause, enabled = !authBlocked && !pending) { Text("暂停") }
                    TaskStatus.PAUSED, TaskStatus.UNKNOWN ->
                        PrimaryButton(onClick = onResume, enabled = !authBlocked && !pending) { Text("继续") }
                    TaskStatus.FAILED ->
                        PrimaryButton(onClick = onRetry, enabled = !authBlocked && !pending) { Text("重试") }
                    else -> Unit
                }
                if (canOpenFile) {
                    SecondaryButton(onClick = onOpenFile) { Text("打开文件") }
                }
                if (canOpenFolder) {
                    SecondaryButton(onClick = onOpenFolder) { Text("打开文件夹") }
                }
                SecondaryButton(onClick = onCopyLink) { Text("复制链接") }
                if (canShare) SecondaryButton(onClick = onShare) { Text("分享") }
                SecondaryButton(destructive = true, onClick = onDelete) { Text("删除", color = XLiteColors.Danger) }
            }
        }
    }
}

@Composable
private fun TrafficDetails(traffic: com.xlite.downloader.domain.model.TaskTraffic?) {
    Text("下载来源", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    if (traffic == null || traffic.total <= 0) {
        Text(if (traffic == null) "暂无来源统计" else "等待下载数据",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        val sources = listOf(
            Triple("原始服务器", traffic.originBytes, MaterialTheme.colorScheme.primary),
            Triple("P2P 加速", traffic.p2pBytes, Color(0xFF10B981)),
            Triple("P2S 加速", traffic.p2sBytes, Color(0xFF8B5CF6)),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 400.dp
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp),
            ) {
                val description = sources.joinToString("，") { (label, bytes, _) ->
                    val percent = (traffic.fraction(bytes) * 1000).toInt() / 10.0
                    "$label $percent%"
                }
                Canvas(
                    Modifier.size(if (compact) 80.dp else 112.dp)
                        .semantics { contentDescription = "下载来源：$description" },
                ) {
                    var startAngle = -90f
                    val nonEmpty = sources.filter { it.second > 0 }
                    nonEmpty.forEachIndexed { index, (_, bytes, color) ->
                        val sweep = if (index == nonEmpty.lastIndex) 270f - startAngle else traffic.fraction(bytes) * 360f
                        drawArc(color = color, startAngle = startAngle, sweepAngle = sweep, useCenter = true)
                        startAngle += sweep
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    sources.forEach { (label, bytes, color) ->
                        val percent = (traffic.fraction(bytes) * 1000).toInt() / 10.0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${Formatters.bytes(bytes)} · $percent%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
