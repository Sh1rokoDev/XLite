@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xlite.downloader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xlite.downloader.domain.model.AuthState
import com.xlite.downloader.platform.PlatformBackHandler
import com.xlite.downloader.ui.components.BrandMark
import com.xlite.downloader.ui.icons.XLIcons
import com.xlite.downloader.ui.screens.AddTaskDialog
import com.xlite.downloader.ui.screens.AuthScreen
import com.xlite.downloader.ui.screens.SettingsScreen
import com.xlite.downloader.ui.screens.TasksScreen
import com.xlite.downloader.ui.theme.XLiteTheme
import com.xlite.downloader.ui.theme.rememberSystemDarkTheme
import kotlinx.coroutines.launch

enum class AppPlatform { Desktop, Android }
private enum class Screen { Tasks, Settings }
@Composable
fun App(
    container: AppContainer,
    platform: AppPlatform,
    initialInput: String? = null,
    themeOverride: Boolean? = null,
) {
    XLiteTheme(darkTheme = themeOverride ?: rememberSystemDarkTheme()) {
        val state by container.state.collectAsState()
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        var screen by remember { mutableStateOf(Screen.Tasks) }
        var showAdd by remember { mutableStateOf(false) }
        var addSeed by remember { mutableStateOf("") }
        val authBlocked = state.auth !is AuthState.Ready
        val showAuthGate = authBlocked && state.tasks.isEmpty()

        fun showMessage(message: String) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(message)
            }
        }
        fun openAdd(seed: String = "") {
            addSeed = seed
            showAdd = true
        }

        LaunchedEffect(initialInput) {
            if (!initialInput.isNullOrBlank()) {
                screen = Screen.Tasks
                openAdd(initialInput)
            }
        }
        PlatformBackHandler(enabled = screen == Screen.Settings) { screen = Screen.Tasks }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxWidth < 600.dp
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        if (screen == Screen.Settings || showAuthGate) {
                            TopAppBar(
                                title = {
                                    if (screen == Screen.Settings) {
                                        Text("设置", fontWeight = FontWeight.SemiBold)
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            BrandMark(30.dp)
                                            Spacer(Modifier.width(10.dp))
                                            Text("XLite", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                },
                                navigationIcon = {
                                    if (screen == Screen.Settings) {
                                        IconButton(onClick = { screen = Screen.Tasks }) {
                                            Icon(XLIcons.ArrowBack, contentDescription = "返回下载任务")
                                        }
                                    }
                                },
                                actions = {
                                    if (screen == Screen.Tasks) {
                                        IconButton(onClick = { screen = Screen.Settings }) {
                                            Icon(XLIcons.Settings, contentDescription = "设置")
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                            )
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                        when {
                            screen == Screen.Settings -> SettingsScreen(
                                container = container,
                                state = state,
                                isDesktop = platform == AppPlatform.Desktop,
                                compact = compact,
                                showMessage = ::showMessage,
                            )
                            showAuthGate -> AuthScreen(
                                auth = state.auth,
                                appIdOverride = state.settings.appIdOverride,
                                engineVersion = state.meta.engineVersion,
                                onTest = { key, override -> container.engine.testConnection(key, override) },
                                onSave = { key, override -> container.engine.saveCredentials(key, override) },
                                onReconnect = { container.engine.reconnect() },
                            )
                            else -> TasksScreen(
                                container = container,
                                state = state,
                                compact = compact,
                                authBlocked = authBlocked,
                                onAdd = { openAdd() },
                                onOpenSettings = { screen = Screen.Settings },
                                showMessage = ::showMessage,
                            )
                        }
                    }
                }
            }
            if (showAdd) {
                AddTaskDialog(
                    container = container,
                    defaultDir = state.settings.saveDir,
                    isDesktop = platform == AppPlatform.Desktop,
                    initialText = addSeed,
                    onDismiss = {
                        showAdd = false
                        addSeed = ""
                    },
                    onAdded = { count -> showMessage("已添加 " + count + " 个任务") },
                    showMessage = ::showMessage,
                )
            }
        }
    }
}
