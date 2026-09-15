package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraSelector

fun supportedLensFacing(
    preferred: Int,
    isSupported: (lensFacing: Int) -> Boolean,
): Int {
    return when {
        isSupported(preferred) -> preferred
        else -> oppositeLensFacing(preferred)
    }
}

fun oppositeLensFacing(lensFacing: Int): Int {
    return when (lensFacing) {
        CameraSelector.LENS_FACING_BACK -> CameraSelector.LENS_FACING_FRONT
        else -> CameraSelector.LENS_FACING_BACK
    }
}
