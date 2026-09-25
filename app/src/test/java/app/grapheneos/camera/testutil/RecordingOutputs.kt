package app.grapheneos.camera.testutil

import android.net.Uri
import app.grapheneos.camera.domain.capture.model.RecordingOutput
import io.mockk.mockk

internal fun recordingOutput(
    uri: Uri,
    dateString: String = "20260920_120000",
    isOwnFile: Boolean = true,
    isPendingMediaStoreUri: Boolean = false,
): RecordingOutput {
    return RecordingOutput(
        uri = uri,
        dateString = dateString,
        fileDescriptor = mockk(relaxed = true),
        isOwnFile = isOwnFile,
        isPendingMediaStoreUri = isPendingMediaStoreUri,
    )
}
