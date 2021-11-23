package app.grapheneos.camera.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.view.MotionEvent

class SettingsFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private val dummyListener: OnInterceptTouchEventListener =
            DummyInterceptTouchEventListener()
    }

    private var mDisallowIntercept = false

    interface OnInterceptTouchEventListener {
        /**
         * If disallowIntercept is true the touch event can't be stolen and the return value
         * is ignored.
         * @see android.view.ViewGroup.onInterceptTouchEvent
         */
        fun onInterceptTouchEvent(
            view: SettingsFrameLayout?,
            ev: MotionEvent?,
            disallowIntercept: Boolean
        ): Boolean

        /**
         * @see android.view.View.onTouchEvent
         */
        fun onTouchEvent(view: SettingsFrameLayout?, event: MotionEvent?): Boolean
    }

    private class DummyInterceptTouchEventListener :
        OnInterceptTouchEventListener {
        override fun onInterceptTouchEvent(
            view: SettingsFrameLayout?,
            ev: MotionEvent?,
            disallowIntercept: Boolean
        ): Boolean {
            return false
        }

        override fun onTouchEvent(view: SettingsFrameLayout?, event: MotionEvent?): Boolean {
            return false
        }
    }

    private var mInterceptTouchEventListener = dummyListener

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        parent.requestDisallowInterceptTouchEvent(disallowIntercept)
        mDisallowIntercept = disallowIntercept
    }

    fun setOnInterceptTouchEventListener(interceptTouchEventListener: OnInterceptTouchEventListener?) {
        mInterceptTouchEventListener = interceptTouchEventListener ?: dummyListener
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        val stealTouchEvent =
            mInterceptTouchEventListener.onInterceptTouchEvent(this, ev, mDisallowIntercept)
        return stealTouchEvent && !mDisallowIntercept || super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val handled = mInterceptTouchEventListener.onTouchEvent(this, event)

        if (event?.action == MotionEvent.ACTION_UP) {
            performClick()
        }

        return handled || super.onTouchEvent(event)
    }
}