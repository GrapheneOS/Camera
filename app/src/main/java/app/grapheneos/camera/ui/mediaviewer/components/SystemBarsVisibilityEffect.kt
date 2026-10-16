package app.grapheneos.camera.ui.mediaviewer.components

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

@Composable
internal fun SystemBarsVisibilityEffect(visible: Boolean) {
    val view = LocalView.current
    val activity = requireNotNull(LocalActivity.current) {
        "System bars belong to a hosting activity"
    }

    LaunchedEffect(activity, view, visible) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val systemBars = WindowInsetsCompat.Type.systemBars()

        when {
            visible -> controller.show(systemBars)
            else -> controller.hide(systemBars)
        }
    }
}
