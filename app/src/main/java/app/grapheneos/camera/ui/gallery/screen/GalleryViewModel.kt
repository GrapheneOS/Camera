package app.grapheneos.camera.ui.gallery.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.usecase.ObserveGalleryItems
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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
    private val observeGalleryItems: ObserveGalleryItems,
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

    private val itemsRefreshes = Channel<Unit>(capacity = Channel.CONFLATED)

    init {
        previewsDelegate.bind(stateHolder)
        detailsDelegate.bind(stateHolder)
        deletionDelegate.bind(stateHolder)

        viewModelScope.launch {
            previewsDelegate.trackPreviews()
        }

        viewModelScope.launch {
            detailsDelegate.trackDetails()
        }

        viewModelScope.launch {
            observeGalleryItems(
                videoOnly = entryPoint.requiresVideoModeOnly,
                sessionItems = sessionItemsOf(arguments),
                refreshes = itemsRefreshes.receiveAsFlow(),
            ).collect(::showItems)
        }
    }

    override fun onCleared() {
        deletionDelegate.finish()
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.PageSelected -> selectPage(action.page)
            is Action.ScreenTapped -> onScreenTapped()
            is Action.ZoomChanged -> onZoomChanged(
                pageKey = action.pageKey,
                isZoomed = action.isZoomed,
            )

            is Action.Details -> onDetailsAction(action)

            is Action.VideoClicked -> openVideo()
            is Action.SystemGalleryClicked -> stateHolder.send(Effect.OpenSystemGallery)
            is Action.EditClicked -> edit(usesDefaultEditor = true)
            is Action.EditWithClicked -> edit(usesDefaultEditor = false)
            is Action.ShareClicked -> share()

            is Action.Deletion -> onDeletionAction(action)
            is Action.DialogDismissed -> deletionDelegate.dismiss()
        }
    }

    private fun onDetailsAction(action: Action.Details) {
        when (action) {
            is Action.DetailsShownChanged -> showDetails(action.isShown)
            is Action.OpenMapClicked -> detailsDelegate.openMap()
            is Action.DetailLongPressed -> stateHolder.send(Effect.CopyText(action.text))
        }
    }

    private fun onDeletionAction(action: Action.Deletion) {
        when (action) {
            is Action.DeleteClicked -> changeItems { deletionDelegate.delete() }
            is Action.DeleteConfirmed -> changeItems { deletionDelegate.confirm() }
            is Action.UndoClicked -> changeItems { deletionDelegate.undo() }
            is Action.UndoDismissed -> deletionDelegate.finish()
        }
    }

    private fun changeItems(change: suspend () -> Unit) {
        viewModelScope.launch {
            change()
            itemsRefreshes.trySend(Unit)
        }
    }

    private fun showItems(items: List<CapturedItem>) {
        if (state.isLoaded && items == state.items) return

        stateHolder.update {
            it.copy(
                items = items,
                currentIndex = it.currentIndexIn(items),
                isLoaded = true,
            )
        }
    }

    private fun selectPage(page: Int) {
        stateHolder.update { it.copy(currentIndex = page) }
        state.currentItem()?.let { saveViewedItem(arguments, it) }
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

    private fun showDetails(isShown: Boolean) {
        when {
            isShown -> detailsDelegate.show()
            else -> detailsDelegate.hide()
        }
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
}
