package app.grapheneos.camera.data.camera.session

import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.ExposureState
import app.grapheneos.camera.data.camera.mapper.CameraXStateMapper
import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.testutil.MainDispatcherRule
import com.google.common.util.concurrent.SettableFuture
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExposureControlTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val firstResult = SettableFuture.create<Int>()
    private val secondResult = SettableFuture.create<Int>()
    private val exposureState = mockk<ExposureState>()
    private val cameraControl = mockk<CameraControl> {
        every { setExposureCompensationIndex(3) } returns firstResult
        every { setExposureCompensationIndex(6) } returns secondResult
    }
    private val camera = mockk<Camera> {
        every { cameraControl } returns this@ExposureControlTest.cameraControl
        every { cameraInfo.exposureState } returns exposureState
    }
    private val cameraXStateMapper = mockk<CameraXStateMapper> {
        every { map(exposureState) } returns EXPOSURE
    }
    private val exposureControl = ExposureControlImpl(
        cameraXStateMapper = cameraXStateMapper,
        mainDispatcher = mainDispatcherRule.testDispatcher,
    )

    @Test
    fun boundCamera_reportsItsExposureAndTakesTheCompensation() {
        exposureControl.attach(camera)

        exposureControl.setCompensationIndex(3)

        assertEquals(EXPOSURE.copy(compensationIndex = 3), exposureControl.exposure.value)
        verify(exactly = 1) { cameraControl.setExposureCompensationIndex(3) }
    }

    @Test
    fun withoutACamera_reportsNoExposureAndIgnoresTheCompensation() {
        exposureControl.setCompensationIndex(3)

        assertNull(exposureControl.exposure.value)
        verify(exactly = 0) { cameraControl.setExposureCompensationIndex(any()) }
    }

    @Test
    fun detach_clearsTheExposureAndStopsSendingCommandsToTheOldCamera() {
        exposureControl.attach(camera)
        exposureControl.setCompensationIndex(3)

        exposureControl.detach()
        exposureControl.setCompensationIndex(6)

        assertNull(exposureControl.exposure.value)
        verify(exactly = 1) { cameraControl.setExposureCompensationIndex(3) }
        verify(exactly = 0) { cameraControl.setExposureCompensationIndex(6) }
    }

    @Test
    fun outOfRangeIndex_doesNotChangeTheReportedExposure() {
        exposureControl.attach(camera)

        exposureControl.setCompensationIndex(100)

        assertEquals(EXPOSURE, exposureControl.exposure.value)
        verify(exactly = 0) { cameraControl.setExposureCompensationIndex(any()) }
    }

    @Test
    fun failedRequest_restoresTheLastConfirmedValue() {
        exposureControl.attach(camera)
        exposureControl.setCompensationIndex(3)
        firstResult.set(3)

        exposureControl.setCompensationIndex(6)
        secondResult.setException(CameraControl.OperationCanceledException("Camera closed"))

        assertEquals(EXPOSURE.copy(compensationIndex = 3), exposureControl.exposure.value)
    }

    @Test
    fun supersededRequest_doesNotRollBackTheNewerRequest() {
        exposureControl.attach(camera)
        exposureControl.setCompensationIndex(3)
        exposureControl.setCompensationIndex(6)

        firstResult.setException(CameraControl.OperationCanceledException("Superseded"))
        assertEquals(EXPOSURE.copy(compensationIndex = 6), exposureControl.exposure.value)
    }

    @Test
    fun requestFailedWhileItsReplacementIsSubmitted_doesNotRollBackTheNewerRequest() {
        every { cameraControl.setExposureCompensationIndex(6) } answers {
            firstResult.setException(CameraControl.OperationCanceledException("Superseded"))
            secondResult
        }
        exposureControl.attach(camera)
        exposureControl.setCompensationIndex(3)

        exposureControl.setCompensationIndex(6)

        assertEquals(EXPOSURE.copy(compensationIndex = 6), exposureControl.exposure.value)
    }

    @Test
    fun cancelledRequest_restoresTheConfirmedValue() {
        exposureControl.attach(camera)
        exposureControl.setCompensationIndex(3)

        firstResult.cancel(false)

        assertEquals(EXPOSURE, exposureControl.exposure.value)
    }

    @Test
    fun previousAttachment_resultDoesNotChangeTheNewBinding() {
        exposureControl.attach(camera)
        exposureControl.setCompensationIndex(3)

        exposureControl.attach(camera)
        assertEquals(EXPOSURE, exposureControl.exposure.value)

        firstResult.set(3)

        assertEquals(EXPOSURE, exposureControl.exposure.value)
    }

    @Test
    fun failedRequest_withAQueuedEarlierConfirmation_restoresItsConfirmedIndex() {
        runTest {
            val queuedControl = ExposureControlImpl(
                cameraXStateMapper = cameraXStateMapper,
                mainDispatcher = StandardTestDispatcher(testScheduler),
            )
            queuedControl.attach(camera)
            queuedControl.setCompensationIndex(3)
            firstResult.set(3)

            queuedControl.setCompensationIndex(6)
            secondResult.setException(CameraControl.OperationCanceledException("Closed"))
            runCurrent()

            assertEquals(EXPOSURE.copy(compensationIndex = 3), queuedControl.exposure.value)
        }
    }

    private companion object {
        val EXPOSURE = CameraExposure(
            compensationIndex = 1,
            compensationRange = -12..12,
            compensationStep = 1f / 6f,
            isCompensationSupported = true,
        )
    }
}
