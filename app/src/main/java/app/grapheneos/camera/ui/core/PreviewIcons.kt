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

internal val PREVIEW_CLOSE_ICON = ImageVector
    .Builder(
        name = "Close",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = SYMBOL_VIEWPORT,
        viewportHeight = SYMBOL_VIEWPORT,
    )
    .group(translationY = SYMBOL_VIEWPORT) {
        addPath(
            pathData = addPathNodes(CLOSE_PATH_DATA),
            fill = SolidColor(Color.Black),
        )
    }
    .build()
