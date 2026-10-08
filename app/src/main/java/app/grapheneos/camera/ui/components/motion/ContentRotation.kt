package app.grapheneos.camera.ui.components.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalContentRotation = staticCompositionLocalOf { { 0f } }

@Composable
internal fun ProvideContentRotation(
    degrees: Float,
    content: @Composable () -> Unit,
) {
    val rotation = animateTurn(
        degrees = { degrees },
        animationSpec = MORPH_SPEC,
    )
    val readRotation = remember(rotation) { { rotation.value } }

    CompositionLocalProvider(
        LocalContentRotation provides readRotation,
        content = content,
    )
}
