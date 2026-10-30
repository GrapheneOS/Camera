package app.grapheneos.camera.ui.statusbar.screen.mapper

import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarState
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarUiState
import app.grapheneos.camera.ui.statusbar.screen.model.StatusItemUiModel
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal interface StatusBarUiStateMapper {
    fun map(state: StatusBarState): StatusBarUiState
}

internal class StatusBarUiStateMapperImpl @Inject constructor() : StatusBarUiStateMapper {

    override fun map(state: StatusBarState): StatusBarUiState {
        val camera = state.boundCamera ?: return StatusBarUiState()
        val isSelfIlluminating = camera.lensFacing == LensFacing.FRONT &&
            state.modeSettings.selfIllumination

        return StatusBarUiState(
            items = items(
                state = state,
                camera = camera,
                isSelfIlluminating = isSelfIlluminating,
            ),
            isSelfIlluminating = isSelfIlluminating,
        )
    }

    // In the order of the sheet's rows; the bar drops them from the end when they do not fit.
    private fun items(
        state: StatusBarState,
        camera: BoundCamera,
        isSelfIlluminating: Boolean,
    ): ImmutableList<StatusItemUiModel> {
        val selfTimer = state.settings.selfTimerDuration
        val flashMode = state.modeSettings.flashMode

        return buildList {
            if (camera.hasFlashUnit && !camera.isVideoMode && flashMode != FlashMode.OFF) {
                add(StatusItemUiModel.Flash(mode = flashMode))
            }
            if (camera.hasFlashUnit && camera.isVideoMode && state.modeSettings.isVideoLightOn()) {
                add(StatusItemUiModel.VideoLight)
            }
            if (isSelfIlluminating) {
                add(StatusItemUiModel.SelfIllumination)
            }
            if (!camera.isVideoMode && selfTimer != Duration.ZERO) {
                add(StatusItemUiModel.SelfTimer(duration = selfTimer))
            }
            if (camera.isVideoMode) {
                val quality = camera.recordedVideoQuality(stored = state.modeSettings.videoQuality)

                add(StatusItemUiModel.Resolution(quality = quality))
            }
        }.toImmutableList()
    }
}
