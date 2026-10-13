package com.cleanlr.engine

import android.content.Context
import android.opengl.GLES30
import android.util.Log

object ShaderHelper {

    private const val TAG = "ShaderHelper"

    fun loadAsset(context: Context, assetPath: String): String {
        return context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }

    fun createProgram(context: Context, vertPath: String, fragPath: String): Int {
        val vertSource = loadAsset(context, vertPath)
        val fragSource = loadAsset(context, fragPath)
        return createProgramFromSource(vertSource, fragSource)
    }

    fun createProgramFromSource(vertSrc: String, fragSrc: String): Int {
        val vertShader = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc)
        val fragShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        if (vertShader == 0 || fragShader == 0) return 0

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertShader)
        GLES30.glAttachShader(program, fragShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "GL Link error: " + GLES30.glGetProgramInfoLog(program))
            GLES30.glDeleteProgram(program)
            GLES30.glDeleteShader(vertShader)
            GLES30.glDeleteShader(fragShader)
            return 0
        }

        GLES30.glDeleteShader(vertShader)
        GLES30.glDeleteShader(fragShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "GL Compile error ($type): " + GLES30.glGetShaderInfoLog(shader))
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
