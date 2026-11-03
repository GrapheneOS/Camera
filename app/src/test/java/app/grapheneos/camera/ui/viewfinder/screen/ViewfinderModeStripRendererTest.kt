package app.grapheneos.camera.ui.viewfinder.screen

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.components.BottomTabLayout
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.ModeTabsUiModel
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ViewfinderModeStripRendererTest {

    private lateinit var host: ActivityController<Activity>
    private lateinit var tabs: BottomTabLayout
    private lateinit var renderer: ViewfinderModeStripRenderer
    private val actions = mutableListOf<ViewfinderAction>()
    private val initial = ModeTabsUiModel(
        visible = true,
        availableModes = linkedSetOf(CameraMode.CAMERA, CameraMode.VIDEO),
    )
    private val target = initial.copy(selectedMode = CameraMode.VIDEO, selectionId = 1)

    @Before
    fun setUp() {
        host = Robolectric.buildActivity(Activity::class.java).setup()
        tabs = BottomTabLayout(ContextThemeWrapper(host.get(), R.style.Theme_Camera))
        host.get().setContentView(tabs)
        renderer = ViewfinderModeStripRenderer(tabs = tabs, onAction = { actions += it })
        renderer.render(initial)
        layOut()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        host.pause().stop().destroy()
    }

    @Test
    fun selection_completesOnceEvenIfSettledAgain() {
        renderer.render(target)
        finishSettling()
        tabs.settleNow()

        assertSettledVideo()
    }

    @Test
    fun rebuildingTheModes_preservesThePendingSelectionAndCompletion() {
        renderer.render(target)
        renderer.render(
            target.copy(
                availableModes = linkedSetOf(CameraMode.NIGHT, CameraMode.CAMERA, CameraMode.VIDEO),
            ),
        )
        layOut()
        finishSettling()
        tabs.settleNow()

        assertSettledVideo()
    }

    @Test
    fun resizingTheStrip_preservesThePendingCompletion() {
        renderer.render(target)
        layOut(width = 480)
        finishSettling()

        assertSettledVideo()
    }

    @Test
    fun settlingBeforeTheRebuiltModesAreLaidOut_completesOnce() {
        renderer.render(target)
        renderer.render(
            target.copy(
                availableModes = linkedSetOf(CameraMode.CAMERA, CameraMode.VIDEO, CameraMode.NIGHT),
            ),
        )
        tabs.settleNow()
        layOut()
        finishSettling()

        assertSettledVideo()
    }

    @Test
    fun replacingAQueuedCompletion_onlySettlesTheLatestSelection() {
        renderer.render(target)
        renderer.render(
            target.copy(
                availableModes = linkedSetOf(CameraMode.NIGHT, CameraMode.CAMERA, CameraMode.VIDEO),
            ),
        )
        layOut()
        renderer.render(initial.copy(selectionId = 2))
        finishSettling()

        assertEquals(listOf(CameraAction.ModeStripSettled(CameraMode.CAMERA)), actions)
        assertEquals(CameraMode.CAMERA, tabs.selectedMode)
    }

    @Test
    fun cancellingAPendingSwitch_followsTheCameraWithoutANewSelectionId() {
        renderer.render(target)
        renderer.render(initial.copy(selectionId = target.selectionId))
        finishSettling()

        assertEquals(listOf(CameraAction.ModeStripSettled(CameraMode.CAMERA)), actions)
        assertEquals(CameraMode.CAMERA, tabs.selectedMode)
    }

    @Test
    fun removingThePendingMode_stillReportsItsCompletionForTheModelToCancel() {
        renderer.render(target)
        renderer.render(target.copy(availableModes = setOf(CameraMode.CAMERA)))
        layOut()
        finishSettling()

        assertEquals(listOf(CameraAction.ModeStripSettled(CameraMode.VIDEO)), actions)
    }

    @Test
    fun cancelledTouch_preservesThePendingCompletion() {
        renderer.render(target)
        dispatchTouch(MotionEvent.ACTION_DOWN)
        dispatchTouch(MotionEvent.ACTION_CANCEL)
        finishSettling()

        assertSettledVideo()
    }

    private fun dispatchTouch(action: Int) {
        val event = MotionEvent.obtain(0L, 0L, action, 10f, 10f, 0)
        try {
            tabs.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun layOut(width: Int = 720) {
        tabs.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(140, View.MeasureSpec.EXACTLY),
        )
        tabs.layout(0, 0, width, 140)
    }

    private fun finishSettling() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
    }

    private fun assertSettledVideo() {
        assertEquals(listOf(CameraAction.ModeStripSettled(CameraMode.VIDEO)), actions)
        assertEquals(CameraMode.VIDEO, tabs.selectedMode)
    }
}
