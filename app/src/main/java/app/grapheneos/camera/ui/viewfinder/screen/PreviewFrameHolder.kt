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

    /** Called right before the camera is unbound, so the frame has to be taken before it returns. */
    fun holdCurrentFrame()
}

internal class PreviewFrameHolderImpl(
    private val previewView: PreviewView,
) : PreviewFrameHolder {

    @Volatile
    var lastFrame: Bitmap? = null
        private set

    @Volatile
    private var frameCopyPending = false

    // When the copy waiting in [lastFrame] was taken, or 0 when there is none waiting.
    @Volatile
    private var framePrefetchedAt = 0L

    private var frameCopyThread: HandlerThread? = null

    private var loggedMissingSurfaceView = false

    override fun holdCurrentFrame() {
        if (hasFreshPrefetch()) {
            framePrefetchedAt = 0
            return
        }
        lastFrame = previewView.bitmap
    }

    // Starts a copy of the preview for [holdCurrentFrame] to pick up. previewView.bitmap blocks the
    // caller on a GPU readback for about a tenth of a second, and startCamera() reads it at the
    // point where it can least afford to block; the same pixels copied asynchronously cost the main
    // thread nothing, as long as the copy is started early enough.
    fun prefetch() {
        if (frameCopyPending || hasFreshPrefetch()) return
        if (previewView.width == 0 || previewView.height == 0) return

        val surfaceView = previewView.getChildAt(0) as? SurfaceView ?: run {
            // PreviewView falls back to a TextureView on hardware that cannot take a SurfaceView,
            // and then there is no surface here to copy the preview out of.
            if (!loggedMissingSurfaceView) {
                loggedMissingSurfaceView = true
                Log.i(TAG, "Preview is not backed by a SurfaceView; no frame to prefetch")
            }
            return
        }
        if (!surfaceView.holder.surface.isValid) return

        frameCopyPending = true
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
        lastFrame = null
        framePrefetchedAt = 0
    }

    fun release() {
        frameCopyThread?.quitSafely()
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
                    result == PixelCopy.SUCCESS -> {
                        lastFrame = copy
                        framePrefetchedAt = SystemClock.uptimeMillis()
                        frameCopyPending = false
                    }
                    // The surface only holds its last buffer until the camera takes the slot back,
                    // so a copy started in the gap between two preview frames comes back empty.
                    retries > 0 -> {
                        handler.postDelayed(
                            { copyPreviewInto(copy, surfaceView, handler, retries - 1) },
                            FRAME_COPY_RETRY_DELAY_MS,
                        )
                    }
                    else -> {
                        frameCopyPending = false
                    }
                }
            }, handler)
        } catch (_: IllegalArgumentException) {
            // A surface that has gone throws here rather than reporting a failure, and the switch
            // this copy is for is what takes it away -- from the main thread, with nothing to keep
            // that from landing between a validity check and this call.
            frameCopyPending = false
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
