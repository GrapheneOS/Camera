package app.grapheneos.camera.ui.videoplayer.screen

import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle

private const val VIDEO_URI = "video_player_uri"
private const val IS_MUTED = "video_player_is_muted"

internal fun videoPlayerArguments(uri: Uri): Bundle {
    return Bundle().apply {
        putParcelable(VIDEO_URI, uri)
    }
}

internal fun videoUriOf(arguments: SavedStateHandle): Uri {
    return requireNotNull(arguments.get<Uri>(VIDEO_URI)) {
        "The video player was opened without a video"
    }
}

internal fun isMutedOf(arguments: SavedStateHandle): Boolean {
    return arguments.get<Boolean>(IS_MUTED) == true
}

internal fun saveIsMuted(
    arguments: SavedStateHandle,
    isMuted: Boolean,
) {
    arguments[IS_MUTED] = isMuted
}
