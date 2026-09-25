package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.location.model.LocationAvailability
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.testutil.collectEffects
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewfinderLocationDelegateTest {

    private val locationRepository = mockk<LocationRepository>(relaxed = true)

    private val availability = MutableSharedFlow<LocationAvailability>()

    private var collectors = 0

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)

    @Test
    fun location_isTrackedWhileRequiredAndPermitted() {
        runTest {
            val delegate = createDelegate()
            startTracking(delegate)
            assertEquals(0, collectors)

            stateHolder.update { it.copy(requireLocation = true) }
            assertEquals(1, collectors)

            stateHolder.update { it.copy(missingPermissions = setOf(AppPermission.LOCATION)) }
            assertEquals(0, collectors)
        }
    }

    @Test
    fun location_stopsBeingTrackedWhenTrackingIsCancelled() {
        runTest {
            val delegate = createDelegate()
            stateHolder.update { it.copy(requireLocation = true) }

            val tracking = startTracking(delegate)
            tracking.cancel()

            assertEquals(0, collectors)
        }
    }

    @Test
    fun disabledProviders_areReportedEachTimeTheyGoOff() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            stateHolder.update { it.copy(requireLocation = true) }
            startTracking(delegate)

            availability.emit(LocationAvailability.PROVIDERS_DISABLED)
            availability.emit(LocationAvailability.AVAILABLE)
            availability.emit(LocationAvailability.PROVIDERS_DISABLED)

            assertEquals(
                List(2) { Effect.Location.ShowDisabled(offersSettings = true) },
                effects,
            )
        }
    }

    @Test
    fun disabledProviders_inALockscreenSession_offerNoSettings() {
        runTest {
            val delegate = createDelegate(isSecureSession = true)
            val effects = collectEffects(stateHolder)
            stateHolder.update { it.copy(requireLocation = true) }
            startTracking(delegate)

            availability.emit(LocationAvailability.PROVIDERS_DISABLED)

            assertEquals(listOf(Effect.Location.ShowDisabled(offersSettings = false)), effects)
        }
    }

    @Test
    fun location_isForgottenWhenGeoTaggingTurnsOffButNotWhenTrackingStops() {
        runTest {
            val delegate = createDelegate()
            stateHolder.update { it.copy(requireLocation = true) }

            val tracking = startTracking(delegate)
            clearMocks(locationRepository, answers = false)

            tracking.cancel()
            verify(exactly = 0) { locationRepository.forgetLocation() }

            stateHolder.update { it.copy(requireLocation = false) }
            verify(exactly = 1) { locationRepository.forgetLocation() }
        }
    }

    private fun TestScope.startTracking(delegate: ViewfinderLocationDelegate): Job {
        return backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            delegate.trackLocation()
        }
    }

    private fun TestScope.createDelegate(
        isSecureSession: Boolean = false,
    ): ViewfinderLocationDelegate {
        every { locationRepository.updates() } returns availability
            .onStart { collectors++ }
            .onCompletion { collectors-- }

        val delegate = ViewfinderLocationDelegateImpl(
            locationRepository = locationRepository,
            entryPoint = cameraEntryPoint(isSecureSession = isSecureSession),
            mainDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        delegate.bind(
            scope = backgroundScope,
            stateHolder = stateHolder,
        )

        return delegate
    }
}
