package app.grapheneos.camera.ui.components.lensswitchbutton

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.grapheneos.camera.ui.components.motion.MORPH_SPEC

private const val HALF_TURN = 180f

@Composable
internal fun animateFlipTurn(flipped: Boolean): State<Float> {
    val turn = remember { Animatable(initialValue = 0f) }
    var turnedFor by remember { mutableStateOf(flipped) }
    var target by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(flipped) {
        if (flipped != turnedFor) {
            turnedFor = flipped
            target -= HALF_TURN
            turn.animateTo(
                targetValue = target,
                animationSpec = MORPH_SPEC,
            )
        }
    }

    return turn.asState()
}
