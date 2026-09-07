package app.grapheneos.camera.data.camera.model

import androidx.camera.core.CameraSelector

data class ExtensionKey(
    val lensFacing: Int,
    val extensionMode: Int,
) {
    companion object {
        fun onBothLenses(extensionMode: Int): List<ExtensionKey> {
            return LENS_FACINGS.map { lensFacing ->
                ExtensionKey(
                    lensFacing = lensFacing,
                    extensionMode = extensionMode,
                )
            }
        }

        private val LENS_FACINGS = listOf(
            CameraSelector.LENS_FACING_FRONT,
            CameraSelector.LENS_FACING_BACK,
        )
    }
}
