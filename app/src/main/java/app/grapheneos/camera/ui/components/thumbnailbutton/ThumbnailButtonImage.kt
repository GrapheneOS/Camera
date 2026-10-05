package app.grapheneos.camera.ui.components.thumbnailbutton

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.MORPH_INT_OFFSET_SPEC

private val IMAGE_GAP = 2.dp

@Composable
internal fun ThumbnailButtonImage(
    image: ImageBitmap?,
    buttonShape: CornerBasedShape,
    modifier: Modifier = Modifier,
) {
    val gap = with(LocalDensity.current) { IMAGE_GAP.roundToPx() }
    val shape = remember(buttonShape) {
        InsetCornerShape(
            outer = buttonShape,
            inset = RING_WIDTH,
        )
    }

    AnimatedContent(
        targetState = image,
        modifier = modifier
            .padding(all = RING_WIDTH)
            .clip(shape = shape),
        transitionSpec = {
            val slideIn = slideInVertically(animationSpec = MORPH_INT_OFFSET_SPEC) { -(it + gap) }
            val slideOut = slideOutVertically(animationSpec = MORPH_INT_OFFSET_SPEC) { it + gap }

            slideIn togetherWith slideOut using null
        },
    ) { shownImage ->
        shownImage?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape = shape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
