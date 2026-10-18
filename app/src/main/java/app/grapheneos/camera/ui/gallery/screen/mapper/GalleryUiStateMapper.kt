package app.grapheneos.camera.ui.gallery.screen.mapper

import app.grapheneos.camera.data.media.mapper.CaptureTimeMapper
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import app.grapheneos.camera.ui.gallery.screen.model.GalleryUiState
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal interface GalleryUiStateMapper {
    fun map(state: GalleryState): GalleryUiState
}

internal class GalleryUiStateMapperImpl @Inject constructor(
    private val captureTimeMapper: CaptureTimeMapper,
) : GalleryUiStateMapper {

    override fun map(state: GalleryState): GalleryUiState {
        return GalleryUiState(
            pages = pages(state),
            currentPage = state.currentIndex,
            capturedAt = state.currentItem()?.let(captureTimeMapper::map),
            showsExternalActions = !state.isSecureSession,
            showsSystemGallery = state.hasSystemGallery && !state.isSecureSession,
            areControlsVisible = state.areControlsVisible,
            isZoomed = state.isZoomed,
            dialog = state.dialog,
        )
    }

    private fun pages(state: GalleryState): ImmutableList<GalleryPageUiModel> {
        return state.items
            .map { item ->
                GalleryPageUiModel(
                    key = item.uri.toString(),
                    isVideo = item.type == CapturedItemType.VIDEO,
                    preview = preview(
                        state = state,
                        item = item,
                    ),
                )
            }
            .toImmutableList()
    }

    private fun preview(
        state: GalleryState,
        item: CapturedItem,
    ): GalleryPreview {
        val preview = state.previews[item]
        val thumbnail = state.thumbnails[item]

        return when {
            preview is GalleryPreview.Loaded -> preview
            thumbnail is GalleryPreview.Loaded -> thumbnail
            else -> preview ?: thumbnail ?: GalleryPreview.Loading
        }
    }
}
