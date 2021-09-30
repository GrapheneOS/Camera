package com.hoko.ktblur.ext

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View

fun View.getBitmap(translateX: Int, translateY: Int, sampleFactor: Float): Bitmap {
    val scale = 1.0f / sampleFactor
    val downScaledWidth = ((this.width - translateX) * scale).toInt()
    val downScaledHeight = ((this.height - translateY) * scale).toInt()
    val bitmap = Bitmap.createBitmap(downScaledWidth, downScaledHeight, Bitmap.Config.ARGB_8888)
    (background as? ColorDrawable)?.let {
        bitmap.eraseColor(it.color)
    } ?: bitmap.eraseColor(Color.TRANSPARENT)
    val canvas = Canvas(bitmap).apply {
        translate((-(translateX * scale).toInt()).toFloat(), (-(translateY * scale).toInt()).toFloat())
        if (sampleFactor > 1.0f) {
            scale(scale, scale)
        }
    }
    draw(canvas)
    return bitmap
}