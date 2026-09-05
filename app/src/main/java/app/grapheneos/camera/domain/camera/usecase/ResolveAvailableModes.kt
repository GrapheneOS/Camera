package app.grapheneos.camera.domain.camera.usecase

import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import app.grapheneos.camera.data.camera.model.ExtensionKey
import app.grapheneos.camera.data.camera.store.ExtensionAvailabilityStore
import app.grapheneos.camera.data.core.model.CameraMode
import javax.inject.Inject

interface ResolveAvailableModes {

    // Extension verdicts are read straight from the cache because tab refreshes must never pay
    // for a vendor probe on the main thread (see loadTabs); an unprobed mode is left out of the
    // tabs for now, exactly like a transiently-failed probe always was, and comes back on the
    // refresh that follows its probe.
    operator fun invoke(
        allowsQrScanning: Boolean,
        extensionsAvailable: Boolean,
    ): Set<CameraMode>
}

internal class ResolveAvailableModesImpl @Inject constructor(
    private val extensionAvailabilityStore: ExtensionAvailabilityStore,
) : ResolveAvailableModes {

    override fun invoke(
        allowsQrScanning: Boolean,
        extensionsAvailable: Boolean,
    ): Set<CameraMode> {
        return CameraMode.entries.filter { mode ->
            when (mode) {
                CameraMode.CAMERA, CameraMode.VIDEO -> true
                CameraMode.QR_SCAN -> allowsQrScanning
                else -> {
                    check(mode.extensionMode != ExtensionMode.NONE)
                    extensionsAvailable && isUsableOnEitherLens(mode.extensionMode)
                }
            }
        }.toSet()
    }

    private fun isUsableOnEitherLens(extensionMode: Int): Boolean {
        return LENS_FACINGS.any { lensFacing ->
            val key = ExtensionKey(
                lensFacing = lensFacing,
                extensionMode = extensionMode,
            )

            extensionAvailabilityStore.verdict(key) == true
        }
    }

    private companion object {
        private val LENS_FACINGS = listOf(
            CameraSelector.LENS_FACING_FRONT,
            CameraSelector.LENS_FACING_BACK,
        )
    }
}
