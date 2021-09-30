package com.hoko.ktblur.processor

import com.hoko.ktblur.api.BlurProcessor
import com.hoko.ktblur.params.Scheme

class BlurProcessorFactory {
    companion object {
        fun getBlurProcessor(scheme: Scheme, builder: HokoBlurBuild): BlurProcessor {
            return when(scheme) {
                Scheme.OPENGL -> OpenGLBlurProcessor(builder)
                Scheme.NATIVE -> NativeBlurProcessor(builder)
                Scheme.KOTLIN -> OriginBlurProcessor(builder)
                else -> throw NotImplementedError()
            }
        }
    }
}