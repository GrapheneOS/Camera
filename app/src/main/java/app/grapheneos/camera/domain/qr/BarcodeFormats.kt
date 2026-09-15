package app.grapheneos.camera.domain.qr

import app.grapheneos.camera.data.settings.repository.SettingsRepository
import com.google.zxing.BarcodeFormat
import javax.inject.Inject

interface BarcodeFormats {
    val enabled: List<BarcodeFormat>

    fun uncommonNames(): List<String>
    fun isEnabled(formatName: String): Boolean

    /** Returns false when the change was refused because it would leave nothing to scan. */
    fun setEnabled(formatName: String, enabled: Boolean): Boolean

    /** Returns false when the selection was refused because it would leave nothing to scan. */
    fun apply(selection: Map<String, Boolean>): Boolean
}

internal class BarcodeFormatsImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : BarcodeFormats {

    override val enabled: List<BarcodeFormat>
        get() {
            val enabledNames = settingsRepository.settings.value.enabledBarcodeFormats
            return BarcodeFormat.entries.filter { it.name in enabledNames }
        }

    override fun uncommonNames(): List<String> {
        return BarcodeFormat.entries
            .filterNot { it in COMMON_FORMATS }
            .map { it.name }
    }

    override fun isEnabled(formatName: String): Boolean {
        return enabled.any { it.name == formatName }
    }

    override fun setEnabled(
        formatName: String,
        enabled: Boolean,
    ): Boolean {
        if (!enabled && this.enabled.size == 1) return false

        settingsRepository.update {
            it.withBarcodeFormat(
                formatName = formatName,
                enabled = enabled,
            )
        }

        return true
    }

    override fun apply(selection: Map<String, Boolean>): Boolean {
        // If all formats displayed outside the dialog are disabled (main QR scanner UI) and no
        // option is selected within the check box either - implying no barcode format is selected
        // at all - don't apply the selection made by the user
        val enabledFormats = enabled
        val allCommonFormatsDisabled = COMMON_FORMATS.none { it in enabledFormats }

        if (allCommonFormatsDisabled && selection.values.none { it }) {
            return false
        }

        settingsRepository.update { stored ->
            selection.entries.fold(stored) { settings, (formatName, isEnabled) ->
                settings.withBarcodeFormat(
                    formatName = formatName,
                    enabled = isEnabled,
                )
            }
        }

        return true
    }

    private companion object {
        val COMMON_FORMATS = listOf(
            BarcodeFormat.AZTEC,
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.PDF_417,
        )
    }
}
