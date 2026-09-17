package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.ExtensionMode

data class ExtensionKey(
    val lensFacing: LensFacing,
    val extensionMode: ExtensionMode,
) {
    companion object {
        fun onBothLenses(extensionMode: ExtensionMode): List<ExtensionKey> {
            return LensFacing.entries.map { lensFacing ->
                ExtensionKey(
                    lensFacing = lensFacing,
                    extensionMode = extensionMode,
                )
            }
        }
    }
}
