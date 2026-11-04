package app.grapheneos.camera.data.orientation.repository

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.orientation.model.DeviceMotion
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeviceOrientationRepositoryTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    private val sensorManager = mockk<SensorManager>(relaxed = true)

    private val accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)

    private val listener = slot<SensorEventListener>()

    @Before
    fun setUp() {
        every { sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) } returns null
        every { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns accelerometer
        every { sensorManager.registerListener(capture(listener), any<Sensor>(), any<Int>()) }
            .returns(true)
    }

    @Test
    fun motion_usesFusedGravityWhenAvailableWithoutAddingAnotherLowPassFilter() {
        runTest {
            val gravitySensor = ShadowSensor.newInstance(Sensor.TYPE_GRAVITY)
            every { sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) } returns gravitySensor
            val reported = collectMotion()
            val event = SensorEventBuilder.newBuilder()
                .setSensor(gravitySensor)
                .setValues(floatArrayOf(0f, GRAVITY, 0f))
                .build()
            listener.captured.onSensorChanged(event)
            runCurrent()
            val tilted = SensorEventBuilder.newBuilder()
                .setSensor(gravitySensor)
                .setValues(
                    floatArrayOf(
                        0f,
                        GRAVITY * cos(PITCH_RADIANS),
                        GRAVITY * sin(PITCH_RADIANS),
                    ),
                )
                .build()

            listener.captured.onSensorChanged(tilted)
            runCurrent()

            verify {
                sensorManager.registerListener(
                    any(),
                    gravitySensor,
                    SensorManager.SENSOR_DELAY_GAME,
                )
            }
            verify(exactly = 0) { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
            assertEquals(70f, reported.last().horizonDegrees, ANGLE_TOLERANCE_DEGREES)
        }
    }

    @Test
    fun motion_fallsBackToTheAccelerometerWhenGravityIsUnavailable() {
        runTest {
            collectMotion()

            verify {
                sensorManager.registerListener(
                    any(),
                    accelerometer,
                    SensorManager.SENSOR_DELAY_GAME,
                )
            }
        }
    }

    @Test
    fun motion_namesEachOfTheFourWaysUp() {
        runTest {
            val reported = collectMotion()

            hold(x = 0f, y = GRAVITY)
            hold(x = -GRAVITY, y = 0f)
            hold(x = 0f, y = -GRAVITY)
            hold(x = GRAVITY, y = 0f)

            assertEquals(
                DeviceOrientation.entries,
                reported.map { it.orientation }.distinct(),
            )
        }
    }

    @Test
    fun motion_keepsTheLastOrientationWhileThePhoneLiesFlat() {
        runTest {
            val reported = collectMotion()

            hold(x = 0f, y = GRAVITY)
            hold(x = 0f, y = 0f, z = GRAVITY)

            assertTrue(reported.all { it.orientation == DeviceOrientation.DEGREES_0 })
        }
    }

    @Test
    fun motion_measuresTheTiltAgainstGravity() {
        runTest {
            val reported = collectMotion()

            hold(x = GRAVITY * sin(TILT_RADIANS), y = GRAVITY * cos(TILT_RADIANS))

            assertEquals(10f, reported.last().tiltDegrees, ANGLE_TOLERANCE_DEGREES)
            assertEquals(0f, reported.last().horizonDegrees)
        }
    }

    @Test
    fun motion_upsideDown_measuresTheTiltTheOtherWay() {
        runTest {
            val reported = collectMotion()

            hold(x = GRAVITY * sin(TILT_RADIANS), y = -GRAVITY * cos(TILT_RADIANS))

            assertEquals(-10f, reported.last().tiltDegrees, ANGLE_TOLERANCE_DEGREES)
        }
    }

    @Test
    fun motion_preservesFractionalAnglesOnBothSidesOfZero() {
        runTest {
            val reported = collectMotion()
            val radians = Math.toRadians(0.25).toFloat()

            hold(x = GRAVITY * sin(radians), y = GRAVITY * cos(radians))
            assertEquals(0.25f, reported.last().tiltDegrees, ANGLE_TOLERANCE_DEGREES)

            hold(x = -GRAVITY * sin(radians), y = GRAVITY * cos(radians))
            assertEquals(-0.25f, reported.last().tiltDegrees, ANGLE_TOLERANCE_DEGREES)
        }
    }

    @Test
    fun motion_reportsUpAndDownWhenStartedFlat() {
        runTest {
            val reported = collectMotion()

            hold(x = 0f, y = 0f, z = GRAVITY)
            assertEquals(90f, reported.last().horizonDegrees)

            hold(x = 0f, y = 0f, z = -GRAVITY)
            assertEquals(-90f, reported.last().horizonDegrees)
        }
    }

    @Test
    fun motion_pointingDown_preservesTheDirectionOfTheTilt() {
        runTest {
            val reported = collectMotion()
            hold(x = 0f, y = GRAVITY)

            hold(x = 1f, y = 0f, z = GRAVITY)
            assertEquals(90f, reported.last().tiltDegrees, ANGLE_TOLERANCE_DEGREES)

            hold(x = -1f, y = 0f, z = GRAVITY)
            assertEquals(-90f, reported.last().tiltDegrees, ANGLE_TOLERANCE_DEGREES)
        }
    }

    @Test
    fun motion_measuresRollAndUpDownPitchInEveryDeviceOrientation() {
        runTest {
            val reported = collectMotion()
            val roll = Math.toRadians(35.0).toFloat()
            val horizontal = GRAVITY * cos(PITCH_RADIANS) * sin(roll)
            val vertical = GRAVITY * cos(PITCH_RADIANS) * cos(roll)

            DeviceOrientation.entries.forEach { orientation ->
                val (uprightX, uprightY) = when (orientation) {
                    DeviceOrientation.DEGREES_0 -> 0f to GRAVITY
                    DeviceOrientation.DEGREES_90 -> -GRAVITY to 0f
                    DeviceOrientation.DEGREES_180 -> 0f to -GRAVITY
                    DeviceOrientation.DEGREES_270 -> GRAVITY to 0f
                }
                hold(x = uprightX, y = uprightY)
                val (gravityX, gravityY) = when (orientation) {
                    DeviceOrientation.DEGREES_0 -> horizontal to vertical
                    DeviceOrientation.DEGREES_90 -> -vertical to horizontal
                    DeviceOrientation.DEGREES_180 -> -horizontal to -vertical
                    DeviceOrientation.DEGREES_270 -> vertical to -horizontal
                }

                listOf(-1f, 1f).forEach { direction ->
                    hold(x = gravityX, y = gravityY, z = direction * GRAVITY * sin(PITCH_RADIANS))

                    assertEquals(orientation, reported.last().orientation)
                    assertEquals(35f, reported.last().tiltDegrees, ANGLE_TOLERANCE_DEGREES)
                    assertEquals(
                        direction * 70f,
                        reported.last().horizonDegrees,
                        ANGLE_TOLERANCE_DEGREES,
                    )
                }
            }
        }
    }

    @Test
    fun motion_sharesOneListenerAndStopsAfterTheLastSubscriber() {
        runTest {
            val repository = repository()
            val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.motion.collect {}
            }
            val second = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.motion.collect {}
            }
            runCurrent()

            verify(exactly = 1) {
                sensorManager.registerListener(
                    any(),
                    any<Sensor>(),
                    SensorManager.SENSOR_DELAY_GAME
                )
            }

            first.cancel()
            runCurrent()
            verify(exactly = 0) { sensorManager.unregisterListener(any<SensorEventListener>()) }

            second.cancel()
            runCurrent()
            verify(exactly = 1) { sensorManager.unregisterListener(listener.captured) }
        }
    }

    @Test
    fun motion_afterRestart_doesNotReplayThePreviousPose() {
        runTest {
            val repository = repository()
            val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.motion.collect {}
            }
            runCurrent()
            hold(x = 0f, y = GRAVITY)
            first.cancel()
            runCurrent()
            val reported = mutableListOf<DeviceMotion>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.motion.collect { reported += it }
            }
            runCurrent()

            assertTrue(reported.isEmpty())

            hold(x = 0f, y = 0f, z = GRAVITY)
            assertEquals(90f, reported.last().horizonDegrees)
        }
    }

    @Test
    fun autoRotateEnabled_readsTheSystemSetting() {
        runTest {
            Settings.System.putInt(
                application.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                1,
            )

            assertTrue(repository().autoRotateEnabled().first())
        }
    }

    private fun TestScope.collectMotion(): List<DeviceMotion> {
        val reported = mutableListOf<DeviceMotion>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository().motion.collect { reported += it }
        }

        runCurrent()
        return reported
    }

    private fun TestScope.hold(
        x: Float,
        y: Float,
        z: Float = 0f,
    ) {
        repeat(SETTLING_EVENTS) {
            val event = SensorEventBuilder.newBuilder()
                .setSensor(accelerometer)
                .setValues(floatArrayOf(x, y, z))
                .build()

            listener.captured.onSensorChanged(event)
            runCurrent()
        }
    }

    private fun TestScope.repository(): DeviceOrientationRepositoryImpl {
        return DeviceOrientationRepositoryImpl(
            sensorManager = sensorManager,
            contentResolver = application.contentResolver,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            applicationScope = backgroundScope,
        )
    }

    private companion object {
        const val GRAVITY = 9.8f
        const val SETTLING_EVENTS = 40
        const val ANGLE_TOLERANCE_DEGREES = 0.001f

        val TILT_RADIANS = Math.toRadians(10.0).toFloat()
        val PITCH_RADIANS = Math.toRadians(70.0).toFloat()
    }
}
