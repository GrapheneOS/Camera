package app.grapheneos.camera.ui

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.view.children
import app.grapheneos.camera.CameraMode
import com.google.android.material.tabs.TabLayout
import kotlin.math.abs
import kotlin.math.roundToInt


enum class FoldTabMode { DUAL, REAR }

/** Movable shortcuts for in-camera dual modes or the panorama variant. */
enum class CameraShortcutMode { DUAL_PHOTO, DUAL_VIDEO, PANORAMA_360 }

/** Actions are never persisted as camera modes and cannot be reordered/hidden. */
enum class ModeTabAction { MORE }

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

    fun getTabForAction(action: ModeTabAction): Tab? = (0 until tabCount)
        .mapNotNull(::getTabAt).firstOrNull { it.tag == action }

    /** CameraMode, FoldTabMode and CameraShortcutMode share one strip. */
    fun getTabForTag(mode: Any): Tab? = (0 until tabCount)
        .mapNotNull(::getTabAt).firstOrNull { it.tag == mode }

    /** Drop obsolete animations and re-center after rebuilding the same tab strip. */
    private var centerAfterRebuild = false
    fun markTabsRebuilding() {
        cancelSettle()
        centerAfterRebuild = true
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

    var onTabReordered: ((Any, Any) -> Unit)? = null
    var onTabHidden: ((Any) -> Unit)? = null
    var onMoreClicked: (() -> Unit)? = null
    var onTabPaste: ((Any, Any, Boolean) -> Unit)? = null
    var onEditorScrollChanged: ((Float) -> Unit)? = null
    var onEditorSelectionChanged: ((Any?) -> Unit)? = null
    private var selectedEditorMode: Any? = null
    private val insertPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(84, 167, 255)
        strokeWidth = 2.5f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 8f, resources.displayMetrics
        )
    }
    private val insertionRadius = 19f * resources.displayMetrics.density
    private fun editorScrollMax(): Int =
        ((getChildAt(0) as? ViewGroup)?.width ?: width).minus(width).coerceAtLeast(0)
    fun scrollEditorToFraction(fraction: Float) {
        if (!isEditingModes) return
        scrollTo((editorScrollMax() * fraction.coerceIn(0f,1f)).roundToInt(),0)
    }
    fun scrollEditorToMode(mode: Any) {
        post {
            val view = getTabForModeTag(mode)?.view ?: return@post
            scrollTo((view.left + view.width / 2 - width / 2).coerceIn(0, editorScrollMax()),0)
        }
    }
    private fun getTabForModeTag(mode: Any): Tab? =
        (0 until tabCount).mapNotNull(::getTabAt).firstOrNull { it.tag == mode }
    fun selectEditorMode(mode: Any?) {
        if (!isEditingModes) return
        selectedEditorMode = mode
        updateEditorHighlight()
        onEditorSelectionChanged?.invoke(mode)
        invalidate()
    }
    private fun updateEditorHighlight() {
        for (i in 0 until tabCount) {
            val tab = getTabAt(i) ?: continue
            (tab.customView?.background as? GradientDrawable)?.mutate()?.let { drawable ->
                (drawable as GradientDrawable).setColor(
                    if (selectedEditorMode != null && selectedEditorMode == tab.tag)
                        0xBB24518B.toInt() else Color.TRANSPARENT)
            }
        }
    }
    /** Draw insertion markers that scroll WITH the underlying mode strip. */
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!isEditingModes || selectedEditorMode == null) return
        val parent = getChildAt(0) as? ViewGroup ?: return
        val y = height - 8f * resources.displayMetrics.density
        for (i in 0 until tabCount - 1) {
            val left = getTabAt(i)?.tag
            val right = getTabAt(i+1)?.tag
            if (left is ModeTabAction || right is ModeTabAction) continue
            val child=parent.getChildAt(i) ?: continue
            val x = child.right - scrollX.toFloat()
            if (x < 0 || x > width) continue
            canvas.drawCircle(x,y,5f*resources.displayMetrics.density,insertPaint)
            val old=insertPaint.color
            insertPaint.color=Color.WHITE
            canvas.drawLine(x-3f*resources.displayMetrics.density,y,
                x+3f*resources.displayMetrics.density,y,insertPaint)
            insertPaint.color=old
        }
    }
    private fun pasteTargetAt(x: Float): Pair<Any,Boolean>? {
        if (selectedEditorMode == null) return null
        val parent = getChildAt(0) as? ViewGroup ?: return null
        val contentX=x+scrollX
        for (i in 0 until tabCount-1) {
            val mode=getTabAt(i)?.tag ?: continue
            val next=getTabAt(i+1)?.tag ?:continue
            if (mode is ModeTabAction || next is ModeTabAction) continue
            val boundary=parent.getChildAt(i)?.right ?: continue
            if (abs(contentX-boundary)<insertionRadius) return next to false // insert before next
        }
        return null
    }
    var canEditModes: (() -> Boolean)? = null
    var onEditModeChanged: ((Boolean) -> Unit)? = null

    var isEditingModes: Boolean = false
        private set

    // Kept for callers predating the editable strip.
    val isReordering: Boolean
        get() = dragging

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var downScrollX = 0
    private var draggedIndex = -1
    private var targetIndex = -1
    private var pendingIndex = -1
    private var dragging = false
    private var consumedLongPress = false

    private val enterEditor = Runnable {
        val tab = getTabAt(pendingIndex)
        if (tab?.tag !is CameraMode && tab?.tag !is FoldTabMode &&
            tab?.tag !is CameraShortcutMode) return@Runnable
        if (canEditModes?.invoke() == false) return@Runnable
        consumedLongPress = true
        tab.view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        // An edit gesture must not also click/switch the currently pressed mode.
        val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, downX, downY, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        setModeEditing(true)
    }

    fun setModeEditing(enabled: Boolean) {
        if (isEditingModes == enabled) return
        removeCallbacks(enterEditor)
        resetReorderVisuals()
        isEditingModes = enabled
        if (!enabled) { selectedEditorMode = null; onEditorSelectionChanged?.invoke(null) }
        cancelSettle()
        onEditModeChanged?.invoke(enabled)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isEditingModes) return dispatchEditorTouch(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                consumedLongPress = false
                downX = ev.x
                downY = ev.y
                downScrollX = scrollX
                pendingIndex = indexAtViewCoordinate(ev.x)
                removeCallbacks(enterEditor)
                if (pendingIndex >= 0) postDelayed(enterEditor, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop) {
                    removeCallbacks(enterEditor)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(enterEditor)
                pendingIndex = -1
                if (consumedLongPress) {
                    consumedLongPress = false
                    return true
                }
                if (ev.actionMasked == MotionEvent.ACTION_UP &&
                    getTabAt(indexAtViewCoordinate(ev.x))?.tag == ModeTabAction.MORE) {
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    onMoreClicked?.invoke()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun dispatchEditorTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downScrollX = scrollX
                draggedIndex = indexAtViewCoordinate(ev.x)
                targetIndex = draggedIndex
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedIndex >= 0 && (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop)) {
                    if (getTabAt(draggedIndex)?.tag !is ModeTabAction) {
                        dragging = true
                        updateReorder(ev.x)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val source = getTabAt(draggedIndex)?.tag
                val destination = getTabAt(targetIndex)?.tag
                val insertion = if (!dragging) pasteTargetAt(ev.x) else null
                val sourceIndex = draggedIndex
                val wasDragging = dragging
                resetReorderVisuals()
                if (!wasDragging && insertion != null && selectedEditorMode != null &&
                    selectedEditorMode != insertion.first) {
                    onTabPaste?.invoke(selectedEditorMode!!,insertion.first,insertion.second)
                    // Keep the mode selected after the strip rebuilds.
                    updateEditorHighlight()
                } else if (wasDragging && source != null && destination != null &&
                    source != destination && destination !is ModeTabAction) {
                    onTabReordered?.invoke(source, destination)
                } else if (!wasDragging) {
                    when (source) {
                        ModeTabAction.MORE -> onMoreClicked?.invoke()
                        is CameraMode, is FoldTabMode, is CameraShortcutMode -> {
                            val child = (getChildAt(0) as? ViewGroup)?.getChildAt(sourceIndex)
                            if (child != null && source != CameraMode.CAMERA) {
                                val contentX = ev.x + scrollX
                                val xInChild = contentX - child.left
                                val yInChild = ev.y - child.top
                                val custom = getTabAt(sourceIndex)?.customView as? ViewGroup
                                val closeView = if (custom?.childCount == 2) custom.getChildAt(1) else null
                                val inset = 5f * resources.displayMetrics.density
                                if (custom != null && closeView != null &&
                                    xInChild >= custom.left + closeView.left - inset &&
                                    xInChild <= custom.left + closeView.right + inset &&
                                    yInChild >= custom.top + closeView.top - inset &&
                                    yInChild <= custom.top + closeView.bottom + inset) {
                                    if (selectedEditorMode == source) selectEditorMode(null)
                                    onTabHidden?.invoke(source)
                                } else selectEditorMode(source)
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> resetReorderVisuals()
        }
        // Swallow editor gestures so TabLayout never switches a capture mode in edit mode.
        return true
    }

    private fun indexAtViewCoordinate(x: Float): Int {
        val container = getChildAt(0) as? ViewGroup ?: return -1
        val contentX = x + scrollX
        return (0 until tabCount).firstOrNull { index ->
            val child = container.getChildAt(index) ?: return@firstOrNull false
            contentX >= child.left && contentX < child.right
        } ?: -1
    }

    private fun updateReorder(x: Float) {
        val parent = getChildAt(0) as? ViewGroup ?: return
        val moving = parent.getChildAt(draggedIndex) ?: return
        // Dragging near the edge can reach modes that start outside the viewport.
        val edge = 32f * resources.displayMetrics.density
        val step = (10f * resources.displayMetrics.density).toInt()
        when {
            x < edge -> super.scrollTo((scrollX - step).coerceAtLeast(0), 0)
            x > width - edge -> super.scrollTo(scrollX + step, 0)
        }
        val dx = x - downX + scrollX - downScrollX
        val movingCenter = moving.left + moving.width / 2f + dx
        val target = (0 until tabCount).filter {
            getTabAt(it)?.tag !is ModeTabAction
        }.minByOrNull { index ->
            val child = parent.getChildAt(index)
            abs(child.left + child.width / 2f - movingCenter)
        } ?: return
        targetIndex = target
        for (i in 0 until tabCount) {
            val child = parent.getChildAt(i)
            val offset = when {
                i == draggedIndex -> dx
                draggedIndex < target && i in (draggedIndex + 1)..target -> -moving.width.toFloat()
                target < draggedIndex && i in target until draggedIndex -> moving.width.toFloat()
                else -> 0f
            }
            child.animate().cancel()
            child.translationX = offset
            child.translationZ = if (i == draggedIndex) 8f else 0f
        }
    }

    private fun resetReorderVisuals() {
        val parent = getChildAt(0) as? ViewGroup
        if (parent != null) for (i in 0 until parent.childCount) {
            parent.getChildAt(i).apply {
                animate().cancel()
                translationX = 0f
                translationZ = 0f
            }
        }
        dragging = false
        draggedIndex = -1
        targetIndex = -1
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        if (tabCount == 0) return
        if (isEditingModes) updateEditorHighlight()

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
            // Rebuilding can change the selected tab without changing a single
            // tab's width, clearing any stale offset from the previous mode.
            if (centerAfterRebuild && !isEditingModes) {
                centerAfterRebuild = false
                tabCenters.getOrNull(selectedTabPosition)?.let { scrollTo(it, 0) }
            } else {
                drawSelectionAtScroll(scrollX)
            }
            return
        }
        centerAfterRebuild = false

        // The tabs really moved, so any scroll still running is aimed at a stale position and
        // there is nothing to animate from.
        tabCenters.clear()
        tabCenters.addAll(centers)
        cancelSettle()
        setScrollPosition(selectedTabPosition, 0f, true)
    }

    override fun onScrollChanged(x: Int, y: Int, oldX: Int, oldY: Int) {
        super.onScrollChanged(x, y, oldX, oldY)
        if (isEditingModes) {
            onEditorScrollChanged?.invoke(x.toFloat() / editorScrollMax().coerceAtLeast(1))
            invalidate()
        }
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

        val target = tabCenters.getOrNull(tab.position) ?: run {
            onSettled?.run()
            return
        }

        val duration = settleDuration(abs(target - scrollX))
        if (duration == 0L || !ValueAnimator.areAnimatorsEnabled()) {
            scrollTo(target, 0)
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
        if (tabCenters.size != tabCount || isEditingModes) return

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

    private companion object {
        private const val SETTLE_DURATION_MS = 300L
    }
}
