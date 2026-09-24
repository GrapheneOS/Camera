package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.annotation.SuppressLint
import app.grapheneos.camera.data.location.model.LocationAvailability
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface ViewfinderLocationDelegate {

    val providersDisabledEvents: Flow<Unit>

    fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    )

    fun setScreenResumed(resumed: Boolean)
}

internal class ViewfinderLocationDelegateImpl @Inject constructor(
    private val locationRepository: LocationRepository,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderLocationDelegate {

    private var isBound = false

    private val isScreenResumed = MutableStateFlow(false)

    private val _providersDisabledEvents = Channel<Unit>(capacity = Channel.BUFFERED)
    override val providersDisabledEvents: Flow<Unit> = _providersDisabledEvents.receiveAsFlow()

    override fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    ) {
        if (isBound) return
        isBound = true

        scope.launch(mainDispatcher) {
            combine(
                stateHolder.state,
                isScreenResumed,
            ) { state, screenResumed ->
                tracksLocation(
                    state = state,
                    isScreenResumed = screenResumed,
                )
            }
                .distinctUntilChanged()
                .collectLatest { tracks ->
                    if (tracks) {
                        trackLocation()
                    }
                }
        }

        scope.launch(mainDispatcher) {
            stateHolder.state
                .map { state -> state.requireLocation }
                .distinctUntilChanged()
                .filterNot { requireLocation -> requireLocation }
                .collect { locationRepository.forgetLocation() }
        }
    }

    override fun setScreenResumed(resumed: Boolean) {
        isScreenResumed.value = resumed
    }

    @SuppressLint("MissingPermission")
    private suspend fun trackLocation() {
        locationRepository.updates()
            .filter { availability -> availability == LocationAvailability.PROVIDERS_DISABLED }
            .collect { _providersDisabledEvents.send(Unit) }
    }

    private fun tracksLocation(
        state: ViewfinderState,
        isScreenResumed: Boolean,
    ): Boolean {
        return state.requireLocation &&
            isScreenResumed &&
            AppPermission.LOCATION !in state.missingPermissions
    }
}
