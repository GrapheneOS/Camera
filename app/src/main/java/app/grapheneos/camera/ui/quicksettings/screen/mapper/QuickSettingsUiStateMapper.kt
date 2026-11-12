package app.grapheneos.camera.ui.quicksettings.screen.mapper

import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.HighFrameRateSupport
import app.grapheneos.camera.data.camera.model.LensFacing
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

internal interface QuickSettingsUiStateMapper {
    fun map(state: QuickSettingsState): QuickSettingsUiState
}

internal class QuickSettingsUiStateMapperImpl @Inject constructor() : QuickSettingsUiStateMapper {

    override fun map(state: QuickSettingsState): QuickSettingsUiState {
        val moreButtonVisible = !state.isRecording
        val camera = state.boundCamera ?: return QuickSettingsUiState(
            moreButtonVisible = moreButtonVisible,
        )

        return QuickSettingsUiState(
            moreButtonVisible = moreButtonVisible,
            isVideoMode = camera.isVideoMode,
            isSelfIlluminating = camera.lensFacing == LensFacing.FRONT &&
                state.modeSettings.selfIllumination,
            flash = flash(
                state = state,
                camera = camera,
            ),
            selfIllumination = selfIllumination(
                state = state,
                camera = camera,
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
        state: QuickSettingsState,
        camera: BoundCamera,
    ): QuickChoiceUiModel<FlashMode>? {
        val options = camera.flashModes()
        val isLowLightBoostLimited = camera.limitsLowLightBoost(state.modeSettings.videoFrameRate)

        return when {
            options.size > 1 -> QuickChoiceUiModel(
                selected = camera.appliedFlashMode(
                    stored = state.modeSettings.flashMode,
                    frameRate = state.modeSettings.videoFrameRate,
                ),
                options = options.toImmutableList(),
                unavailable = when {
                    isLowLightBoostLimited -> persistentSetOf(FlashMode.LOW_LIGHT_BOOST)
                    else -> persistentSetOf()
                },
                limit = OptionLimit.LowLightBoostAtHighFrameRate.takeIf { isLowLightBoostLimited },
            )

            else -> null
        }
    }

    private fun selfIllumination(
        state: QuickSettingsState,
        camera: BoundCamera,
    ): Boolean? {
        return when (camera.lensFacing) {
            LensFacing.FRONT -> state.modeSettings.selfIllumination
            LensFacing.BACK -> null
        }
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
                selected = camera.recordedVideoQuality(stored = state.modeSettings.videoQuality),
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
