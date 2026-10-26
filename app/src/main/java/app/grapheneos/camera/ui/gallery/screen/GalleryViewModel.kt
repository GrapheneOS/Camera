package app.grapheneos.camera.ui.gallery.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.usecase.ObserveGalleryItems
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryControlsDelegate
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryDeletionDelegate
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryDetailsDelegate
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryPlaybackDelegate
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryPreviewsDelegate
import app.grapheneos.camera.ui.gallery.screen.mapper.GalleryUiStateMapper
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.DeletionAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.DetailsAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.OtherAppAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.PageAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.PlaybackOptionsAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.ScreenAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.VideoAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.VideoControlsAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDialog as Dialog
import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import app.grapheneos.camera.ui.gallery.screen.model.GalleryUiState
import app.grapheneos.camera.ui.mediaviewer.navigation.SystemGalleryLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal interface GalleryScreenModel {

    val uiState: StateFlow<GalleryUiState>

    val effects: Flow<Effect>

    val videoPosition: StateFlow<Duration>

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
    private val playbackDelegate: GalleryPlaybackDelegate,
    private val controlsDelegate: GalleryControlsDelegate,
    uiStateMapper: GalleryUiStateMapper,
) : ViewModel(),
    GalleryScreenModel {

    private val stateHolder = GalleryStateHolder(
        GalleryState(
            items = listOfNotNull(firstItemOf(entryPoint)),
            isSecureSession = entryPoint.isSecureSession,
            hasSystemGallery = systemGalleryLauncher.canOpen(),
            showsDetails = showsDetailsOf(arguments),
            playbackOptions = playbackOptionsOf(arguments),
        ),
    )

    override val uiState: StateFlow<GalleryUiState> = stateHolder.derive(uiStateMapper::map)

    override val effects: Flow<Effect> = stateHolder.effects

    override val videoPosition: StateFlow<Duration> = playbackDelegate.position

    private val state: GalleryState
        get() = stateHolder.state.value

    private val itemsRefreshes = Channel<Unit>(capacity = Channel.CONFLATED)

    init {
        previewsDelegate.bind(stateHolder)
        detailsDelegate.bind(stateHolder)
        deletionDelegate.bind(stateHolder)
        playbackDelegate.bind(stateHolder)
        controlsDelegate.bind(stateHolder)

        viewModelScope.launch {
            previewsDelegate.trackPreviews()
        }

        viewModelScope.launch {
            detailsDelegate.trackDetails()
        }

        viewModelScope.launch {
            playbackDelegate.trackPlayback()
        }

        viewModelScope.launch {
            controlsDelegate.hideIdleControls()
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
            is ScreenAction -> onScreenAction(action)
            is PageAction -> onPageAction(action)
            is VideoAction -> onVideoAction(action)
            is VideoControlsAction -> onVideoControlsAction(action)
            is PlaybackOptionsAction -> onPlaybackOptionsAction(action)
            is DetailsAction -> onDetailsAction(action)
            is OtherAppAction -> onOtherAppAction(action)
            is DeletionAction -> onDeletionAction(action)
        }
    }

    private fun onScreenAction(action: ScreenAction) {
        when (action) {
            is ScreenAction.ScreenTapped -> onScreenTapped()
            is ScreenAction.ScreenStarted -> playbackDelegate.onScreenStarted()
            is ScreenAction.ScreenStopped -> playbackDelegate.onScreenStopped()
            is ScreenAction.DialogDismissed -> showDialog(null)
        }
    }

    private fun onPageAction(action: PageAction) {
        when (action) {
            is PageAction.PageSelected -> selectPage(action.page)
            is PageAction.PageSettled -> settlePage(action.page)
            is PageAction.ZoomChanged -> onZoomChanged(
                pageKey = action.pageKey,
                isZoomed = action.isZoomed,
            )
        }
    }

    private fun onVideoAction(action: VideoAction) {
        when (action) {
            is VideoAction.VideoHoldStarted -> playbackDelegate.startFastForward()
            is VideoAction.VideoHoldEnded -> playbackDelegate.stopFastForward()
            is VideoAction.VideoOutputChanged -> playbackDelegate.changeOutput(action.change)
            is VideoAction.SkipTapped -> {
                playbackDelegate.skip(action.direction)
                controlsDelegate.hide()
            }
        }
    }

    private fun onVideoControlsAction(action: VideoControlsAction) {
        controlsDelegate.restartIdleTimer()

        when (action) {
            is VideoControlsAction.PlayPauseClicked -> playbackDelegate.togglePlayback()
            is VideoControlsAction.SeekDragged -> playbackDelegate.seekTo(action.position)
            is VideoControlsAction.SeekReleased -> playbackDelegate.finishSeek()
        }
    }

    private fun onPlaybackOptionsAction(action: PlaybackOptionsAction) {
        controlsDelegate.restartIdleTimer()

        when (action) {
            is PlaybackOptionsAction.PlaybackSpeedClicked -> showDialog(Dialog.PlaybackSpeed)
            is PlaybackOptionsAction.PlaybackSpeedSelected -> setPlaybackSpeed(action.speed)
            is PlaybackOptionsAction.MuteClicked -> changePlaybackOptions(
                change = playbackDelegate::toggleMute,
            )
            is PlaybackOptionsAction.LoopClicked -> changePlaybackOptions(
                change = playbackDelegate::toggleLooping,
            )
        }
    }

    private fun onDetailsAction(action: DetailsAction) {
        when (action) {
            is DetailsAction.DetailsShownChanged -> showDetails(action.isShown)
            is DetailsAction.OpenMapClicked -> detailsDelegate.openMap()
            is DetailsAction.DetailLongPressed -> stateHolder.send(Effect.CopyText(action.text))
        }
    }

    private fun onOtherAppAction(action: OtherAppAction) {
        when (action) {
            is OtherAppAction.SystemGalleryClicked -> stateHolder.send(Effect.OpenSystemGallery)
            is OtherAppAction.CaptureTimeClicked -> showInSystemGallery()
            is OtherAppAction.EditClicked -> edit(usesDefaultEditor = true)
            is OtherAppAction.EditWithClicked -> edit(usesDefaultEditor = false)
            is OtherAppAction.ShareClicked -> share()
            is OtherAppAction.UseAsClicked -> useAs()
        }
    }

    private fun onDeletionAction(action: DeletionAction) {
        when (action) {
            is DeletionAction.DeleteClicked -> changeItems { deletionDelegate.delete() }
            is DeletionAction.DeleteConfirmed -> changeItems { deletionDelegate.confirm() }
            is DeletionAction.UndoClicked -> changeItems { deletionDelegate.undo() }
            is DeletionAction.UndoDismissed -> deletionDelegate.finish()
        }
    }

    private fun changeItems(change: suspend () -> Unit) {
        viewModelScope.launch {
            change()
            itemsRefreshes.trySend(Unit)
        }
    }

    private fun firstItemOf(entryPoint: CameraEntryPoint): CapturedItem? {
        val item = viewedItemOf(arguments) ?: lastCapturedItemOf(arguments)

        return when {
            entryPoint.requiresVideoModeOnly -> item?.takeIf { it.type == CapturedItemType.VIDEO }
            else -> item
        }
    }

    private fun showItems(items: List<CapturedItem>) {
        if (state.isLoaded && items == state.items) return

        stateHolder.update { it.withItems(items).copy(isLoaded = true) }
    }

    private fun showDialog(dialog: Dialog?) {
        stateHolder.update { it.copy(dialog = dialog) }
    }

    private fun settlePage(page: Int) {
        stateHolder.update { it.copy(settledIndex = page) }
    }

    private fun setPlaybackSpeed(speed: Float) {
        changePlaybackOptions { playbackDelegate.setSpeed(speed) }
    }

    private fun changePlaybackOptions(change: () -> Unit) {
        change()
        savePlaybackOptions(arguments, state.playbackOptions)
    }

    private fun selectPage(page: Int) {
        stateHolder.update { it.copy(currentIndex = page) }
        state.currentItem()?.let { saveViewedItem(arguments, it) }
    }

    private fun onScreenTapped() {
        when {
            state.showsDetails -> detailsDelegate.hide()
            else -> controlsDelegate.toggle()
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
        val pager = uiState.value.pager

        return pager.pages.getOrNull(pager.currentPage)?.key == pageKey
    }

    private fun showDetails(isShown: Boolean) {
        when {
            isShown -> detailsDelegate.show()
            else -> detailsDelegate.hide()
        }
    }

    private fun showInSystemGallery() {
        sendToOtherApp { item -> Effect.ShowInSystemGallery(item) }
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

    private fun useAs() {
        sendToOtherApp { item -> Effect.UseAs(item) }
    }

    private fun sendToOtherApp(effect: (CapturedItem) -> Effect.OutsideSession) {
        state.currentItem()?.let { item ->
            stateHolder.send(effect(item))
        }
    }
}
