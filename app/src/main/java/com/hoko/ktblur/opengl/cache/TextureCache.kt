package com.hoko.ktblur.opengl.cache

import com.hoko.ktblur.api.Texture
import com.hoko.ktblur.opengl.texture.TextureFactory
import com.hoko.ktblur.opengl.util.Size

object TextureCache {
    private val cachePool = object : CachePool<Size, Texture>() {
        override fun create(key: Size): Texture {
            return TextureFactory.create(key.width, key.height)
        }

        override fun checkHit(key: Size, value: Texture): Boolean {
            return key.width == value.width && key.height == value.height
        }

        override fun entryDeleted(removed: Texture) {
            removed.delete()
        }
    }

    fun getTexture(width: Int, height: Int): Texture {
        return cachePool.get(Size(width, height))
    }

    fun recycleTexture(texture: Texture) {
        cachePool.put(texture)
    }

    fun clear() {
        cachePool.evictAll()
    }

}