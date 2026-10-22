package app.grapheneos.camera.ui.gallery.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.usecase.LoadGalleryItems
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryDeletionDelegate
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryDetailsDelegate
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryPreviewsDelegate
import app.grapheneos.camera.ui.gallery.screen.mapper.GalleryUiStateMapper
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import app.grapheneos.camera.ui.gallery.screen.model.GalleryUiState
import app.grapheneos.camera.ui.mediaviewer.navigation.SystemGalleryLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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
    private val loadGalleryItems: LoadGalleryItems,
    private val previewsDelegate: GalleryPreviewsDelegate,
    private val detailsDelegate: GalleryDetailsDelegate,
    private val deletionDelegate: GalleryDeletionDelegate,
    uiStateMapper: GalleryUiStateMapper,
) : ViewModel(),
    GalleryScreenModel {

    private val stateHolder = GalleryStateHolder(
        GalleryState(
            items = listOfNotNull(viewedItemOf(arguments) ?: lastCapturedItemOf(arguments)),
            isSecureSession = entryPoint.isSecureSession,
            hasSystemGallery = systemGalleryLauncher.canOpen(),
            showsDetails = showsDetailsOf(arguments),
        ),
    )

    override val uiState: StateFlow<GalleryUiState> = stateHolder.derive(uiStateMapper::map)

    override val effects: Flow<Effect> = stateHolder.effects

    private val state: GalleryState
        get() = stateHolder.state.value

    init {
        previewsDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
        detailsDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
        deletionDelegate.bind(stateHolder)

        loadAround(state.currentIndex)

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
            is Action.Details -> detailsDelegate.onAction(action)
            is Action.VideoClicked -> openVideo()
            is Action.SystemGalleryClicked -> stateHolder.send(Effect.OpenSystemGallery)
            is Action.EditClicked -> edit(usesDefaultEditor = true)
            is Action.EditWithClicked -> edit(usesDefaultEditor = false)
            is Action.ShareClicked -> share()
            is Action.DeleteClicked -> deletionDelegate.confirm()
            is Action.DeleteConfirmed -> deleteCurrentItem()
            is Action.DialogDismissed -> deletionDelegate.dismiss()

            is Action.ZoomChanged -> onZoomChanged(
                pageKey = action.pageKey,
                isZoomed = action.isZoomed,
            )
        }
    }

    private fun showItems(items: List<CapturedItem>) {
        if (items.isEmpty()) {
            stateHolder.send(Effect.NothingToShow)
            return
        }

        val currentIndex = items.indexOf(state.currentItem()).coerceAtLeast(0)

        stateHolder.update {
            it.copy(
                items = items,
                currentIndex = currentIndex,
            )
        }

        loadAround(currentIndex)
    }

    private fun selectPage(page: Int) {
        stateHolder.update { it.copy(currentIndex = page) }
        state.currentItem()?.let { saveViewedItem(arguments, it) }
        loadAround(page)
    }

    private fun loadAround(page: Int) {
        previewsDelegate.loadAround(page)
        detailsDelegate.loadAround(page)
    }

    private fun onScreenTapped() {
        when {
            state.showsDetails -> detailsDelegate.hide()
            else -> stateHolder.update { it.copy(areControlsVisible = !it.areControlsVisible) }
        }
    }

    private fun onZoomChanged(
        pageKey: String,
        isZoomed: Boolean,
    ) {
        if (!isCurrentPage(pageKey) || state.isZoomed == isZoomed) return

        stateHolder.update {
            it.copy(
                isZoomed = isZoomed,
                areControlsVisible = it.areControlsVisible && !isZoomed,
            )
        }
    }

    private fun isCurrentPage(pageKey: String): Boolean {
        val shownState = uiState.value

        return shownState.pages.getOrNull(shownState.currentPage)?.key == pageKey
    }

    private fun openVideo() {
        state.currentItem()?.let { item ->
            stateHolder.send(Effect.OpenVideo(item.uri))
        }
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

    private fun sendToOtherApp(effect: (CapturedItem) -> Effect.OtherApp) {
        state.currentItem()?.let { item ->
            stateHolder.send(effect(item))
        }
    }

    private fun deleteCurrentItem() {
        viewModelScope.launch {
            if (deletionDelegate.deleteCurrentItem()) {
                loadAround(state.currentIndex)
            }
        }
    }
}
