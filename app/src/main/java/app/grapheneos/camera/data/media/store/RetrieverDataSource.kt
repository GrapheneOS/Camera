package app.grapheneos.camera.data.media.store

import android.media.MediaMetadataRetriever
import android.net.Uri

fun interface RetrieverDataSource {
    fun setOn(retriever: MediaMetadataRetriever, uri: Uri)
}
