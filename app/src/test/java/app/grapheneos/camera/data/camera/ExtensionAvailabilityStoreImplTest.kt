package app.grapheneos.camera.data.camera

import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import app.grapheneos.camera.data.camera.model.ExtensionKey
import app.grapheneos.camera.data.camera.store.ExtensionAvailabilityStoreImpl
import app.grapheneos.camera.data.core.model.CameraMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExtensionAvailabilityStoreImplTest {

    private val store = ExtensionAvailabilityStoreImpl()

    @Test
    fun verdict_anUnprobedKey_isUnknown() {
        assertNull(store.verdict(NIGHT_FRONT))
    }

    @Test
    fun record_aVerdict_readsBack() {
        store.record(NIGHT_FRONT, usable = true)
        store.record(NIGHT_BACK, usable = false)

        assertTrue(store.verdict(NIGHT_FRONT) == true)
        assertFalse(store.verdict(NIGHT_BACK) == true)
    }

    @Test
    fun unprobed_aColdCache_listsEveryExtensionModeOnBothLenses() {
        val extensionModes = CameraMode.entries
            .map { it.extensionMode }
            .filter { it != ExtensionMode.NONE }
            .toSet()

        val unprobed = store.unprobed()

        assertEquals(extensionModes.size * 2, unprobed.size)
        assertEquals(extensionModes, unprobed.map { it.extensionMode }.toSet())
        assertEquals(
            setOf(CameraSelector.LENS_FACING_FRONT, CameraSelector.LENS_FACING_BACK),
            unprobed.map { it.lensFacing }.toSet(),
        )
    }

    @Test
    fun unprobed_aModeWithoutAnExtension_isNeverListed() {
        assertTrue(store.unprobed().none { it.extensionMode == ExtensionMode.NONE })
    }

    @Test
    fun unprobed_anAnsweredKey_dropsOutOfTheList() {
        store.record(NIGHT_FRONT, usable = false)

        assertFalse(NIGHT_FRONT in store.unprobed())
        assertTrue(NIGHT_BACK in store.unprobed())
    }

    @Test
    fun recordProbeRound_aVerdictForAnUnprobedKey_isRemembered() {
        store.recordProbeRound(mapOf(NIGHT_FRONT to true))

        assertTrue(store.verdict(NIGHT_FRONT) == true)
    }

    @Test
    fun recordProbeRound_aTransientFailure_isNotRemembered() {
        store.recordProbeRound(mapOf(NIGHT_FRONT to null))

        assertNull(store.verdict(NIGHT_FRONT))
    }

    @Test
    fun recordProbeRound_aKeyBlacklistedWhileTheRoundWasInFlight_staysBlacklisted() {
        store.record(NIGHT_FRONT, usable = false)
        store.recordProbeRound(mapOf(NIGHT_FRONT to true))

        assertFalse(store.verdict(NIGHT_FRONT) == true)
    }

    @Test
    fun clear_aWarmCache_forgetsEveryVerdict() {
        store.record(NIGHT_FRONT, usable = true)
        store.record(NIGHT_BACK, usable = false)

        store.clear()

        assertNull(store.verdict(NIGHT_FRONT))
        assertNull(store.verdict(NIGHT_BACK))
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
