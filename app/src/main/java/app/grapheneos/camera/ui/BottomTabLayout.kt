package app.grapheneos.camera.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.google.android.material.tabs.TabLayout

class BottomTabLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TabLayout(context, attrs) {

    private var sp = 0

    private val snapPoints: ArrayList<Int> = arrayListOf()

    private lateinit var tabParent: ViewGroup

    val selectedTab: Tab?
        get() {
            return getTabAt(selectedTabPosition)
        }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        if (tabCount == 0) return

        tabParent = getChildAt(0) as ViewGroup
        val firstTab = tabParent.getChildAt(0)
        val lastTab = tabParent.getChildAt(tabParent.childCount - 1)
        sp = width / 2 - firstTab.width / 2
        ViewCompat.setPaddingRelative(
            getChildAt(0),
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

        if (snapPoints.last() != 0) {

            for (i in 0 until snapPoints.size step 2) {

                val start = snapPoints[i]
                val end = snapPoints[i + 1]

                if (x in start..end) {
                    val index = i / 2
                    if (selectedTabPosition != index) {
                        return selectTab(getTabAt(index))
                    }
                }

            }
        }
    }

    fun getTabAtX(x: Int): Tab? {
        for (i in 0 until snapPoints.size step 2) {

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

    fun getAllModes(): ArrayList<Int> {
        val modes = arrayListOf<Int>()

        for (index in 0..tabCount) {
            val tab = getTabAt(index)
            if (tab != null)
                modes.add(tab.id)
        }

        return modes
    }
}
