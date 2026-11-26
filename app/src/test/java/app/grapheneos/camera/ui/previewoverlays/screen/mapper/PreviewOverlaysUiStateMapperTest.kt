package app.grapheneos.camera.ui.previewoverlays.screen.mapper

import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.domain.camera.mapper.ZoomPresetsMapperImpl
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.components.focusindicator.model.FocusIndicatorAppearance
import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.ExposureState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusPhase
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusPress
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusSelection
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.LevelPose
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.LevelState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewContentState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewControl
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.ZoomState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.FocusUiModel
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.LevelUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewControlUiModel
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewOverlaysUiState
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewOverlaysUiStateMapperTest {

    private val mapper: PreviewOverlaysUiStateMapper = PreviewOverlaysUiStateMapperImpl(
        zoomPresetsMapper = ZoomPresetsMapperImpl(),
    )

    @Test
    fun exposure_mapsCameraIndicesToEvAndTakesTheZoomSlot() {
        val state = zoomState().copy(
            activeControl = PreviewControl.Exposure,
            exposure = ExposureState(
                value = CameraExposure(
                    compensationIndex = 3,
                    compensationRange = -12..12,
                    compensationStep = 1f / 6f,
                    isCompensationSupported = true,
                ),
                isAdjusted = true,
            ),
        )

        val mapped = mapper.map(state)
        val exposure = requireNotNull(mapped.exposure)

        assertEquals(0.5f, exposure.value)
        assertEquals(-2f..2f, exposure.range)
        assertEquals(23, exposure.steps)
        assertEquals(6, exposure.majorTickInterval)
        assertTrue(exposure.isResetVisible)
        assertNull(mapped.zoom)
        val zoomSelected = mapper.map(state.copy(activeControl = PreviewControl.Zoom))
        assertNotNull(zoomSelected.zoom)
        assertNull(zoomSelected.exposure)
    }

    @Test
    fun unavailableExposure_fallsBackToZoom() {
        val value = CameraExposure(
            compensationIndex = 0,
            compensationRange = 0..0,
            compensationStep = 0f,
            isCompensationSupported = false,
        )
        val state = zoomState().copy(
            activeControl = PreviewControl.Exposure,
            exposure = ExposureState(value = value),
        )

        val mapped = mapper.map(state)

        assertNull(mapped.exposure)
        assertNotNull(mapped.zoom)
    }

    @Test
    fun exposure_twoSupportedIndices_haveNoIntermediateSteps() {
        val state = zoomState().copy(
            activeControl = PreviewControl.Exposure,
            exposure = ExposureState(
                value = CameraExposure(
                    compensationIndex = 0,
                    compensationRange = 0..1,
                    compensationStep = 0.5f,
                    isCompensationSupported = true,
                ),
            ),
        )

        val exposure = requireNotNull(mapper.map(state).exposure)

        assertEquals(0f..0.5f, exposure.range)
        assertEquals(0, exposure.steps)
    }

    @Test
    fun zoom_currentValueIsShownExceptDuringAPresetTransition() {
        val state = zoomState()
        val selecting = state.copy(
            zoom = state.zoom.copy(
                pendingPreset = ZoomState.Preset(
                    ratio = 2f,
                    requestId = 1,
                ),
            ),
        )

        assertTrue(requireNotNull(mapper.map(state).zoom).isCurrentValueVisible)
        assertFalse(requireNotNull(mapper.map(selecting).zoom).isCurrentValueVisible)
    }

    @Test
    fun zoom_previewPinchIsPassedToTheControl() {
        val state = zoomState()
        val pinching = state.copy(zoom = state.zoom.copy(isPreviewPinching = true))

        assertFalse(requireNotNull(mapper.map(state).zoom).isPinching)
        assertTrue(requireNotNull(mapper.map(pinching).zoom).isPinching)
    }

    @Test
    fun zoom_mapsTheRatioRangeAndBackCameraPresets() {
        val state = zoomState()
        val zoom = requireNotNull(mapper.map(state).zoom)

        assertEquals(1f, mapper.mapZoomRatio(state))
        assertEquals(0.5f..8f, zoom.range)
        assertEquals(listOf(0.5f, 1f, 2f), zoom.presets)
        assertFalse(zoom.isExpanded)
        val expanded = state.copy(zoom = state.zoom.copy(isExpanded = true))
        assertTrue(requireNotNull(mapper.map(expanded).zoom).isExpanded)
    }

    @Test
    fun switchingLenses_keepsThePreviousStopsUntilTheNewCameraReportsItsZoom() {
        val back = zoomState()
        val frontContent = requireNotNull(back.previewContent).copy(lensFacing = LensFacing.FRONT)
        val switching = back.copy(
            previewContent = frontContent,
            zoom = back.zoom.copy(value = requireNotNull(back.zoom.value).copy(zoomRatio = 0.5f)),
        )

        val zoom = requireNotNull(mapper.map(switching).zoom)

        assertEquals(listOf(0.5f, 1f, 2f), zoom.presets)
        assertTrue(zoom.isCurrentValueVisible)
    }

    @Test
    fun frontZoom_usesItsOwnPresetsAndTheEntireCameraRange() {
        val state = zoomState()
        val front = state.copy(
            previewContent = state.previewContent?.copy(lensFacing = LensFacing.FRONT),
            zoom = state.zoom.copy(
                lensFacing = LensFacing.FRONT,
                value = state.zoom.value?.copy(
                    minZoomRatio = 1f,
                    maxZoomRatio = 4f,
                ),
            ),
        )
        val zoom = requireNotNull(mapper.map(front).zoom)

        assertEquals(listOf(1f, 2f), zoom.presets)
        assertEquals(1f..4f, zoom.range)
    }

    @Test
    fun zoom_isHiddenUntilAnAdjustableRangeIsKnown() {
        val state = zoomState()
        val value = requireNotNull(state.zoom.value)
        val unavailable = listOf(
            null,
            value.copy(
                minZoomRatio = 1f,
                maxZoomRatio = 1f,
            ),
            value.copy(
                minZoomRatio = 2f,
                maxZoomRatio = 1f,
            ),
            value.copy(minZoomRatio = 0f),
            value.copy(maxZoomRatio = Float.POSITIVE_INFINITY),
        )
        unavailable.forEach { cameraZoom ->
            assertNull(mapper.map(state.copy(zoom = ZoomState(value = cameraZoom))).zoom)
        }
    }

    @Test
    fun zoom_isHiddenWithoutContentAndBehindCaptureResultsOrFlash() {
        val state = zoomState()
        val content = requireNotNull(state.previewContent)
        val unavailable = listOf(
            state.copy(previewContent = null),
            state.copy(isPreviewFlashVisible = true),
            state.copy(previewContent = content.copy(mode = CameraMode.QR_SCAN)),
            state.copy(previewContent = content.copy(isCaptureResultVisible = true)),
        )

        unavailable.forEach { hidden -> assertNull(mapper.map(hidden).zoom) }
    }

    @Test
    fun zoom_staysVisibleWhileTheCameraIsSwitching() {
        val state = zoomState()
        val content = requireNotNull(state.previewContent)
        val switching = listOf(
            content.copy(isPreviewStreaming = false),
            content.copy(isTransitionFrameVisible = true),
            content.copy(
                isPreviewStreaming = false,
                isTransitionFrameVisible = true,
                lensFacing = LensFacing.FRONT,
            ),
        )

        switching.forEach { preview ->
            val zoom = requireNotNull(mapper.map(state.copy(previewContent = preview)).zoom)

            assertEquals(0.5f..8f, zoom.range)
        }
    }

    @Test
    fun focus_showsTheSelectionAtItsCoordinates() {
        val state = focusState(selection = FocusSelection(requestId = 3, x = 10f, y = 20f))

        assertEquals(
            FocusUiModel(requestId = 3, x = 10f, y = 20f, visible = true),
            mapper.map(state).focus,
        )
    }

    @Test
    fun focus_appearanceFollowsThePhase() {
        val appearances = mapOf(
            FocusPhase.Focusing to FocusIndicatorAppearance.Focusing,
            FocusPhase.Focused to FocusIndicatorAppearance.Focused,
            FocusPhase.Locked to FocusIndicatorAppearance.Locked,
        )

        appearances.forEach { (phase, appearance) ->
            val selection = FocusSelection(requestId = 1, x = 10f, y = 20f, phase = phase)
            val focus = mapper.map(focusState(selection = selection)).focus

            assertEquals(appearance, focus?.appearance)
        }
    }

    @Test
    fun hiddenSelectionOrCaptureFlash_hidesTheRing() {
        val selection = FocusSelection(requestId = 1, x = 10f, y = 20f)
        val hiddenStates = listOf(
            focusState(selection = selection.copy(visible = false)),
            focusState(selection = selection).copy(isPreviewFlashVisible = true),
        )

        hiddenStates.forEach { state ->
            assertFalse(requireNotNull(mapper.map(state).focus).visible)
        }
    }

    @Test
    fun pressOnANewPoint_waitsForTheTapBeforeShowingAFocusRing() {
        val press = FocusPress.NewPoint(requestId = 2, x = 30f, y = 40f)
        val selection = FocusSelection(requestId = 1, x = 10f, y = 20f)

        assertNull(mapper.map(focusState(selection = null, press = press)).focus)
        assertEquals(
            mapper.map(focusState(selection = selection)).focus,
            mapper.map(focusState(selection = selection, press = press)).focus,
        )
    }

    @Test
    fun pressOnTheRing_keepsShowingTheSelection() {
        val state = focusState(
            selection = FocusSelection(requestId = 1, x = 10f, y = 20f),
            press = FocusPress.OnRing(x = 12f, y = 21f),
        )

        assertEquals(1L, mapper.map(state).focus?.requestId)
    }

    @Test
    fun beforePreviewContentArrives_showsNothing() {
        val settings = CameraSettings(gridType = GridType.THREE_BY_THREE)
        val state = mapper.map(PreviewOverlaysState(settings = settings))

        assertEquals(PreviewOverlaysUiState(), state)
    }

    @Test
    fun gridNone_showsNoGrid() {
        val state = mapper.map(
            PreviewOverlaysState(previewContent = PreviewContentState()),
        )

        assertNull(state.gridPattern)
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
                        settings = CameraSettings(gridType = gridType),
                        previewContent = PreviewContentState(mode = mode),
                    ),
                )

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
                        settings = CameraSettings(gridType = gridType),
                        previewContent = content,
                    ),
                )

                assertNull(state.gridPattern)
            }
        }
    }

    @Test
    fun previewFlash_hidesTheGridUntilItEnds() {
        val state = PreviewOverlaysState(
            settings = CameraSettings(gridType = GridType.GOLDEN_RATIO),
            previewContent = PreviewContentState(),
            isPreviewFlashVisible = true,
        )

        assertNull(mapper.map(state).gridPattern)
        assertEquals(
            CompositionGridPattern.GoldenRatio,
            mapper.map(state.copy(isPreviewFlashVisible = false)).gridPattern,
        )
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
            settings = CameraSettings(gyroscopeSuggestions = true),
            previewContent = PreviewContentState(isPreviewStreaming = true),
            level = LevelState(
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
            settings = CameraSettings(gyroscopeSuggestions = true),
            previewContent = content,
            level = LevelState(visible = true, pose = LevelPose.Horizon),
        )
        val hiddenStates = listOf(
            shown.copy(level = shown.level.copy(pose = null)),
            shown.copy(settings = CameraSettings(gyroscopeSuggestions = false)),
            shown.copy(previewContent = null),
            shown.copy(previewContent = content.copy(mode = CameraMode.VIDEO)),
            shown.copy(previewContent = content.copy(mode = CameraMode.QR_SCAN)),
            shown.copy(previewContent = content.copy(lensFacing = LensFacing.FRONT)),
            shown.copy(previewContent = content.copy(isTransitionFrameVisible = true)),
            shown.copy(previewContent = content.copy(isCaptureResultVisible = true)),
            shown.copy(previewContent = content.copy(isPreviewStreaming = false)),
            shown.copy(previewContent = content.copy(selfTimerRemaining = 3.seconds)),
        )

        assertTrue(mapper.mapLevel(shown).visible)
        assertTrue(mapper.mapLevel(shown.copy(isPreviewFlashVisible = true)).visible)
        hiddenStates.forEach { state -> assertFalse(mapper.mapLevel(state).visible) }
    }

    private fun focusState(
        selection: FocusSelection?,
        press: FocusPress? = null,
    ): PreviewOverlaysState {
        return PreviewOverlaysState(
            previewContent = PreviewContentState(isPreviewStreaming = true),
            focus = FocusState(selection = selection, press = press),
        )
    }
    private fun zoomState(): PreviewOverlaysState {
        return PreviewOverlaysState(
            previewContent = PreviewContentState(isPreviewStreaming = true),
            zoom = ZoomState(
                value = CameraZoom(
                    zoomRatio = 1f,
                    linearZoom = 0f,
                    minZoomRatio = 0.5f,
                    maxZoomRatio = 8f,
                ),
            ),
        )
    }

    private val PreviewOverlaysUiState.zoom: PreviewControlUiModel.Zoom?
        get() {
            return control as? PreviewControlUiModel.Zoom
        }

    private val PreviewOverlaysUiState.exposure: PreviewControlUiModel.Exposure?
        get() {
            return control as? PreviewControlUiModel.Exposure
        }
}
