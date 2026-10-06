package app.grapheneos.camera.ui.components.squarebutton

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.LocalContentRotation
import app.grapheneos.camera.ui.components.motion.crossfade
import app.grapheneos.camera.ui.components.shuttercore.AnimatedShutterCore
import app.grapheneos.camera.ui.components.squarebutton.model.SquareButtonMark

private val ICON_SIZE = 32.dp
private val CORE_SIZE = 46.dp

@Composable
internal fun SquareButtonMarkContent(
    mark: SquareButtonMark,
    iconTint: Color,
    turn: () -> Float = { 0f },
) {
    val rotation = LocalContentRotation.current

    AnimatedContent(
        targetState = mark,
        modifier = Modifier.graphicsLayer { rotationZ = rotation() + turn() },
        transitionSpec = { crossfade() },
        contentAlignment = Alignment.Center,
        contentKey = { shownMark ->
            when (shownMark) {
                is SquareButtonMark.Icon -> shownMark.icon
                is SquareButtonMark.Core -> SquareButtonMark.Core::class
            }
        },
    ) { shownMark ->
        when (shownMark) {
            is SquareButtonMark.Icon -> Icon(
                imageVector = shownMark.icon,
                contentDescription = null,
                modifier = Modifier.size(size = ICON_SIZE),
                tint = iconTint,
            )

            is SquareButtonMark.Core -> AnimatedShutterCore(
                core = shownMark.core,
                tone = shownMark.tone,
                modifier = Modifier.size(size = CORE_SIZE),
            )
        }
    }
}
