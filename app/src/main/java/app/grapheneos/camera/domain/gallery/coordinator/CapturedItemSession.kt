package app.grapheneos.camera.domain.gallery.coordinator

import android.util.Log
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.mapper.VisibleCaptureMapper
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

interface CapturedItemSession {

    val lastCapturedItem: CapturedItem?

    suspend fun prepare()

    /** Follows the stored last captured item until the caller is cancelled. */
    suspend fun trackLastCapturedItem()

    fun recordCapturedItem(item: CapturedItem)
}

internal class CapturedItemSessionImpl @Inject constructor(
    private val capturedItemRepository: CapturedItemRepository,
    private val visibleCaptureMapper: VisibleCaptureMapper,
    private val entryPoint: CameraEntryPoint,
) : CapturedItemSession {

    override var lastCapturedItem: CapturedItem? = runBlocking { restoredCapture() }
        private set

    private suspend fun restoredCapture(): CapturedItem? {
        if (entryPoint.isSecureSession) {
            return null
        }

        val stored = try {
            capturedItemRepository.lastCapturedItem.first()
        } catch (e: IOException) {
            Log.e(TAG, "unable to read the last captured item", e)
            null
        }

        return visibleCaptureMapper.map(stored)
    }

    override suspend fun prepare() {
        if (entryPoint.isSecureSession) {
            return
        }

        // A failed migration leaves the legacy keys for the next launch to retry.
        try {
            capturedItemRepository.migrateStoredCaptures()?.let {
                capturedItemRepository.saveLastCapturedItem(it)
            }
            capturedItemRepository.releaseUntrackedSafTrees()
        } catch (e: IOException) {
            Log.e(TAG, "unable to migrate the stored captures", e)
        }
    }

    override suspend fun trackLastCapturedItem() {
        if (entryPoint.isSecureSession) {
            return
        }

        capturedItemRepository.lastCapturedItem.collect { item ->
            lastCapturedItem = visibleCaptureMapper.map(item)
        }
    }

    override fun recordCapturedItem(item: CapturedItem) {
        lastCapturedItem = visibleCaptureMapper.map(item)
    }

    private companion object {
        private const val TAG = "CapturedItemSession"
    }
}
