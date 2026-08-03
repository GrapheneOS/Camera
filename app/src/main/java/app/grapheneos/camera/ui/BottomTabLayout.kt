package app.grapheneos.camera.ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import app.grapheneos.camera.CameraMode
import com.google.android.material.tabs.TabLayout
import kotlin.math.abs
import kotlin.math.roundToInt


class BottomTabLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TabLayout(context, attrs) {

    // Scroll position that centers each tab, indexed by tab position.
    private val tabCenters: ArrayList<Int> = arrayListOf()

    private var suppressScroll = false

    private var onSettled: Runnable? = null

    private val scroller = ValueAnimator().apply {
        interpolator = AnimationUtils.loadInterpolator(
            context, android.R.interpolator.fast_out_slow_in
        )
        addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
    }

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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            cancelSettle()
        }

        val isHandled = super.dispatchTouchEvent(ev)

        // Only a lifted finger commits a mode, so nothing else would put the strip back.
        if (ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            selectedTab?.let(::goToTab)
        }

        return isHandled
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        if (tabCount == 0) return

        val tabParent = getChildAt(0) as ViewGroup
        tabParent.setPaddingRelative(
            width / 2 - tabParent.getChildAt(0).width / 2,
            0,
            width / 2 - tabParent.getChildAt(tabParent.childCount - 1).width / 2,
            0
        )

        val centers = (0 until tabParent.childCount).map {
            val tabView = tabParent.getChildAt(it)
            tabView.left + tabView.width / 2 - width / 2
        }

        if (centers == tabCenters) {
            drawSelectionAtScroll(scrollX)
            return
        }

        // The tabs really moved, so any scroll still running is aimed at a stale position and
        // there is nothing to animate from.
        tabCenters.clear()
        tabCenters.addAll(centers)
        cancelSettle()
        setScrollPosition(selectedTabPosition, 0f, true)
    }

    override fun onScrollChanged(x: Int, y: Int, oldX: Int, oldY: Int) {
        super.onScrollChanged(x, y, oldX, oldY)

        drawSelectionAtScroll(x)
    }

    fun getTabAtX(x: Int): Tab? {
        return when {
            tabCenters.size != tabCount -> null
            else -> getTabAt(fractionalTabPosition(x).roundToInt())
        }
    }

    fun goToTab(tab: Tab, onSettled: Runnable? = null) {
        selectTab(tab)

        drawSelectionAtScroll(scrollX)

        cancelSettle()

        val target = tabCenters.getOrNull(tab.position)
        val duration = if (target == null) 0L else settleDuration(abs(target - scrollX))

        if (target == null || duration == 0L || !ValueAnimator.areAnimatorsEnabled()) {
            target?.let { scrollTo(it, 0) }
            onSettled?.run()
            return
        }

        scroller.duration = duration
        scroller.setIntValues(scrollX, target)
        scroller.start()

        onSettled?.let {
            this.onSettled = it
            postDelayed(it, duration)
        }
    }

    fun settleNow() {
        if (scroller.isStarted) {
            scroller.end()
        }

        val settled = onSettled ?: return
        onSettled = null
        removeCallbacks(settled)

        settled.run()
    }

    private fun cancelSettle() {
        scroller.cancel()
        onSettled?.let { removeCallbacks(it) }
        onSettled = null
    }

    private fun settleDuration(distance: Int): Long {
        if (tabCenters.size < 2) {
            return SETTLE_DURATION_MS
        }

        val pitch = abs(tabCenters.last() - tabCenters.first()) / (tabCenters.size - 1)
        if (pitch == 0) {
            return SETTLE_DURATION_MS
        }

        return (SETTLE_DURATION_MS * distance / pitch)
            .coerceAtMost(SETTLE_DURATION_MS)
    }

    private fun drawSelectionAtScroll(x: Int) {
        // tabCenters stays stale from a rebuild of the tab set until the layout pass after it.
        if (tabCenters.size != tabCount) return

        val position = fractionalTabPosition(x)
        val index = position.toInt()

        // setScrollPosition() also scrolls to whatever it draws, and the strip is already exactly
        // where it wants to be.
        suppressScroll = true

        try {
            setScrollPosition(index, position - index, true)
        } finally {
            suppressScroll = false
        }
    }

    override fun scrollTo(x: Int, y: Int) {
        if (!suppressScroll) {
            super.scrollTo(x, y)
        }
    }

    private fun fractionalTabPosition(x: Int): Float {
        for (index in 0 until tabCenters.size - 1) {
            val fraction =
                (x - tabCenters[index]).toFloat() / (tabCenters[index + 1] - tabCenters[index])
            if (fraction < 1f) {
                return (index + fraction).coerceAtLeast(0f)
            }
        }
        return (tabCenters.size - 1).toFloat()
    }

    fun getAllModes(): Set<CameraMode> {
        return IntRange(0, tabCount - 1).map {
            getTabAt(it)!!.tag as CameraMode
        }.toSet()
    }

    private companion object {
        private const val SETTLE_DURATION_MS = 300L
    }
}
