package com.xlite.downloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
actual fun rememberSystemDarkTheme(): Boolean = isSystemInDarkTheme()
