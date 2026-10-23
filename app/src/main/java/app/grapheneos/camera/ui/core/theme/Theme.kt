package app.grapheneos.camera.ui.core.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun CameraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = cameraColorScheme(
        context = LocalContext.current,
        darkTheme = darkTheme,
    )

    CompositionLocalProvider(LocalCameraColors provides CameraColors.DEFAULT) {
        MaterialTheme(
            colorScheme = colorScheme,
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
