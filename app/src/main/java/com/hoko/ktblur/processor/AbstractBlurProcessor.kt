package com.hoko.ktblur.processor

import android.graphics.Bitmap
import android.view.View
import com.hoko.ktblur.api.BlurProcessor
import com.hoko.ktblur.api.BlurResultDispatcher
import com.hoko.ktblur.ext.getBitmap
import com.hoko.ktblur.ext.scale
import com.hoko.ktblur.ext.translate
import com.hoko.ktblur.params.Mode
import com.hoko.ktblur.params.Scheme
import com.hoko.ktblur.task.AsyncBlurTask
import com.hoko.ktblur.task.BitmapAsyncBlurTask
import com.hoko.ktblur.task.BlurTaskManager
import com.hoko.ktblur.task.ViewAsyncBlurTask
import kotlinx.coroutines.Job

abstract class AbstractBlurProcessor(builder: HokoBlurBuild) : BlurProcessor {

    override var radius: Int = builder.radius
    override var mode: Mode = builder.mode
    override var scheme: Scheme = builder.scheme
    override var sampleFactor: Float = builder.sampleFactor
    override var forceCopy: Boolean = builder.forceCopy
    override var needUpscale: Boolean = builder.needUpscale
    override var translateX: Int = builder.translateX
    override var translateY: Int = builder.translateY
    override var dispatcher: BlurResultDispatcher = builder.dispatcher

    override fun blur(bitmap: Bitmap): Bitmap {
        return blur(bitmap, true)
    }

    override fun blur(view: View): Bitmap {
        return realBlur(view.getBitmap(translateX, translateY, sampleFactor), true)
            .scale(if (needUpscale) (1.0f / sampleFactor) else 1.0f)
    }

    private fun blur(bitmap: Bitmap, parallel: Boolean): Bitmap {
        checkParams()
        val inBitmap = if (forceCopy) {
            bitmap.copy(bitmap.config, true)
        } else {
            bitmap
        }
        val scaledBitmap = inBitmap.translate(translateX, translateY).scale(sampleFactor)
        return realBlur(scaledBitmap, parallel).scale(if (needUpscale) (1.0f / sampleFactor) else 1.0f)
    }

    protected abstract fun realBlur(bitmap: Bitmap, parallel: Boolean): Bitmap

    override fun asyncBlur(bitmap: Bitmap, block: AsyncBlurTask.Callback.() -> Unit): Job {
        return BlurTaskManager.submit(BitmapAsyncBlurTask(this, block, bitmap, dispatcher).suspendAction())
    }

    override fun asyncBlur(view: View, block: AsyncBlurTask.Callback.() -> Unit): Job {
        return BlurTaskManager.submit(ViewAsyncBlurTask(this, block, view, dispatcher).suspendAction())
    }

    private fun checkParams() {
        radius = if (radius <= 0) 1 else radius
        sampleFactor = if (sampleFactor < 1.0f) 1.0f else sampleFactor
    }


}