package com.hoko.ktblur.processor

import android.graphics.Bitmap
import com.hoko.ktblur.opengl.offscreen.EglBuffer

class OpenGLBlurProcessor(builder: HokoBlurBuild) : AbstractBlurProcessor(builder) {

    private val eglBuffer: EglBuffer = EglBuffer()

    override fun realBlur(bitmap: Bitmap, parallel: Boolean): Bitmap {
        check(!bitmap.isRecycled)
        eglBuffer.blurMode = mode
        eglBuffer.blurRadius = radius
        return eglBuffer.getBlurBitmap(bitmap)
    }

    protected fun finalize() {
        eglBuffer.free()
    }

}