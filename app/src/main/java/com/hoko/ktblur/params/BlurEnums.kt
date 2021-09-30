package com.hoko.ktblur.params

enum class Direction {
    HORIZONTAL,
    VERTICAL,
    BOTH
}

enum class Mode {
    BOX,
    GAUSSIAN,
    STACK
}

enum class Scheme {
    RENDERSCRIPT,
    OPENGL,
    NATIVE,
    KOTLIN
}