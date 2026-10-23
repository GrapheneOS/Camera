package app.grapheneos.camera.ui.gallery.screen.delegate

import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemDetailsRepository
import app.grapheneos.camera.ui.gallery.screen.GalleryStateHolder
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDetails
import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import app.grapheneos.camera.ui.gallery.screen.saveShowsDetails
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

internal interface GalleryDetailsDelegate {

    fun bind(stateHolder: GalleryStateHolder)

    suspend fun trackDetails()

    fun show()

    fun hide()

    fun openMap()
}

internal class GalleryDetailsDelegateImpl @Inject constructor(
    private val arguments: SavedStateHandle,
    private val capturedItemDetailsRepository: CapturedItemDetailsRepository,
) : GalleryDetailsDelegate {

    private lateinit var stateHolder: GalleryStateHolder

    override fun bind(stateHolder: GalleryStateHolder) {
        this.stateHolder = stateHolder
    }

    override suspend fun trackDetails() {
        coroutineScope {
            stateHolder.state
                .filter { it.showsDetails }
                .distinctUntilChanged(GalleryState::showsSamePageAs)
                .collect { state ->
                    keepDetailsAround(state).forEach { item ->
                        this@coroutineScope.launch { load(item) }
                    }
                }
        }
    }

    override fun show() {
        if (stateHolder.state.value.currentItem() != null) {
            setShown(true)
        }
    }

    override fun hide() {
        setShown(false)
    }

    override fun openMap() {
        val state = stateHolder.state.value
        val details = state.currentItem()?.let(state.details::get) as? GalleryDetails.Loaded

        details?.details?.location?.let { location ->
            stateHolder.send(Effect.OpenMap(location))
        }
    }

    private fun setShown(isShown: Boolean) {
        if (stateHolder.state.value.showsDetails == isShown) return

        stateHolder.update {
            it.copy(showsDetails = isShown)
        }

        saveShowsDetails(arguments, isShown)
    }

    private fun keepDetailsAround(state: GalleryState): List<CapturedItem> {
        val items = state.itemsAround(DETAILS_RADIUS)
        val missingItems = items.filterNot(state.details::containsKey)

        stateHolder.update { current ->
            current.copy(
                details = items.associateWith { item ->
                    current.details[item] ?: GalleryDetails.Loading
                },
            )
        }

        return missingItems
    }

    private suspend fun load(item: CapturedItem) {
        val details = when (val loaded = capturedItemDetailsRepository.details(item)) {
            null -> GalleryDetails.Unavailable
            else -> GalleryDetails.Loaded(loaded)
        }

        if (item in stateHolder.state.value.details) {
            stateHolder.update {
                it.copy(details = it.details + (item to details))
            }
        }
    }

    private companion object {
        const val DETAILS_RADIUS = 1
    }
}
