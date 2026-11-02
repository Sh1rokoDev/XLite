@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xlite.downloader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xlite.downloader.ui.components.SecondaryButton
import com.xlite.downloader.ui.components.PrimaryButton
import com.xlite.downloader.ui.components.ActionRow
import com.xlite.downloader.AppContainer
import com.xlite.downloader.auth.AppIdResolver
import com.xlite.downloader.domain.AppUiState
import com.xlite.downloader.domain.engine.ConnectionTestResult
import com.xlite.downloader.domain.model.AppSettings
import com.xlite.downloader.domain.model.AuthState
import com.xlite.downloader.ui.components.InfoRow
import com.xlite.downloader.ui.components.SectionCard
import com.xlite.downloader.ui.icons.XLIcons
import com.xlite.downloader.ui.theme.XLiteColors
import com.xlite.downloader.util.Formatters
import com.xlite.downloader.util.SpeedLimits
import com.xlite.downloader.util.SpeedUnit
import kotlinx.coroutines.launch
import kotlin.time.Clock
import okio.Path.Companion.toPath

@Composable
fun SettingsScreen(
    container: AppContainer,
    state: AppUiState,
    isDesktop: Boolean,
    compact: Boolean,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var override by remember { mutableStateOf(state.settings.appIdOverride.orEmpty()) }
    var editCredentials by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(state.settings.appIdOverride.isNullOrBlank().not()) }
    var accountBusy by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDynamic by remember { mutableStateOf(false) }

    LaunchedEffect(state.settings.appIdOverride) {
        if (override.isBlank()) override = state.settings.appIdOverride.orEmpty()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch {
            val result = container.engine.updateSettings(transform(container.settings.settings.value))
            showMessage(result.message)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 16.dp else 28.dp, vertical = if (compact) 12.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth()) {
            if (!compact) {
                Text(
                    "账号与下载偏好",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
            }

            SectionCard(
                title = "账号与引擎",
                subtitle = "API Key 保存在本机。",
            ) {
                val authStatus = when (val auth = state.auth) {
                    is AuthState.Ready -> "已连接 · 自动续期"
                    AuthState.NotConfigured -> "未配置"
                    AuthState.Validating -> "登录中"
                    is AuthState.Expired -> "已过期"
                    is AuthState.Error -> "错误：${auth.message}"
                }
                val statusColor = when (state.auth) {
                    is AuthState.Ready -> XLiteColors.Success
                    is AuthState.Expired, is AuthState.Error -> XLiteColors.Danger
                    AuthState.Validating -> XLiteColors.Warning
                    AuthState.NotConfigured -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                ActionRow(leading = {
                    Column(Modifier.widthIn(max = 220.dp)) {
                        Text("状态", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(authStatus, style = MaterialTheme.typography.bodyMedium, color = statusColor,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }) {
                    SecondaryButton(onClick = { showDiagnostics = !showDiagnostics }) {
                        Text(if (showDiagnostics) "收起连接详情" else "连接详情")
                    }
                    SecondaryButton(
                        onClick = {
                            scope.launch {
                                testing = true
                                val result = if (key.isNotBlank()) {
                                    container.engine.testConnection(
                                        key.trim(),
                                        override.trim().takeIf { it.isNotEmpty() },
                                    )
                                } else {
                                    container.engine.testStoredConnection()
                                }
                                testResult = result
                                testing = false
                            }
                        },
                        enabled = !testing && !accountBusy,
                    ) {
                        if (testing) {
                            CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(7.dp))
                        }
                        Text("测试连接")
                    }
                    if (state.auth !is AuthState.Ready) {
                        SecondaryButton(
                            onClick = {
                                scope.launch {
                                    accountBusy = true
                                    showMessage(container.engine.reconnect().message)
                                    accountBusy = false
                                }
                            },
                            enabled = !accountBusy,
                        ) {
                            Text("使用已保存的 Key 重新登录")
                        }
                    }
                    SecondaryButton(destructive = true, onClick = { confirmClear = true }, enabled = !accountBusy) {
                        Text("清除密钥", color = XLiteColors.Danger)
                    }
                    if (state.auth is AuthState.Ready) {
                        SecondaryButton(onClick = { editCredentials = !editCredentials }) {
                            Text(if (editCredentials) "收起密钥设置" else "更换 API Key")
                        }
                    }
                }

                if (showDiagnostics) {
                    (state.auth as? AuthState.Ready)?.let { ready ->
                        val display = AppIdResolver.displayName(ready.appId) ?: ready.appId
                        InfoRow("App ID", display)
                        val remaining = ready.tokenExpiresAtEpochSeconds - Clock.System.now().epochSeconds
                        InfoRow(
                            "当前凭证到期",
                            "${Formatters.tokenExpiry(ready.tokenExpiresAtEpochSeconds)}（${Formatters.duration(remaining)}）",
                        )
                    }
                    state.meta.engineVersion?.let { InfoRow("引擎版本", it) }
                }

                testResult?.let { result ->
                    Spacer(Modifier.height(10.dp))
                    val color = if (result.success) XLiteColors.Success else XLiteColors.Danger
                    Surface(color = color.copy(alpha = 0.09f), shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                if (result.success) "连接成功" else "连接失败：${result.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = color,
                            )
                            if (result.success) {
                                result.appId?.let {
                                    Text(
                                        "App ID：${AppIdResolver.displayName(it) ?: it}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                if (editCredentials || state.auth !is AuthState.Ready) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (state.auth is AuthState.Ready) "更换 API Key" else "设置 API Key",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = key,
                        onValueChange = {
                            key = it
                            testResult = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key") },
                        placeholder = { Text("xl_...") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) XLIcons.EyeOff else XLIcons.Eye,
                                    contentDescription = if (showKey) "隐藏" else "显示",
                                )
                            }
                        },
                    )
                    if (showAdvanced) {
                        OutlinedTextField(
                            value = override,
                            onValueChange = { override = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("App ID 覆盖（通常留空）") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    ActionRow(leading = {
                        SecondaryButton(onClick = { showAdvanced = !showAdvanced }) {
                            Text(if (showAdvanced) "收起高级选项" else "高级选项")
                        }
                    }) {
                        PrimaryButton(
                            onClick = {
                                if (key.isBlank()) {
                                    showMessage("请输入 API Key")
                                    return@PrimaryButton
                                }
                                scope.launch {
                                    accountBusy = true
                                    val result = container.engine.saveCredentials(
                                        key.trim(),
                                        override.trim().takeIf { it.isNotEmpty() },
                                    )
                                    accountBusy = false
                                    showMessage(result.message)
                                    if (result.ok) {
                                        editCredentials = false
                                        key = ""
                                        testResult = null
                                    }
                                }
                            },
                            enabled = !accountBusy && !testing && key.isNotBlank(),
                        ) {
                            if (accountBusy) {
                                CircularProgressIndicator(
                                    Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(7.dp))
                            }
                            Text("保存并登录")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "更换 Key 会短暂中断正在进行的任务。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionCard(title = "下载设置", subtitle = "修改后立即应用到下载引擎。") {
                SettingRow(
                    title = "同时下载数",
                    description = "范围 ${AppSettings.MIN_CONCURRENT_TASKS}–${AppSettings.MAX_CONCURRENT_TASKS}，超出的任务会排队。",
                ) {
                    Stepper(
                        value = state.settings.concurrentTasks,
                        min = AppSettings.MIN_CONCURRENT_TASKS,
                        max = AppSettings.MAX_CONCURRENT_TASKS,
                        enabled = true,
                        onChange = { value -> update { it.copy(concurrentTasks = value) } },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = "下载限速",
                    description = "0 表示不限速；填写时最小 ${AppSettings.MIN_DOWNLOAD_LIMIT_KBPS} KB/s。",
                ) {
                    LimitField(
                        label = "下载限速",
                        limitKbps = state.settings.downloadLimitKbps,
                        minimumKbps = AppSettings.MIN_DOWNLOAD_LIMIT_KBPS,
                        onApply = { value -> update { it.copy(downloadLimitKbps = value) } },
                        showMessage = showMessage,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = "上传",
                    description = "默认关闭。P2SP 上传可帮助资源加速，但会占用上行带宽。",
                ) {
                    Switch(
                        modifier = Modifier.semantics { contentDescription = "上传" },
                        checked = state.settings.uploadEnabled,
                        onCheckedChange = { enabled -> update { it.copy(uploadEnabled = enabled) } },
                    )
                }
                if (state.settings.uploadEnabled) {
                    SettingRow(
                        title = "上传限速",
                        description = "0 表示不限速；填写时最小 ${AppSettings.MIN_UPLOAD_LIMIT_KBPS} KB/s。",
                    ) {
                        LimitField(
                            label = "上传限速",
                            limitKbps = state.settings.uploadLimitKbps,
                            minimumKbps = AppSettings.MIN_UPLOAD_LIMIT_KBPS,
                            onApply = { value -> update { it.copy(uploadLimitKbps = value) } },
                            showMessage = showMessage,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = "动态链接加速",
                    description = "官方提示存在极低概率文件不一致，仅对支持的源生效。",
                ) {
                    Switch(
                        modifier = Modifier.semantics { contentDescription = "动态链接加速" },
                        checked = state.settings.dynamicAcceleration,
                        onCheckedChange = { enabled ->
                            if (enabled && !state.settings.dynamicAccelerationAcknowledged) {
                                confirmDynamic = true
                            } else {
                                update { it.copy(dynamicAcceleration = enabled) }
                            }
                        },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = "保存目录",
                    description = if (isDesktop) "新任务默认保存到这里。" else "Android 使用应用专属目录，卸载应用会删除文件。",
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.settings.saveDir,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).widthIn(max = if (isDesktop) 300.dp else 180.dp).padding(end = 8.dp),
                        )
                        if (container.actions.canPickDirectory) {
                            SecondaryButton(
                                onClick = {
                                    scope.launch {
                                        val picked = container.actions.pickDirectory(state.settings.saveDir) ?: return@launch
                                        runCatching { container.fileSystem.createDirectories(picked.toPath()) }
                                            .onFailure {
                                                showMessage("无法创建目录：${it.message ?: "未知错误"}")
                                                return@launch
                                            }
                                        update { it.copy(saveDir = picked) }
                                    }
                                },
                            ) {
                                Text("选择")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionCard(title = "行为") {
                SettingRow(
                    title = "下载完成通知",
                    description = "任务完成时发送系统通知。",
                ) {
                    Switch(
                        modifier = Modifier.semantics { contentDescription = "下载完成通知" },
                        checked = state.settings.notifyOnComplete,
                        onCheckedChange = { enabled -> update { it.copy(notifyOnComplete = enabled) } },
                    )
                }
                if (isDesktop) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(
                        title = "关闭窗口时最小化到托盘",
                        description = "关闭主窗口后下载继续在托盘中运行。",
                    ) {
                        Switch(
                            modifier = Modifier.semantics { contentDescription = "关闭窗口时最小化到托盘" },
                            checked = state.settings.closeToTray,
                            onCheckedChange = { enabled -> update { it.copy(closeToTray = enabled) } },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionCard(title = "关于") {
                InfoRow("应用版本", container.appVersion)
                state.meta.engineVersion?.let { InfoRow("迅雷 SDK", it) }
                InfoRow("支持协议", "HTTP / HTTPS（含 thunder:// 转换）")
                if (container.actions.canOpenFolder) {
                    Spacer(Modifier.height(12.dp))
                    ActionRow {
                        SecondaryButton(onClick = {
                            scope.launch {
                                if (!container.actions.openFolder(container.paths.logsDir.toString())) {
                                    showMessage("无法打开日志目录")
                                }
                            }
                        }) { Text("日志目录") }
                        SecondaryButton(onClick = {
                            scope.launch {
                                if (!container.actions.openFolder(container.paths.dataDir.toString())) {
                                    showMessage("无法打开数据目录")
                                }
                            }
                        }) { Text("数据目录") }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除 API Key") },
            text = { Text("清除后将停止所有任务并退出登录。已下载的文件会保留，任务记录仍会显示。") },
            confirmButton = {
                SecondaryButton(destructive = true,
                    onClick = {
                        confirmClear = false
                        scope.launch {
                            accountBusy = true
                            showMessage(container.engine.clearCredentials().message)
                            accountBusy = false
                            key = ""
                            testResult = null
                        }
                    },
                ) {
                    Text("清除", color = XLiteColors.Danger)
                }
            },
            dismissButton = {
                SecondaryButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }

    if (confirmDynamic) {
        AlertDialog(
            onDismissRequest = { confirmDynamic = false },
            title = { Text("启用动态链接加速？") },
            text = {
                Text(
                    "迅雷官方说明：动态链接加速在极低概率下可能导致下载文件与源文件不一致。该功能默认开启，可随时关闭。",
                )
            },
            confirmButton = {
                SecondaryButton(
                    onClick = {
                        confirmDynamic = false
                        update {
                            it.copy(
                                dynamicAcceleration = true,
                                dynamicAccelerationAcknowledged = true,
                            )
                        }
                    },
                ) {
                    Text("启用")
                }
            },
            dismissButton = {
                SecondaryButton(onClick = { confirmDynamic = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String? = null,
    trailing: @Composable () -> Unit,
) {
    @Composable
    fun Label(modifier: Modifier = Modifier) {
        Column(modifier) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        if (maxWidth < 440.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label()
                trailing()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun Stepper(
    value: Int,
    min: Int,
    max: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { if (value > min) onChange(value - 1) },
            enabled = enabled && value > min,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(48.dp).semantics { contentDescription = "减少同时下载数" },
        ) {
            Text("−")
        }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        OutlinedButton(
            onClick = { if (value < max) onChange(value + 1) },
            enabled = enabled && value < max,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(48.dp).semantics { contentDescription = "增加同时下载数" },
        ) {
            Text("+")
        }
    }
}

@Composable
private fun LimitField(
    label: String,
    limitKbps: Int,
    minimumKbps: Int,
    onApply: (Int) -> Unit,
    showMessage: (String) -> Unit,
) {
    var unit by remember { mutableStateOf(SpeedLimits.preferredUnit(limitKbps)) }
    var input by remember { mutableStateOf(SpeedLimits.format(limitKbps, unit)) }
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(limitKbps) { input = SpeedLimits.format(limitKbps, unit) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { raw ->
                val value = raw.replace(',', '.')
                if (value.length <= 32 && value.all { it.isDigit() || it == '.' } && value.count { it == '.' } <= 1) input = value
            },
            modifier = Modifier.width(110.dp).semantics { contentDescription = label },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Box {
            SecondaryButton(
                onClick = { menuOpen = true },
                modifier = Modifier.semantics { contentDescription = "${label}单位" },
            ) { Text(unit.label + " ▾") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                SpeedUnit.entries.forEach { next ->
                    DropdownMenuItem(
                        text = { Text(next.label) },
                        onClick = {
                            SpeedLimits.parse(input, unit)?.let { input = SpeedLimits.format(it, next) }
                            unit = next
                            menuOpen = false
                        },
                    )
                }
            }
        }
        SecondaryButton(modifier = Modifier.semantics { contentDescription = "${label}应用" }, onClick = {
            val value = SpeedLimits.parse(input, unit)
            when {
                value == null -> showMessage("请输入有效的${label}，数值不能超过 ${Int.MAX_VALUE} KB/s")
                value != 0 && value < minimumKbps -> showMessage("${label}最小为 $minimumKbps KB/s，0 表示不限速")
                else -> onApply(value)
            }
        }) { Text("应用") }
    }
}
