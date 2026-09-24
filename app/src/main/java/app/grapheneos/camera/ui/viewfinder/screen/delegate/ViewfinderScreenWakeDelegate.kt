package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart

interface ViewfinderScreenWakeDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    fun onScreenInteracted()

    suspend fun keepScreenAwake()
}

internal class ViewfinderScreenWakeDelegateImpl @Inject constructor() :
    ViewfinderScreenWakeDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    private val interactions = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun bind(stateHolder: ViewfinderStateHolder) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder
    }

    override fun onScreenInteracted() {
        interactions.tryEmit(Unit)
    }

    override suspend fun keepScreenAwake() {
        try {
            interactions
                .onStart { emit(Unit) }
                .collectLatest {
                    setKeepsScreenAwake(true)
                    delay(AWAKE_DURATION)
                    setKeepsScreenAwake(false)
                }
        } finally {
            setKeepsScreenAwake(false)
        }
    }

    private fun setKeepsScreenAwake(keepsScreenAwake: Boolean) {
        stateHolder.update { state ->
            state.copy(keepsScreenAwake = keepsScreenAwake)
        }
    }

    private companion object {
        private val AWAKE_DURATION = 5.minutes
    }
}
