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

    /** Returns whether the item was removed and the gallery still has items to show. */
    suspend fun deleteCurrentItem(): Boolean
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

    override suspend fun deleteCurrentItem(): Boolean {
        val item = stateHolder.state.value.currentItem() ?: return false

        dismiss()

        return when (deleteCapturedItem(item)) {
            CapturedItemDeletion.Deleted -> remove(item)
            CapturedItemDeletion.Failed -> {
                stateHolder.send(Effect.DeletionFailed)
                false
            }
        }
    }

    private fun remove(item: CapturedItem): Boolean {
        val items = stateHolder.state.value.items - item

        when {
            items.isEmpty() -> stateHolder.send(Effect.LastItemDeleted)
            else -> {
                stateHolder.update {
                    it.copy(
                        items = items,
                        thumbnails = it.thumbnails - item,
                        previews = it.previews - item,
                        details = it.details - item,
                        currentIndex = it.currentIndex.coerceAtMost(items.lastIndex),
                        isZoomed = false,
                    )
                }
                stateHolder.send(Effect.ItemDeleted)
            }
        }

        return items.isNotEmpty()
    }
}
