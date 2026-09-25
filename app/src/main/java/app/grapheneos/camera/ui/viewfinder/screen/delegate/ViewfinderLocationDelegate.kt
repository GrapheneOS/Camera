package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.annotation.SuppressLint
import app.grapheneos.camera.data.location.model.LocationAvailability
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface ViewfinderLocationDelegate {

    fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    )

    suspend fun trackLocation()
}

internal class ViewfinderLocationDelegateImpl @Inject constructor(
    private val locationRepository: LocationRepository,
    private val entryPoint: CameraEntryPoint,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderLocationDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    override fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    ) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder

        scope.launch(mainDispatcher) {
            stateHolder.state
                .map { state -> state.requireLocation }
                .distinctUntilChanged()
                .filterNot { requireLocation -> requireLocation }
                .collect { locationRepository.forgetLocation() }
        }
    }

    override suspend fun trackLocation() {
        stateHolder.state
            .map { state -> tracksLocation(state) }
            .distinctUntilChanged()
            .collectLatest { tracks ->
                if (tracks) {
                    reportDisabledProviders()
                }
            }
    }

    private fun tracksLocation(state: ViewfinderState): Boolean {
        return state.requireLocation && AppPermission.LOCATION !in state.missingPermissions
    }

    @SuppressLint("MissingPermission")
    private suspend fun reportDisabledProviders() {
        locationRepository.updates()
            .filter { availability -> availability == LocationAvailability.PROVIDERS_DISABLED }
            .collect {
                stateHolder.postEffect(
                    Effect.Location.ShowDisabled(offersSettings = !entryPoint.isSecureSession),
                )
            }
    }
}
