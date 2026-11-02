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
    private var dualStartPending = false
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
            latestRearAreaInfoAcrossRecreation = info
            finishDualTeardownIfReady()
            tryLaunchPendingRearSelfie()
            Log.i(TAG, "WindowArea status: rear=${status(rearOperation)}, " +
                "dual=${status(dualOperation)}, teardown=$dualTeardownPendingAcrossRecreation")
            refreshSettings()
            // A different Activity instance may now own the returning inner viewfinder.
            if (rearReturnPendingAcrossRecreation) onActivityResumed()
        }
    }

    private val dualCallback = object : WindowAreaPresentationSessionCallback {
        override fun onSessionStarted(session: WindowAreaSessionPresenter) {
            dualStartPending = false
            if (disposed || dualTeardownPendingAcrossRecreation ||
                pendingRearAfterDualAcrossRecreation) {
                // User requested Rear Camera Selfie while this presentation was opening.
                // Never let two WindowArea operations overlap.
                startDualTeardown()
                Log.i(TAG, "Dual session arrived during pending Rear Selfie; closing it")
                session.close()
                return
            }
            Log.i(TAG, "Dual Screen Preview session started")
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
            if (wasVisible && !isVisible && dualSession != null) {
                closeDualSession()
            }
        }

        override fun onSessionEnded(t: Throwable?) {
            val handingOffToRear = pendingRearAfterDualAcrossRecreation
            if (handingOffToRear) {
                // Allow Android to finish the display transition before the rear-camera transfer.
                pendingRearNotBeforeUptimeMs = android.os.SystemClock.uptimeMillis() + 2000L
                Log.i(TAG, "Waiting 2s after Dual session end before Rear transfer")
            }
            Log.i(TAG, "Dual Screen Preview session ended (error=${t != null})")
            dualSession = null
            dualStartPending = false
            dualVisible = false
            secondaryPreviewView = null
            if (dualTeardownPendingAcrossRecreation) {
                dualSessionEndObservedAcrossRecreation = true
                finishDualTeardownIfReady()
            }
            if (t != null && !disposed) {
                Log.w(TAG, "Dual Screen Preview ended with error", t)
                activity.showMessage("Dual Screen Preview ended")
            }
            // When the second display disappears, rebind only the ordinary inner Preview.
            // Never restart camera while paused or during teardown.
            val rebind = !skipNextSessionEndRebind && !handingOffToRear
            skipNextSessionEndRebind = false
            if (!disposed && rebind && activity.lifecycle.currentState.isAtLeast(
                    androidx.lifecycle.Lifecycle.State.RESUMED)) {
                activity.camConfig.startCamera(true)
            }
            if (!disposed) refreshSettings()
            // The session-end callback can belong to an Activity already recreated
            // by Android. Wake the current Activity/controller, not the old one.
            latestController?.tryLaunchPendingRearSelfie()
        }
    }

    init {
        latestController = this
        controller.addWindowAreaInfoListListener(executor, areasListener)
    }

    /**
     * The transfer callback can arrive before Android has moved the Activity back to
     * the default (inner) display. Rebinding at that point would use the old surface.
     * The layout, configuration and resume callbacks retry once the inner view is ready.
     */
    // Window-area transfer can DESTROY and recreate MainActivity.  The session callback
    // can still belong to the disposed previous controller.  Keep return state per process.
    private fun requestInnerPreviewRestore() {
        rearReturnPendingAcrossRecreation = true
        innerPreviewRestorePending = true
        val preview = activity.previewView
        preview.removeOnLayoutChangeListener(innerPreviewLayoutListener)
        preview.addOnLayoutChangeListener(innerPreviewLayoutListener)
        restoreInnerPreviewWhenReady()
    }

    private fun restoreInnerPreviewWhenReady() {
        if (disposed || (!innerPreviewRestorePending && !rearReturnPendingAcrossRecreation)
            || rearSession != null) return
        val preview = activity.previewView
        preview.post {
            if (disposed || (!innerPreviewRestorePending && !rearReturnPendingAcrossRecreation)
                || rearSession != null) return@post
            // Do not bind to the cover-screen surface or race against the transfer teardown.
            if (!activity.lifecycle.currentState.isAtLeast(
                    androidx.lifecycle.Lifecycle.State.RESUMED) ||
                !preview.isAttachedToWindow || preview.width == 0 || preview.height == 0 ||
                preview.display?.displayId != Display.DEFAULT_DISPLAY ||
                status(rearOperation) == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE) return@post

            innerPreviewRestorePending = false
            rearReturnPendingAcrossRecreation = false
            preview.removeOnLayoutChangeListener(innerPreviewLayoutListener)
            Log.i(TAG, "Rear Camera Selfie returned; rebinding inner CameraX preview")
            activity.camConfig.startCamera(true)
        }
    }

    fun onActivityResumed() {
        if (rearReturnPendingAcrossRecreation) {
            innerPreviewRestorePending = true
            val preview = activity.previewView
            preview.removeOnLayoutChangeListener(innerPreviewLayoutListener)
            preview.addOnLayoutChangeListener(innerPreviewLayoutListener)
        }
        restoreInnerPreviewWhenReady()
        tryLaunchPendingRearSelfie()
    }
    fun onActivityWindowChanged() = onActivityResumed()

    private fun status(operation: WindowAreaCapability.Operation): WindowAreaCapability.Status? =
        info?.getCapability(operation)?.status

    // WindowAreaInfo.getActiveSession() THROWS when the capability is merely AVAILABLE.
    // Even ACTIVE can change asynchronously between the status check and this call.
    private fun activeSessionIfAvailable(
        area: WindowAreaInfo?,
        operation: WindowAreaCapability.Operation,
    ): WindowAreaSession? {
        if (area?.getCapability(operation)?.status !=
            WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE) return null
        return try {
            area.getActiveSession(operation)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Window-area session disappeared during transition", e)
            null
        }
    }

    // The presentation callback may belong to an Activity that Android has destroyed
    // during a display/configuration change. Track its teardown across recreation.
    private fun startDualTeardown() {
        dualTeardownPendingAcrossRecreation = true
        dualSessionEndObservedAcrossRecreation = false
    }

    private fun closeDualSession() {
        val active = dualSession ?: activeSessionIfAvailable(info, dualOperation)
        if (dualTeardownPendingAcrossRecreation) return
        startDualTeardown()
        Log.i(TAG, "Closing Dual Screen Preview; waiting for session end")
        if (active != null) {
            active.close()
        } else {
            // A callback can already have ended before the local session reference
            // was restored; re-check the actual WindowArea capability instead.
            dualSessionEndObservedAcrossRecreation = true
            finishDualTeardownIfReady()
        }
        refreshSettings()
    }

    private fun finishDualTeardownIfReady() {
        if (!dualTeardownPendingAcrossRecreation ||
            !dualSessionEndObservedAcrossRecreation) return
        // A previous controller can receive onSessionEnded after it was disposed.
        // Its local info may be stale; use the newest WindowAreaInfo across Activities.
        val currentInfo = latestRearAreaInfoAcrossRecreation ?: info
        val rearReady = currentInfo?.getCapability(rearOperation)?.status ==
            WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE
        val dualStillActive = currentInfo?.getCapability(dualOperation)?.status ==
            WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE ||
            activeSessionIfAvailable(currentInfo, dualOperation) != null
        if (rearReady && !dualStillActive && dualSession == null) {
            dualTeardownPendingAcrossRecreation = false
            dualSessionEndObservedAcrossRecreation = false
            Log.i(TAG, "Dual presentation released; Rear Camera Selfie can start")
            latestController?.tryLaunchPendingRearSelfie()
        }
    }

    // Keep the user's requested destination across MainActivity recreation, but
    // only actually transfer AFTER the dual session has ended and rear is AVAILABLE.
    private fun tryLaunchPendingRearSelfie() {
        // Leave the WindowArea callback before requesting another session.
        // Recheck everything on the next main-thread loop because the old
        // Activity or its display may have changed in the meantime.
        activity.window.decorView.post {
            if (!pendingRearAfterDualAcrossRecreation || disposed ||
                latestController !== this || dualTeardownPendingAcrossRecreation ||
                dualStartPending || dualSession != null ||
                status(dualOperation) == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE ||
                status(rearOperation) != WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE ||
                !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
                return@post
            // Do not start another WindowArea transfer on the same UI frame.
            // One timer shared by recreated Activities; it dispatches to the newest owner.
            val waitMs = pendingRearNotBeforeUptimeMs - android.os.SystemClock.uptimeMillis()
            if (waitMs > 0L) {
                if (!pendingRearDelayScheduledAcrossRecreation) {
                    pendingRearDelayScheduledAcrossRecreation = true
                    Log.i(TAG, "Rear Camera Selfie handoff cooldown: ${waitMs}ms remaining")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        pendingRearDelayScheduledAcrossRecreation = false
                        latestController?.tryLaunchPendingRearSelfie()
                    }, waitMs + 20L)
                }
                return@post
            }
            val token = info?.token ?: return@post
            if (!prepareCamera()) {
                pendingRearAfterDualAcrossRecreation = false
                refreshSettings()
                return@post
            }
            // Clear BEFORE requesting transfer so callbacks cannot request it twice.
            pendingRearAfterDualAcrossRecreation = false
            try {
                activity.settingsDialog.dismiss()
                Log.i(TAG, "Dual released; starting queued Rear Camera Selfie")
                controller.transferActivityToWindowArea(token, activity, executor, this)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Queued Rear Camera Selfie could not start", e)
                activity.showMessage("Rear Camera Selfie could not start")
            } catch (e: UnsupportedOperationException) {
                Log.e(TAG, "Queued Rear Camera Selfie unsupported", e)
                activity.showMessage("Rear Camera Selfie is not supported")
            } finally {
                refreshSettings()
            }
        }
    }

    private fun refreshSettings() {
        if (disposed) return
        val rearStatus = status(rearOperation)
        val dualStatus = status(dualOperation)
        val rearActive = rearSession != null ||
            rearStatus == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE
        val dualActive = dualSession != null ||
            dualStatus == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE
        val dualBusy = dualTeardownPendingAcrossRecreation || dualStartPending || dualActive ||
            activeSessionIfAvailable(info, dualOperation) != null
        activity.settingsDialog.updateFoldDisplayControls(
            rearSupported = rearStatus != null &&
                rearStatus != WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED,
            // Allow one-tap handoff from Dual Screen Preview to Rear Camera Selfie.
            // The transfer itself still waits for onSessionEnded and AVAILABLE.
            rearEnabled = rearActive ||
                (rearStatus == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE && !dualBusy) ||
                (dualActive && !pendingRearAfterDualAcrossRecreation && !rearActive),
            rearChecked = rearActive || pendingRearAfterDualAcrossRecreation,
            dualSupported = dualStatus != null &&
                dualStatus != WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED,
            dualEnabled = dualActive ||
                (dualStatus == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE &&
                    !rearActive && !dualTeardownPendingAcrossRecreation && !dualStartPending),
            dualChecked = dualActive && !pendingRearAfterDualAcrossRecreation,
        )
    }

    fun toggleRearSelfie() {
        if (disposed) return
        try {
            val state = status(rearOperation)
            if (rearSession != null || state == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE) {
                val active = rearSession ?: activeSessionIfAvailable(info, rearOperation)
                if (active == null) {
                    Log.w(TAG, "Rear session is active but no closable session was returned")
                    activity.showMessage("Could not stop Rear Camera Selfie")
                    return
                }
                // The settings Dialog is attached to the outgoing Activity's window.
                // Dismiss BEFORE transfer causes Android to recreate that Activity.
                activity.settingsDialog.dismiss()
                requestInnerPreviewRestore()
                Log.i(TAG, "Closing Rear Camera Selfie; inner preview restore pending")
                active.close()
                return
            }
            finishDualTeardownIfReady()
            if (dualTeardownPendingAcrossRecreation || dualStartPending ||
                dualSession != null ||
                status(dualOperation) == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE ||
                activeSessionIfAvailable(info, dualOperation) != null) {
                // Queue the other mode rather than telling the user to switch it off.
                pendingRearAfterDualAcrossRecreation = true
                Log.i(TAG, "Rear Selfie requested; closing Dual Screen Preview first")
                if (!dualTeardownPendingAcrossRecreation &&
                    (dualSession != null ||
                     status(dualOperation) == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE)) {
                    closeDualSession()
                }
                refreshSettings()
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
        if (pendingRearAfterDualAcrossRecreation) {
            activity.showMessage("Switching to Rear Camera Selfie")
            return
        }
        try {
            if (dualTeardownPendingAcrossRecreation || dualStartPending) {
                activity.showMessage("Wait for the display transition to finish")
                return
            }
            if (dualSession != null ||
                status(dualOperation) == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE) {
                closeDualSession()
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
            dualStartPending = true
            Log.i(TAG, "Requesting Dual Screen Preview")
            controller.presentContentOnWindowArea(token, activity, executor, dualCallback)
        } catch (e: IllegalStateException) {
            dualStartPending = false
            Log.w(TAG, "Dual screen refused", e)
            activity.showMessage("Dual Screen Preview could not start")
        } catch (e: UnsupportedOperationException) {
            dualStartPending = false
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
        rearReturnPendingAcrossRecreation = false
        activity.previewView.removeOnLayoutChangeListener(innerPreviewLayoutListener)
        rearSession = session
        refreshSettings()
    }

    override fun onSessionEnded(t: Throwable?) {
        val wasActive = rearSession != null
        rearSession = null
        if (wasActive && !disposed) {
            requestInnerPreviewRestore()
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
        closeDualSession()
        // The upcoming switchMode() will perform the ordinary CameraX rebind itself.
    }

    /** Called when CameraX HAL rejects a second Preview. Synchronous clear enables fallback. */
    fun onPreviewBindFailed() {
        if (secondaryPreviewView == null) return
        secondaryPreviewView = null
        skipNextSessionEndRebind = true
        closeDualSession()
        activity.showMessage("This camera cannot run two live previews")
    }

    fun onActivityPaused() {
        secondaryPreviewView = null
        if (dualSession != null) closeDualSession()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        innerPreviewRestorePending = false
        activity.previewView.removeOnLayoutChangeListener(innerPreviewLayoutListener)
        secondaryPreviewView = null
        if (dualSession != null && !dualTeardownPendingAcrossRecreation) closeDualSession()
        dualSession = null
        controller.removeWindowAreaInfoListListener(areasListener)
        if (latestController === this) latestController = null
        // Activity transfer is owned by Android and follows the original activity lifecycle.
    }

    companion object {
        private const val TAG = "FoldDisplay"
        @Volatile private var dualTeardownPendingAcrossRecreation = false
        @Volatile private var dualSessionEndObservedAcrossRecreation = false
        @Volatile private var latestRearAreaInfoAcrossRecreation: WindowAreaInfo? = null
        private var pendingRearAfterDualAcrossRecreation = false
        private var latestController: RearDisplaySelfieController? = null
        private var pendingRearNotBeforeUptimeMs = 0L
        private var pendingRearDelayScheduledAcrossRecreation = false
        // Survives MainActivity recreation during display transfer.
        @Volatile private var rearReturnPendingAcrossRecreation = false
    }
}
