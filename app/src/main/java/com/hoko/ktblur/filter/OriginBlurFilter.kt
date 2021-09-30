package com.hoko.ktblur.filter

import android.graphics.Bitmap
import android.util.Log
import com.hoko.ktblur.ext.replaceWithPixels
import com.hoko.ktblur.params.Direction
import com.hoko.ktblur.params.Mode
import java.lang.IllegalStateException

object OriginBlurFilter {
    private const val TAG = "OriginBlurFilter"
    private var nativeLoaded = false

    fun doBlur(mode: Mode, bitmap: Bitmap, radius: Int, cores: Int, index: Int, direction: Direction) {
        if (!nativeLoaded) {
            return
        }
        val w = bitmap.width
        val h = bitmap.height
        var x = 0
        var y = 0
        var deltaX = 0
        var deltaY = 0
        if (direction == Direction.HORIZONTAL) {
            deltaY = h / cores
            y = index * deltaY
            if (index == cores - 1) {
                deltaY = h - (cores - 1) * deltaY
            }
            deltaX = w
        } else if (direction == Direction.VERTICAL) {
            deltaX = w / cores
            x = index * deltaX
            if (index == cores - 1) {
                deltaX = w - (cores - 1) * deltaX
            }
            deltaY = h
        }
        val pixels = IntArray(deltaX * deltaY) { 0 }
        bitmap.getPixels(pixels, 0, deltaX, x, y, deltaX, deltaY)
        when (mode) {
            Mode.BOX -> BoxBlurFilter.doBlur(pixels, deltaX, deltaY, radius, direction)
            Mode.GAUSSIAN -> GaussianBlurFilter.doBlur(pixels, deltaX, deltaY, radius, direction)
            Mode.STACK -> StackBlurFilter.doBlur(pixels, deltaX, deltaY, radius, direction)
        }
        if (bitmap.isMutable) {
            bitmap.setPixels(pixels, 0, deltaX, x, y, deltaX, deltaY)
        } else {
            bitmap.replaceWithPixels(pixels, x, y, deltaX, deltaY)
        }
    }

    fun doFullBlur(mode: Mode, bitmap: Bitmap, radius: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h) { 0 }
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        when (mode) {
            Mode.BOX -> BoxBlurFilter.doBlur(pixels, w, h, radius, Direction.BOTH)
            Mode.STACK -> StackBlurFilter.doBlur(pixels, w, h, radius, Direction.BOTH)
            Mode.GAUSSIAN -> GaussianBlurFilter.doBlur(pixels, w, h, radius, Direction.BOTH)
        }
        if (bitmap.isMutable) {
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        } else {
            bitmap.replaceWithPixels(pixels, 0, 0, w, h)
        }
    }

    init {
        kotlin.runCatching {
            System.loadLibrary("hoko_ktblur")
            nativeLoaded = true
        }.onFailure { t ->
            Log.e(TAG, "failed to load so", t)
        }
    }
}