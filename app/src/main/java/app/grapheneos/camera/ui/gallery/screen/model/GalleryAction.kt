package app.grapheneos.camera.ui.gallery.screen.model

import android.view.Surface
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

    data class VideoSurfaceCreated(
        val surface: Surface,
    ) : Video

    data object VideoSurfaceDestroyed : Video

    data object PlayPauseClicked : Video

    data object MuteClicked : Video

    data class SeekDragged(
        val position: Duration,
    ) : Video

    data object SeekReleased : Video

    data object VideoHoldStarted : Video

    data object VideoHoldEnded : Video

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
