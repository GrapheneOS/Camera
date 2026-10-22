package app.grapheneos.camera.ui.gallery.screen.delegate

import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemDetailsRepository
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.ui.gallery.screen.GalleryStateHolder
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDetails
import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import app.grapheneos.camera.ui.gallery.screen.saveShowsDetails
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal interface GalleryDetailsDelegate {

    fun bind(
        scope: CoroutineScope,
        stateHolder: GalleryStateHolder,
    )

    fun onAction(action: Action.Details)

    fun hide()

    fun loadAround(page: Int)
}

internal class GalleryDetailsDelegateImpl @Inject constructor(
    private val arguments: SavedStateHandle,
    private val capturedItemDetailsRepository: CapturedItemDetailsRepository,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : GalleryDetailsDelegate {

    private lateinit var scope: CoroutineScope
    private lateinit var stateHolder: GalleryStateHolder

    override fun bind(
        scope: CoroutineScope,
        stateHolder: GalleryStateHolder,
    ) {
        this.scope = scope
        this.stateHolder = stateHolder
    }

    override fun onAction(action: Action.Details) {
        when (action) {
            is Action.DetailsShownChanged -> show(action.isShown)
            is Action.OpenMapClicked -> openMap()
            is Action.DetailLongPressed -> stateHolder.send(Effect.CopyText(action.text))
        }
    }

    override fun hide() {
        setShown(false)
    }

    override fun loadAround(page: Int) {
        val state = stateHolder.state.value
        if (!state.showsDetails) return

        val items = state.itemsAround(
            page = page,
            radius = DETAILS_RADIUS,
        )

        stateHolder.update {
            it.copy(details = it.details.filterKeys(items::contains))
        }

        items
            .filterNot(stateHolder.state.value.details::containsKey)
            .forEach(::load)
    }

    private fun show(isShown: Boolean) {
        val state = stateHolder.state.value
        val canShow = state.currentItem() != null

        when {
            !isShown -> setShown(false)
            canShow && !state.showsDetails -> {
                setShown(true)
                loadAround(state.currentIndex)
            }
        }
    }

    private fun setShown(isShown: Boolean) {
        if (stateHolder.state.value.showsDetails == isShown) return

        stateHolder.update {
            it.copy(showsDetails = isShown)
        }

        saveShowsDetails(arguments, isShown)
    }

    private fun load(item: CapturedItem) {
        stateHolder.update {
            it.copy(details = it.details + (item to GalleryDetails.Loading))
        }

        scope.launch(mainDispatcher) {
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
    }

    private fun openMap() {
        val state = stateHolder.state.value
        val details = state.currentItem()?.let(state.details::get) as? GalleryDetails.Loaded

        details?.details?.location?.let { location ->
            stateHolder.send(Effect.OpenMap(location))
        }
    }

    private companion object {
        const val DETAILS_RADIUS = 1
    }
}
