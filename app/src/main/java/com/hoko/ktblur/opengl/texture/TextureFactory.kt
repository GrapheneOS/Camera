package com.hoko.ktblur.opengl.texture

import android.graphics.Bitmap
import com.hoko.ktblur.api.Texture

class TextureFactory {
    companion object {
        fun create(width: Int, height: Int): Texture {
            require(width > 0 && height > 0)
            return SimpleTexture(width, height)
        }

        fun create(bitmap: Bitmap): Texture {
            return BitmapTexture(bitmap)
        }
    }
}