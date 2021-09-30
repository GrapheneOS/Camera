package com.hoko.ktblur.task

import android.graphics.Bitmap

class BlurCallbackDSL : AsyncBlurTask.Callback {
    override var onSuccess: ((Bitmap?) -> Unit)? = null
    override var onFailed: ((Throwable?) -> Unit)? = null
    override fun onSuccess(onSuccess: ((Bitmap?) -> Unit)?) {
        this.onSuccess = onSuccess
    }

    override fun onFailed(onFailed: ((Throwable?) -> Unit)?) {
        this.onFailed = onFailed
    }
}