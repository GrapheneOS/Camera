package app.grapheneos.camera.ui.videoplayer.screen

import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle

private const val VIDEO_URI = "video_player_uri"

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
