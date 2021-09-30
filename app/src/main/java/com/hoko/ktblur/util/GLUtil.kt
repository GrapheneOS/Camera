package com.hoko.ktblur.util

import android.opengl.GLES20
import android.util.Log

private const val TAG = "GLUtil"
/**
 * return true if no GL Error
 */
fun checkGLError(msg: String): Boolean {
    val error = GLES20.glGetError()
    if (error != 0) {
        Log.e(TAG, "checkGLError: error=$error, msg=$msg")
    }
    return error == 0
}