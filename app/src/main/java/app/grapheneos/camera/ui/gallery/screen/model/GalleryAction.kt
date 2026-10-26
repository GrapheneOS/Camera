package app.grapheneos.camera.ui.gallery.screen.model

import app.grapheneos.camera.data.media.model.VideoOutputChange
import app.grapheneos.camera.ui.mediaviewer.playback.SkipDirection
import kotlin.time.Duration

internal sealed interface GalleryAction {

    sealed interface ScreenAction : GalleryAction {

        data object ScreenTapped : ScreenAction

        data object ScreenStopped : ScreenAction

        data object DialogDismissed : ScreenAction
    }

    sealed interface PageAction : GalleryAction {

        data class PageSelected(
            val page: Int,
        ) : PageAction

        data class PageSettled(
            val page: Int,
        ) : PageAction

        data class ZoomChanged(
            val pageKey: String,
            val isZoomed: Boolean,
        ) : PageAction
    }

    sealed interface VideoAction : GalleryAction {

        data object VideoHoldStarted : VideoAction

        data object VideoHoldEnded : VideoAction

        data class VideoOutputChanged(
            val change: VideoOutputChange,
        ) : VideoAction

        data class SkipTapped(
            val direction: SkipDirection,
        ) : VideoAction
    }

    sealed interface VideoControlsAction : GalleryAction {

        data object PlayPauseClicked : VideoControlsAction

        data object SeekReleased : VideoControlsAction

        data class SeekDragged(
            val position: Duration,
        ) : VideoControlsAction
    }

    sealed interface PlaybackOptionsAction : GalleryAction {

        data object MuteClicked : PlaybackOptionsAction

        data object LoopClicked : PlaybackOptionsAction

        data object PlaybackSpeedClicked : PlaybackOptionsAction

        data class PlaybackSpeedSelected(
            val speed: Float,
        ) : PlaybackOptionsAction
    }

    sealed interface DetailsAction : GalleryAction {

        data class DetailsShownChanged(
            val isShown: Boolean,
        ) : DetailsAction

        data object OpenMapClicked : DetailsAction

        data class DetailLongPressed(
            val text: String,
        ) : DetailsAction
    }

    sealed interface OtherAppAction : GalleryAction {

        data object SystemGalleryClicked : OtherAppAction

        data object CaptureTimeClicked : OtherAppAction

        data object EditClicked : OtherAppAction

        data object EditWithClicked : OtherAppAction

        data object ShareClicked : OtherAppAction

        data object UseAsClicked : OtherAppAction
    }

    sealed interface DeletionAction : GalleryAction {

        data object DeleteClicked : DeletionAction

        data object DeleteConfirmed : DeletionAction

        data object UndoClicked : DeletionAction

        data object UndoDismissed : DeletionAction
    }
}
