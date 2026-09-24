package app.grapheneos.camera.data.orientation

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepositoryImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeviceOrientationRepositoryTest {

    private val sensorManager = mockk<SensorManager>(relaxed = true)

    private val accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)

    private val listener = slot<SensorEventListener>()

    private val repository = DeviceOrientationRepositoryImpl(sensorManager = sensorManager)

    @Before
    fun setUp() {
        every { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns accelerometer
        every { sensorManager.registerListener(capture(listener), any<Sensor>(), any<Int>()) }
            .returns(true)
    }

    @Test
    fun orientation_namesEachOfTheFourWaysUp() {
        runTest(UnconfinedTestDispatcher()) {
            val reported = mutableListOf<DeviceOrientation>()
            backgroundScope.launch { repository.orientation().collect { reported += it } }

            hold(x = 0f, y = GRAVITY)
            hold(x = -GRAVITY, y = 0f)
            hold(x = 0f, y = -GRAVITY)
            hold(x = GRAVITY, y = 0f)

            assertEquals(DeviceOrientation.entries, reported)
        }
    }

    @Test
    fun orientation_keepsTheLastOneWhileThePhoneLiesFlat() {
        runTest(UnconfinedTestDispatcher()) {
            val reported = mutableListOf<DeviceOrientation>()
            backgroundScope.launch { repository.orientation().collect { reported += it } }

            hold(x = 0f, y = GRAVITY)
            hold(x = 0f, y = 0f)

            assertEquals(listOf(DeviceOrientation.DEGREES_0), reported)
        }
    }

    @Test
    fun orientation_stopsListeningWhenNoLongerCollected() {
        runTest(UnconfinedTestDispatcher()) {
            val collection = backgroundScope.launch { repository.orientation().collect {} }

            collection.cancel()

            verify { sensorManager.unregisterListener(listener.captured) }
        }
    }

    private fun hold(x: Float, y: Float) {
        repeat(SETTLING_EVENTS) {
            val event = SensorEventBuilder.newBuilder()
                .setSensor(accelerometer)
                .setValues(floatArrayOf(x, y, 0f))
                .build()

            listener.captured.onSensorChanged(event)
        }
    }

    private companion object {
        const val GRAVITY = 9.8f
        const val SETTLING_EVENTS = 10
    }
}
