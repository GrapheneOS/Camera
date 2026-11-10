package app.grapheneos.camera.ui.statusbar.screen.mapper

import app.grapheneos.camera.data.camera.mapper.CameraFlashMapper
import app.grapheneos.camera.data.camera.mapper.VideoQualityMapper
import app.grapheneos.camera.data.camera.model.BoundCamera
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

internal class StatusBarUiStateMapperImpl @Inject constructor(
    private val flashMapper: CameraFlashMapper,
    private val videoQualityMapper: VideoQualityMapper,
) : StatusBarUiStateMapper {

    override fun map(state: StatusBarState): StatusBarUiState {
        val camera = state.boundCamera ?: return StatusBarUiState()
        val flash = flashMapper.map(
            camera = camera,
            requestedMode = state.modeSettings.flashMode,
            frameRate = state.modeSettings.videoFrameRate,
        )

        return StatusBarUiState(
            items = items(
                state = state,
                camera = camera,
                flashMode = flash.selectedMode,
            ),
            isSelfIlluminating = flash.isSelfIlluminating(),
        )
    }

    // In the order of the sheet's rows; the bar drops them from the end when they do not fit.
    private fun items(
        state: StatusBarState,
        camera: BoundCamera,
        flashMode: FlashMode,
    ): ImmutableList<StatusItemUiModel> {
        val selfTimer = state.settings.selfTimerDuration

        return buildList {
            if (flashMode != FlashMode.OFF) {
                add(StatusItemUiModel.Flash(mode = flashMode))
            }
            if (!camera.isVideoMode && selfTimer != Duration.ZERO) {
                add(StatusItemUiModel.SelfTimer(duration = selfTimer))
            }
            if (camera.isVideoMode) {
                val quality = videoQualityMapper.map(
                    camera = camera,
                    requestedQuality = state.modeSettings.videoQuality,
                )

                add(StatusItemUiModel.Resolution(quality = quality))
            }
        }.toImmutableList()
    }
}
