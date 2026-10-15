// SPDX-License-Identifier: MIT
package app.grapheneos.camera.ui.activities

import android.util.Log
import android.view.Display
import android.view.View
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSession
import androidx.window.area.WindowAreaSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import androidx.window.java.area.WindowAreaControllerCallbackAdapter

/**
 * Unmodified rear-camera activity transfer plus direct CameraX dual display preview.
 *
 * Dual mode exposes a second PreviewView in WindowAreaSessionPresenter. CamConfig binds TWO
 * CameraX Preview use cases to the SAME camera: one feeds the inner viewfinder, one the cover
 * display. No PixelCopy, Bitmaps, MediaProjection, image polling, or artificial FPS cap.
 * The Pixel Fold's camera HAL ultimately determines whether simultaneous output is supported.
 */
@OptIn(ExperimentalWindowApi::class)
internal class RearDisplaySelfieController(
    private val activity: MainActivity,
    private val prepareCamera: () -> Boolean,
) : WindowAreaSessionCallback {

    private val rearOperation = WindowAreaCapability.Operation.OPERATION_TRANSFER_ACTIVITY_TO_AREA
    private val dualOperation = WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA
    private val controller = WindowAreaControllerCallbackAdapter(WindowAreaController.getOrCreate())
    private val executor = ContextCompat.getMainExecutor(activity)

    private var info: WindowAreaInfo? = null
    private var rearSession: WindowAreaSession? = null
    private var dualSession: WindowAreaSessionPresenter? = null
    private var dualVisible = false
    private var disposed = false
    private var skipNextSessionEndRebind = false
    // Rear Camera Selfie transfers the Activity itself; when Android moves it back,
    // the PreviewView's Surface can change even though the Activity remains RESUMED.
    private var innerPreviewRestorePending = false
    private val innerPreviewLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        restoreInnerPreviewWhenReady()
    }

    /** Non-null only while an outer PreviewView exists for a live CameraX output. */
    var secondaryPreviewView: PreviewView? = null
        private set

    private val areasListener = Consumer<List<WindowAreaInfo>> { areas ->
        if (!disposed) {
            info = areas.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
            refreshSettings()
        }
    }

    private val dualCallback = object : WindowAreaPresentationSessionCallback {
        override fun onSessionStarted(session: WindowAreaSessionPresenter) {
            if (disposed) {
                session.close()
                return
            }
            dualSession = session
            val preview = PreviewView(session.context).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FIT_CENTER
                contentDescription = "Dual Screen Preview"
            }
            // setContentView turns on the cover display. The two Preview use cases are bound
            // only after the view has obtained nonzero dimensions and a display assignment.
            val container = FrameLayout(session.context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                addView(preview, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ))
            }
            session.setContentView(container)
            secondaryPreviewView = preview
            preview.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View, left: Int, top: Int, right: Int,
                                            bottom: Int, oldLeft: Int, oldTop: Int,
                                            oldRight: Int, oldBottom: Int) {
                    if (right <= left || bottom <= top || disposed || dualSession !== session) return
                    preview.removeOnLayoutChangeListener(this)
                    Log.i(TAG, "Outer display ready: ${right-left}x${bottom-top}")
                    activity.camConfig.startCamera(true)
                }
            })
            refreshSettings()
        }

        override fun onContainerVisibilityChanged(isVisible: Boolean) {
            val wasVisible = dualVisible
            dualVisible = isVisible
            // Camera must not keep driving an invisible display in the background. Don't
            // close before the first visible callback: some devices report false initially.
            if (wasVisible && !isVisible) dualSession?.close()
        }

        override fun onSessionEnded(t: Throwable?) {
            dualSession = null
            dualVisible = false
            secondaryPreviewView = null
            if (t != null && !disposed) {
                Log.w(TAG, "Dual Screen Preview ended with error", t)
                activity.showMessage("Dual Screen Preview ended")
            }
            // When the second display disappears, rebind only the ordinary inner Preview.
            // Never restart camera while paused or during teardown.
            val rebind = !skipNextSessionEndRebind
            skipNextSessionEndRebind = false
            if (!disposed && rebind && activity.lifecycle.currentState.isAtLeast(
                    androidx.lifecycle.Lifecycle.State.RESUMED)) {
                activity.camConfig.startCamera(true)
            }
            if (!disposed) refreshSettings()
        }
    }

    init {
        controller.addWindowAreaInfoListListener(executor, areasListener)
    }

    /**
     * The transfer callback can arrive before Android has moved the Activity back to
     * the default (inner) display. Rebinding at that point would use the old surface.
     * The layout, configuration and resume callbacks retry once the inner view is ready.
     */
    private fun restoreInnerPreviewWhenReady() {
        if (disposed || !innerPreviewRestorePending || rearSession != null) return
        val preview = activity.previewView
        preview.post {
            if (disposed || !innerPreviewRestorePending || rearSession != null) return@post
            if (!activity.lifecycle.currentState.isAtLeast(
                    androidx.lifecycle.Lifecycle.State.RESUMED) ||
                !preview.isAttachedToWindow || preview.width == 0 || preview.height == 0 ||
                preview.display?.displayId != Display.DEFAULT_DISPLAY) return@post

            innerPreviewRestorePending = false
            preview.removeOnLayoutChangeListener(innerPreviewLayoutListener)
            Log.i(TAG, "Rear Camera Selfie finished; rebinding preview on inner display")
            activity.camConfig.startCamera(true)
        }
    }

    fun onActivityResumed() = restoreInnerPreviewWhenReady()
    fun onActivityWindowChanged() = restoreInnerPreviewWhenReady()

    private fun status(operation: WindowAreaCapability.Operation): WindowAreaCapability.Status? =
        info?.getCapability(operation)?.status

    private fun refreshSettings() {
        if (disposed) return
        val rearStatus = status(rearOperation)
        val dualStatus = status(dualOperation)
        val rearActive = rearSession != null ||
            rearStatus == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE
        val dualActive = dualSession != null
        activity.settingsDialog.updateFoldDisplayControls(
            rearSupported = rearStatus != null &&
                rearStatus != WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED,
            rearEnabled = rearActive ||
                (rearStatus == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE && !dualActive),
            rearChecked = rearActive,
            dualSupported = dualStatus != null &&
                dualStatus != WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED,
            dualEnabled = dualActive ||
                (dualStatus == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE && !rearActive),
            dualChecked = dualActive,
        )
    }

    fun toggleRearSelfie() {
        if (disposed) return
        try {
            val state = status(rearOperation)
            if (rearSession != null || state == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE) {
                (rearSession ?: info?.getActiveSession(rearOperation))?.close()
                return
            }
            if (dualSession != null) {
                activity.showMessage("Disable Dual Screen Preview first")
                return
            }
            if (state != WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE || !prepareCamera()) {
                if (state != WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE)
                    activity.showMessage("Rear Camera Selfie is not available")
                refreshSettings()
                return
            }
            val token = info?.token ?: return
            activity.settingsDialog.dismiss()
            controller.transferActivityToWindowArea(token, activity, executor, this)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Rear display refused", e)
            activity.showMessage("Rear Camera Selfie could not start")
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "Rear display unsupported", e)
            activity.showMessage("Rear Camera Selfie is not supported")
        } finally {
            refreshSettings()
        }
    }

    fun toggleDualPreview() {
        if (disposed) return
        try {
            if (dualSession != null) {
                dualSession?.close()
                return
            }
            if (rearSession != null ||
                status(rearOperation) == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE) {
                activity.showMessage("Disable Rear Camera Selfie first")
                return
            }
            if (status(dualOperation) != WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE) {
                activity.showMessage("Dual Screen Preview is not available")
                return
            }
            if (activity.camConfig.currentMode != app.grapheneos.camera.CameraMode.CAMERA) {
                activity.showMessage("Switch to Camera mode for Dual Screen Preview")
                return
            }
            if (!prepareCamera()) return
            val token = info?.token ?: return
            activity.settingsDialog.dismiss()
            controller.presentContentOnWindowArea(token, activity, executor, dualCallback)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Dual screen refused", e)
            activity.showMessage("Dual Screen Preview could not start")
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "Dual screen unsupported", e)
            activity.showMessage("Dual Screen Preview is not supported")
        } finally {
            refreshSettings()
        }
    }

    override fun onSessionStarted(session: WindowAreaSession) {
        if (disposed) {
            session.close()
            return
        }
        innerPreviewRestorePending = false
        activity.previewView.removeOnLayoutChangeListener(innerPreviewLayoutListener)
        rearSession = session
        refreshSettings()
    }

    override fun onSessionEnded(t: Throwable?) {
        val wasActive = rearSession != null
        rearSession = null
        if (wasActive && !disposed) {
            innerPreviewRestorePending = true
            activity.previewView.addOnLayoutChangeListener(innerPreviewLayoutListener)
            restoreInnerPreviewWhenReady()
        }
        if (t != null && !disposed) {
            Log.w(TAG, "Rear-display session ended with error", t)
            activity.showMessage("Rear Camera Selfie ended")
        }
        if (!disposed) refreshSettings()
    }

    /** Called before CamConfig enters another mode: no 2-preview bind in video/extensions. */
    fun stopDualForModeChange() {
        if (dualSession == null) return
        skipNextSessionEndRebind = true
        secondaryPreviewView = null
        dualSession?.close()
        // The upcoming switchMode() will perform the ordinary CameraX rebind itself.
    }

    /** Called when CameraX HAL rejects a second Preview. Synchronous clear enables fallback. */
    fun onPreviewBindFailed() {
        if (secondaryPreviewView == null) return
        secondaryPreviewView = null
        skipNextSessionEndRebind = true
        dualSession?.close()
        activity.showMessage("This camera cannot run two live previews")
    }

    fun onActivityPaused() {
        secondaryPreviewView = null
        dualSession?.close()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        innerPreviewRestorePending = false
        activity.previewView.removeOnLayoutChangeListener(innerPreviewLayoutListener)
        secondaryPreviewView = null
        dualSession?.close()
        dualSession = null
        controller.removeWindowAreaInfoListListener(areasListener)
        // Activity transfer is owned by Android and follows the original activity lifecycle.
    }

    companion object {
        private const val TAG = "FoldDisplay"
    }
}
