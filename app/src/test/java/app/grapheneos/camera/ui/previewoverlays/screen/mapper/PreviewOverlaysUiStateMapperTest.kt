package app.grapheneos.camera.ui.previewoverlays.screen.mapper

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelPose
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelState
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewContentState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysUiState
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewOverlaysUiStateMapperTest {

    private val mapper: PreviewOverlaysUiStateMapper = PreviewOverlaysUiStateMapperImpl()

    @Test
    fun beforePreviewContentArrives_showsNothing() {
        val state = mapper.map(PreviewOverlaysState(gridType = GridType.THREE_BY_THREE))

        assertEquals(PreviewOverlaysUiState(), state)
    }

    @Test
    fun gridNone_showsNoGrid() {
        val state = mapper.map(
            PreviewOverlaysState(previewContent = PreviewContentState()),
        )

        assertFalse(state.isGridVisible)
    }

    @Test
    fun photoAndVideoModes_useEachEnabledGridPattern() {
        val patterns = mapOf(
            GridType.THREE_BY_THREE to CompositionGridPattern.Thirds,
            GridType.FOUR_BY_FOUR to CompositionGridPattern.Quarters,
            GridType.GOLDEN_RATIO to CompositionGridPattern.GoldenRatio,
        )

        val modes = listOf(
            CameraMode.CAMERA,
            CameraMode.VIDEO,
            CameraMode.AUTO,
            CameraMode.FACE_RETOUCH,
            CameraMode.PORTRAIT,
            CameraMode.NIGHT,
            CameraMode.HDR,
        )

        modes.forEach { mode ->
            patterns.forEach { (gridType, pattern) ->
                val state = mapper.map(
                    PreviewOverlaysState(
                        gridType = gridType,
                        previewContent = PreviewContentState(mode = mode),
                    ),
                )

                assertTrue(state.isGridVisible)
                assertEquals(pattern, state.gridPattern)
            }
        }
    }

    @Test
    fun qrTransitionAndCaptureResult_eachHideEveryGrid() {
        val previewStates = listOf(
            PreviewContentState(mode = CameraMode.QR_SCAN),
            PreviewContentState(isTransitionFrameVisible = true),
            PreviewContentState(isCaptureResultVisible = true),
        )

        previewStates.forEach { content ->
            GridType.entries.forEach { gridType ->
                val state = mapper.map(
                    PreviewOverlaysState(
                        gridType = gridType,
                        previewContent = content,
                    ),
                )

                assertFalse(state.isGridVisible)
            }
        }
    }

    @Test
    fun previewFlash_hidesTheGridUntilItEnds() {
        val state = PreviewOverlaysState(
            gridType = GridType.GOLDEN_RATIO,
            previewContent = PreviewContentState(),
            isPreviewFlashVisible = true,
        )

        assertFalse(mapper.map(state).isGridVisible)
        assertTrue(mapper.map(state.copy(isPreviewFlashVisible = false)).isGridVisible)
    }

    @Test
    fun selfTimer_preservesTheRemainingDuration() {
        val remaining = 1500.milliseconds
        val state = mapper.map(
            PreviewOverlaysState(
                previewContent = PreviewContentState(selfTimerRemaining = remaining),
            ),
        )

        assertTrue(remaining == state.selfTimerRemaining)
    }

    @Test
    fun selfTimer_isHiddenBehindOtherPreviewContent() {
        val content = PreviewContentState(selfTimerRemaining = 3.seconds)
        val states = listOf(
            PreviewOverlaysState(previewContent = content.copy(mode = CameraMode.QR_SCAN)),
            PreviewOverlaysState(
                previewContent = content.copy(isTransitionFrameVisible = true),
            ),
            PreviewOverlaysState(
                previewContent = content.copy(isCaptureResultVisible = true),
            ),
            PreviewOverlaysState(
                previewContent = content,
                isPreviewFlashVisible = true,
            ),
        )

        states.forEach { state ->
            assertNull(mapper.map(state).selfTimerRemaining)
        }
    }

    @Test
    fun selfTimer_isRemovedWhenTheRemainingTimeIsCleared() {
        val content = PreviewContentState(selfTimerRemaining = 3.seconds)
        val state = PreviewOverlaysState(previewContent = content)

        assertTrue(3.seconds == mapper.map(state).selfTimerRemaining)
        assertNull(
            mapper.map(
                state.copy(previewContent = content.copy(selfTimerRemaining = null)),
            ).selfTimerRemaining,
        )
    }

    @Test
    fun level_mapsTheSensorAxesToRollAndPitch() {
        val state = PreviewOverlaysState(
            previewContent = PreviewContentState(isPreviewStreaming = true),
            level = LevelState(
                isEnabled = true,
                visible = true,
                pose = LevelPose.Horizon,
                tiltDegrees = -3f,
                horizonDegrees = 12f,
            ),
        )

        assertEquals(
            LevelUiState(
                visible = true,
                rollDegrees = -3f,
                pitchDegrees = -12f,
            ),
            mapper.mapLevel(state),
        )
    }

    @Test
    fun level_modeFollowsTheClassifiedPoseInsteadOfReclassifyingTheAngles() {
        val modes = mapOf(
            LevelPose.Horizon to LevelIndicatorMode.Horizon,
            LevelPose.FacingUp to LevelIndicatorMode.Crosshairs,
            LevelPose.FacingDown to LevelIndicatorMode.Crosshairs,
        )
        modes.forEach { (pose, mode) ->
            val state = PreviewOverlaysState(
                level = LevelState(pose = pose, tiltDegrees = 35f, horizonDegrees = 70f),
            )

            assertEquals(mode, mapper.mapLevel(state).mode)
        }
    }

    @Test
    fun level_turnsAgainstEveryDeviceOrientation() {
        val rotations = mapOf(
            DeviceOrientation.DEGREES_0 to 0f,
            DeviceOrientation.DEGREES_90 to 270f,
            DeviceOrientation.DEGREES_180 to 180f,
            DeviceOrientation.DEGREES_270 to 90f,
        )

        rotations.forEach { (orientation, rotation) ->
            val state = PreviewOverlaysState(
                level = LevelState(pose = LevelPose.Horizon, orientation = orientation),
            )

            assertEquals(rotation, mapper.mapLevel(state).rotationDegrees)
        }
    }

    @Test
    fun level_rotationIsRelativeToTheDisplay() {
        val state = PreviewOverlaysState(
            level = LevelState(
                pose = LevelPose.Horizon,
                orientation = DeviceOrientation.DEGREES_90,
            ),
            displayRotation = DeviceOrientation.DEGREES_270,
        )

        assertEquals(180f, mapper.mapLevel(state).rotationDegrees)
    }

    @Test
    fun level_withoutDeviceOrientation_staysUpright() {
        val state = PreviewOverlaysState(
            level = LevelState(pose = LevelPose.Horizon),
            displayRotation = DeviceOrientation.DEGREES_270,
        )

        assertEquals(LevelUiState(), mapper.mapLevel(state))
    }

    @Test
    fun level_zeroHorizonMapsToTheDefaultPitchFromEitherSide() {
        listOf(0f, -0f).forEach { horizon ->
            val state = PreviewOverlaysState(
                level = LevelState(pose = LevelPose.Horizon, horizonDegrees = horizon),
            )

            assertEquals(LevelUiState(), mapper.mapLevel(state))
        }
    }

    @Test
    fun level_isHiddenOutsidePhotoModeAndBehindOtherPreviewContent() {
        val content = PreviewContentState(isPreviewStreaming = true)
        val shown = PreviewOverlaysState(
            previewContent = content,
            level = LevelState(isEnabled = true, visible = true, pose = LevelPose.Horizon),
        )
        val hiddenStates = listOf(
            shown.copy(level = shown.level.copy(pose = null)),
            shown.copy(level = shown.level.copy(isEnabled = false)),
            shown.copy(previewContent = null),
            shown.copy(previewContent = content.copy(mode = CameraMode.VIDEO)),
            shown.copy(previewContent = content.copy(mode = CameraMode.QR_SCAN)),
            shown.copy(previewContent = content.copy(lensFacing = LensFacing.FRONT)),
            shown.copy(previewContent = content.copy(isTransitionFrameVisible = true)),
            shown.copy(previewContent = content.copy(isCaptureResultVisible = true)),
            shown.copy(isPreviewFlashVisible = true),
            shown.copy(previewContent = content.copy(isPreviewStreaming = false)),
            shown.copy(previewContent = content.copy(isTakingPicture = true)),
            shown.copy(previewContent = content.copy(selfTimerRemaining = 3.seconds)),
        )

        assertTrue(mapper.mapLevel(shown).visible)
        hiddenStates.forEach { state -> assertFalse(mapper.mapLevel(state).visible) }
    }
}
