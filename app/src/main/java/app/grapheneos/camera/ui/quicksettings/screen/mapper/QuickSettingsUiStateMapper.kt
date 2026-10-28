package app.grapheneos.camera.ui.quicksettings.screen.mapper

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsState
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList

internal interface QuickSettingsUiStateMapper {
    fun map(state: QuickSettingsState): QuickSettingsUiState
}

internal class QuickSettingsUiStateMapperImpl @Inject constructor() : QuickSettingsUiStateMapper {

    override fun map(state: QuickSettingsState): QuickSettingsUiState {
        val settings = state.settings
        val camera = state.boundCamera
        val isVideoMode = camera?.isVideoMode == true
        val hasFlashUnit = camera?.hasFlashUnit == true
        val videoQualities = camera?.videoQualities.orEmpty()
        val aspectRatio = when {
            isVideoMode -> AspectRatio.RATIO_16_9
            else -> settings.aspectRatio
        }

        val flash = flashOf(
            flashMode = state.modeSettings.flashMode,
            hasFlashUnit = hasFlashUnit,
        )

        return QuickSettingsUiState(
            flashIcon = flash.first,
            flashDescription = flash.second,
            includeAudio = settings.includeAudio,
            selfTimerSeconds = settings.selfTimerDurationSeconds,
            videoQuality = state.modeSettings.videoQuality,
            videoQualities = videoQualities.toImmutableList(),
            videoQualityPosition = videoQualityPosition(
                videoQualities = videoQualities,
                videoQuality = state.modeSettings.videoQuality,
            ),
            torchAvailable = hasFlashUnit,
            torchOn = state.isTorchOn,
            selfIllumination = state.modeSettings.selfIllumination,
            is16by9 = aspectRatio == AspectRatio.RATIO_16_9,
            aspectRatioFixed = isVideoMode,
            aspectRatioDescription = when (aspectRatio) {
                AspectRatio.RATIO_16_9 -> R.string.aspect_ratio_16_9
                AspectRatio.RATIO_4_3 -> R.string.aspect_ratio_4_3
            },
            includeAudioSettingVisible = isVideoMode,
            videoQualitySettingVisible = isVideoMode,
            selfIlluminationSettingVisible = camera?.lensFacing == LensFacing.FRONT,
            timerSettingVisible = !isVideoMode,
        )
    }

    private fun videoQualityPosition(
        videoQualities: List<VideoQuality>,
        videoQuality: VideoQuality,
    ): Int? {
        // CameraX lists the supported qualities from the highest down.
        val position = when (videoQuality) {
            VideoQuality.HIGHEST -> 0
            else -> videoQualities.indexOf(videoQuality)
        }

        return position.takeIf { it in videoQualities.indices }
    }

    private fun flashOf(
        flashMode: FlashMode,
        hasFlashUnit: Boolean,
    ): Pair<Int, Int> {
        if (!hasFlashUnit) {
            return R.drawable.flash_off_circle to R.string.flash_off
        }

        return when (flashMode) {
            FlashMode.ON -> R.drawable.flash_on_circle to R.string.flash_on
            FlashMode.AUTO -> R.drawable.flash_auto_circle to R.string.flash_auto
            FlashMode.OFF -> R.drawable.flash_off_circle to R.string.flash_off
        }
    }
}
