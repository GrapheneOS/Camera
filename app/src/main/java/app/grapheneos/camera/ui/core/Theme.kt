package app.grapheneos.camera.ui.core

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val CAMERA_SHAPES = Shapes(
    extraSmall = RoundedCornerShape(size = 12.dp),
    small = RoundedCornerShape(size = 16.dp),
    medium = RoundedCornerShape(size = 20.dp),
    large = RoundedCornerShape(size = 28.dp),
    extraLarge = RoundedCornerShape(size = 36.dp),
)

@Composable
internal fun CameraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = cameraColorScheme(
        context = context,
        darkTheme = darkTheme,
    )
    // Overlays sit on the camera image whatever the theme, so their accent is always the dark one.
    val cameraColors = remember(context) {
        CameraColors.withAccent(
            overlayAccent = cameraColorScheme(
                context = context,
                darkTheme = true,
            ).primary,
        )
    }

    CompositionLocalProvider(LocalCameraColors provides cameraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = CAMERA_SHAPES,
            content = content,
        )
    }
}

private fun cameraColorScheme(
    context: Context,
    darkTheme: Boolean,
): ColorScheme {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicColorScheme(
                context = context,
                darkTheme = darkTheme,
            )
        }

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun dynamicColorScheme(
    context: Context,
    darkTheme: Boolean,
): ColorScheme {
    return when {
        darkTheme -> dynamicDarkColorScheme(context = context)
        else -> dynamicLightColorScheme(context = context)
    }
}
