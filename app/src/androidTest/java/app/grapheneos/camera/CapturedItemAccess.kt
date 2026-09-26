package app.grapheneos.camera

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.di.media.CapturedItemRepositoryEntryPoint
import app.grapheneos.camera.ui.activities.MainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** The last captured item as stored, which the viewfinder writes shortly after a save. */
internal fun storedLastCapturedItem(activity: MainActivity): CapturedItem? {
    val repository = EntryPointAccessors
        .fromActivity(activity, CapturedItemRepositoryEntryPoint::class.java)
        .capturedItemRepository()

    return runBlocking { repository.lastCapturedItem.first() }
}
