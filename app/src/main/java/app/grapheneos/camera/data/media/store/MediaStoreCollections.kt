package app.grapheneos.camera.data.media.store

import android.net.Uri
import android.provider.MediaStore

val imageCollectionUri: Uri = requireNotNull(
    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
) { "the primary external volume has no image collection" }

val videoCollectionUri: Uri = requireNotNull(
    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
) { "the primary external volume has no video collection" }
