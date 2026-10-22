package app.grapheneos.camera.ui.gallery.screen.delegate

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.domain.gallery.model.CapturedItemDeletion
import app.grapheneos.camera.domain.gallery.usecase.DeleteCapturedItem
import app.grapheneos.camera.ui.gallery.screen.GalleryStateHolder
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDialog as Dialog
import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import javax.inject.Inject

internal interface GalleryDeletionDelegate {

    fun bind(stateHolder: GalleryStateHolder)

    fun confirm()

    fun dismiss()

    suspend fun deleteCurrentItem()
}

internal class GalleryDeletionDelegateImpl @Inject constructor(
    private val deleteCapturedItem: DeleteCapturedItem,
) : GalleryDeletionDelegate {

    private lateinit var stateHolder: GalleryStateHolder

    override fun bind(stateHolder: GalleryStateHolder) {
        this.stateHolder = stateHolder
    }

    override fun confirm() {
        if (stateHolder.state.value.currentItem() != null) {
            stateHolder.update {
                it.copy(dialog = Dialog.DeleteConfirmation)
            }
        }
    }

    override fun dismiss() {
        stateHolder.update { it.copy(dialog = null) }
    }

    override suspend fun deleteCurrentItem() {
        val item = stateHolder.state.value.currentItem() ?: return

        dismiss()

        when (deleteCapturedItem(item)) {
            CapturedItemDeletion.Deleted -> remove(item)
            CapturedItemDeletion.Failed -> stateHolder.send(Effect.DeletionFailed)
        }
    }

    private fun remove(item: CapturedItem) {
        val items = stateHolder.state.value.items - item

        when {
            items.isEmpty() -> stateHolder.send(Effect.LastItemDeleted)
            else -> {
                stateHolder.update {
                    it.copy(
                        items = items,
                        currentIndex = it.currentIndex.coerceAtMost(items.lastIndex),
                        isZoomed = false,
                    )
                }
                stateHolder.send(Effect.ItemDeleted)
            }
        }
    }
}
