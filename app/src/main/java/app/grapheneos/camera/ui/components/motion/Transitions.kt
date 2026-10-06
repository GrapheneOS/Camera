package app.grapheneos.camera.ui.components.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

private const val POP_SCALE = 0.8f

internal val FADE_IN = fadeIn(animationSpec = SETTLE_SPEC)
internal val FADE_OUT = fadeOut(animationSpec = SETTLE_SPEC)
internal val HIDE_AT_ONCE = fadeOut(animationSpec = snap())

internal val POP_IN = FADE_IN + scaleIn(
    animationSpec = MORPH_SPEC,
    initialScale = POP_SCALE,
)
internal val POP_OUT = FADE_OUT + scaleOut(
    animationSpec = MORPH_SPEC,
    targetScale = POP_SCALE,
)

internal fun <S> AnimatedContentTransitionScope<S>.crossfade(): ContentTransform {
    return FADE_IN togetherWith FADE_OUT using null
}
