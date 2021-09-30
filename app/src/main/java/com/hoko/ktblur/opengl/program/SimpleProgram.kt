package com.hoko.ktblur.opengl.program

import android.opengl.GLES20
import android.util.Log
import com.hoko.ktblur.api.Program
import com.hoko.ktblur.util.checkGLError

class SimpleProgram(vertexShaderCode: String, fragmentShaderCode: String) : Program {
    companion object {
        private val TAG = SimpleProgram::class.java.simpleName
    }

    override var id: Int = 0

    init {
        create(vertexShaderCode, fragmentShaderCode)
    }

    override fun create(vertexShaderCode: String, fragmentShaderCode: String) {
        var vertexShader = 0
        var fragmentShader = 0
        try {
            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
            check(vertexShader != 0 && fragmentShader != 0)
            id = GLES20.glCreateProgram()
            if (id != 0) {
                GLES20.glAttachShader(id, vertexShader)
                GLES20.glAttachShader(id, fragmentShader)
                GLES20.glLinkProgram(id)
                checkGLError("Attach Shader")
                val linkStatus = IntArray(1)
                GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, linkStatus, 0)
                if (linkStatus[0] != 1) {
                    Log.e(TAG, "Failed to link program")
                    GLES20.glDeleteProgram(id)
                    id = 0
                }
            }
        } finally {
            GLES20.glDetachShader(id, vertexShader)
            GLES20.glDetachShader(id, fragmentShader)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
        }

    }


    private fun loadShader(type: Int, shaderCode: String): Int {
        var shader = GLES20.glCreateShader(type)
        if (shader != 0) {
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(TAG, "Failed to compile the shader")
                GLES20.glDeleteShader(shader)
                shader = 0
            }
        }
        return shader
    }


    override fun delete() {
        if (id != 0) {
            GLES20.glUseProgram(0)
            GLES20.glDeleteProgram(id)
        }
    }
}