package app.grapheneos.camera.data.camera.session

import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder

fun interface RecordingPreparer {
    fun prepare(recorder: Recorder, outputOptions: FileDescriptorOutputOptions): PendingRecording
}
