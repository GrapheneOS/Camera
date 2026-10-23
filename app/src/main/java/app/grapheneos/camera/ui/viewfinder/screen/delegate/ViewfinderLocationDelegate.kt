package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.annotation.SuppressLint
import app.grapheneos.camera.data.location.model.LocationAvailability
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface ViewfinderLocationDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    suspend fun trackGeoTagging()

    suspend fun trackLocation()
}

internal class ViewfinderLocationDelegateImpl @Inject constructor(
    private val locationRepository: LocationRepository,
    private val entryPoint: CameraEntryPoint,
) : ViewfinderLocationDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    override fun bind(stateHolder: ViewfinderStateHolder) {
        this.stateHolder = stateHolder
    }

    override suspend fun trackGeoTagging() {
        coroutineScope {
            launch {
                stateHolder.state
                    .map { state -> state.includesLocation() }
                    .distinctUntilChanged()
                    .filterNot { includesLocation -> includesLocation }
                    .collect { locationRepository.forgetLocation() }
            }

            stateHolder.state.first { state ->
                needsPermission(state)
            }

            stateHolder.postEffect(
                Effect.Permission.Request(
                    permission = AppPermission.LOCATION,
                    explainsFirst = false,
                ),
            )
        }
    }

    override suspend fun trackLocation() {
        stateHolder.state
            .map { state -> state.includesLocation() }
            .distinctUntilChanged()
            .collectLatest { tracks ->
                if (tracks) {
                    reportDisabledProviders()
                }
            }
    }

    private fun needsPermission(state: ViewfinderState): Boolean {
        val missing = state.missingPermissions

        return when {
            entryPoint.isSecureSession || AppPermission.CAMERA in missing -> false
            else -> state.settings.geoTagging && AppPermission.LOCATION in missing
        }
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
