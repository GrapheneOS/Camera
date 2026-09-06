package app.grapheneos.camera.domain.qr

import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapperImpl
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapperImpl
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapperImpl
import app.grapheneos.camera.data.settings.repository.SettingsRepositoryImpl
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BarcodeFormatsTest {

    private val prefs = InMemoryDataStore(SettingsPrefs())

    private val repository = SettingsRepositoryImpl(
        dataStore = prefs,
        cameraSettingsMapper = CameraSettingsMapperImpl(),
        modeSettingsMapper = ModeSettingsMapperImpl(),
        storedVideoQualityMapper = StoredVideoQualityMapperImpl(),
    )

    private val formats = BarcodeFormats(repository)

    private fun stored(): Set<String> {
        return runBlocking { repository.settings.first() }.enabledBarcodeFormats
    }

    @Test
    fun load_theStoredNames_becomeTheAllowedFormats() {
        formats.load(setOf(BarcodeFormat.QR_CODE.name, BarcodeFormat.AZTEC.name))

        assertEquals(
            setOf(BarcodeFormat.AZTEC, BarcodeFormat.QR_CODE),
            formats.enabled.toSet(),
        )
    }

    @Test
    fun setEnabled_aNewFormat_isAllowedAndStored() {
        formats.load(setOf(BarcodeFormat.QR_CODE.name))

        assertTrue(formats.setEnabled(BarcodeFormat.AZTEC.name, enabled = true))

        assertTrue(BarcodeFormat.AZTEC in formats.enabled)
        assertTrue(BarcodeFormat.AZTEC.name in stored())
    }

    @Test
    fun setEnabled_disablingTheLastFormat_isRefused() {
        formats.load(setOf(BarcodeFormat.QR_CODE.name))

        assertFalse(formats.setEnabled(BarcodeFormat.QR_CODE.name, enabled = false))

        assertTrue(BarcodeFormat.QR_CODE in formats.enabled)
    }

    @Test
    fun setEnabled_disablingOneOfSeveral_isAllowed() {
        formats.load(setOf(BarcodeFormat.QR_CODE.name, BarcodeFormat.AZTEC.name))

        assertTrue(formats.setEnabled(BarcodeFormat.AZTEC.name, enabled = false))

        assertFalse(BarcodeFormat.AZTEC in formats.enabled)
    }

    @Test
    fun apply_aSelectionThatLeavesNothingToScan_isRefused() {
        formats.load(emptySet())

        assertFalse(formats.apply(formats.uncommonNames().associateWith { false }))
    }

    @Test
    fun apply_aSelectionWhileACommonFormatIsOn_isStored() {
        formats.load(setOf(BarcodeFormat.QR_CODE.name))

        assertTrue(formats.apply(mapOf(BarcodeFormat.CODE_128.name to true)))

        assertTrue(BarcodeFormat.CODE_128 in formats.enabled)
        assertTrue(BarcodeFormat.CODE_128.name in stored())
    }

    @Test
    fun uncommonNames_excludeTheFormatsWithTheirOwnToggles() {
        val uncommon = formats.uncommonNames()

        BarcodeFormats.COMMON_FORMATS.forEach { assertFalse(it.name in uncommon) }
    }
}
