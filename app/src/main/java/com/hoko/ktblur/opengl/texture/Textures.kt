package com.hoko.ktblur.opengl.texture

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import com.hoko.ktblur.api.Texture
import java.lang.ref.WeakReference
import java.nio.Buffer

sealed class AbstractTexture(override val width: Int, override val height: Int) : Texture {
    override var id: Int = 0

    override fun create() {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        id = textureIds[0]
        if (id != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST.toFloat())
            onTextureCreated()
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    abstract fun onTextureCreated()

    override fun delete() {
        if (id != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(id), 0)
        }
    }
}


class BitmapTexture(bitmap: Bitmap) : AbstractTexture(bitmap.width, bitmap.height){

    private val bitmapWeakRef = WeakReference<Bitmap>(bitmap)

    init {
        create()
    }

    override fun onTextureCreated() {
        check(width > 0 && height > 0)
        val bitmap = bitmapWeakRef.get()
        if (bitmap != null && !(bitmap.isRecycled)) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        }

    }
}

class SimpleTexture(width: Int, height: Int) : AbstractTexture(width, height) {

    init {
        create()
    }

    override fun onTextureCreated() {
        check(width > 0 && height > 0)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null as Buffer?
        )

    }
}

