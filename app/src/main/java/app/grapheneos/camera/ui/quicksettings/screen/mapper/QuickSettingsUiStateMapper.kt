package app.grapheneos.camera.ui.quicksettings.screen.mapper

import app.grapheneos.camera.data.camera.mapper.CameraFlashMapper
import app.grapheneos.camera.data.camera.mapper.VideoQualityMapper
import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.CameraFlash
import app.grapheneos.camera.data.camera.model.HighFrameRateSupport
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.ui.quicksettings.screen.model.OptionLimit
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickChoiceUiModel
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsDialog
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsState
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

internal interface QuickSettingsUiStateMapper {
    fun map(state: QuickSettingsState): QuickSettingsUiState
}

internal class QuickSettingsUiStateMapperImpl @Inject constructor(
    private val flashMapper: CameraFlashMapper,
    private val videoQualityMapper: VideoQualityMapper,
) : QuickSettingsUiStateMapper {

    override fun map(state: QuickSettingsState): QuickSettingsUiState {
        val settingsButtonVisible = !state.isRecording
        val camera = state.boundCamera ?: return QuickSettingsUiState(
            settingsButtonVisible = settingsButtonVisible,
        )

        val flash = flashMapper.map(
            camera = camera,
            requestedMode = state.modeSettings.flashMode,
            frameRate = state.modeSettings.videoFrameRate,
        )

        return QuickSettingsUiState(
            settingsButtonVisible = settingsButtonVisible,
            isVideoMode = camera.isVideoMode,
            isSelfIlluminating = flash.isSelfIlluminating(),
            flash = flash(
                camera = camera,
                flash = flash,
            ),
            selfTimer = selfTimer(
                state = state,
                camera = camera,
            ),
            aspectRatio = aspectRatio(
                state = state,
                camera = camera,
            ),
            videoQuality = videoQuality(
                state = state,
                camera = camera,
            ),
            videoFrameRate = videoFrameRate(
                state = state,
                camera = camera,
            ),
            audio = audio(
                state = state,
                camera = camera,
            ),
            dialog = state.shownLimit?.let(QuickSettingsDialog::OptionUnavailable),
        )
    }

    private fun flash(
        camera: BoundCamera,
        flash: CameraFlash,
    ): QuickChoiceUiModel<FlashMode>? {
        val options = camera.flashModes()
        if (options.size <= 1) return null

        return QuickChoiceUiModel(
            selected = flash.selectedMode,
            options = options.toImmutableList(),
            unavailable = flash.unavailableModes.toImmutableSet(),
            limit = OptionLimit.LowLightBoostAtHighFrameRate.takeIf {
                FlashMode.LOW_LIGHT_BOOST in flash.unavailableModes
            },
        )
    }

    private fun selfTimer(
        state: QuickSettingsState,
        camera: BoundCamera,
    ): QuickChoiceUiModel<Duration>? {
        return when {
            !state.isRecording && !camera.isVideoMode -> QuickChoiceUiModel(
                selected = state.settings.selfTimerDuration,
                options = SELF_TIMER_OPTIONS.toImmutableList(),
            )

            else -> null
        }
    }

    private fun aspectRatio(
        state: QuickSettingsState,
        camera: BoundCamera,
    ): QuickChoiceUiModel<AspectRatio>? {
        return when {
            !state.isRecording && !camera.isVideoMode -> QuickChoiceUiModel(
                selected = state.settings.aspectRatio,
                options = AspectRatio.entries.toImmutableList(),
            )

            else -> null
        }
    }

    private fun videoQuality(
        state: QuickSettingsState,
        camera: BoundCamera,
    ): QuickChoiceUiModel<VideoQuality>? {
        return when {
            !state.isRecording && camera.isVideoMode -> QuickChoiceUiModel(
                selected = videoQualityMapper.map(
                    camera = camera,
                    requestedQuality = state.modeSettings.videoQuality,
                ),
                options = camera.videoQualities.reversed().toImmutableList(),
            )

            else -> null
        }
    }

    // Shows what is recorded; a stored 60 waits for a camera that can record it.
    private fun videoFrameRate(
        state: QuickSettingsState,
        camera: BoundCamera,
    ): QuickChoiceUiModel<VideoFrameRate>? {
        if (state.isRecording) return null

        return when (val highFrameRate = camera.highFrameRate) {
            null -> null
            HighFrameRateSupport.Supported -> QuickChoiceUiModel(
                selected = state.modeSettings.videoFrameRate,
                options = VideoFrameRate.entries.toImmutableList(),
            )

            is HighFrameRateSupport.Unavailable -> QuickChoiceUiModel(
                selected = VideoFrameRate.FPS_30,
                options = VideoFrameRate.entries.toImmutableList(),
                unavailable = persistentSetOf(VideoFrameRate.FPS_60),
                limit = OptionLimit.HighFrameRate(limit = highFrameRate.limit),
            )
        }
    }

    private fun audio(
        state: QuickSettingsState,
        camera: BoundCamera,
    ): Boolean? {
        return when {
            !state.isRecording && camera.isVideoMode -> state.settings.includeAudio
            else -> null
        }
    }

    private companion object {
        val SELF_TIMER_OPTIONS = listOf(
            Duration.ZERO,
            3.seconds,
            5.seconds,
            10.seconds,
        )
    }
}
