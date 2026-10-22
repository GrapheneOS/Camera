package app.grapheneos.camera.ui.mediaviewer.components

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
internal fun HdrColorModeEffect(isHdr: Boolean) {
    val window = requireNotNull(LocalActivity.current) {
        "The color mode belongs to a hosting activity"
    }.window

    DisposableEffect(window, isHdr) {
        window.colorMode = when {
            isHdr -> ActivityInfo.COLOR_MODE_HDR
            else -> ActivityInfo.COLOR_MODE_DEFAULT
        }

        onDispose {
            window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }
    }
}
