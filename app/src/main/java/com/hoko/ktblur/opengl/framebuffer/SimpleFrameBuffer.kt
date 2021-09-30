package com.hoko.ktblur.opengl.framebuffer

import android.opengl.GLES20
import com.hoko.ktblur.api.FrameBuffer
import com.hoko.ktblur.api.Texture

class SimpleFrameBuffer(private var frameBufferId: Int = 0) : FrameBuffer{
    private lateinit var texture: Texture

    init {
        create()
    }

    override fun create() {
        val frameBufferIds = IntArray(1)
        GLES20.glGenFramebuffers(1, frameBufferIds, 0)
        frameBufferId = frameBufferIds[0]
    }

    override fun bindTexture(texture: Texture) {
        this.texture = texture.also {
            check(it.id != 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferId)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, it.id, 0
            )
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }
    }

    override fun bindSelf() {
        if (frameBufferId != 0) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferId)
        }
    }

    override fun delete() {
        if (frameBufferId != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(frameBufferId), 0)
        }
    }
}