package app.grapheneos.camera.domain.qr

import app.grapheneos.camera.data.settings.repository.SettingsRepository
import com.google.zxing.BarcodeFormat
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@ActivityScoped
class BarcodeFormats @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    private val enabledFormats = arrayListOf<BarcodeFormat>()

    val enabled: List<BarcodeFormat>
        get() {
            return enabledFormats
        }

    fun load() {
        val enabledNames = runBlocking {
            settingsRepository.settings.first()
        }.enabledBarcodeFormats

        load(enabledNames)
    }

    internal fun load(enabledNames: Set<String>) {
        enabledFormats.clear()
        enabledFormats.addAll(BarcodeFormat.entries.filter { it.name in enabledNames })
    }

    fun uncommonNames(): List<String> {
        return BarcodeFormat.entries
            .filterNot { it in COMMON_FORMATS }
            .map { it.name }
    }

    fun isEnabled(formatName: String): Boolean {
        return enabledFormats.any { it.name == formatName }
    }

    /** Returns false when the change was refused because it would leave nothing to scan. */
    fun setEnabled(
        formatName: String,
        enabled: Boolean,
    ): Boolean {
        store(mapOf(formatName to enabled))

        val format = BarcodeFormat.valueOf(formatName)

        if (enabled) {
            if (format !in enabledFormats) {
                enabledFormats.add(format)
            }

            return true
        }

        if (enabledFormats.size == 1) return false

        enabledFormats.remove(format)

        return true
    }

    /** Returns false when the selection was refused because it would leave nothing to scan. */
    fun apply(selection: Map<String, Boolean>): Boolean {
        // If all formats displayed outside the dialog are disabled (main QR scanner UI) and no
        // option is selected within the check box either - implying no barcode format is selected
        // at all - don't apply the selection made by the user
        val allCommonFormatsDisabled = COMMON_FORMATS.none { enabledFormats.contains(it) }

        if (allCommonFormatsDisabled && selection.values.none { it }) {
            return false
        }

        selection.forEach { (formatName, isEnabled) ->
            val format = BarcodeFormat.valueOf(formatName)

            when {
                !isEnabled -> enabledFormats.remove(format)
                format !in enabledFormats -> enabledFormats.add(format)
            }
        }

        store(selection)

        return true
    }

    private fun store(selection: Map<String, Boolean>) {
        runBlocking {
            settingsRepository.update { stored ->
                selection.entries.fold(stored) { settings, (formatName, isEnabled) ->
                    settings.withBarcodeFormat(
                        formatName = formatName,
                        enabled = isEnabled,
                    )
                }
            }
        }
    }

    companion object {
        val COMMON_FORMATS = listOf(
            BarcodeFormat.AZTEC,
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.PDF_417,
        )
    }
}
