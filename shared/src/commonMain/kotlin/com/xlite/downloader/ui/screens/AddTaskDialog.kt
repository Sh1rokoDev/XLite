@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xlite.downloader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xlite.downloader.ui.components.SecondaryButton
import com.xlite.downloader.ui.components.PrimaryButton
import com.xlite.downloader.ui.components.ResponsiveDialog
import com.xlite.downloader.ui.components.ActionRow
import com.xlite.downloader.AppContainer
import com.xlite.downloader.domain.engine.AddFailure
import com.xlite.downloader.ui.icons.XLIcons
import com.xlite.downloader.ui.theme.XLiteColors
import com.xlite.downloader.util.UrlValidator
import kotlinx.coroutines.launch

@Composable
fun AddTaskDialog(
    container: AppContainer,
    defaultDir: String,
    isDesktop: Boolean,
    initialText: String,
    onDismiss: () -> Unit,
    onAdded: (Int) -> Unit,
    showMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf(initialText) }
    var singleName by remember { mutableStateOf("") }
    var saveDir by remember { mutableStateOf(defaultDir) }
    var busy by remember { mutableStateOf(false) }
    var failures by remember { mutableStateOf<List<AddFailure>>(emptyList()) }

    LaunchedEffect(initialText) {
        if (initialText.isNotBlank()) text = initialText
    }

    val lineCount = UrlValidator.splitInput(text).size
    val canPick = container.actions.canPickDirectory

    ResponsiveDialog(onDismiss = { if (!busy) onDismiss() }, maxWidth = 640.dp) {
        Column(
            Modifier
                .padding(22.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("添加下载", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "每行一个链接，仅支持 HTTP/HTTPS；thunder:// 会自动转换为真实地址。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    failures = emptyList()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("下载链接") },
                placeholder = { Text("https://example.com/file.zip") },
                minLines = 5,
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$lineCount 条链接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                SecondaryButton(
                    onClick = {
                        val pasted = clipboard.getText()?.text.orEmpty()
                        if (pasted.isNotBlank()) {
                            text = if (text.isBlank()) pasted else "${text.trimEnd()}\n$pasted"
                            failures = emptyList()
                        }
                    },
                    enabled = !busy,
                ) {
                    Icon(XLIcons.Copy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("从剪贴板粘贴")
                }
            }

            if (lineCount == 1) {
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = singleName,
                    onValueChange = { singleName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("文件名（可选）") },
                    placeholder = { Text("留空则从链接推断") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("保存位置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        XLIcons.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        saveDir,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (canPick) {
                        SecondaryButton(
                            onClick = {
                                scope.launch {
                                    container.actions.pickDirectory(saveDir)?.let { saveDir = it }
                                }
                            },
                            enabled = !busy,
                        ) {
                            Text("选择")
                        }
                    }
                }
            }
            if (!isDesktop) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Android 使用应用专属下载目录，卸载应用会同时删除文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (failures.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = XLiteColors.Danger.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${failures.size} 条未能添加",
                            style = MaterialTheme.typography.labelLarge,
                            color = XLiteColors.Danger,
                        )
                        failures.take(6).forEach { failure ->
                            Text(
                                "${failure.url.take(80)}：${failure.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (failures.size > 6) {
                            Text(
                                "其余 ${failures.size - 6} 条已省略",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            ActionRow {
                SecondaryButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
                PrimaryButton(
                    onClick = {
                        if (text.isBlank()) {
                            showMessage("请输入至少一个链接")
                            return@PrimaryButton
                        }
                        scope.launch {
                            busy = true
                            failures = emptyList()
                            val result = container.engine.addTasks(
                                rawInput = text,
                                saveDir = saveDir.ifBlank { defaultDir },
                                singleName = singleName.trim().takeIf { it.isNotEmpty() && lineCount == 1 },
                            )
                            busy = false
                            if (result.added > 0) onAdded(result.added)
                            if (result.failures.isEmpty()) {
                                if (result.added > 0) onDismiss() else showMessage("没有可添加的任务")
                            } else {
                                failures = result.failures
                                showMessage(result.message)
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (lineCount > 1) "开始下载 ($lineCount)" else "开始下载")
                }
            }
        }
    }
}
