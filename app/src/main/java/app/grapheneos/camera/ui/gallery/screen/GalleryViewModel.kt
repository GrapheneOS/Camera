package app.grapheneos.camera.ui.gallery.screen

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemDetailsRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.model.CapturedItemDeletion
import app.grapheneos.camera.domain.gallery.usecase.DeleteCapturedItem
import app.grapheneos.camera.domain.gallery.usecase.LoadGalleryItems
import app.grapheneos.camera.ui.gallery.screen.mapper.GalleryUiStateMapper
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDetails
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDialog as Dialog
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import app.grapheneos.camera.ui.gallery.screen.model.GalleryUiState
import app.grapheneos.camera.ui.mediaviewer.navigation.SystemGalleryLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal interface GalleryScreenModel {

    val uiState: StateFlow<GalleryUiState>

    val effects: Flow<Effect>

    fun onAction(action: Action)
}

@HiltViewModel
internal class GalleryViewModel @Inject constructor(
    private val arguments: SavedStateHandle,
    entryPoint: CameraEntryPoint,
    systemGalleryLauncher: SystemGalleryLauncher,
    private val capturedItemRepository: CapturedItemRepository,
    private val capturedItemDetailsRepository: CapturedItemDetailsRepository,
    private val loadGalleryItems: LoadGalleryItems,
    private val deleteCapturedItem: DeleteCapturedItem,
    private val uiStateMapper: GalleryUiStateMapper,
) : ViewModel(),
    GalleryScreenModel {

    private var state = GalleryState(
        items = listOfNotNull(viewedItemOf(arguments) ?: lastCapturedItemOf(arguments)),
        isSecureSession = entryPoint.isSecureSession,
        hasSystemGallery = systemGalleryLauncher.canOpen(),
    )

    private val _uiState = MutableStateFlow(uiStateMapper.map(state))
    override val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _effects = Channel<Effect>(capacity = Channel.BUFFERED)
    override val effects: Flow<Effect> = _effects.receiveAsFlow()

    init {
        loadPreviewsAround(state.currentIndex)

        viewModelScope.launch {
            val items = loadGalleryItems(
                videoOnly = entryPoint.requiresVideoModeOnly,
                sessionItems = sessionItemsOf(arguments),
            )

            showItems(items)
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.PageSelected -> selectPage(action.page)
            is Action.ScreenTapped -> onScreenTapped()
            is Action.InfoClicked -> openDetails()
            is Action.DetailsShownChanged -> showDetails(action.isShown)
            is Action.VideoClicked -> openVideo()
            is Action.SystemGalleryClicked -> openSystemGallery()
            is Action.EditClicked -> edit(usesDefaultEditor = true)
            is Action.EditWithClicked -> edit(usesDefaultEditor = false)
            is Action.ShareClicked -> share()
            is Action.DeleteClicked -> confirmDeletion()
            is Action.DeleteConfirmed -> deleteCurrentItem()
            is Action.DialogDismissed -> update { it.copy(dialog = null) }

            is Action.ZoomChanged -> onZoomChanged(
                pageKey = action.pageKey,
                isZoomed = action.isZoomed,
            )
        }
    }

    private fun showItems(items: List<CapturedItem>) {
        if (items.isEmpty()) {
            _effects.trySend(Effect.NothingToShow)
            return
        }

        val currentIndex = items.indexOf(state.currentItem()).coerceAtLeast(0)

        update {
            it.copy(
                items = items,
                currentIndex = currentIndex,
            )
        }
        loadPreviewsAround(currentIndex)
    }

    private fun selectPage(page: Int) {
        update { it.copy(currentIndex = page) }
        state.currentItem()?.let { saveViewedItem(arguments, it) }
        loadPreviewsAround(page)

        if (state.showsDetails) {
            loadDetailsAround(page)
        }
    }

    private fun onScreenTapped() {
        when {
            state.showsDetails -> update { it.copy(showsDetails = false) }
            else -> toggleControls()
        }
    }

    private fun toggleControls() {
        update { it.copy(areControlsVisible = !it.areControlsVisible) }
    }

    private fun onZoomChanged(
        pageKey: String,
        isZoomed: Boolean,
    ) {
        if (!isCurrentPage(pageKey) || state.isZoomed == isZoomed) return

        update {
            it.copy(
                isZoomed = isZoomed,
                areControlsVisible = it.areControlsVisible && !isZoomed,
            )
        }
    }

    private fun isCurrentPage(pageKey: String): Boolean {
        val shownState = _uiState.value

        return shownState.pages.getOrNull(shownState.currentPage)?.key == pageKey
    }

    private fun showDetails(isShown: Boolean) {
        when {
            isShown -> openDetails()
            else -> update { it.copy(showsDetails = false) }
        }
    }

    private fun openDetails() {
        if (state.showsDetails || state.currentItem() == null) return

        update { it.copy(showsDetails = true) }
        loadDetailsAround(state.currentIndex)
    }

    private fun loadDetailsAround(page: Int) {
        val items = itemsAround(
            page = page,
            radius = DETAILS_RADIUS,
        )

        update { it.copy(details = it.details.filterKeys(items::contains)) }

        items
            .filterNot(state.details::containsKey)
            .forEach(::loadDetails)
    }

    private fun loadDetails(item: CapturedItem) {
        update { it.copy(details = it.details + (item to GalleryDetails.Loading)) }

        viewModelScope.launch {
            val details = when (val loaded = capturedItemDetailsRepository.details(item)) {
                null -> GalleryDetails.Unavailable
                else -> GalleryDetails.Loaded(loaded)
            }

            if (item in state.details) {
                update { it.copy(details = it.details + (item to details)) }
            }
        }
    }

    private fun openVideo() {
        val item = state.currentItem() ?: return

        _effects.trySend(Effect.OpenVideo(item.uri))
    }

    private fun openSystemGallery() {
        if (state.isSecureSession) return

        _effects.trySend(Effect.OpenSystemGallery)
    }

    private fun edit(usesDefaultEditor: Boolean) {
        sendToOtherApp { item ->
            Effect.Edit(
                item = item,
                usesDefaultEditor = usesDefaultEditor,
            )
        }
    }

    private fun share() {
        sendToOtherApp { item -> Effect.Share(item) }
    }

    private fun sendToOtherApp(effect: (CapturedItem) -> Effect) {
        val item = state.currentItem() ?: return
        if (state.isSecureSession) return

        _effects.trySend(effect(item))
    }

    private fun confirmDeletion() {
        if (state.currentItem() == null) return

        update { it.copy(dialog = Dialog.DeleteConfirmation) }
    }

    private fun deleteCurrentItem() {
        val item = state.currentItem() ?: return

        update { it.copy(dialog = null) }
        viewModelScope.launch {
            when (deleteCapturedItem(item)) {
                CapturedItemDeletion.Deleted -> removeItem(item)
                CapturedItemDeletion.Failed -> _effects.trySend(Effect.DeletionFailed)
            }
        }
    }

    private fun removeItem(item: CapturedItem) {
        val items = state.items - item

        if (items.isEmpty()) {
            _effects.trySend(Effect.LastItemDeleted)
            return
        }

        update {
            it.copy(
                items = items,
                thumbnails = it.thumbnails - item,
                previews = it.previews - item,
                details = it.details - item,
                currentIndex = it.currentIndex.coerceAtMost(items.lastIndex),
                isZoomed = false,
            )
        }
        _effects.trySend(Effect.ItemDeleted)
        loadPreviewsAround(state.currentIndex)
    }

    private fun loadPreviewsAround(page: Int) {
        val thumbnailItems = itemsAround(
            page = page,
            radius = THUMBNAIL_RADIUS,
        )
        val previewItems = itemsAround(
            page = page,
            radius = PREVIEW_RADIUS,
        )

        update {
            it.copy(
                thumbnails = it.thumbnails.filterKeys(thumbnailItems::contains),
                previews = it.previews.filterKeys(previewItems::contains),
            )
        }
        thumbnailItems
            .filterNot(state.thumbnails::containsKey)
            .forEach(::loadThumbnail)
        previewItems
            .filterNot(state.previews::containsKey)
            .forEach(::loadPreview)
    }

    private fun itemsAround(
        page: Int,
        radius: Int,
    ): List<CapturedItem> {
        return (page - radius..page + radius).mapNotNull { state.items.getOrNull(it) }
    }

    private fun loadThumbnail(item: CapturedItem) {
        update { it.copy(thumbnails = it.thumbnails + (item to GalleryPreview.Loading)) }

        viewModelScope.launch {
            val bitmap = capturedItemRepository.loadThumbnail(
                item = item,
                targetWidth = THUMBNAIL_SIDE,
                targetHeight = THUMBNAIL_SIDE,
            )

            if (item in state.thumbnails) {
                update { it.copy(thumbnails = it.thumbnails + (item to previewOf(bitmap))) }
            }
        }
    }

    private fun loadPreview(item: CapturedItem) {
        update { it.copy(previews = it.previews + (item to GalleryPreview.Loading)) }

        viewModelScope.launch {
            val bitmap = capturedItemRepository.loadPreview(item)

            if (item in state.previews) {
                update { it.copy(previews = it.previews + (item to previewOf(bitmap))) }
            }
        }
    }

    private fun previewOf(bitmap: Bitmap?): GalleryPreview {
        return when (bitmap) {
            null -> GalleryPreview.Unavailable
            else -> GalleryPreview.Loaded(bitmap.asImageBitmap())
        }
    }

    private fun update(transform: (GalleryState) -> GalleryState) {
        state = transform(state)
        _uiState.value = uiStateMapper.map(state)
    }

    private companion object {
        const val PREVIEW_RADIUS = 1
        const val THUMBNAIL_RADIUS = 5
        const val THUMBNAIL_SIDE = 512
        const val DETAILS_RADIUS = 1
    }
}
