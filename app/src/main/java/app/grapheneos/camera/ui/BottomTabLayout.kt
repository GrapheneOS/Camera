package app.grapheneos.camera.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import app.grapheneos.camera.CameraMode
import com.google.android.material.tabs.TabLayout

class BottomTabLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TabLayout(context, attrs) {

    private var sp = 0

    private val snapPoints: ArrayList<Int> = arrayListOf()

    private var isDragging = false

    private lateinit var tabParent: ViewGroup

    val selectedTab: Tab?
        get() {
            return getTabAt(selectedTabPosition)
        }

    fun getTabForMode(mode: CameraMode): Tab? {
        for (index in 0 until tabCount) {
            val tab = getTabAt(index)
            if (tab?.tag == mode) {
                return tab
            }
        }

        return null
    }

    // Called for every event dispatched to the strip, tabs included, so this sees the whole gesture
    // even once a tab's own view has taken the touch.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_MOVE -> isDragging = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging = false
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        if (tabCount == 0) return

        tabParent = getChildAt(0) as ViewGroup
        val firstTab = tabParent.getChildAt(0)
        val lastTab = tabParent.getChildAt(tabParent.childCount - 1)
        sp = width / 2 - firstTab.width / 2
        getChildAt(0).setPaddingRelative(
            sp,
            0,
            width / 2 - lastTab.width / 2,
            0
        )

        snapPoints.clear()

        for (tabIndex in 0 until tabCount) {
            snapPoints.add(calculateScrollXStartForTab(tabIndex))
            snapPoints.add(calculateScrollXEndForTab(tabIndex))
        }

        centerSelectedTab()
    }

    private fun centerSelectedTab() {
        getTabAt(selectedTabPosition)?.let {
            centerTab(it)
        }
    }

    override fun onScrollChanged(x: Int, y: Int, oldX: Int, oldY: Int) {
        super.onScrollChanged(x, y, oldX, oldY)

        // snapPoints is empty until the first layout pass, and goes stale when the tab set is
        // rebuilt, so an index taken from it may no longer name a tab.
        if (snapPoints.isEmpty() || snapPoints.last() == 0) {
            return
        }

        // Only a finger on the strip picks a mode this way. Centering a tab animates the scroll
        // position through the snap ranges of every tab in between, and switching mode blocks the
        // main thread for long enough that those frames arrive after the switch -- the first of them
        // still at the tab the animation started from, which would drag the highlight back onto the
        // mode the camera just left.
        if (!isDragging) {
            return
        }

        for (i in snapPoints.indices step 2) {

            val start = snapPoints[i]
            val end = snapPoints[i + 1]

            if (x in start..end) {
                val index = i / 2
                val tab = getTabAt(index) ?: return
                if (selectedTabPosition != index) {
                    selectTab(tab)
                }
                return
            }

        }
    }

    fun getTabAtX(x: Int): Tab? {
        for (i in snapPoints.indices step 2) {
            val start = snapPoints[i]
            val end = snapPoints[i + 1]

            if (x in start..end) {
                val index = i / 2
                if (selectedTabPosition != index) {
                    return getTabAt(index)
                }
            }

        }

        return null
    }

    fun centerTab(tab: Tab) {
        if (!this::tabParent.isInitialized) return
        val targetScrollX = calculateScrollXForTab(tab.position)

        if (scrollX != targetScrollX)
            smoothScrollTo(targetScrollX, 0)
    }

    private fun calculateScrollXForTab(position: Int): Int {
        val selectedChild = tabParent.getChildAt(position) ?: return 0
        val selectedWidth = selectedChild.width

        return selectedChild.left + selectedWidth / 2 - width / 2
    }

    private fun calculateScrollXStartForTab(position: Int): Int {
        val selectedChild = tabParent.getChildAt(position) ?: return 0
        val selectedWidth = selectedChild.width

        return selectedChild.left + selectedWidth / 2 - width / 2
    }

    private fun calculateScrollXEndForTab(position: Int): Int {
        val selectedChild = tabParent.getChildAt(position) ?: return 0
        val selectedWidth = selectedChild.width

        return selectedChild.left + selectedWidth - width / 2
    }

    fun getAllModes(): Set<CameraMode> {
        return IntRange(0, tabCount - 1).map {
            getTabAt(it)!!.tag as CameraMode
        }.toSet()
    }
}
