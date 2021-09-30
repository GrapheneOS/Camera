package com.hoko.ktblur.opengl.framebuffer

import android.opengl.GLES20
import com.hoko.ktblur.api.FrameBuffer

class FrameBufferFactory {
    companion object {
        fun create(): FrameBuffer {
            return SimpleFrameBuffer()
        }

        fun create(id: Int): FrameBuffer {
            return SimpleFrameBuffer(id)
        }

        /**
         * Get the bound FBO（On Screen）
         */
        fun getDisplayFrameBuffer(): FrameBuffer {
            val displayFbo = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, displayFbo, 0)
            return create(displayFbo[0])
        }
    }
}