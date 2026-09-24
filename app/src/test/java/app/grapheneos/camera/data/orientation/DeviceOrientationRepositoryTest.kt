package app.grapheneos.camera.data.orientation

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.orientation.model.DeviceMotion
import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepositoryImpl
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

    private val repository = DeviceOrientationRepositoryImpl(
        sensorManager = sensorManager,
        contentResolver = application.contentResolver,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Before
    fun setUp() {
        every { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns accelerometer
        every { sensorManager.registerListener(capture(listener), any<Sensor>(), any<Int>()) }
            .returns(true)
    }

    @Test
    fun motion_namesEachOfTheFourWaysUp() {
        runTest(UnconfinedTestDispatcher()) {
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
        runTest(UnconfinedTestDispatcher()) {
            val reported = collectMotion()

            hold(x = 0f, y = GRAVITY)
            hold(x = 0f, y = 0f, z = GRAVITY)

            assertTrue(reported.all { it.orientation == DeviceOrientation.DEGREES_0 })
        }
    }

    @Test
    fun motion_measuresTheTiltAgainstGravity() {
        runTest(UnconfinedTestDispatcher()) {
            val reported = collectMotion()

            hold(x = GRAVITY * sin(TILT_RADIANS), y = GRAVITY * cos(TILT_RADIANS))

            assertTrue(reported.last().tiltDegrees in 9..10)
            assertEquals(0, reported.last().horizonDegrees)
        }
    }

    @Test
    fun motion_upsideDown_measuresTheTiltTheOtherWay() {
        runTest(UnconfinedTestDispatcher()) {
            val reported = collectMotion()

            hold(x = GRAVITY * sin(TILT_RADIANS), y = -GRAVITY * cos(TILT_RADIANS))

            assertTrue(reported.last().tiltDegrees in -10..-9)
        }
    }

    @Test
    fun motion_stopsListeningWhenNoLongerCollected() {
        runTest(UnconfinedTestDispatcher()) {
            val collection = backgroundScope.launch { repository.motion().collect {} }

            collection.cancel()

            verify { sensorManager.unregisterListener(listener.captured) }
        }
    }

    @Test
    fun autoRotateEnabled_readsTheSystemSetting() {
        runTest(UnconfinedTestDispatcher()) {
            Settings.System.putInt(
                application.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                1,
            )

            assertTrue(repository.autoRotateEnabled().first())
        }
    }

    private fun TestScope.collectMotion(): List<DeviceMotion> {
        val reported = mutableListOf<DeviceMotion>()
        backgroundScope.launch { repository.motion().collect { reported += it } }

        return reported
    }

    private fun hold(
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
        }
    }

    private companion object {
        const val GRAVITY = 9.8f
        const val SETTLING_EVENTS = 20

        val TILT_RADIANS = Math.toRadians(10.0).toFloat()
    }
}
