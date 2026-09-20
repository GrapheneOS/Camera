package app.grapheneos.camera.domain.capture.model

import android.net.Uri

sealed interface StoreCapturedImageResult {

    data class Stored(
        val uri: Uri,
    ) : StoreCapturedImageResult

    data class StorageLocationNotFound(
        val cause: Exception,
    ) : StoreCapturedImageResult

    data class Failed(
        val stage: Stage,
        val cause: Exception,
    ) : StoreCapturedImageResult

    enum class Stage {
        FILE_CREATION,
        FILE_WRITE,
        FILE_WRITE_COMPLETION,
    }
}
