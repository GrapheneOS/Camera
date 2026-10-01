package app.grapheneos.camera.ui.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

private const val SYMBOL_VIEWPORT = 960f
private const val CLOSE_PATH_DATA = "M480-424 284-228q-11 11-28 11t-28-11q-11-11-11-28" +
    "t11-28l196-196-196-196q-11-11-11-28t11-28q11-11 28-11t28 11l196 196 196-196q11-11 28-11" +
    "t28 11q11 11 11 28t-11 28L536-480l196 196q11 11 11 28t-11 28q-11 11-28 11t-28-11L480-424Z"
private const val LOCK_PATH_DATA = "M240-80q-33 0-56.5-23.5T160-160v-400q0-33 23.5-56.5" +
    "T240-640h40v-80q0-83 58.5-141.5T480-920q83 0 141.5 58.5T680-720v80h40q33 0 56.5 23.5" +
    "T800-560v400q0 33-23.5 56.5T720-80H240Zm240-200q33 0 56.5-23.5T560-360q0-33-23.5-56.5" +
    "T480-440q-33 0-56.5 23.5T400-360q0 33 23.5 56.5T480-280ZM360-640h240v-80q0-50-35-85" +
    "t-85-35q-50 0-85 35t-35 85v80Z"
private const val TIMER_PATH_DATA = "M400-840q-17 0-28.5-11.5T360-880q0-17 11.5-28.5T400-920h160" +
    "q17 0 28.5 11.5T600-880q0 17-11.5 28.5T560-840H400Zm80 440q17 0 28.5-11.5T520-440v-160" +
    "q0-17-11.5-28.5T480-640q-17 0-28.5 11.5T440-600v160q0 17 11.5 28.5T480-400Zm0 320" +
    "q-74 0-139.5-28.5T226-186q-49-49-77.5-114.5T120-440q0-74 28.5-139.5T226-694" +
    "q49-49 114.5-77.5T480-800q62 0 119 20t107 58l28-28q11-11 28-11t28 11q11 11 11 28" +
    "t-11 28l-28 28q38 50 58 107t20 119q0 74-28.5 139.5T734-186q-49 49-114.5 77.5T480-80Z"

internal val PREVIEW_CLOSE_ICON = symbol(
    name = "Close",
    pathData = CLOSE_PATH_DATA,
)

internal val PREVIEW_LOCK_ICON = symbol(
    name = "Lock",
    pathData = LOCK_PATH_DATA,
)

internal val PREVIEW_TIMER_ICON = symbol(
    name = "Timer",
    pathData = TIMER_PATH_DATA,
)

private fun symbol(
    name: String,
    pathData: String,
): ImageVector {
    return ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = SYMBOL_VIEWPORT,
            viewportHeight = SYMBOL_VIEWPORT,
        )
        .group(translationY = SYMBOL_VIEWPORT) {
            addPath(
                pathData = addPathNodes(pathData),
                fill = SolidColor(Color.Black),
            )
        }
        .build()
}
