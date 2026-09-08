package app.grapheneos.camera.domain.gallery

import android.util.Log
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.mapper.VisibleCaptureMapper
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

interface CapturedItemSession {

    val lastCapturedItem: CapturedItem?

    val storageLocation: String

    suspend fun prepare()

    fun recordCapturedItem(item: CapturedItem)

    fun close()
}

internal class CapturedItemSessionImpl @Inject constructor(
    private val capturedItemRepository: CapturedItemRepository,
    private val visibleCaptureMapper: VisibleCaptureMapper,
    private val entryPoint: CameraEntryPoint,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : CapturedItemSession {

    override var lastCapturedItem: CapturedItem? = runBlocking { restoredCapture() }
        private set

    override var storageLocation: String = runBlocking {
        capturedItemRepository.storageLocation.first()
    }
        private set

    private val scope = CoroutineScope(mainDispatcher)

    init {
        scope.launch {
            capturedItemRepository.storageLocation.collect {
                storageLocation = it
            }
        }

        if (!entryPoint.isSecureSession) {
            scope.launch {
                capturedItemRepository.lastCapturedItem.collect {
                    lastCapturedItem = visibleCaptureMapper.map(it)
                }
            }
        }
    }

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
            capturedItemRepository.migrateStoredCaptures()?.let { recordCapturedItem(it) }
            capturedItemRepository.releaseUntrackedSafTrees()
        } catch (e: IOException) {
            Log.e(TAG, "unable to migrate the stored captures", e)
        }
    }

    override fun recordCapturedItem(item: CapturedItem) {
        lastCapturedItem = visibleCaptureMapper.map(item)

        scope.launch {
            try {
                capturedItemRepository.saveLastCapturedItem(item)
            } catch (e: IOException) {
                Log.e(TAG, "unable to store the last captured item", e)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    private companion object {
        private const val TAG = "CapturedItemSession"
    }
}
