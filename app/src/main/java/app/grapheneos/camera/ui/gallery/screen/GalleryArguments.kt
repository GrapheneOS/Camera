package app.grapheneos.camera.ui.gallery.screen

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPlaybackOptions

private const val SESSION_ITEMS = "gallery_session_items"
private const val LAST_CAPTURED_ITEM = "gallery_last_captured_item"
private const val VIEWED_ITEM = "gallery_viewed_item"
private const val SHOWS_DETAILS = "gallery_shows_details"
private const val IS_MUTED = "gallery_is_muted"
private const val PLAYBACK_SPEED = "gallery_playback_speed"
private const val IS_LOOPING = "gallery_is_looping"

internal fun galleryArguments(
    sessionItems: List<CapturedItem>?,
    lastCapturedItem: CapturedItem?,
): Bundle {
    return Bundle().apply {
        sessionItems?.let { putParcelableArrayList(SESSION_ITEMS, ArrayList(it)) }
        putParcelable(LAST_CAPTURED_ITEM, lastCapturedItem)
    }
}

internal fun sessionItemsOf(arguments: SavedStateHandle): List<CapturedItem>? {
    return arguments.get<ArrayList<CapturedItem>>(SESSION_ITEMS)
}

internal fun lastCapturedItemOf(arguments: SavedStateHandle): CapturedItem? {
    return arguments.get<CapturedItem>(LAST_CAPTURED_ITEM)
}

internal fun viewedItemOf(arguments: SavedStateHandle): CapturedItem? {
    return arguments.get<CapturedItem>(VIEWED_ITEM)
}

internal fun showsDetailsOf(arguments: SavedStateHandle): Boolean {
    return arguments.get<Boolean>(SHOWS_DETAILS) == true
}

internal fun playbackOptionsOf(arguments: SavedStateHandle): GalleryPlaybackOptions {
    val defaults = GalleryPlaybackOptions()

    return GalleryPlaybackOptions(
        isMuted = arguments.get<Boolean>(IS_MUTED) ?: defaults.isMuted,
        speed = arguments.get<Float>(PLAYBACK_SPEED) ?: defaults.speed,
        isLooping = arguments.get<Boolean>(IS_LOOPING) ?: defaults.isLooping,
    )
}

internal fun saveViewedItem(
    arguments: SavedStateHandle,
    item: CapturedItem,
) {
    arguments[VIEWED_ITEM] = item
}

internal fun saveShowsDetails(
    arguments: SavedStateHandle,
    showsDetails: Boolean,
) {
    arguments[SHOWS_DETAILS] = showsDetails
}

internal fun savePlaybackOptions(
    arguments: SavedStateHandle,
    options: GalleryPlaybackOptions,
) {
    arguments[IS_MUTED] = options.isMuted
    arguments[PLAYBACK_SPEED] = options.speed
    arguments[IS_LOOPING] = options.isLooping
}
