package app.grapheneos.camera.ui.gallery.screen.delegate

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.DefaultDispatcher
import app.grapheneos.camera.domain.gallery.model.CapturedItemDeletion
import app.grapheneos.camera.domain.gallery.model.CapturedItemRestoring
import app.grapheneos.camera.domain.gallery.model.CapturedItemTrashing
import app.grapheneos.camera.domain.gallery.usecase.DeleteCapturedItem
import app.grapheneos.camera.domain.gallery.usecase.DeleteTrashedItem
import app.grapheneos.camera.domain.gallery.usecase.RestoreCapturedItem
import app.grapheneos.camera.domain.gallery.usecase.TrashCapturedItem
import app.grapheneos.camera.ui.gallery.screen.GalleryStateHolder
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDialog as Dialog
import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import app.grapheneos.camera.ui.gallery.screen.model.GalleryTrashedItem
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface GalleryDeletionDelegate {

    fun bind(stateHolder: GalleryStateHolder)

    suspend fun delete()

    suspend fun confirm()

    fun dismiss()

    suspend fun undo()

    /** Deletes the trashed item for good; the deletion outlives the screen. */
    fun finish()
}

internal class GalleryDeletionDelegateImpl @Inject constructor(
    private val trashCapturedItem: TrashCapturedItem,
    private val restoreCapturedItem: RestoreCapturedItem,
    private val deleteTrashedItem: DeleteTrashedItem,
    private val deleteCapturedItem: DeleteCapturedItem,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : GalleryDeletionDelegate {

    private val operations = Mutex()

    private lateinit var stateHolder: GalleryStateHolder

    override fun bind(stateHolder: GalleryStateHolder) {
        this.stateHolder = stateHolder
    }

    override suspend fun delete() {
        operations.withLock {
            val item = stateHolder.state.value.currentItem() ?: return
            val trashedItem = GalleryTrashedItem(
                item = item,
                index = stateHolder.state.value.currentIndex,
            )

            when (trashCapturedItem(item)) {
                CapturedItemTrashing.Trashed -> showTrashed(trashedItem)

                CapturedItemTrashing.NotTrashable -> stateHolder.update {
                    it.copy(dialog = Dialog.DeleteConfirmation)
                }

                CapturedItemTrashing.Failed -> stateHolder.send(Effect.DeletionFailed)
            }
        }
    }

    override suspend fun confirm() {
        operations.withLock {
            val item = stateHolder.state.value.currentItem() ?: return

            dismiss()

            when (deleteCapturedItem(item)) {
                CapturedItemDeletion.Deleted -> {
                    remove(item)
                    stateHolder.send(Effect.ItemDeleted)
                }

                CapturedItemDeletion.Failed -> stateHolder.send(Effect.DeletionFailed)
            }
        }
    }

    override fun dismiss() {
        stateHolder.update { it.copy(dialog = null) }
    }

    override suspend fun undo() {
        operations.withLock {
            val trashedItem = stateHolder.state.value.trashedItem ?: return

            stateHolder.update { it.copy(trashedItem = null) }

            when (restoreCapturedItem(trashedItem.item)) {
                CapturedItemRestoring.Restored -> insert(trashedItem)
                CapturedItemRestoring.Failed -> stateHolder.send(Effect.RestoreFailed)
            }
        }
    }

    override fun finish() {
        val trashedItem = stateHolder.state.value.trashedItem ?: return

        stateHolder.update { it.copy(trashedItem = null) }

        applicationScope.launch(defaultDispatcher) {
            deleteTrashedItem(trashedItem.item)
        }
    }

    private fun showTrashed(trashedItem: GalleryTrashedItem) {
        finish()
        remove(trashedItem.item)
        stateHolder.update { it.copy(trashedItem = trashedItem) }
        stateHolder.send(Effect.ItemTrashed)
    }

    private fun remove(item: CapturedItem) {
        val items = stateHolder.state.value.items - item

        stateHolder.update {
            it.copy(
                items = items,
                currentIndex = it.currentIndexIn(items),
                isZoomed = false,
            )
        }
    }

    private fun insert(trashedItem: GalleryTrashedItem) {
        stateHolder.update {
            val index = trashedItem.index.coerceAtMost(it.items.size)

            it.copy(
                items = it.items.take(index) + trashedItem.item + it.items.drop(index),
                currentIndex = index,
                isZoomed = false,
            )
        }
    }
}
