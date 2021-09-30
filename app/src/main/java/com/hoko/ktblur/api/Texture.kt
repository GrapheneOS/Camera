package com.hoko.ktblur.api

interface Texture {
    val width: Int

    val height: Int

    val id: Int

    fun create()

    fun delete()
}