package app.grapheneos.camera.ui.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
internal data class CameraColors(
    val recording: Color,
    val locked: Color,
    val overlay: Color,
    val onOverlay: Color,
    val overlayScrim: Color,
    val selfIllumination: Color,
    val controlContainer: Color,
    val onControlContainer: Color,
) {

    companion object {

        fun fromScheme(colorScheme: ColorScheme): CameraColors {
            return CameraColors(
                recording = Color(color = 0xFFEC0000),
                locked = Color(color = 0xFFEAC76B),
                overlay = Color.White,
                onOverlay = Color.Black,
                overlayScrim = Color.Black.copy(alpha = 0.6f),
                selfIllumination = Color(color = 0xFFFFE8CF),
                controlContainer = colorScheme.surfaceContainerHighest,
                onControlContainer = colorScheme.onSurface,
            )
        }
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
