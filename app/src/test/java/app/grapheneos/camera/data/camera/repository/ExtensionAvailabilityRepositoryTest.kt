package app.grapheneos.camera.data.camera.repository

import app.grapheneos.camera.data.camera.model.ExtensionKey
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.repository.ExtensionAvailabilityRepositoryImpl
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.ExtensionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExtensionAvailabilityRepositoryTest {

    private val repository = ExtensionAvailabilityRepositoryImpl()

    @Test
    fun verdict_anUnprobedKey_isUnknown() {
        assertNull(repository.verdict(NIGHT_FRONT))
    }

    @Test
    fun record_aVerdict_readsBack() {
        repository.record(NIGHT_FRONT, usable = true)
        repository.record(NIGHT_BACK, usable = false)

        assertEquals(true, repository.verdict(NIGHT_FRONT))
        assertEquals(false, repository.verdict(NIGHT_BACK))
    }

    @Test
    fun unprobed_aColdCache_listsEveryExtensionModeOnBothLenses() {
        val extensionModes = CameraMode.entries
            .mapNotNull { it.extensionMode }
            .toSet()

        val unprobed = repository.unprobed()

        assertEquals(extensionModes.size * 2, unprobed.size)
        assertEquals(extensionModes, unprobed.map { it.extensionMode }.toSet())
        assertEquals(
            setOf(LensFacing.FRONT, LensFacing.BACK),
            unprobed.map { it.lensFacing }.toSet(),
        )
    }

    @Test
    fun unprobed_anAnsweredKey_dropsOutOfTheList() {
        repository.record(NIGHT_FRONT, usable = false)

        assertFalse(NIGHT_FRONT in repository.unprobed())
        assertTrue(NIGHT_BACK in repository.unprobed())
    }

    @Test
    fun recordProbeRound_aVerdictForAnUnprobedKey_isRemembered() {
        repository.recordProbeRound(mapOf(NIGHT_FRONT to true))

        assertEquals(true, repository.verdict(NIGHT_FRONT))
    }

    @Test
    fun recordProbeRound_aTransientFailure_isNotRemembered() {
        repository.recordProbeRound(mapOf(NIGHT_FRONT to null))

        assertNull(repository.verdict(NIGHT_FRONT))
    }

    @Test
    fun recordProbeRound_aKeyBlacklistedWhileTheRoundWasInFlight_staysBlacklisted() {
        repository.record(NIGHT_FRONT, usable = false)
        repository.recordProbeRound(mapOf(NIGHT_FRONT to true))

        assertEquals(false, repository.verdict(NIGHT_FRONT))
    }

    @Test
    fun clear_aWarmCache_forgetsEveryVerdict() {
        repository.record(NIGHT_FRONT, usable = true)
        repository.record(NIGHT_BACK, usable = false)

        repository.clear()

        assertNull(repository.verdict(NIGHT_FRONT))
        assertNull(repository.verdict(NIGHT_BACK))
    }

    private companion object {
        val NIGHT_FRONT = ExtensionKey(
            lensFacing = LensFacing.FRONT,
            extensionMode = ExtensionMode.NIGHT,
        )

        val NIGHT_BACK = ExtensionKey(
            lensFacing = LensFacing.BACK,
            extensionMode = ExtensionMode.NIGHT,
        )
    }
}
