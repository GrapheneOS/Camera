package com.hoko.ktblur

import android.content.Context
import com.hoko.ktblur.api.BlurBuild
import com.hoko.ktblur.processor.HokoBlurBuild

object HokoBlur {

    fun with(context: Context): BlurBuild = HokoBlurBuild(context.applicationContext)

}
