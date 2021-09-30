package com.hoko.ktblur.util

import kotlin.math.max
import kotlin.math.min

fun Int.clamp(minValue: Int, maxValue: Int): Int = min(max(this, minValue), maxValue)