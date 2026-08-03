package app.grapheneos.camera

import android.Manifest
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.BottomTabLayout
import app.grapheneos.camera.ui.activities.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomTabLayoutRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    /**
     * Every tab the scroll position crossed used to be selected on the way past, and selecting one
     * starts an animation towards its centre -- backwards, since the finger has already moved on.
     * That fight was the jumpiness users reported.
     */
    @Test
    fun draggingTheStrip_neverMovesItAgainstTheFinger() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitStrip(scenario)

            var travel = 0
            scenario.onActivity { activity ->
                activity.camConfig.switchMode(activity.tabLayout.getTabAt(0)!!.tag as CameraMode)
            }
            waitUntil(scenario, "the strip is centred on the first tab") {
                it.tabLayout.scrollX == centreOf(it.tabLayout, 0)
            }
            scenario.onActivity { activity ->
                val tabs = activity.tabLayout
                travel = centreOf(tabs, tabs.tabCount - 1) - centreOf(tabs, 0)
            }

            // Ending in a cancel rather than a lift: no mode is committed, so nothing may legally
            // move the strip except the finger.
            val trace = dragStrip(scenario, scrollPx = travel, endWith = DragEnd.CANCEL)

            trace.zipWithNext { before, after ->
                assertTrue("the strip moved backwards, $before -> $after, in $trace", after >= before)
            }
            assertEquals("the strip did not follow the finger", trace.first() + travel, trace.last())
        }
    }

    /**
     * Each tab claimed only the upper half of the band between itself and the next, so a drag that
     * stopped in the lower half committed no mode at all and the strip snapped back.
     */
    @Test
    fun everyScrollPosition_belongsToTheNearestTab() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitStrip(scenario)

            scenario.onActivity { activity ->
                val tabs = activity.tabLayout
                val last = tabs.tabCount - 1

                var previous = 0
                for (x in centreOf(tabs, 0)..centreOf(tabs, last)) {
                    val tab = tabs.getTabAtX(x)
                    assertNotNull("scroll position $x belongs to no tab", tab)
                    assertTrue(
                        "scroll position $x went back to tab ${tab!!.position} from $previous",
                        tab.position >= previous,
                    )
                    previous = tab.position
                }

                for (position in 0..last) {
                    assertEquals(
                        "the centre of tab $position does not belong to it",
                        position,
                        tabs.getTabAtX(centreOf(tabs, position))?.position,
                    )
                }
            }
        }
    }

    /**
     * A drag left the strip believing a finger was still down, which killed the centring animation
     * on its first frame and stranded the strip between two modes.
     */
    @Test
    fun liftingAFinger_leavesTheStripCentredOnTheModeItCommitted() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitStrip(scenario)

            var target = 0
            var targetMode: CameraMode? = null
            var travel = 0
            scenario.onActivity { activity ->
                val tabs = activity.tabLayout
                target = nextTo(tabs.selectedTabPosition, tabs.tabCount)
                targetMode = tabs.getTabAt(target)!!.tag as CameraMode
                // Two thirds of the way over: past the halfway point that decides the mode, but
                // short of the centre, so the strip still has somewhere to glide to.
                travel = (centreOf(tabs, target) - tabs.scrollX) * 2 / 3
            }

            dragStrip(scenario, scrollPx = travel)

            waitUntil(scenario, "the camera switched to $targetMode") {
                it.camConfig.currentMode == targetMode
            }
            waitUntil(scenario, "the strip settled on tab $target") {
                it.tabLayout.scrollX == centreOf(it.tabLayout, target)
            }
            scenario.onActivity {
                assertEquals(target, it.tabLayout.selectedTabPosition)
            }
        }
    }

    /**
     * The highlight has to glide with the finger, but selecting a tab to move it also switches the
     * camera, so a drag across the strip rebound it once per tab it passed.
     */
    @Test
    fun draggingTheStrip_movesTheHighlightWithoutSelectingATab() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitStrip(scenario)

            var selectedAtStart = 0
            var target = 0
            var travel = 0
            scenario.onActivity { activity ->
                val tabs = activity.tabLayout
                selectedAtStart = tabs.selectedTabPosition
                target = nextTo(selectedAtStart, tabs.tabCount)
                travel = centreOf(tabs, target) - tabs.scrollX
            }

            // An odd number of steps, so no sample lands exactly halfway between two tabs, where
            // either of them is a fair answer.
            dragStrip(scenario, scrollPx = travel, steps = 13, onStep = { activity ->
                val tabs = activity.tabLayout
                assertEquals(
                    "the drag selected a tab at scrollX ${tabs.scrollX}",
                    selectedAtStart,
                    tabs.selectedTabPosition,
                )
                val nearest = tabs.getTabAtX(tabs.scrollX)!!
                assertTrue(
                    "tab ${nearest.position} is nearest at scrollX ${tabs.scrollX} but not drawn " +
                        "as selected",
                    tabViewAt(tabs, nearest.position).isSelected,
                )
            })

            assertNotEquals(selectedAtStart, target)
            waitUntil(scenario, "the camera switched to the tab the drag ended on") {
                it.camConfig.currentMode == it.tabLayout.getTabAt(target)!!.tag
            }
        }
    }

    /**
     * Switching mode rebinds the camera, which blocks the main thread for up to half a second. Run
     * on the lift, that block swallowed the strip's whole centring animation: the slider died under
     * the finger the moment it left, which is what still felt laggy once the strip itself was
     * tracking properly.
     */
    @Test
    fun liftingAFinger_letsTheStripSettleBeforeRebindingTheCamera() {
        assumeTrue("the strip cannot settle gradually with animations off", animatorsEnabled())

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitStrip(scenario)

            var startMode: CameraMode? = null
            var target = 0
            var travel = 0
            scenario.onActivity { activity ->
                val tabs = activity.tabLayout
                startMode = activity.camConfig.currentMode
                target = nextTo(tabs.selectedTabPosition, tabs.tabCount)
                // Two thirds of the way over: enough to commit the mode, and far enough short of
                // the centre that the strip has a settle worth measuring left to run.
                travel = (centreOf(tabs, target) - tabs.scrollX) * 2 / 3
            }

            dragStrip(scenario, scrollPx = travel) { activity ->
                assertNotEquals(
                    "the strip was already at rest, so it cannot show the rebind waiting for it",
                    centreOf(activity.tabLayout, target),
                    activity.tabLayout.scrollX,
                )
                assertEquals(
                    "the camera rebound while the strip was still moving",
                    startMode,
                    activity.camConfig.currentMode,
                )
            }

            waitUntil(scenario, "the camera switched once the strip had settled") {
                it.camConfig.currentMode == it.tabLayout.getTabAt(target)!!.tag
            }
        }
    }

    /**
     * Anything that acts on the mode has to be able to overtake the settle, or a capture that lands
     * in the window would go to the mode being left behind.
     */
    @Test
    fun settlingTheStripEarly_appliesTheModeItWasHeadingFor() {
        assumeTrue("nothing is left to overtake with animations off", animatorsEnabled())

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitStrip(scenario)

            var target = 0
            var travel = 0
            scenario.onActivity { activity ->
                val tabs = activity.tabLayout
                target = nextTo(tabs.selectedTabPosition, tabs.tabCount)
                travel = (centreOf(tabs, target) - tabs.scrollX) * 2 / 3
            }

            // Overtaking has to be asked for while there is still a settle to overtake, which is
            // only guaranteed in the block the finger left in.
            dragStrip(scenario, scrollPx = travel) { activity ->
                val tabs = activity.tabLayout
                tabs.settleNow()

                assertEquals("the strip did not finish its move", centreOf(tabs, target), tabs.scrollX)
                assertEquals(
                    "the mode the strip was settling into never reached the camera",
                    tabs.getTabAt(target)!!.tag,
                    activity.camConfig.currentMode,
                )
            }
        }
    }

    /**
     * The rebind holds the main thread for half a second with the preview already gone, and the
     * stream state that used to raise the blurred stand-in only changes from inside that block --
     * so it reached the screen after the freeze it was there to cover, and the switch read as a
     * dead UI. It has to be up while the strip is still gliding.
     */
    @Test
    fun liftingAFinger_blursThePreviewBeforeTheCameraRebinds() {
        assumeTrue("nothing glides before the rebind with animations off", animatorsEnabled())

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitStrip(scenario)

            var startMode: CameraMode? = null
            var target = 0
            var travel = 0
            scenario.onActivity { activity ->
                val tabs = activity.tabLayout
                startMode = activity.camConfig.currentMode
                target = nextTo(tabs.selectedTabPosition, tabs.tabCount)
                travel = (centreOf(tabs, target) - tabs.scrollX) * 2 / 3
            }

            dragStrip(scenario, scrollPx = travel) { activity ->
                assertEquals(
                    "the camera rebound before there was anything to check",
                    startMode,
                    activity.camConfig.currentMode,
                )
                assertEquals(
                    "the preview was left live over the freeze the switch is about to cost",
                    View.VISIBLE,
                    activity.mainOverlay.visibility,
                )
            }
        }
    }

    private fun animatorsEnabled() = ValueAnimator.areAnimatorsEnabled()

    // A neighbouring tab, whichever side of [position] the strip has one on.
    private fun nextTo(position: Int, tabCount: Int): Int =
        if (position + 1 < tabCount) position + 1 else position - 1

    private fun <A : MainActivity> awaitStrip(scenario: ActivityScenario<A>) {
        awaitModeTabs(scenario)
        waitUntil(scenario, "the strip is laid out and scrollable") {
            val tabs = it.tabLayout
            tabs.width > 0 && (tabs.getChildAt(0)?.width ?: 0) > tabs.width
        }
        scenario.onActivity {
            assertTrue("a strip of ${it.tabLayout.tabCount} tabs cannot be dragged across",
                it.tabLayout.tabCount >= 2)
        }
        // The opening centring scroll is animated, and a drag started during it would be measuring
        // that animation rather than the finger.
        waitUntil(scenario, "the strip is centred on the selected tab") {
            it.tabLayout.scrollX == centreOf(it.tabLayout, it.tabLayout.selectedTabPosition)
        }
        // A surface the camera has not filled yet has no frame to copy and nothing to blur, so a
        // gesture dispatched before this would be testing the wrong half of the switch.
        waitUntil(scenario, "the preview is streaming") {
            it.previewView.previewStreamState.value == PreviewView.StreamState.STREAMING
        }
    }

    private fun centreOf(tabs: BottomTabLayout, position: Int): Int {
        val tabView = tabViewAt(tabs, position)
        return tabView.left + tabView.width / 2 - tabs.width / 2
    }

    private fun tabViewAt(tabs: BottomTabLayout, position: Int): View =
        (tabs.getChildAt(0) as ViewGroup).getChildAt(position)
}
