package com.hoko.ktblur.processor

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import com.hoko.ktblur.api.BlurBuild
import com.hoko.ktblur.api.BlurProcessor
import com.hoko.ktblur.api.BlurResultDispatcher
import com.hoko.ktblur.params.Mode
import com.hoko.ktblur.params.Scheme
import com.hoko.ktblur.task.AndroidBlurResultDispatcher
import com.hoko.ktblur.task.AsyncBlurTask
import kotlinx.coroutines.Job

class HokoBlurBuild(var context: Context) : BlurBuild {
    internal var radius: Int = 10
    internal var mode: Mode = Mode.STACK
    internal var scheme: Scheme = Scheme.NATIVE
    internal var sampleFactor: Float = 5.0f
    internal var forceCopy: Boolean = false
    internal var needUpscale: Boolean = true
    internal var translateX: Int = 0
    internal var translateY: Int = 0
    internal var dispatcher: BlurResultDispatcher = AndroidBlurResultDispatcher.MAIN_THREAD_DISPATCHER

    override fun context(context: Context): BlurBuild {
        this.context = context
        return this
    }

    override fun mode(mode: Mode): BlurBuild {
        this.mode = mode
        return this
    }

    override fun scheme(scheme: Scheme): BlurBuild {
        this.scheme = scheme
        return this
    }

    override fun radius(radius: Int): BlurBuild {
        this.radius = radius
        return this
    }

    override fun sampleFactor(sampleFactor: Float): BlurBuild {
        this.sampleFactor = sampleFactor
        return this
    }

    override fun forceCopy(forceCopy: Boolean): BlurBuild {
        this.forceCopy = forceCopy
        return this
    }

    override fun needUpscale(needUpscale: Boolean): BlurBuild {
        this.needUpscale = needUpscale
        return this
    }

    override fun translateX(translateX: Int): BlurBuild {
        this.translateX = translateX
        return this
    }

    override fun translateY(translateY: Int): BlurBuild {
        this.translateY = translateY
        return this
    }

    override fun dispatcher(dispatcher: BlurResultDispatcher): BlurBuild {
        this.dispatcher = dispatcher
        return this
    }

    override fun processor(): BlurProcessor {
        return BlurProcessorFactory.getBlurProcessor(scheme, this)
    }

    override fun blur(bitmap: Bitmap): Bitmap {
        return processor().blur(bitmap)
    }

    override fun blur(view: View): Bitmap {
        return processor().blur(view)
    }

    override fun asyncBlur(bitmap: Bitmap, block: AsyncBlurTask.Callback.() -> Unit): Job {
        return processor().asyncBlur(bitmap, block)
    }

    override fun asyncBlur(view: View, block: AsyncBlurTask.Callback.() -> Unit): Job {
        return processor().asyncBlur(view, block)
    }
}