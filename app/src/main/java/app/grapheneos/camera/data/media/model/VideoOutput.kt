package app.grapheneos.camera.data.media.model

import android.view.Surface

/** A surface the video is drawn into. Playback state refers to it by [id]. */
internal class VideoOutput(
    val id: Int,
    val surface: Surface,
)
