package com.hoko.ktblur.api

interface FrameBuffer {
    fun create()

    fun bindTexture(texture: Texture)

    fun bindSelf()

    fun delete()
}