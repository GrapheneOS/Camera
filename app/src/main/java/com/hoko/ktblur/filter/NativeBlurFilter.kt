package com.hoko.ktblur.filter

import android.graphics.Bitmap
import android.util.Log
import com.hoko.ktblur.params.Direction
import com.hoko.ktblur.params.Mode

object NativeBlurFilter {
    private const val TAG = "OriginBlurFilter"
    private var nativeLoaded = false
    fun doBlur(mode: Mode, bitmap: Bitmap, radius: Int, cores: Int, index: Int, direction: Direction) {
        if (!nativeLoaded) {
            return
        }
        when (mode) {
            Mode.BOX -> nativeBoxBlur(bitmap, radius, cores, index, direction.ordinal)
            Mode.STACK -> nativeStackBlur(bitmap, radius, cores, index, direction.ordinal)
            Mode.GAUSSIAN -> nativeGaussianBlur(bitmap, radius, cores, index, direction.ordinal)
        }
    }

    fun doFullBlur(mode: Mode, bitmap: Bitmap, radius: Int) {
        doBlur(mode, bitmap, radius, 1, 0, Direction.HORIZONTAL)
        doBlur(mode, bitmap, radius, 1, 0, Direction.VERTICAL)
    }

    private external fun nativeBoxBlur(bitmap: Bitmap, radius: Int, cores: Int, index: Int, direction: Int)
    private external fun nativeStackBlur(bitmap: Bitmap, radius: Int, cores: Int, index: Int, direction: Int)
    private external fun nativeGaussianBlur(bitmap: Bitmap, radius: Int, cores: Int, index: Int, direction: Int)

    init {
        kotlin.runCatching {
            System.loadLibrary("hoko_ktblur")
            nativeLoaded = true
        }.onFailure { t ->
            Log.e(TAG, "failed to load so", t)
        }
    }

}