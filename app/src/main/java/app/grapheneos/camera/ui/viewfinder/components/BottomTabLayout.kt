package app.grapheneos.camera.ui.viewfinder.components

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.view.children
import app.grapheneos.camera.data.core.model.CameraMode
import com.google.android.material.tabs.TabLayout
import kotlin.math.abs
import kotlin.math.roundToInt

class BottomTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TabLayout(context, attrs) {

    // Scroll position that centers each tab, indexed by tab position.
    private val tabCenters: ArrayList<Int> = arrayListOf()

    private var suppressScroll = false

    private var onSettled: Runnable? = null
    private val settleCompletion = Runnable { completeSettle() }

    private val scroller = ValueAnimator().apply {
        interpolator = AnimationUtils.loadInterpolator(
            context,
            android.R.interpolator.fast_out_slow_in,
        )
        addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
    }

    val selectedTab: Tab?
        get() {
            return getTabAt(selectedTabPosition)
        }

    val selectedMode: CameraMode?
        get() {
            return selectedTab?.mode
        }

    private val Tab.mode: CameraMode?
        get() {
            return tag as? CameraMode
        }

    fun modeAt(position: Int): CameraMode? {
        return getTabAt(position)?.mode
    }

    fun centredMode(): CameraMode? {
        return (getTabAtX(scrollX) ?: selectedTab)?.mode
    }

    fun goToMode(
        mode: CameraMode,
        onSettled: Runnable? = null,
    ) {
        val tab = (0 until tabCount)
            .firstNotNullOfOrNull { index -> getTabAt(index)?.takeIf { it.mode == mode } }
            ?: return

        goToTab(tab, onSettled)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            pauseSettle()
        }

        val isHandled = super.dispatchTouchEvent(ev)

        // Only a lifted finger commits a mode, so nothing else would put the strip back.
        if (ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            selectedTab?.let { goToTab(tab = it, onSettled = onSettled) }
        }

        return isHandled
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        if (tabCount == 0) {
            postSettleCompletion()
            return
        }

        val tabParent = getChildAt(0) as ViewGroup
        tabParent.setPaddingRelative(
            width / 2 - tabParent.getChildAt(0).width / 2,
            0,
            width / 2 - tabParent.getChildAt(tabParent.childCount - 1).width / 2,
            0
        )

        val centers = tabParent
            .children
            .take(tabCount)
            .map { it.left + it.width / 2 - width / 2 }
            .toList()

        if (centers == tabCenters) {
            drawSelectionAtScroll(scrollX)
            return
        }

        // The tabs really moved, so any scroll still running is aimed at a stale position and
        // there is nothing to animate from.
        tabCenters.clear()
        tabCenters.addAll(centers)
        pauseSettle()
        setScrollPosition(selectedTabPosition, 0f, true)
        postSettleCompletion()
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

    private fun goToTab(
        tab: Tab,
        onSettled: Runnable? = null,
    ) {
        selectTab(tab)

        drawSelectionAtScroll(scrollX)

        cancelSettle()
        this.onSettled = onSettled

        val target = tabCenters.getOrNull(tab.position) ?: run {
            requestLayout()
            return
        }

        val duration = settleDuration(abs(target - scrollX))
        if (duration == 0L || !ValueAnimator.areAnimatorsEnabled()) {
            scrollTo(target, 0)
            completeSettle()
            return
        }

        scroller.duration = duration
        scroller.setIntValues(scrollX, target)
        scroller.start()

        if (onSettled != null) {
            postDelayed(settleCompletion, duration)
        }
    }

    fun settleNow() {
        if (scroller.isStarted) {
            scroller.end()
        }

        completeSettle()
    }

    private fun completeSettle() {
        val settled = onSettled
        onSettled = null
        removeCallbacks(settleCompletion)
        settled?.run()
    }

    private fun postSettleCompletion() {
        if (onSettled != null) {
            removeCallbacks(settleCompletion)
            post(settleCompletion)
        }
    }

    private fun pauseSettle() {
        scroller.cancel()
        removeCallbacks(settleCompletion)
    }

    private fun cancelSettle() {
        pauseSettle()
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
        for (index in 0 until tabCenters.lastIndex) {
            val fraction =
                (x - tabCenters[index]).toFloat() / (tabCenters[index + 1] - tabCenters[index])
            if (fraction < 1f) {
                return (index + fraction).coerceAtLeast(0f)
            }
        }
        return tabCenters.lastIndex.toFloat()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setModes(
        modes: Set<CameraMode>,
        currentMode: CameraMode,
        onModeTouched: (CameraMode) -> Unit,
    ) {
        if (modes == getAllModes()) {
            return
        }

        Log.i(TAG, "Refreshing tabs...")

        pauseSettle()
        tabCenters.clear()
        removeAllTabs()

        modes.forEach { mode ->
            newTab().let { tab ->
                tab.setText(cameraModeLabel(mode))

                tab.view.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        onModeTouched(mode)
                    }
                    false
                }
                tab.tag = mode

                addTab(tab, mode == currentMode)
            }
        }
    }

    fun getAllModes(): Set<CameraMode> {
        return (0 until tabCount).mapNotNull(::modeAt).toSet()
    }

    private companion object {
        private const val TAG = "BottomTabLayout"

        private const val SETTLE_DURATION_MS = 300L
    }
}
