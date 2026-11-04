package app.grapheneos.camera.ui.previewoverlays.screen.mapper

import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.orientation.model.DeviceMotion
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelPose
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewContentState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysState
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelStateMapperTest {

    private val mapper = LevelStateMapperImpl()
    private val photoState = PreviewOverlaysState(
        previewContent = PreviewContentState(isPreviewStreaming = true),
        level = LevelState(isEnabled = true),
    )

    @Test
    fun eligiblePose_doesNotAppearWithoutTheStabilityDelay() {
        val level = map(tilt = 1f, horizon = 2f)

        assertEquals(LevelPose.Horizon, level.pose)
        assertFalse(level.visible)
    }

    @Test
    fun hiddenHorizon_requiresTheNarrowEntryRangeOnBothAxes() {
        assertEquals(LevelPose.Horizon, map(tilt = 8f, horizon = 25f).pose)
        assertEquals(LevelPose.Horizon, map(tilt = -8f, horizon = -25f).pose)
        listOf(
            motionOf(tilt = 9f),
            motionOf(tilt = -9f),
            motionOf(horizon = 26f),
            motionOf(horizon = -26f),
        ).forEach { motion ->
            assertNull(mapper.map(state = photoState, motion = motion).pose)
        }
    }

    @Test
    fun shownHorizon_staysVisibleInTheWiderRangeButCannotReappearThereAfterReset() {
        val shown = LevelState(isEnabled = true, visible = true, pose = LevelPose.Horizon)
        val retained = map(tilt = 45f, horizon = -45f, state = photoState.copy(level = shown))

        assertEquals(LevelPose.Horizon, retained.pose)
        assertTrue(retained.visible)

        val reset = photoState.copy(level = retained.copy(visible = false))
        assertNull(map(tilt = 45f, horizon = -45f, state = reset).pose)
    }

    @Test
    fun shownHorizon_disappearsPastTheOriginalRangeOnEitherAxis() {
        val shown = LevelState(isEnabled = true, visible = true, pose = LevelPose.Horizon)
        val motions = listOf(
            motionOf(tilt = 46f),
            motionOf(tilt = -46f),
            motionOf(horizon = 46f),
            motionOf(horizon = -46f),
        )

        motions.forEach { motion ->
            val level = mapper.map(state = photoState.copy(level = shown), motion = motion)

            assertFalse(level.visible)
            assertNull(level.pose)
        }
    }

    @Test
    fun betweenHorizonAndVertical_neitherGuideIsEligible() {
        listOf(-74f, -50f, 50f, 74f).forEach { horizon ->
            assertNull(map(horizon = horizon).pose)
        }
    }

    @Test
    fun verticalGuide_usesTheConfiguredEntryAngle() {
        assertEquals(LevelPose.FacingUp, map(horizon = -75f).pose)
        assertEquals(LevelPose.FacingDown, map(horizon = 75f).pose)
    }

    @Test
    fun verticalPoses_areIndependentOfTheTiltDirection() {
        assertEquals(LevelPose.FacingUp, map(tilt = -130f, horizon = -85f).pose)
        assertEquals(LevelPose.FacingDown, map(tilt = 120f, horizon = 85f).pose)
    }

    @Test
    fun changingPose_hidesThePreviousGuideBeforeShowingTheNextOne() {
        val shown = LevelState(isEnabled = true, visible = true, pose = LevelPose.Horizon)
        val level = map(horizon = 90f, state = photoState.copy(level = shown))

        assertEquals(LevelPose.FacingDown, level.pose)
        assertFalse(level.visible)
    }

    @Test
    fun disabledLevel_hasNoEligiblePose() {
        val state = photoState.copy(level = LevelState(isEnabled = false))

        assertNull(map(state = state).pose)
        assertFalse(map(state = state).visible)
    }

    @Test
    fun selfTimer_hidesAnAlreadyShownLevel() {
        val shown = LevelState(
            isEnabled = true,
            visible = true,
            pose = LevelPose.Horizon,
            tiltDegrees = 1f,
            horizonDegrees = 2f,
            orientation = DeviceOrientation.DEGREES_0,
        )
        val state = photoState.copy(
            level = shown,
            previewContent = PreviewContentState(
                isPreviewStreaming = true,
                selfTimerRemaining = 3.seconds,
            ),
        )

        val level = mapper.map(
            state = state,
            motion = motionOf(tilt = 3f, horizon = 4f),
        )

        assertFalse(level.visible)
        assertNull(level.pose)
    }

    private fun map(
        tilt: Float = 0f,
        horizon: Float = 0f,
        state: PreviewOverlaysState = photoState,
    ): LevelState {
        return mapper.map(state = state, motion = motionOf(tilt = tilt, horizon = horizon))
    }

    private fun motionOf(
        tilt: Float = 0f,
        horizon: Float = 0f,
    ): DeviceMotion {
        return DeviceMotion(
            orientation = DeviceOrientation.DEGREES_0,
            tiltDegrees = tilt,
            horizonDegrees = horizon,
        )
    }
}
