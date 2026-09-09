package app.grapheneos.camera.ui.viewfinder.screen.mapper

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.viewfinder.screen.model.SettingsSheetUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import javax.inject.Inject

interface SettingsSheetUiStateMapper {

    fun map(
        isVideoMode: Boolean,
        flashMode: Int,
        session: ViewfinderSessionState,
    ): SettingsSheetUiState
}

internal class SettingsSheetUiStateMapperImpl @Inject constructor() : SettingsSheetUiStateMapper {

    override fun map(
        isVideoMode: Boolean,
        flashMode: Int,
        session: ViewfinderSessionState,
    ): SettingsSheetUiState {
        val flash = flashOf(
            flashMode = flashMode,
            isFlashAvailable = session.isFlashAvailable,
        )

        return SettingsSheetUiState(
            flashIcon = flash.first,
            flashDescription = flash.second,
            includeAudioSettingVisible = isVideoMode,
            videoQualitySettingVisible = isVideoMode,
            stabilizationSettingVisible = isVideoMode && session.canApplyVideoStabilization,
            selfIlluminationSettingVisible =
                session.lensFacing == CameraSelector.LENS_FACING_FRONT,
            timerSettingVisible = !isVideoMode,
        )
    }

    private fun flashOf(
        flashMode: Int,
        isFlashAvailable: Boolean,
    ): Pair<Int, Int> {
        if (!isFlashAvailable) {
            return R.drawable.flash_off_circle to R.string.flash_off
        }

        return when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> R.drawable.flash_on_circle to R.string.flash_on
            ImageCapture.FLASH_MODE_AUTO -> R.drawable.flash_auto_circle to R.string.flash_auto
            else -> R.drawable.flash_off_circle to R.string.flash_off
        }
    }
}
