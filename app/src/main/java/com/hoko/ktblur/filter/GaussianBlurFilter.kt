package com.hoko.ktblur.filter

import com.hoko.ktblur.params.Direction
import com.hoko.ktblur.util.clamp
import kotlin.math.exp

internal object GaussianBlurFilter {

    fun doBlur(data: IntArray, width: Int, height: Int, radius: Int, direction: Direction) {
        val result = IntArray(width * height)
        val kernel = makeKernel(radius)
        when (direction) {
            Direction.HORIZONTAL -> {
                gaussianBlurHorizontal(kernel, data, result, width, height)
                result.copyInto(data, 0, 0, result.size)
            }
            Direction.VERTICAL -> {
                gaussianBlurVertical(kernel, data, result, width, height)
                result.copyInto(data, 0, 0, result.size)
            }
            else -> {
                gaussianBlurHorizontal(kernel, data, result, width, height)
                gaussianBlurVertical(kernel, result, data, width, height)
            }
        }
    }

    private fun gaussianBlurHorizontal(
        kernel: FloatArray, inPixels: IntArray,
        outPixels: IntArray, width: Int, height: Int) {
        val cols = kernel.size
        val cols2 = cols / 2
        for (y in 0 until height) {
            val ioffset = y * width
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (col in -cols2..cols2) {
                    val f = kernel[cols2 + col]
                    if (f != 0f) {
                        var ix = x + col
                        if (ix < 0) {
                            ix = 0
                        } else if (ix >= width) {
                            ix = width - 1
                        }
                        val rgb = inPixels[ioffset + ix]
                        r += f * (rgb shr 16 and 0xff).toLong().toFloat()
                        g += f * (rgb shr 8 and 0xff).toLong().toFloat()
                        b += f * (rgb and 0xff).toLong().toFloat()
                    }
                }
                val outIndex = ioffset + x
                val ia = inPixels[outIndex] shr 24 and 0xff
                val ir = (r + 0.5).toInt().clamp(0, 255)
                val ig = (g + 0.5).toInt().clamp(0, 255)
                val ib = (b + 0.5).toInt().clamp(0, 255)
                outPixels[outIndex] = (ia shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }
    }

    private fun gaussianBlurVertical(
        kernel: FloatArray, inPixels: IntArray,
        outPixels: IntArray, width: Int, height: Int) {
        val cols = kernel.size
        val cols2 = cols / 2
        for (x in 0 until width) {
            for (y in 0 until height) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (col in -cols2..cols2) {
                    val f = kernel[cols2 + col]
                    if (f != 0f) {
                        var iy = y + col
                        if (iy < 0) {
                            iy = 0
                        } else if (iy >= height) {
                            iy = height - 1
                        }
                        val rgb = inPixels[x + iy * width]
                        r += f * (rgb shr 16 and 0xff).toLong().toFloat()
                        g += f * (rgb shr 8 and 0xff).toLong().toFloat()
                        b += f * (rgb and 0xff).toLong().toFloat()
                    }
                }
                val outIndex = x + y * width
                val ia = inPixels[outIndex] shr 24 and 0xff
                val ir = (r + 0.5).toInt().clamp(0, 255)
                val ig = (g + 0.5).toInt().clamp(0, 255)
                val ib = (b + 0.5).toInt().clamp(0, 255)
                outPixels[outIndex] = (ia shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }
    }

    /**
     * Make a Gaussian blur kernel.
     */
    private fun makeKernel(r: Int): FloatArray {
        val rows = r * 2 + 1
        val matrix = FloatArray(rows)
        val sigma = (r + 1) / 2.0f
        val sigma22 = 2f * sigma * sigma
        var total = 0f
        for ((index, row) in (-r..r).withIndex()) {
            matrix[index] = (exp((-1 * (row * row) / sigma22).toDouble()) / sigma).toFloat()
            total += matrix[index]
        }
        for (i in 0 until rows) {
            matrix[i] /= total
        }
        return matrix
    }
}
