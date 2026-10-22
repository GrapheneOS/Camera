package app.grapheneos.camera.ui.gallery.screen.mapper

import app.grapheneos.camera.data.media.mapper.CaptureTimeMapper
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDetails
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import app.grapheneos.camera.ui.gallery.screen.model.GalleryUiState
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

internal interface GalleryUiStateMapper {
    fun map(state: GalleryState): GalleryUiState
}

internal class GalleryUiStateMapperImpl @Inject constructor(
    private val captureTimeMapper: CaptureTimeMapper,
) : GalleryUiStateMapper {

    private var pagedItems: List<CapturedItem>? = null
    private var pages: ImmutableList<GalleryPageUiModel> = persistentListOf()

    override fun map(state: GalleryState): GalleryUiState {
        return GalleryUiState(
            pages = pagesOf(state.items),
            previews = previews(state),
            details = details(state),
            currentPage = state.currentIndex,
            isEmpty = state.isLoaded && state.items.isEmpty(),
            capturedAt = state.currentItem()?.let(captureTimeMapper::map),
            showsExternalActions = !state.isSecureSession,
            showsSystemGallery = state.hasSystemGallery && !state.isSecureSession,
            showsUseAs = !state.isSecureSession &&
                state.currentItem()?.type == CapturedItemType.IMAGE,
            showsHdr = showsHdr(state),
            areControlsVisible = state.areControlsVisible,
            isZoomed = state.isZoomed,
            showsDetails = state.showsDetails,
            dialog = state.dialog,
        )
    }

    private fun pagesOf(items: List<CapturedItem>): ImmutableList<GalleryPageUiModel> {
        if (items !== pagedItems) {
            pages = items
                .map { item ->
                    GalleryPageUiModel(
                        key = pageKeyOf(item),
                        isVideo = item.type == CapturedItemType.VIDEO,
                    )
                }
                .toImmutableList()
            pagedItems = items
        }

        return pages
    }

    private fun previews(state: GalleryState): ImmutableMap<String, GalleryPreview> {
        return (state.thumbnails.keys + state.previews.keys)
            .associate { item ->
                pageKeyOf(item) to preview(
                    state = state,
                    item = item,
                )
            }
            .toImmutableMap()
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

    private fun showsHdr(state: GalleryState): Boolean {
        val shown = state.currentItem()?.let { item ->
            preview(
                state = state,
                item = item,
            )
        }

        return (shown as? GalleryPreview.Loaded)?.isHdr == true
    }

    private fun details(state: GalleryState): ImmutableMap<String, GalleryDetails> {
        return state.details.entries
            .associate { (item, details) -> pageKeyOf(item) to details }
            .toImmutableMap()
    }

    private fun pageKeyOf(item: CapturedItem): String {
        return item.uri.toString()
    }
}
