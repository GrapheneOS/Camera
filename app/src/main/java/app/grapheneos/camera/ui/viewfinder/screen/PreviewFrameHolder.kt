package app.grapheneos.camera.ui.viewfinder.screen

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.camera.view.PreviewView
import androidx.core.graphics.createBitmap

interface PreviewFrameHolder {

    /**
     * Called right before the camera is unbound, which empties the preview. It does not wait for a
     * copy already under way: that copy stands in for the preview once it arrives.
     */
    fun holdCurrentFrame()
}

internal class PreviewFrameHolderImpl(
    private val previewView: PreviewView,
    private val onLateFrame: () -> Unit,
) : PreviewFrameHolder {

    @Volatile
    var lastFrame: Bitmap? = null
        private set

    private val lock = Any()

    private var frameCopyPending = false

    // When the copy waiting in [lastFrame] was taken, or 0 when there is none waiting.
    private var framePrefetchedAt = 0L

    private var isCopyAwaited = false

    private var frameCopyThread: HandlerThread? = null

    private var loggedMissingSurfaceView = false

    override fun holdCurrentFrame() {
        synchronized(lock) {
            when {
                hasFreshPrefetch() -> framePrefetchedAt = 0

                frameCopyPending -> {
                    lastFrame = null
                    isCopyAwaited = true
                }

                else -> lastFrame = previewView.bitmap
            }
        }
    }

    // Starts a copy of the preview for [holdCurrentFrame] to pick up. previewView.bitmap blocks the
    // caller on a GPU readback for about a tenth of a second, and startCamera() reads it at the
    // point where it can least afford to block; the same pixels copied asynchronously cost the main
    // thread nothing, as long as the copy is started early enough.
    fun prefetch() {
        if (synchronized(lock) { frameCopyPending || hasFreshPrefetch() }) return

        val surfaceView = copyableSurfaceView() ?: return

        synchronized(lock) { frameCopyPending = true }
        // Copying the surface rather than the window is what leaves the grid, the level and the
        // focus ring out of it, the way previewView.bitmap does -- and the window holds nothing but
        // a hole where the preview is, since the camera draws into a layer of its own.
        copyPreviewInto(
            createBitmap(previewView.width, previewView.height),
            surfaceView,
            Handler(frameCopyLooper()),
            FRAME_COPY_RETRIES,
        )
    }

    fun clear() {
        synchronized(lock) {
            lastFrame = null
            framePrefetchedAt = 0
            isCopyAwaited = false
        }
    }

    fun release() {
        frameCopyThread?.quitSafely()
    }

    private fun copyableSurfaceView(): SurfaceView? {
        if (previewView.width == 0 || previewView.height == 0) return null

        val surfaceView = previewView.getChildAt(0) as? SurfaceView

        // PreviewView falls back to a TextureView on hardware that cannot take a SurfaceView, and
        // then there is no surface here to copy the preview out of.
        if (surfaceView == null && !loggedMissingSurfaceView) {
            loggedMissingSurfaceView = true
            Log.i(TAG, "Preview is not backed by a SurfaceView; no frame to prefetch")
        }

        return surfaceView?.takeIf { it.holder.surface.isValid }
    }

    // [handler] is deliberately not the main thread's: the copy itself takes about 40ms, but the
    // switch it is meant for blocks the main thread, so a callback queued there would only arrive
    // once the switch it was supposed to spare had already paid for a frame of its own.
    private fun copyPreviewInto(
        copy: Bitmap,
        surfaceView: SurfaceView,
        handler: Handler,
        retries: Int,
    ) {
        try {
            PixelCopy.request(surfaceView, copy, { result ->
                when {
                    result == PixelCopy.SUCCESS -> onCopied(copy)
                    // The surface only holds its last buffer until the camera takes the slot back,
                    // so a copy started in the gap between two preview frames comes back empty.
                    retries > 0 -> {
                        handler.postDelayed(
                            { copyPreviewInto(copy, surfaceView, handler, retries - 1) },
                            FRAME_COPY_RETRY_DELAY_MS,
                        )
                    }
                    else -> onCopyFailed()
                }
            }, handler)
        } catch (_: IllegalArgumentException) {
            // A surface that has gone throws here rather than reporting a failure, and the switch
            // this copy is for is what takes it away -- from the main thread, with nothing to keep
            // that from landing between a validity check and this call.
            onCopyFailed()
        }
    }

    private fun onCopied(copy: Bitmap) {
        val wasAwaited = synchronized(lock) {
            lastFrame = copy
            frameCopyPending = false
            framePrefetchedAt = when {
                isCopyAwaited -> 0L
                else -> SystemClock.uptimeMillis()
            }

            isCopyAwaited.also { isCopyAwaited = false }
        }

        if (wasAwaited) {
            previewView.post(onLateFrame)
        }
    }

    private fun onCopyFailed() {
        synchronized(lock) {
            frameCopyPending = false
            isCopyAwaited = false
        }
    }

    private fun frameCopyLooper(): Looper {
        frameCopyThread?.let { return it.looper }
        return HandlerThread("frame-copy").apply {
            start()
            frameCopyThread = this
        }.looper
    }

    // A copy taken for a switch that never happened shows a scene the camera has since moved on
    // from, which is worse behind the transition than paying for a fresh one.
    private fun hasFreshPrefetch(): Boolean {
        return framePrefetchedAt != 0L &&
            SystemClock.uptimeMillis() - framePrefetchedAt < PREFETCH_FRESHNESS_MS
    }

    private companion object {
        const val TAG = "PreviewFrameHolder"

        const val PREFETCH_FRESHNESS_MS = 2_000L

        // One preview frame at 30fps, the wait for the camera to fill the surface again.
        const val FRAME_COPY_RETRY_DELAY_MS = 33L
        const val FRAME_COPY_RETRIES = 3
    }
}
