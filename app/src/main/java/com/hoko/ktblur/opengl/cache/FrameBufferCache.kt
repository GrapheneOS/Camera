package com.hoko.ktblur.opengl.cache

import com.hoko.ktblur.api.FrameBuffer
import com.hoko.ktblur.opengl.framebuffer.FrameBufferFactory

object FrameBufferCache {
    val sDisplayFrameBuffer: FrameBuffer by lazy { FrameBufferFactory.getDisplayFrameBuffer() }

    private val cachePool = object : CachePool<Any, FrameBuffer>() {
        override fun create(key: Any): FrameBuffer {
            return FrameBufferFactory.create()
        }

        override fun checkHit(key: Any, value: FrameBuffer): Boolean {
            return true
        }

        override fun entryDeleted(removed: FrameBuffer) {
            removed.delete()
        }
    }

    fun getFrameBuffer(): FrameBuffer {
        return cachePool.get(Any())
    }


    fun recycleFrameBuffer(frameBuffer: FrameBuffer) {
        cachePool.put(frameBuffer)
    }

    fun clear() {
        cachePool.evictAll()
    }

}