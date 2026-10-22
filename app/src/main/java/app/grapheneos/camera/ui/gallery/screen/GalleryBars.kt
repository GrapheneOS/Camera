package app.grapheneos.camera.ui.gallery.screen

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.grapheneos.camera.ui.common.components.bottomBar
import app.grapheneos.camera.ui.core.animation.CONTROLS_SPEC
import app.grapheneos.camera.ui.core.theme.cameraColors
import app.grapheneos.camera.ui.gallery.components.GALLERY_ACTION_BAR_HEIGHT
import app.grapheneos.camera.ui.gallery.components.GalleryActionBar
import app.grapheneos.camera.ui.gallery.components.details.DetailsBackButton
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryUiState as UiState

private const val SCRIM_STEPS = 8

private val SCRIM_OVERHANG = 48.dp

@Composable
internal fun BoxScope.GalleryBars(
    uiState: UiState,
    fadeProgress: () -> Float,
    detailsPosition: () -> Float,
    backButtonVisibility: () -> Float,
    backgroundColor: () -> Color,
    onAction: (Action) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val isPanelVisible by remember(detailsPosition) { derivedStateOf { detailsPosition() > 0f } }
    val visibility = animateBarsVisibilityAsState(
        isVisible = uiState.areControlsVisible && !isPanelVisible,
        hidesInstantly = uiState.isZoomed || isPanelVisible,
    )
    val isShown by remember(visibility) { derivedStateOf { visibility.value > 0f } }
    val overPhoto = animateOverPhotoAsState(isZoomed = uiState.isZoomed)
    val contentColor = barsContentColor(overPhoto = overPhoto.value)
    val backgrounds = rememberBarBackgrounds(
        overPhoto = overPhoto,
        backgroundColor = backgroundColor,
    )
    val barsAlpha = Modifier.modulatedAlpha { visibility.value * (1f - fadeProgress()) }

    if (isShown) {
        GalleryTopBar(
            uiState = uiState,
            onAction = onAction,
            onNavigateBack = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(barsAlpha)
                .then(backgrounds.top),
            containerColor = Color.Transparent,
            contentColor = contentColor,
        )
    }

    DetailsBackButton(
        visibility = backButtonVisibility,
        onClick = { onAction(Action.DetailsShownChanged(isShown = false)) },
        modifier = Modifier.align(Alignment.TopCenter),
    )

    Column(
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .zIndex(1f)
                .modulatedAlpha { 1f - fadeProgress() },
        )
        ActionBarSlot(
            isShown = isShown && uiState.pages.isNotEmpty(),
            showsExternalActions = uiState.showsExternalActions,
            contentColor = contentColor,
            onAction = onAction,
            modifier = barsAlpha.then(backgrounds.bottom),
        )
    }
}

@Composable
private fun ActionBarSlot(
    isShown: Boolean,
    showsExternalActions: Boolean,
    contentColor: Color,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val bottomInset = WindowInsets.bottomBar.asPaddingValues().calculateBottomPadding()
    var barHeight by remember { mutableStateOf(GALLERY_ACTION_BAR_HEIGHT + bottomInset) }

    Box(modifier = modifier.heightIn(min = barHeight)) {
        if (isShown) {
            GalleryActionBar(
                showsExternalActions = showsExternalActions,
                onShareClick = { onAction(Action.ShareClicked) },
                onEditClick = { onAction(Action.EditClicked) },
                onDeleteClick = { onAction(Action.DeleteClicked) },
                modifier = Modifier.onSizeChanged { size ->
                    barHeight = with(density) { size.height.toDp() }
                },
                containerColor = Color.Transparent,
                contentColor = contentColor,
            )
        }
    }
}

@Composable
private fun animateBarsVisibilityAsState(
    isVisible: Boolean,
    hidesInstantly: Boolean,
): State<Float> {
    return animateFloatAsState(
        targetValue = when {
            isVisible -> 1f
            else -> 0f
        },
        animationSpec = when {
            hidesInstantly && !isVisible -> snap()
            else -> CONTROLS_SPEC
        },
    )
}

@Composable
private fun animateOverPhotoAsState(isZoomed: Boolean): State<Float> {
    val windowSize = LocalWindowInfo.current.containerSize
    val isLandscape = windowSize.width > windowSize.height

    return animateFloatAsState(
        targetValue = when {
            isZoomed || isLandscape -> 1f
            else -> 0f
        },
    )
}

@Composable
private fun barsContentColor(overPhoto: Float): Color {
    return lerp(
        start = MaterialTheme.colorScheme.onSurface,
        stop = MaterialTheme.cameraColors.overlay,
        fraction = overPhoto,
    )
}

@Composable
private fun rememberBarBackgrounds(
    overPhoto: State<Float>,
    backgroundColor: () -> Color,
): BarBackgrounds {
    val scrimColor = MaterialTheme.cameraColors.overlayScrim
    val scrimOverhang = with(LocalDensity.current) { SCRIM_OVERHANG.toPx() }

    return remember(
        overPhoto,
        backgroundColor,
        scrimColor,
        scrimOverhang,
    ) {
        val scrimColors = scrimColors(scrimColor)

        BarBackgrounds(
            top = Modifier.barBackground(
                color = backgroundColor,
                scrimColors = scrimColors,
                scrimEdge = ScrimEdge.Top,
                scrimOverhang = scrimOverhang,
                scrimFraction = { overPhoto.value },
            ),
            bottom = Modifier.barBackground(
                color = backgroundColor,
                scrimColors = scrimColors,
                scrimEdge = ScrimEdge.Bottom,
                scrimOverhang = scrimOverhang,
                scrimFraction = { overPhoto.value },
            ),
        )
    }
}

private fun scrimColors(color: Color): List<Color> {
    return List(SCRIM_STEPS + 1) { step ->
        val position = step.toFloat() / SCRIM_STEPS

        color.copy(alpha = color.alpha * (1f - EaseInOutSine.transform(position)))
    }
}

private fun Modifier.modulatedAlpha(alpha: () -> Float): Modifier {
    return graphicsLayer {
        this.alpha = alpha()
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

private fun Modifier.barBackground(
    color: () -> Color,
    scrimColors: List<Color>,
    scrimEdge: ScrimEdge,
    scrimOverhang: Float,
    scrimFraction: () -> Float,
): Modifier {
    return drawWithCache {
        val scrimHeight = size.height + scrimOverhang
        val scrimTop = when (scrimEdge) {
            ScrimEdge.Top -> 0f
            ScrimEdge.Bottom -> -scrimOverhang
        }
        val scrim = when (scrimEdge) {
            ScrimEdge.Top -> Brush.verticalGradient(
                colors = scrimColors,
                startY = 0f,
                endY = scrimHeight,
            )
            ScrimEdge.Bottom -> Brush.verticalGradient(
                colors = scrimColors,
                startY = size.height,
                endY = -scrimOverhang,
            )
        }

        onDrawBehind {
            val fraction = scrimFraction()

            drawRect(
                color = color(),
                alpha = 1f - fraction,
            )
            drawRect(
                brush = scrim,
                topLeft = Offset(
                    x = 0f,
                    y = scrimTop,
                ),
                size = Size(
                    width = size.width,
                    height = scrimHeight,
                ),
                alpha = fraction,
            )
        }
    }
}

private class BarBackgrounds(
    val top: Modifier,
    val bottom: Modifier,
)

private enum class ScrimEdge {
    Top,
    Bottom,
}
