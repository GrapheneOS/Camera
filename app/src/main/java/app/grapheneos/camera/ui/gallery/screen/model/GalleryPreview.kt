package app.grapheneos.camera.ui.gallery.screen.model

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap

internal sealed interface GalleryPreview {

    fun imageSize(): Size? {
        return null
    }

    data object Loading : GalleryPreview

    data class Loaded(
        val image: ImageBitmap,
        val isHdr: Boolean = false,
    ) : GalleryPreview {

        override fun imageSize(): Size {
            return Size(
                width = image.width.toFloat(),
                height = image.height.toFloat(),
            )
        }
    }

    data object Unavailable : GalleryPreview
}
