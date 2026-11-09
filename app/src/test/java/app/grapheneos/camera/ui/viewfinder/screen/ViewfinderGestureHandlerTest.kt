package app.grapheneos.camera.ui.viewfinder.screen

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ViewfinderGestureHandlerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val view = View(context)
    private val onAction = mockk<(ViewfinderAction) -> Unit>(relaxed = true)
    private val onPressed = mockk<(Float, Float) -> Unit>(relaxed = true)
    private val onTapped = mockk<() -> Unit>(relaxed = true)
    private val onLongPressed = mockk<() -> Unit>(relaxed = true)
    private val onGestureStarted = mockk<() -> Unit>(relaxed = true)
    private val onPinchStarted = mockk<() -> Unit>(relaxed = true)
    private val onPinchScaleChanged = mockk<(Float) -> Unit>(relaxed = true)
    private val onPinchFinished = mockk<() -> Unit>(relaxed = true)
    private val isSettingsPanelShown = mockk<() -> Boolean>().also { isShown ->
        every { isShown() } returns false
    }
    private val handler = ViewfinderGestureHandler(
        context = context,
        onAction = onAction,
        previewFocus = PreviewFocusCallbacks(
            onPressed = onPressed,
            onTapped = onTapped,
            onLongPressed = onLongPressed,
            onGestureStarted = onGestureStarted,
        ),
        previewZoom = PreviewZoomCallbacks(
            onPinchStarted = onPinchStarted,
            onPinchScaleChanged = onPinchScaleChanged,
            onPinchFinished = onPinchFinished,
        ),
        settingsPanelDrag = SettingsPanelDragCallbacks(
            isShown = isSettingsPanelShown,
            onDragStarted = {},
            onDragged = {},
            onDragStopped = {},
        ),
    )
    private val downTime = SystemClock.uptimeMillis()

    @Test
    fun touchingThePreview_showsTheRingImmediatelyAndConfirmsOnlyOnRelease() {
        touch(MotionEvent.ACTION_DOWN)

        verify(exactly = 1) { onPressed(50f, 50f) }
        verify(exactly = 0) { onTapped() }

        touch(MotionEvent.ACTION_UP)

        verifyOrder {
            onPressed(50f, 50f)
            onAction(CameraAction.Preview.Tapped)
            onTapped()
        }
        confirmVerified(onPressed, onAction, onTapped, onLongPressed, onGestureStarted)
    }

    @Test
    fun holdingThePreview_requestsALockAndReleaseDoesNotStartAnotherFocus() {
        touch(MotionEvent.ACTION_DOWN)
        waitForLongPress()
        touch(MotionEvent.ACTION_UP)

        verifyOrder {
            onPressed(50f, 50f)
            onAction(CameraAction.Preview.Tapped)
            onLongPressed()
        }
        confirmVerified(onPressed, onAction, onTapped, onLongPressed, onGestureStarted)
    }

    @Test
    fun draggingThePreview_dismissesTheInitialFocusAndPreventsALock() {
        touch(MotionEvent.ACTION_DOWN)
        touch(action = MotionEvent.ACTION_MOVE, x = 200f)
        waitForLongPress()
        touch(action = MotionEvent.ACTION_UP, x = 200f)

        verifyOrder {
            onPressed(50f, 50f)
            onGestureStarted()
        }
        confirmVerified(onPressed, onAction, onTapped, onLongPressed, onGestureStarted)
    }

    @Test
    fun pressUnderTheSettingsPanel_doesNotBecomeALockAfterThePanelCloses() {
        every { isSettingsPanelShown() } returns true
        touch(MotionEvent.ACTION_DOWN)
        every { isSettingsPanelShown() } returns false
        waitForLongPress()
        touch(MotionEvent.ACTION_UP)

        confirmVerified(onPressed, onAction, onTapped, onLongPressed, onGestureStarted)
    }

    @Test
    fun tapUnderTheSettingsPanel_reachesOnlyTheViewfinder() {
        every { isSettingsPanelShown() } returns true

        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_UP)

        verify(exactly = 1) { onAction(CameraAction.Preview.Tapped) }
        confirmVerified(onPressed, onAction, onTapped, onLongPressed, onGestureStarted)
    }

    @Test
    fun pinchingRoutesTheWholeGestureToZoom_andDoesNotConfirmTheInitialFocus() {
        val detector = mockk<ScaleGestureDetector> {
            every { scaleFactor } returns 1.5f
        }
        touch(MotionEvent.ACTION_DOWN)
        assertTrue(handler.onScaleBegin(detector))
        handler.onScale(detector)
        handler.onScaleEnd(detector)
        touch(MotionEvent.ACTION_UP)

        verifyOrder {
            onGestureStarted()
            onPinchStarted()
            onPinchScaleChanged(1.5f)
            onPinchFinished()
        }
        verify(exactly = 0) { onTapped() }
        verify(exactly = 0) { onAction(CameraAction.Preview.Tapped) }
    }

    @Test
    fun anOpenSettingsPanel_preventsStartingAPinch() {
        every { isSettingsPanelShown() } returns true
        touch(MotionEvent.ACTION_DOWN)

        assertFalse(handler.onScaleBegin(mockk()))

        verify(exactly = 0) { onPinchStarted() }
    }

    private fun waitForLongPress() {
        shadowOf(Looper.getMainLooper()).idleFor(
            ViewConfiguration.getLongPressTimeout().toLong() + 1,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun touch(
        action: Int,
        x: Float = 50f,
    ) {
        val event = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            x,
            50f,
            0,
        )
        handler.onTouch(v = view, event = event)
        event.recycle()
    }
}
