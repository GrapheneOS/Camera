package com.hoko.ktblur.api

interface Render<T> {
    fun onDrawFrame(t: T)
}