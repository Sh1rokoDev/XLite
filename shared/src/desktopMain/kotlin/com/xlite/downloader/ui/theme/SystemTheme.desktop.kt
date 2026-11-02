package com.xlite.downloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.delay

@Composable
actual fun rememberSystemDarkTheme(): Boolean {
    val fallback = isSystemInDarkTheme()
    if (!isWindows()) return fallback
    var dark by remember { mutableStateOf(readWindowsTheme() ?: fallback) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            readWindowsTheme()?.let { if (it != dark) dark = it }
        }
    }
    return dark
}

private fun isWindows(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("windows")

private fun readWindowsTheme(): Boolean? = runCatching {
    val value = Advapi32Util.registryGetIntValue(
        WinReg.HKEY_CURRENT_USER,
        "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
        "AppsUseLightTheme",
    )
    value == 0
}.getOrNull()
