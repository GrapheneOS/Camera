package app.grapheneos.camera.ui.gallery.screen.model

import app.grapheneos.camera.data.media.model.VideoOutput
import app.grapheneos.camera.ui.mediaviewer.playback.SkipDirection
import kotlin.time.Duration

internal sealed interface GalleryAction {

    data object ScreenTapped : GalleryAction

    data object ScreenStopped : GalleryAction

    data object DialogDismissed : GalleryAction

    sealed interface Page : GalleryAction

    data class PageSelected(
        val page: Int,
    ) : Page

    data class PageSettled(
        val page: Int,
    ) : Page

    data class ZoomChanged(
        val pageKey: String,
        val isZoomed: Boolean,
    ) : Page

    sealed interface Video : GalleryAction

    data object PlayPauseClicked : Video

    data object VideoHoldStarted : Video

    data object VideoHoldEnded : Video

    data object SeekReleased : Video

    data class VideoOutputChanged(
        val output: VideoOutput?,
    ) : Video

    data class SeekDragged(
        val position: Duration,
    ) : Video

    data class SkipTapped(
        val direction: SkipDirection,
    ) : Video

    sealed interface PlaybackOptions : GalleryAction

    data object MuteClicked : PlaybackOptions

    data object LoopClicked : PlaybackOptions

    data object PlaybackSpeedClicked : PlaybackOptions

    data class PlaybackSpeedSelected(
        val speed: Float,
    ) : PlaybackOptions

    sealed interface Details : GalleryAction

    data class DetailsShownChanged(
        val isShown: Boolean,
    ) : Details

    data object OpenMapClicked : Details

    data class DetailLongPressed(
        val text: String,
    ) : Details

    sealed interface OtherApp : GalleryAction

    data object SystemGalleryClicked : OtherApp

    data object CaptureTimeClicked : OtherApp

    data object EditClicked : OtherApp

    data object EditWithClicked : OtherApp

    data object ShareClicked : OtherApp

    data object UseAsClicked : OtherApp

    sealed interface Deletion : GalleryAction

    data object DeleteClicked : Deletion

    data object DeleteConfirmed : Deletion

    data object UndoClicked : Deletion

    data object UndoDismissed : Deletion
}
