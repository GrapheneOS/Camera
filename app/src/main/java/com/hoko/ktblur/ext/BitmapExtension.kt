package com.hoko.ktblur.ext

import android.graphics.Bitmap
import android.graphics.Matrix

fun Bitmap.scale(factor: Float): Bitmap {
    if (factor == 1.0f) {
        return this
    }
    val ratio = 1f / factor
    val matrix = Matrix().apply {
        postScale(ratio, ratio)
    }
    return Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
}

fun Bitmap.translate(translateX: Int, translateY: Int): Bitmap {
    if (translateX == 0 && translateY == 0) {
        return this
    }
    return Bitmap.createBitmap(this, translateX, translateY, this.width - translateX, this.height - translateY)
}

external fun Bitmap.replaceWithPixels(pixels: IntArray, x: Int, y: Int, deltaX: Int, deltaY: Int)