package app.grapheneos.camera.domain.capture.model

import android.net.Uri
import android.os.ParcelFileDescriptor

class RecordingOutput(
    val uri: Uri,
    val dateString: String,
    val fileDescriptor: ParcelFileDescriptor,
    val isOwnFile: Boolean,
    val isPendingMediaStoreUri: Boolean,
)
