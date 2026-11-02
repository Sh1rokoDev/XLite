package com.xlite.downloader.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object XLIcons {
    val Brand: ImageVector by lazy {
        filled("XLite", "M5 5 L9 5 L12 10 L15 5 L19 5 L14 13 L19 21 L15 21 L12 16 L9 21 L5 21 L10 13 Z")
    }
    val Add: ImageVector by lazy { outline("Add", "M12 5v14 M5 12h14") }
    val CheckCircle: ImageVector by lazy {
        outline("CheckCircle", "M22 12a10 10 0 1 1-20 0a10 10 0 0 1 20 0z M8 12l2.7 2.7L16.5 9")
    }
    val Close: ImageVector by lazy { outline("Close", "M5 5l14 14 M19 5L5 19") }
    val List: ImageVector by lazy {
        outline("List", "M4 6h1 M9 6h11 M4 12h1 M9 12h11 M4 18h1 M9 18h11")
    }
    val MoreVert: ImageVector by lazy { outline("MoreVert", "M12 5v0.01 M12 12v0.01 M12 19v0.01", 2.8f) }
    val PlayArrow: ImageVector by lazy { outline("PlayArrow", "M8 5l11 7-11 7z") }
    val Refresh: ImageVector by lazy {
        outline("Refresh", "M20 7v5h-5 M19.3 12a7.5 7.5 0 1 1-2.2-5.3L20 9")
    }
    val Warning: ImageVector by lazy {
        outline("Warning", "M12 3L22 20H2L12 3z M12 9v5 M12 17v0.01", 1.9f)
    }
    val ArrowBack: ImageVector by lazy { outline("ArrowBack", "M19 12H5 M11 6l-6 6 6 6") }
    val Settings: ImageVector by lazy {
        outline("Settings", "M4 7h8 M16 7h4 M14 4v6 M4 17h3 M11 17h9 M9 14v6")
    }
    val Search: ImageVector by lazy {
        outline("Search", "M16.5 10a6.5 6.5 0 1 1-13 0a6.5 6.5 0 0 1 13 0z M15 15l5 5")
    }
    val Pause: ImageVector by lazy { outline("Pause", "M8 5v14 M16 5v14", 2.2f) }
    val Download: ImageVector by lazy {
        outline("Download", "M12 3v12 M7.5 10.5L12 15l4.5-4.5 M4 17v3h16v-3")
    }
    val Folder: ImageVector by lazy {
        outline("Folder", "M3 6.5A1.5 1.5 0 0 1 4.5 5H9l2 2h8.5A1.5 1.5 0 0 1 21 8.5v9A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5z")
    }
    val FileOpen: ImageVector by lazy {
        outline("FileOpen", "M13 3H5v18h14v-8 M13 3v6h6 M14 10l7-7 M16 3h5v5")
    }
    val MultiSelect: ImageVector by lazy {
        outline("MultiSelect", "M4 4h16v16H4z M7 11l3 3 7-7")
    }
    val Copy: ImageVector by lazy {
        outline("Copy", "M8 8h11v13H8z M16 8V4H4v13h4")
    }
    val Eye: ImageVector by lazy {
        outline("Eye", "M2.5 12s3.4-6 9.5-6 9.5 6 9.5 6-3.4 6-9.5 6-9.5-6-9.5-6z M15 12a3 3 0 1 1-6 0a3 3 0 0 1 6 0z")
    }
    val EyeOff: ImageVector by lazy {
        outline("EyeOff", "M3 3l18 18 M9.8 6.3A10 10 0 0 1 12 6c6.1 0 9.5 6 9.5 6a13 13 0 0 1-3 3.4 M6.2 7.3A13.4 13.4 0 0 0 2.5 12s3.4 6 9.5 6c1 0 1.9-.2 2.7-.4 M9.9 9.9a3 3 0 0 0 4.2 4.2")
    }
    val Bolt: ImageVector by lazy { outline("Bolt", "M13 2L5 13h6l-1 9 9-12h-6z") }
    val Link: ImageVector by lazy {
        outline("Link", "M10 13.5l4-3 M8.5 16H7a4 4 0 0 1 0-8h3 M15.5 8H17a4 4 0 0 1 0 8h-3")
    }
    val Shield: ImageVector by lazy {
        outline("Shield", "M12 2l8 3v6c0 5-3.4 8.8-8 11-4.6-2.2-8-6-8-11V5l8-3z M9 12l2 2 4-4")
    }

    private fun outline(name: String, path: String, width: Float = 1.8f): ImageVector = builder(name)
        .addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    private fun filled(name: String, path: String): ImageVector = builder(name)
        .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()

    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
}
