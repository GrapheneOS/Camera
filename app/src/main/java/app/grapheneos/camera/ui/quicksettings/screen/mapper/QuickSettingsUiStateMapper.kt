package app.grapheneos.camera.ui.quicksettings.screen.mapper

import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickChoiceUiModel
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsState
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.toImmutableList

internal interface QuickSettingsUiStateMapper {
    fun map(state: QuickSettingsState): QuickSettingsUiState
}

internal class QuickSettingsUiStateMapperImpl @Inject constructor() : QuickSettingsUiStateMapper {

    override fun map(state: QuickSettingsState): QuickSettingsUiState {
        val camera = state.boundCamera ?: return QuickSettingsUiState()

        return QuickSettingsUiState(
            isVideoMode = camera.isVideoMode,
            isSelfIlluminating = camera.lensFacing == LensFacing.FRONT &&
                state.modeSettings.selfIllumination,
            flash = flash(
                state = state,
                camera = camera,
            ),
            torch = torch(
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
            audio = audio(
                state = state,
                camera = camera,
            ),
        )
    }

    private fun flash(
        state: QuickSettingsState,
        camera: BoundCamera,
    ): QuickChoiceUiModel<FlashMode>? {
        return when {
            camera.hasFlashUnit -> QuickChoiceUiModel(
                selected = state.modeSettings.flashMode,
                options = FlashMode.entries.toImmutableList(),
                enabled = !state.requiresVideoModeOnly,
            )

            else -> null
        }
    }

    private fun torch(
        state: QuickSettingsState,
        camera: BoundCamera,
    ): Boolean? {
        return when {
            camera.hasFlashUnit -> state.isTorchOn
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
            !camera.isVideoMode -> QuickChoiceUiModel(
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
            !camera.isVideoMode -> QuickChoiceUiModel(
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
            camera.isVideoMode -> QuickChoiceUiModel(
                selected = shownVideoQuality(
                    stored = state.modeSettings.videoQuality,
                    offered = camera.videoQualities,
                ),
                options = camera.videoQualities.toImmutableList(),
            )

            else -> null
        }
    }

    private fun shownVideoQuality(
        stored: VideoQuality,
        offered: List<VideoQuality>,
    ): VideoQuality {
        // CameraX lists the supported qualities from the highest down.
        return when (stored) {
            VideoQuality.HIGHEST -> offered.firstOrNull() ?: stored
            else -> stored
        }
    }

    private fun audio(
        state: QuickSettingsState,
        camera: BoundCamera,
    ): Boolean? {
        return when {
            camera.isVideoMode -> state.settings.includeAudio
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
