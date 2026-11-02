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
 * Rear-camera selfie transfer and dual-screen CameraX preview on foldable devices.
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
    private var rearStartPending = false
    // Every asynchronous WindowArea request has its own identity. Cancelled callbacks
    // must not install previews or transfer the Activity after the user has left the mode.
    private val dualRequests = FoldRequestGeneration()
    private val rearRequests = FoldRequestGeneration()
    private var disposed = false
    private var skipNextSessionEndRebind = false
    // If Camera is selected while a Dual -> Rear handoff is pending, currentMode
    // remains CAMERA. switchMode(CAMERA) therefore does not bind the one-preview
    // pipeline. Restore it explicitly once the WindowArea presentation has ended.
    private var restoreCameraAfterFoldCancel = false
    private var cameraPreviewRecoveryCount = 0
    internal val foldPreviewRecoveryCount: Int get() = cameraPreviewRecoveryCount
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
            restoreCameraPreviewAfterCancellationIfReady()
            Log.i(TAG, "WindowArea status: rear=${status(rearOperation)}, " +
                "dual=${status(dualOperation)}, teardown=$dualTeardownPendingAcrossRecreation")
            refreshSettings()
            // A different Activity instance may now own the returning inner viewfinder.
            if (rearReturnPendingAcrossRecreation) onActivityResumed()
        }
    }

    private fun dualCallbackFor(request: Long) = object : WindowAreaPresentationSessionCallback {
        private var requestSession: WindowAreaSessionPresenter? = null

        override fun onSessionStarted(session: WindowAreaSessionPresenter) {
            requestSession = session
            if (!dualRequests.isCurrent(request) || disposed ||
                latestController !== this@RearDisplaySelfieController) {
                // A cancelled startup can still deliver a real WindowArea session.
                // Close it without ever attaching its preview to the camera.
                Log.i(TAG, "Rejecting obsolete Dual presentation callback")
                // This callback cannot change the state of a newer start request.
                session.close()
                return
            }
            dualStartPending = false
            if (dualTeardownPendingAcrossRecreation ||
                pendingRearAfterDualAcrossRecreation ||
                activity.camConfig.currentMode != app.grapheneos.camera.CameraMode.CAMERA) {
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
                    if (right <= left || bottom <= top || disposed || dualSession !== session ||
                        !dualRequests.isCurrent(request)) return
                    preview.removeOnLayoutChangeListener(this)
                    Log.i(TAG, "Outer display ready: ${right-left}x${bottom-top}")
                    activity.camConfig.startCamera(true)
                }
            })
            refreshSettings()
        }

        override fun onContainerVisibilityChanged(isVisible: Boolean) {
            if (!dualRequests.isCurrent(request) || disposed) return
            val wasVisible = dualVisible
            dualVisible = isVisible
            // Camera must not keep driving an invisible display in the background. Don't
            // close before the first visible callback: some devices report false initially.
            if (wasVisible && !isVisible && dualSession != null) {
                closeDualSession()
            }
        }

        override fun onSessionEnded(t: Throwable?) {
            val obsolete = !dualRequests.isCurrent(request) ||
                latestController !== this@RearDisplaySelfieController
            // Do not let a late end from an old request erase a newer live session.
            if (obsolete && (requestSession == null || dualSession !== requestSession)) {
                // A cancelled pending session never owned our active preview. Its
                // late end must not clear a newer request's flags or rebind CameraX.
                if (dualTeardownPendingAcrossRecreation &&
                    dualSession == null && !dualStartPending) {
                    dualSessionEndObservedAcrossRecreation = true
                    finishDualTeardownIfReady()
                }
                restoreCameraPreviewAfterCancellationIfReady()
                return
            }
            val handingOffToRear = !obsolete && pendingRearAfterDualAcrossRecreation
            if (handingOffToRear) {
                // Allow the display transition to finish before starting rear selfie.
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
            val rebind = !obsolete && !skipNextSessionEndRebind && !handingOffToRear
            skipNextSessionEndRebind = false
            if (!disposed && rebind && activity.lifecycle.currentState.isAtLeast(
                    androidx.lifecycle.Lifecycle.State.RESUMED)) {
                activity.camConfig.startCamera(true)
            }
            if (!disposed) {
                refreshSettings()
                restoreCameraPreviewAfterCancellationIfReady()
            }
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
        restoreCameraPreviewAfterCancellationIfReady()
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
        if ((!pendingRearAfterDualAcrossRecreation || rearReady) &&
            !dualStillActive && dualSession == null) {
            dualTeardownPendingAcrossRecreation = false
            dualSessionEndObservedAcrossRecreation = false
            Log.i(TAG, "Dual presentation released; Rear Camera Selfie can start")
            latestController?.tryLaunchPendingRearSelfie()
        }
    }

    // Keep the user's requested destination across MainActivity recreation, but
    // only actually transfer AFTER the dual session has ended and rear is AVAILABLE.
    private fun tryLaunchPendingRearSelfie() {
        val handoffGeneration = rearHandoffGenerationAcrossRecreation
        // Leave the WindowArea callback before requesting another session.
        // Recheck everything on the next main-thread loop because the old
        // Activity or its display may have changed in the meantime.
        activity.window.decorView.post {
            if (handoffGeneration != rearHandoffGenerationAcrossRecreation ||
                !pendingRearAfterDualAcrossRecreation || disposed ||
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
                        if (handoffGeneration != rearHandoffGenerationAcrossRecreation)
                            return@postDelayed
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
                val request = rearRequests.begin()
                rearStartPending = true
                controller.transferActivityToWindowArea(
                    token, activity, executor, rearCallbackFor(request))
            } catch (e: IllegalStateException) {
                rearStartPending = false
                Log.e(TAG, "Queued Rear Camera Selfie could not start", e)
                activity.showMessage("Rear Camera Selfie could not start")
            } catch (e: UnsupportedOperationException) {
                rearStartPending = false
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
        activity.updateFoldTabState(
            rearSupported = rearStatus != null &&
                rearStatus != WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED,
            rearChecked = rearActive || rearStartPending ||
                pendingRearAfterDualAcrossRecreation,
            dualSupported = dualStatus != null &&
                dualStatus != WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED,
            dualChecked = (dualActive || dualStartPending) &&
                !pendingRearAfterDualAcrossRecreation,
        )
    }

    fun toggleRearSelfie() {
        if (disposed) return
        if (pendingRearAfterDualAcrossRecreation) {
            cancelForModeChange()
            return
        }
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
                rearHandoffGenerationAcrossRecreation++
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
            val request = rearRequests.begin()
            rearStartPending = true
            controller.transferActivityToWindowArea(
                token, activity, executor, rearCallbackFor(request))
        } catch (e: IllegalStateException) {
            rearStartPending = false
            Log.w(TAG, "Rear display refused", e)
            activity.showMessage("Rear Camera Selfie could not start")
        } catch (e: UnsupportedOperationException) {
            rearStartPending = false
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
            skipNextSessionEndRebind = false
            val request = dualRequests.begin()
            Log.i(TAG, "Requesting Dual Screen Preview")
            controller.presentContentOnWindowArea(
                token, activity, executor, dualCallbackFor(request))
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

    // A callback is tied to the request that started it, never to whichever
    // operation happens to be current by the time Android delivers the event.
    private fun rearCallbackFor(request: Long) = object : WindowAreaSessionCallback {
        private var requestSession: WindowAreaSession? = null

        override fun onSessionStarted(session: WindowAreaSession) {
            requestSession = session
            if (!rearRequests.isCurrent(request) || disposed ||
                latestController !== this@RearDisplaySelfieController ||
                activity.camConfig.currentMode != app.grapheneos.camera.CameraMode.CAMERA) {
                Log.i(TAG, "Rejecting obsolete Rear Selfie transfer")
                session.close()
                if (!disposed && latestController === this@RearDisplaySelfieController &&
                    rearSession == null) requestInnerPreviewRestore()
                return
            }
            rearStartPending = false
            this@RearDisplaySelfieController.onSessionStarted(session)
        }
        override fun onSessionEnded(t: Throwable?) {
            if (disposed || latestController !== this@RearDisplaySelfieController) return
            if (!rearRequests.isCurrent(request)) {
                // A cancelled ACTIVE transfer still owns the old session cleanup.
                // A cancelled PENDING transfer must not clear a newer Rear session.
                if (requestSession != null && rearSession === requestSession) {
                    this@RearDisplaySelfieController.onSessionEnded(t)
                }
                return
            }
            rearStartPending = false
            this@RearDisplaySelfieController.onSessionEnded(t)
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

    /**
     * The old CameraX graph may still contain two Preview use cases even after
     * WindowArea reports that Dual has ended. In that case the outer Surface is
     * gone but the inner UI can display its last frame while ImageCapture works.
     * Rebind exactly once, after teardown; never during Video or an active Rear
     * transfer. This also handles the case where Dual ended *before* Camera
     * was tapped and a queued Rear handoff was subsequently cancelled.
     */
    private fun restoreCameraPreviewAfterCancellationIfReady() {
        if (!restoreCameraAfterFoldCancel || disposed ||
            latestController !== this) return
        activity.previewView.post {
            if (!restoreCameraAfterFoldCancel || disposed ||
                latestController !== this ||
                activity.camConfig.currentMode != app.grapheneos.camera.CameraMode.CAMERA ||
                dualSession != null || dualStartPending || rearSession != null ||
                rearStartPending || pendingRearAfterDualAcrossRecreation ||
                dualTeardownPendingAcrossRecreation ||
                status(dualOperation) == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE ||
                status(rearOperation) == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE ||
                !activity.lifecycle.currentState.isAtLeast(
                    androidx.lifecycle.Lifecycle.State.RESUMED)) return@post

            restoreCameraAfterFoldCancel = false
            skipNextSessionEndRebind = false
            cameraPreviewRecoveryCount++
            Log.i(TAG, "Cancelled fold handoff; rebinding single inner CameraX preview")
            activity.camConfig.startCamera(true)
        }
    }

    /**
     * Cancel the *requested* operation, not just an already opened session. In particular,
     * dismiss a queued Rear transfer and invalidate Dual/Rear start callbacks that arrive
     * after Camera or Video has been selected. Safe to call more than once.
     */
    fun cancelForModeChange(restoreCameraPreview: Boolean = false) {
        if (disposed) return
        val foldHandoffWasQueued = pendingRearAfterDualAcrossRecreation
        val dualWasPending = dualStartPending
        val dualWasActive = dualSession != null ||
            status(dualOperation) == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE
        val rearWasActive = rearSession != null ||
            status(rearOperation) == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE

        // On a Camera -> Camera selection, CamConfig.switchMode() is not called.
        // Force a one-Preview rebind if the outgoing fold operation had changed
        // (or was about to change) the camera's preview pipeline.
        restoreCameraAfterFoldCancel = restoreCameraPreview &&
            activity.camConfig.currentMode == app.grapheneos.camera.CameraMode.CAMERA &&
            (foldHandoffWasQueued || dualWasPending || dualWasActive ||
                dualTeardownPendingAcrossRecreation || secondaryPreviewView != null)
        pendingRearAfterDualAcrossRecreation = false
        rearHandoffGenerationAcrossRecreation++
        pendingRearNotBeforeUptimeMs = 0L
        pendingRearDelayScheduledAcrossRecreation = false
        dualRequests.cancel()
        rearRequests.cancel()
        dualStartPending = false
        rearStartPending = false
        secondaryPreviewView = null

        if (dualWasPending || dualWasActive) {
            skipNextSessionEndRebind = true
            if (!dualTeardownPendingAcrossRecreation) closeDualSession()
        }
        if (rearWasActive) {
            val active = rearSession ?: activeSessionIfAvailable(info, rearOperation)
            if (active != null) {
                requestInnerPreviewRestore()
                active.close()
            }
        }
        refreshSettings()
        restoreCameraPreviewAfterCancellationIfReady()
    }

    /** Called before CamConfig enters another mode: no 2-preview bind in video/extensions. */
    fun stopDualForModeChange() = cancelForModeChange()

    /** Called when CameraX HAL rejects a second Preview. Synchronous clear enables fallback. */
    fun onPreviewBindFailed() {
        if (secondaryPreviewView == null) return
        cancelForModeChange()
        activity.showMessage("This camera cannot run two live previews")
    }

    fun onActivityPaused() {
        secondaryPreviewView = null
        if (dualSession != null) closeDualSession()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        dualRequests.cancel()
        rearRequests.cancel()
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
        private var rearHandoffGenerationAcrossRecreation = 0L
        private var latestController: RearDisplaySelfieController? = null
        private var pendingRearNotBeforeUptimeMs = 0L
        private var pendingRearDelayScheduledAcrossRecreation = false
        // Survives MainActivity recreation during display transfer.
        @Volatile private var rearReturnPendingAcrossRecreation = false
    }
}
