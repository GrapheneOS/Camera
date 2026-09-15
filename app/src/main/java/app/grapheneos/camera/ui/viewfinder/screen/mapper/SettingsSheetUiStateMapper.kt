package app.grapheneos.camera.ui.viewfinder.screen.mapper

import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import app.grapheneos.camera.R
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.ui.viewfinder.screen.model.SettingsSheetUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import javax.inject.Inject

interface SettingsSheetUiStateMapper {
    fun map(state: ViewfinderState): SettingsSheetUiState
}

internal class SettingsSheetUiStateMapperImpl @Inject constructor() : SettingsSheetUiStateMapper {

    override fun map(state: ViewfinderState): SettingsSheetUiState {
        val settings = state.settings
        val session = state.session
        val isVideoMode = state.isVideoMode()
        val aspectRatio = state.aspectRatio()

        val flash = flashOf(
            flashMode = state.flashMode,
            isFlashAvailable = session.isFlashAvailable,
        )

        return SettingsSheetUiState(
            flashIcon = flash.first,
            flashDescription = flash.second,
            includeAudio = settings.includeAudio,
            focusTimeoutSeconds = settings.focusTimeoutSeconds,
            selfTimerSeconds = settings.selfTimerDurationSeconds,
            videoQuality = state.modeSettings.videoQuality,
            geoTagging = state.requireLocation,
            selfIllumination = state.modeSettings.selfIllumination,
            stabilizationEnabled = settings.enableEis,
            waitForFocusLock = settings.waitForFocusLock,
            is16by9 = aspectRatio == AspectRatio.RATIO_16_9,
            aspectRatioFixed = isVideoMode,
            aspectRatioDescription = when (aspectRatio) {
                AspectRatio.RATIO_16_9 -> R.string.aspect_ratio_16_9
                else -> R.string.aspect_ratio_4_3
            },
            gridIcon = gridIconOf(settings.gridType),
            gridDescription = gridDescriptionOf(settings.gridType),
            includeAudioSettingVisible = isVideoMode,
            videoQualitySettingVisible = isVideoMode,
            stabilizationSettingVisible = isVideoMode && session.canApplyVideoStabilization,
            selfIlluminationSettingVisible =
                session.lensFacing == CameraSelector.LENS_FACING_FRONT,
            timerSettingVisible = !isVideoMode,
        )
    }

    private fun gridIconOf(gridType: GridType): Int {
        return when (gridType) {
            GridType.NONE -> R.drawable.grid_off_circle
            GridType.THREE_BY_THREE -> R.drawable.grid_3x3_circle
            GridType.FOUR_BY_FOUR -> R.drawable.grid_4x4_circle
            GridType.GOLDEN_RATIO -> R.drawable.grid_goldenratio_circle
        }
    }

    private fun gridDescriptionOf(gridType: GridType): Int {
        return when (gridType) {
            GridType.NONE -> R.string.grid_off
            GridType.THREE_BY_THREE -> R.string.grid_3x3
            GridType.FOUR_BY_FOUR -> R.string.grid_4x4
            GridType.GOLDEN_RATIO -> R.string.grid_golden_ratio
        }
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
