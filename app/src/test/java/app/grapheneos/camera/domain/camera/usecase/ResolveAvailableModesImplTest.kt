package app.grapheneos.camera.domain.camera.usecase

import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import app.grapheneos.camera.data.camera.model.ExtensionKey
import app.grapheneos.camera.data.camera.store.ExtensionAvailabilityStoreImpl
import app.grapheneos.camera.data.core.model.CameraMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResolveAvailableModesImplTest {

    private val store = ExtensionAvailabilityStoreImpl()


    @Test
    fun invoke_aColdCache_offersOnlyTheModesThatNeedNoExtension() {
        assertEquals(
            setOf(CameraMode.QR_SCAN, CameraMode.CAMERA, CameraMode.VIDEO),
            availableModes(allowsQrScanning = true, extensionsAvailable = true),
        )
    }

    @Test
    fun invoke_aLockedSession_withholdsQrScanning() {
        val modes = availableModes(allowsQrScanning = false, extensionsAvailable = true)

        assertFalse(CameraMode.QR_SCAN in modes)
        assertTrue(CameraMode.CAMERA in modes)
        assertTrue(CameraMode.VIDEO in modes)
    }

    @Test
    fun invoke_anExtensionUsableOnOneLens_offersThatMode() {
        store.record(NIGHT_BACK, usable = true)

        assertTrue(CameraMode.NIGHT in availableModes(allowsQrScanning = true, extensionsAvailable = true))
    }

    @Test
    fun invoke_anExtensionUnusableOnBothLenses_withholdsThatMode() {
        store.record(NIGHT_FRONT, usable = false)
        store.record(NIGHT_BACK, usable = false)

        assertFalse(CameraMode.NIGHT in availableModes(allowsQrScanning = true, extensionsAvailable = true))
    }

    @Test
    fun invoke_noExtensionsManagerYet_withholdsEveryExtensionModeDespiteAWarmCache() {
        store.record(NIGHT_FRONT, usable = true)
        store.record(NIGHT_BACK, usable = true)

        assertEquals(
            setOf(CameraMode.QR_SCAN, CameraMode.CAMERA, CameraMode.VIDEO),
            availableModes(allowsQrScanning = true, extensionsAvailable = false),
        )
    }

    private fun availableModes(
        allowsQrScanning: Boolean,
        extensionsAvailable: Boolean,
    ): Set<CameraMode> {
        return ResolveAvailableModesImpl(store).invoke(
            allowsQrScanning = allowsQrScanning,
            extensionsAvailable = extensionsAvailable,
        )
    }

    private companion object {
        val NIGHT_FRONT = ExtensionKey(
            lensFacing = CameraSelector.LENS_FACING_FRONT,
            extensionMode = ExtensionMode.NIGHT,
        )

        val NIGHT_BACK = ExtensionKey(
            lensFacing = CameraSelector.LENS_FACING_BACK,
            extensionMode = ExtensionMode.NIGHT,
        )
    }
}
