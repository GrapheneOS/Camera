package app.grapheneos.camera.ui.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
internal data class CameraColors(
    val recording: Color,
    val overlay: Color,
    val onOverlay: Color,
    val overlayScrim: Color,
) {

    companion object {
        val DEFAULT = CameraColors(
            recording = Color(color = 0xFFEC0000),
            overlay = Color.White,
            onOverlay = Color.Black,
            overlayScrim = Color.Black.copy(alpha = 0.6f),
        )
    }
}

internal val LocalCameraColors = staticCompositionLocalOf<CameraColors> {
    error("CameraColors are provided by CameraTheme")
}

internal val MaterialTheme.cameraColors: CameraColors
    @Composable
    @ReadOnlyComposable
    get() {
        return LocalCameraColors.current
    }
