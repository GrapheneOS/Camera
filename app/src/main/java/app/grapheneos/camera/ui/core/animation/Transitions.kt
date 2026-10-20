package app.grapheneos.camera.ui.core.animation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

private val FADE_IN = fadeIn(animationSpec = SETTLE_SPEC)
private val FADE_OUT = fadeOut(animationSpec = SETTLE_SPEC)

internal fun <S> AnimatedContentTransitionScope<S>.crossfade(): ContentTransform {
    return FADE_IN togetherWith FADE_OUT using null
}

internal fun <S> AnimatedContentTransitionScope<S>.crossfadeResizing(): ContentTransform {
    return FADE_IN togetherWith FADE_OUT using SizeTransform { _, _ -> SETTLE_SIZE_SPEC }
}
