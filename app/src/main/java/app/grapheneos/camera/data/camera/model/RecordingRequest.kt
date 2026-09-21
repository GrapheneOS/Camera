package app.grapheneos.camera.data.camera.model

import android.location.Location
import android.os.ParcelFileDescriptor

class RecordingRequest(
    val fileDescriptor: ParcelFileDescriptor,
    val location: Location?,
    val includeAudio: Boolean,
)
