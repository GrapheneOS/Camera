package app.grapheneos.camera.domain.qr

import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import com.google.zxing.BarcodeFormat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BarcodeFormatsTest {

    private val storedSettings = MutableStateFlow(CameraSettings())

    private val settingsRepository = mockk<SettingsRepository>()
    private val formats = BarcodeFormats(settingsRepository)

    @Before
    fun setUp() {
        every { settingsRepository.settings } returns storedSettings
        every { settingsRepository.update(transform = any()) } answers {
            val transform = firstArg<(CameraSettings) -> CameraSettings>()
            storedSettings.value = transform(storedSettings.value)
            storedSettings.value
        }
    }

    @Test
    fun enabled_isWhatTheRepositoryHolds() {
        stored(BarcodeFormat.QR_CODE.name, BarcodeFormat.AZTEC.name)

        assertEquals(
            setOf(BarcodeFormat.AZTEC, BarcodeFormat.QR_CODE),
            formats.enabled.toSet(),
        )
    }

    @Test
    fun enabled_aStoredNameThatIsNoFormat_isLeftOut() {
        stored(BarcodeFormat.QR_CODE.name, UNKNOWN_FORMAT)

        assertEquals(listOf(BarcodeFormat.QR_CODE), formats.enabled)
    }

    @Test
    fun setEnabled_aNewFormat_isAllowedAndStored() {
        stored(BarcodeFormat.QR_CODE.name)

        assertTrue(formats.setEnabled(BarcodeFormat.AZTEC.name, enabled = true))

        assertTrue(BarcodeFormat.AZTEC in formats.enabled)
        assertTrue(BarcodeFormat.AZTEC.name in storedSettings.value.enabledBarcodeFormats)
    }

    @Test
    fun setEnabled_disablingTheLastFormat_isRefusedWithoutStoringIt() {
        stored(BarcodeFormat.QR_CODE.name)

        assertFalse(formats.setEnabled(BarcodeFormat.QR_CODE.name, enabled = false))

        assertTrue(BarcodeFormat.QR_CODE in formats.enabled)
        verify(exactly = 0) { settingsRepository.update(transform = any()) }
    }

    @Test
    fun setEnabled_disablingOneOfSeveral_isAllowed() {
        stored(BarcodeFormat.QR_CODE.name, BarcodeFormat.AZTEC.name)

        assertTrue(formats.setEnabled(BarcodeFormat.AZTEC.name, enabled = false))

        assertFalse(BarcodeFormat.AZTEC in formats.enabled)
    }

    @Test
    fun apply_aSelectionThatLeavesNothingToScan_isRefused() {
        stored()

        assertFalse(formats.apply(formats.uncommonNames().associateWith { false }))

        verify(exactly = 0) { settingsRepository.update(transform = any()) }
    }

    @Test
    fun apply_aSelectionWhileACommonFormatIsOn_isStored() {
        stored(BarcodeFormat.QR_CODE.name)

        assertTrue(formats.apply(mapOf(BarcodeFormat.CODE_128.name to true)))

        assertTrue(BarcodeFormat.CODE_128 in formats.enabled)
        assertTrue(BarcodeFormat.CODE_128.name in storedSettings.value.enabledBarcodeFormats)
    }

    @Test
    fun uncommonNames_excludeTheFormatsWithTheirOwnToggles() {
        val uncommon = formats.uncommonNames()

        BarcodeFormats.COMMON_FORMATS.forEach { assertFalse(it.name in uncommon) }
    }

    private fun stored(vararg formatNames: String) {
        storedSettings.value = CameraSettings(enabledBarcodeFormats = formatNames.toSet())
    }

    private companion object {
        const val UNKNOWN_FORMAT = "NOT_A_FORMAT"
    }
}
