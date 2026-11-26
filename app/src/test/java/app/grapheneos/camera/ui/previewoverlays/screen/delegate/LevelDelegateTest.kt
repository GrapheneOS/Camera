package app.grapheneos.camera.ui.previewoverlays.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.orientation.model.DeviceMotion
import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.domain.camera.mapper.ZoomPresetsMapperImpl
import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.previewoverlays.screen.mapper.LevelStateMapperImpl
import app.grapheneos.camera.ui.previewoverlays.screen.mapper.PreviewOverlaysUiStateMapperImpl
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.LevelPose
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.LevelState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewContentState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewOverlaysState
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LevelDelegateTest {

    private val deviceOrientationRepository = mockk<DeviceOrientationRepository>()

    private val motion = MutableSharedFlow<DeviceMotion>()

    private val previewContent = PreviewContentState(isPreviewStreaming = true)

    private val framing = PreviewOverlaysState(
        settings = CameraSettings(gyroscopeSuggestions = true),
        previewContent = previewContent,
    )

    private val stateHolder = StateHolder(initial = framing)

    private val delegate = LevelDelegateImpl(
        deviceOrientationRepository = deviceOrientationRepository,
        levelStateMapper = LevelStateMapperImpl(),
    )

    @Test
    fun levelAfterChange_clearsTheLevelWhenFramingIsInterrupted() {
        val shown = framing.copy(level = SHOWN_LEVEL)
        val interruptions = listOf(
            shown.copy(previewContent = previewContent.copy(selfTimerRemaining = 3.seconds)),
            shown.copy(previewContent = previewContent.copy(isPreviewStreaming = false)),
            shown.copy(previewContent = previewContent.copy(isTransitionFrameVisible = true)),
            shown.copy(previewContent = previewContent.copy(isCaptureResultVisible = true)),
            shown.copy(previewContent = previewContent.copy(mode = CameraMode.VIDEO)),
            shown.copy(previewContent = previewContent.copy(mode = CameraMode.QR_SCAN)),
            shown.copy(previewContent = previewContent.copy(mode = CameraMode.NIGHT)),
            shown.copy(previewContent = previewContent.copy(lensFacing = LensFacing.FRONT)),
            shown.copy(settings = shown.settings.copy(gyroscopeSuggestions = false)),
        )

        interruptions.forEach { current ->
            val level = delegate.levelAfterChange(previous = shown, current = current)

            assertEquals(current.toString(), LevelState(), level)
        }
    }

    @Test
    fun levelAfterChange_keepsTheLevelWhileFramingContinues() {
        val shown = framing.copy(level = SHOWN_LEVEL)
        val updates = listOf(
            shown,
            shown.copy(isPreviewFlashVisible = true),
            shown.copy(displayRotation = DeviceOrientation.DEGREES_90),
            shown.copy(settings = shown.settings.copy(gridType = GridType.GOLDEN_RATIO)),
        )

        updates.forEach { current ->
            val level = delegate.levelAfterChange(previous = shown, current = current)

            assertEquals(current.toString(), SHOWN_LEVEL, level)
        }
    }

    @Test
    fun level_disappearsOutsidePhotoMode() {
        runTest {
            startTracking()
            motion.emit(motionOf(tilt = 1f))
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(stateHolder.state.value.level.visible)

            stateHolder.update { state ->
                state.copy(previewContent = previewContent.copy(mode = CameraMode.VIDEO))
            }

            assertFalse(stateHolder.state.value.level.visible)
        }
    }

    @Test
    fun level_disappearsWhenTheSettingIsDisabledWithoutAnotherMotionReading() {
        runTest {
            startTracking()
            motion.emit(motionOf(tilt = 1f))
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(stateHolder.state.value.level.visible)

            changeLevelSetting(enabled = false)

            assertFalse(stateHolder.state.value.level.visible)
        }
    }

    @Test
    fun stoppingTracking_doesNotProcessMoreMotion() {
        runTest {
            val tracking = startTracking()
            motion.emit(motionOf(tilt = 2f))
            val previous = stateHolder.state.value.level

            tracking.cancel()
            motion.emit(motionOf(tilt = 10f))

            assertEquals(previous, stateHolder.state.value.level)
        }
    }

    @Test
    fun disabledLevel_doesNotSubscribeToMotion() {
        runTest {
            changeLevelSetting(enabled = false)
            startTracking()

            assertEquals(0, motion.subscriptionCount.value)

            changeLevelSetting(enabled = true)
            assertEquals(1, motion.subscriptionCount.value)
        }
    }

    @Test
    fun switchingToTheFrontCamera_hidesTheLevelAndStopsItsMotionSubscription() {
        runTest {
            startTracking()
            motion.emit(motionOf())
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(stateHolder.state.value.level.visible)

            stateHolder.update { state ->
                state.copy(previewContent = previewContent.copy(lensFacing = LensFacing.FRONT))
            }

            assertFalse(stateHolder.state.value.level.visible)
            assertEquals(0, motion.subscriptionCount.value)
        }
    }

    @Test
    fun switchingToTheFrontCamera_cancelsAPendingReveal() {
        runTest {
            startTracking()
            motion.emit(motionOf(horizon = 85f))
            advanceTimeBy(900.milliseconds)

            stateHolder.update { state ->
                state.copy(previewContent = previewContent.copy(lensFacing = LensFacing.FRONT))
            }
            advanceTimeBy(2.seconds)

            assertFalse(stateHolder.state.value.level.visible)
        }
    }

    @Test
    fun everyPose_appearsAfterOneFullSecondInsideItsRange() {
        runTest {
            startTracking()
            listOf(0f, 88f, -88f).forEach { horizon ->
                val reading = motionOf(horizon = horizon)
                motion.emit(reading)
                advanceTimeBy(999.milliseconds)
                assertFalse(stateHolder.state.value.level.visible)

                advanceTimeBy(1.milliseconds)
                runCurrent()
                assertTrue(stateHolder.state.value.level.visible)
            }
        }
    }

    @Test
    fun movementWithinTheRange_doesNotRestartTheDelay() {
        runTest {
            startTracking()
            motion.emit(motionOf())
            advanceTimeBy(400.milliseconds)
            motion.emit(motionOf(tilt = 5f, horizon = 20f))
            advanceTimeBy(400.milliseconds)
            motion.emit(motionOf(tilt = -5f, horizon = -20f))
            advanceTimeBy(199.milliseconds)
            assertFalse(stateHolder.state.value.level.visible)

            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertTrue(stateHolder.state.value.level.visible)
            assertEquals(-5f, stateHolder.state.value.level.tiltDegrees)
            assertEquals(-20f, stateHolder.state.value.level.horizonDegrees)
        }
    }

    @Test
    fun leavingTheEntryRange_cancelsTheRevealAndReenteringRestartsTheDelay() {
        runTest {
            startTracking()
            motion.emit(motionOf())
            advanceTimeBy(900.milliseconds)
            motion.emit(motionOf(horizon = 35f))
            advanceTimeBy(2.seconds)
            runCurrent()
            assertFalse(stateHolder.state.value.level.visible)

            motion.emit(motionOf(horizon = 20f))
            advanceTimeBy(999.milliseconds)
            assertFalse(stateHolder.state.value.level.visible)

            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertTrue(stateHolder.state.value.level.visible)
        }
    }

    @Test
    fun horizon_canReachTheOuterRangeButNeedsTheEntryRangeAgainAfterReset() {
        runTest {
            startTracking()
            motion.emit(motionOf(horizon = 20f))
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(stateHolder.state.value.level.visible)

            motion.emit(motionOf(tilt = 40f, horizon = 45f))
            assertTrue(stateHolder.state.value.level.visible)

            motion.emit(motionOf(horizon = 46f))
            assertFalse(stateHolder.state.value.level.visible)
            motion.emit(motionOf(horizon = 35f))
            advanceTimeBy(2.seconds)
            runCurrent()
            assertFalse(stateHolder.state.value.level.visible)

            motion.emit(motionOf(horizon = 20f))
            advanceTimeBy(999.milliseconds)
            assertFalse(stateHolder.state.value.level.visible)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertTrue(stateHolder.state.value.level.visible)
        }
    }

    @Test
    fun stoppingTracking_cancelsThePendingReveal() {
        runTest {
            val tracking = startTracking()
            motion.emit(motionOf())
            advanceTimeBy(900.milliseconds)
            tracking.cancel()
            advanceTimeBy(2.seconds)

            assertFalse(stateHolder.state.value.level.visible)
        }
    }

    @Test
    fun selfTimer_doesNotStartLevelTracking() {
        runTest {
            startTracking()
            stateHolder.update { state ->
                state.copy(previewContent = previewContent.copy(selfTimerRemaining = 3.seconds))
            }
            motion.emit(motionOf(horizon = 88f))
            advanceTimeBy(2.seconds)

            assertFalse(stateHolder.state.value.level.visible)
            assertEquals(0, motion.subscriptionCount.value)
        }
    }

    @Test
    fun interruptedFraming_hidesTheLevelAndRequiresFreshReadingsAndDelayOnReturn() {
        runTest {
            val interrupted = listOf(
                previewContent.copy(selfTimerRemaining = 3.seconds),
                previewContent.copy(isPreviewStreaming = false),
                previewContent.copy(isTransitionFrameVisible = true),
                previewContent.copy(isCaptureResultVisible = true),
            )
            startTracking()
            motion.emit(motionOf())
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(stateHolder.state.value.level.visible)

            interrupted.forEach { content ->
                stateHolder.update { it.copy(previewContent = content) }

                assertFalse(content.toString(), stateHolder.state.value.level.visible)
                assertNull(stateHolder.state.value.level.pose)
                assertEquals(0, motion.subscriptionCount.value)

                stateHolder.update { it.copy(previewContent = previewContent) }
                advanceTimeBy(2.seconds)
                runCurrent()
                assertFalse(stateHolder.state.value.level.visible)

                motion.emit(motionOf())
                advanceTimeBy(999.milliseconds)
                assertFalse(stateHolder.state.value.level.visible)
                advanceTimeBy(1.milliseconds)
                runCurrent()
                assertTrue(stateHolder.state.value.level.visible)
            }
        }
    }

    @Test
    fun switchingBetweenPhotoModes_restartsTheDelay() {
        runTest {
            startTracking()
            motion.emit(motionOf())
            advanceTimeBy(900.milliseconds)

            stateHolder.update {
                it.copy(previewContent = previewContent.copy(mode = CameraMode.NIGHT))
            }
            motion.emit(motionOf())
            advanceTimeBy(999.milliseconds)
            assertFalse(stateHolder.state.value.level.visible)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertTrue(stateHolder.state.value.level.visible)
        }
    }

    @Test
    fun clearedTrackingState_invalidatesAnAlreadyScheduledReveal() {
        runTest {
            startTracking()
            motion.emit(motionOf())
            advanceTimeBy(900.milliseconds)

            stateHolder.update { it.copy(level = LevelState()) }
            advanceTimeBy(100.milliseconds)
            runCurrent()
            assertFalse(stateHolder.state.value.level.visible)

            motion.emit(motionOf())
            advanceTimeBy(999.milliseconds)
            assertFalse(stateHolder.state.value.level.visible)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertTrue(stateHolder.state.value.level.visible)
        }
    }

    @Test
    fun movingFromHorizonToCross_requiresAnotherSecondInRange() {
        runTest {
            startTracking()
            motion.emit(motionOf())
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(stateHolder.state.value.level.visible)

            motion.emit(motionOf(horizon = 50f))
            assertFalse(stateHolder.state.value.level.visible)
            motion.emit(motionOf(horizon = 85f))
            advanceTimeBy(999.milliseconds)
            assertFalse(stateHolder.state.value.level.visible)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertTrue(stateHolder.state.value.level.visible)
        }
    }

    @Test
    fun hiddenSensorReadings_doNotPublishAnglesToTheScreen() {
        runTest {
            startTracking()
            motion.emit(motionOf())
            val published = mutableListOf<PreviewOverlaysState>()
            val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                stateHolder.state.collect { published += it }
            }
            repeat(50) { index ->
                motion.emit(motionOf(tilt = index / 100f))
            }

            assertEquals(1, published.size)
            collection.cancel()
        }
    }

    @Test
    fun downwardPose_revealsCrosshairsWithTheCurrentAngles() {
        runTest {
            val mapper = PreviewOverlaysUiStateMapperImpl(
                zoomPresetsMapper = ZoomPresetsMapperImpl(),
            )
            val levelUiState = stateHolder.derive { mapper.mapLevel(it) }
            startTracking()
            motion.emit(motionOf(tilt = 35f, horizon = 77f))
            advanceTimeBy(999.milliseconds)
            assertFalse(levelUiState.value.visible)

            advanceTimeBy(1.milliseconds)
            runCurrent()

            assertTrue(levelUiState.value.visible)
            assertEquals(LevelIndicatorMode.Crosshairs, levelUiState.value.mode)
            assertEquals(-77f, levelUiState.value.pitchDegrees)
        }
    }

    @Test
    fun countdownStartingBeforeTheDelayEnds_blocksTheRevealAndAllowsRetryAfterwards() {
        runTest {
            startTracking()
            motion.emit(motionOf(horizon = 77f))
            advanceTimeBy(900.milliseconds)
            stateHolder.update { state ->
                state.copy(previewContent = previewContent.copy(selfTimerRemaining = 3.seconds))
            }
            advanceTimeBy(100.milliseconds)
            runCurrent()

            assertFalse(stateHolder.state.value.level.visible)

            stateHolder.update { state -> state.copy(previewContent = previewContent) }
            motion.emit(motionOf(horizon = 77f))
            advanceTimeBy(999.milliseconds)
            assertFalse(stateHolder.state.value.level.visible)
            advanceTimeBy(1.milliseconds)
            runCurrent()

            assertTrue(stateHolder.state.value.level.visible)
        }
    }

    private fun changeLevelSetting(enabled: Boolean) {
        stateHolder.update { state ->
            state.copy(settings = state.settings.copy(gyroscopeSuggestions = enabled))
        }
    }

    private fun TestScope.startTracking(): Job {
        every { deviceOrientationRepository.motion } returns motion

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        delegate.bind(stateHolder)
        return backgroundScope.launch(dispatcher) { delegate.trackLevel() }
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

    private companion object {
        private val SHOWN_LEVEL = LevelState(
            visible = true,
            pose = LevelPose.Horizon,
            tiltDegrees = 3f,
        )
    }
}
