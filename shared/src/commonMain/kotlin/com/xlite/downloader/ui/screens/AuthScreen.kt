@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xlite.downloader.ui.screens

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xlite.downloader.ui.components.SecondaryButton
import com.xlite.downloader.ui.components.PrimaryButton
import com.xlite.downloader.ui.components.ActionRow
import com.xlite.downloader.auth.AppIdResolver
import com.xlite.downloader.domain.engine.ActionResult
import com.xlite.downloader.domain.engine.ConnectionTestResult
import com.xlite.downloader.domain.model.AuthState
import com.xlite.downloader.ui.icons.XLIcons
import com.xlite.downloader.ui.theme.XLiteColors
import com.xlite.downloader.util.Formatters
import kotlinx.coroutines.launch
import kotlin.time.Clock

@Composable
fun AuthScreen(
    auth: AuthState,
    appIdOverride: String?,
    engineVersion: String?,
    onTest: suspend (String, String?) -> ConnectionTestResult,
    onSave: suspend (String, String?) -> ActionResult,
    onReconnect: suspend () -> ActionResult,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var override by remember { mutableStateOf(appIdOverride.orEmpty()) }
    var showKey by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(appIdOverride.isNullOrBlank().not()) }
    var busy by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<ConnectionTestResult?>(null) }

    val errorText = when (auth) {
        is AuthState.Error -> auth.message
        is AuthState.Expired -> auth.message
        else -> null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Text("连接下载引擎", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text("输入迅雷开放平台 API Key。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(22.dp)) {
                    if (errorText != null) {
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            color = XLiteColors.Danger.copy(alpha = 0.10f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                errorText,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = XLiteColors.Danger,
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = key,
                        onValueChange = {
                            key = it
                            message = null
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
                                    contentDescription = if (showKey) "隐藏 API Key" else "显示 API Key",
                                )
                            }
                        },
                    )

                    Spacer(Modifier.height(18.dp))
                    ActionRow(leading = {
                        SecondaryButton(onClick = { showAdvanced = !showAdvanced }) {
                            Text(if (showAdvanced) "收起高级选项" else "高级选项")
                        }
                    }) {
                        SecondaryButton(
                            onClick = {
                                scope.launch {
                                    testing = true
                                    message = null
                                    val result = if (key.isBlank()) {
                                        // No stored-key test is exposed here; ask for a key instead.
                                        ConnectionTestResult(false, null, null, "请先输入 API Key")
                                    } else {
                                        onTest(key.trim(), override.trim().takeIf { it.isNotEmpty() })
                                    }
                                    testResult = result
                                    testing = false
                                }
                            },
                            enabled = !busy && !testing && key.isNotBlank(),
                        ) {
                            if (testing) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("测试连接")
                        }
                        PrimaryButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    message = null
                                    val result = onSave(key.trim(), override.trim().takeIf { it.isNotEmpty() })
                                    message = result.message
                                    busy = false
                                }
                            },
                            enabled = !busy && key.isNotBlank(),
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("保存并登录")
                        }
                        if (auth is AuthState.Expired) {
                            SecondaryButton(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        val result = onReconnect()
                                        message = result.message
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                            ) {
                                Text("用已保存的 Key 重新登录")
                            }
                        }
                    }

                    if (showAdvanced) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = override,
                            onValueChange = { override = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("App ID（可选，留空自动识别）") },
                            placeholder = { Text("eGwt...") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "通常可从 API Key 的登录令牌自动识别。如果提示无法识别或初始化失败，请填写控制台应用详情中的 App ID。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }


                    testResult?.let { result ->
                        Spacer(Modifier.height(14.dp))
                        val color = if (result.success) XLiteColors.Success else XLiteColors.Danger
                        Surface(color = color.copy(alpha = 0.10f), shape = MaterialTheme.shapes.small) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    if (result.success) "连接成功" else "连接失败",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = color,
                                )
                                if (result.success) {
                                    val display = result.appId?.let { AppIdResolver.displayName(it) ?: it }
                                    Text(
                                        "App ID：${display.orEmpty()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (result.appId != null && result.appId != display) {
                                        Text(
                                            "标识：${result.appId}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    result.tokenExpiresAtEpochSeconds?.let {
                                        val remaining = it - Clock.System.now().epochSeconds
                                        Text(
                                            "Token 有效期至 ${Formatters.tokenExpiry(it)}（${Formatters.duration(remaining)}）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    Text(result.message, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    message?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            XLIcons.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "API Key 仅保存在本机，不会写入日志。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "仅支持 HTTP/HTTPS 下载（包括 thunder:// 转换后的链接），不支持 BT、磁力链、FTP 或 ed2k。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (engineVersion != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "下载引擎 ${engineVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
