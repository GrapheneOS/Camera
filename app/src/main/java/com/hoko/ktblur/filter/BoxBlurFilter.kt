package com.hoko.ktblur.filter

import com.hoko.ktblur.params.Direction
import com.hoko.ktblur.util.clamp

internal object BoxBlurFilter {

    fun doBlur(data: IntArray, width: Int, height: Int, radius: Int, direction: Direction) {
        val result = IntArray(width * height)
        when (direction) {
            Direction.HORIZONTAL -> {
                blurHorizontal(data, result, width, height, radius)
                result.copyInto(data, 0, 0, result.size)
            }
            Direction.VERTICAL -> {
                blurVertical(data, result, width, height, radius)
                result.copyInto(data, 0, 0, result.size)
            }
            else -> {
                blurHorizontal(data, result, width, height, radius)
                blurVertical(result, data, width, height, radius)
            }
        }
    }

    private fun blurHorizontal(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
        val widthMinus1 = width - 1
        val tableSize = 2 * radius + 1
        // construct a query table from 0 to 255
        val divide = IntArray(256 * tableSize) { i ->
            (i / tableSize)
        }

        var inIndex = 0
        for (y in 0 until height) {
            var ta = 0
            var tr = 0
            var tg = 0
            var tb = 0 // ARGB

            for (i in -radius..radius) {
                val rgb = input[inIndex + i.clamp(0, width - 1)]
                ta += rgb shr 24 and 0xff
                tr += rgb shr 16 and 0xff
                tg += rgb shr 8 and 0xff
                tb += rgb and 0xff
            }

            val baseIndex = y * width
            for (x in 0 until width) { // Sliding window computation.
                output[baseIndex + x] = divide[ta] shl 24 or (divide[tr] shl 16) or (divide[tg] shl 8) or divide[tb]

                var i1 = x + radius + 1
                if (i1 > widthMinus1)
                    i1 = widthMinus1
                var i2 = x - radius
                if (i2 < 0)
                    i2 = 0
                val rgb1 = input[inIndex + i1]
                val rgb2 = input[inIndex + i2]

                ta += (rgb1 shr 24 and 0xff) - (rgb2 shr 24 and 0xff)
                tr += (rgb1 and 0xff0000) - (rgb2 and 0xff0000) shr 16
                tg += (rgb1 and 0xff00) - (rgb2 and 0xff00) shr 8
                tb += (rgb1 and 0xff) - (rgb2 and 0xff)
            }
            inIndex += width
        }
    }

    private fun blurVertical(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
        val heightMinus1 = height - 1
        val tableSize = 2 * radius + 1

        // construct a query table from 0 to 255
        val divide = IntArray(256 * tableSize) { i ->
            (i / tableSize)
        }

        for (x in 0 until width) {
            var ta = 0
            var tr = 0
            var tg = 0
            var tb = 0 // ARGB

            for (i in -radius..radius) {
                val rgb = input[x + i.clamp(0, height - 1) * width]
                ta += rgb shr 24 and 0xff
                tr += rgb shr 16 and 0xff
                tg += rgb shr 8 and 0xff
                tb += rgb and 0xff
            }

            for (y in 0 until height) { // Sliding window computation
                output[y * width + x] = divide[ta] shl 24 or (divide[tr] shl 16) or (divide[tg] shl 8) or divide[tb]

                var i1 = y + radius + 1
                if (i1 > heightMinus1)
                    i1 = heightMinus1
                var i2 = y - radius
                if (i2 < 0)
                    i2 = 0
                val rgb1 = input[x + i1 * width]
                val rgb2 = input[x + i2 * width]

                ta += (rgb1 shr 24 and 0xff) - (rgb2 shr 24 and 0xff)
                tr += (rgb1 and 0xff0000) - (rgb2 and 0xff0000) shr 16
                tg += (rgb1 and 0xff00) - (rgb2 and 0xff00) shr 8
                tb += (rgb1 and 0xff) - (rgb2 and 0xff)
            }
        }
    }

}
