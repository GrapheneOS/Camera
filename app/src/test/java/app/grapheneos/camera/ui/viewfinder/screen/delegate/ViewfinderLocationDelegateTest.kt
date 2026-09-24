package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.location.model.LocationAvailability
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(mode = CameraMode.CAMERA, requiresVideoModeOnly = false),
        render = { ViewfinderUiState() },
    )

    @Test
    fun location_isTrackedWhileRequiredResumedAndPermitted() {
        runTest {
            val delegate = createDelegate()

            stateHolder.update { it.copy(requireLocation = true) }
            assertEquals(0, collectors)

            delegate.setScreenResumed(true)
            assertEquals(1, collectors)

            delegate.setScreenResumed(false)
            assertEquals(0, collectors)
        }
    }

    @Test
    fun location_isNotTrackedWithoutThePermission() {
        runTest {
            val delegate = createDelegate()
            stateHolder.update {
                it.copy(
                    requireLocation = true,
                    missingPermissions = setOf(AppPermission.LOCATION),
                )
            }

            delegate.setScreenResumed(true)

            assertEquals(0, collectors)
        }
    }

    @Test
    fun disabledProviders_areReportedEachTimeTheyGoOff() {
        runTest {
            val delegate = createDelegate()
            val reports = mutableListOf<Unit>()
            backgroundScope.launch { delegate.providersDisabledEvents.collect { reports += it } }
            stateHolder.update { it.copy(requireLocation = true) }
            delegate.setScreenResumed(true)

            availability.emit(LocationAvailability.PROVIDERS_DISABLED)
            availability.emit(LocationAvailability.AVAILABLE)
            availability.emit(LocationAvailability.PROVIDERS_DISABLED)

            assertEquals(2, reports.size)
        }
    }

    @Test
    fun location_isForgottenWhenGeoTaggingTurnsOffButNotOnPause() {
        runTest {
            val delegate = createDelegate()
            stateHolder.update { it.copy(requireLocation = true) }
            delegate.setScreenResumed(true)
            clearMocks(locationRepository, answers = false)

            delegate.setScreenResumed(false)
            verify(exactly = 0) { locationRepository.forgetLocation() }

            stateHolder.update { it.copy(requireLocation = false) }
            verify(exactly = 1) { locationRepository.forgetLocation() }
        }
    }

    private fun TestScope.createDelegate(): ViewfinderLocationDelegate {
        every { locationRepository.updates() } returns availability
            .onStart { collectors++ }
            .onCompletion { collectors-- }

        val delegate = ViewfinderLocationDelegateImpl(
            locationRepository = locationRepository,
            mainDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        delegate.bind(
            scope = backgroundScope,
            stateHolder = stateHolder,
        )

        return delegate
    }
}
