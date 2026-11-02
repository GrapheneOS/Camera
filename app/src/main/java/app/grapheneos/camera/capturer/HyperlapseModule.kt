package app.grapheneos.camera.capturer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLExt
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Incremental JPEG-to-H.264 encoder: one frame enters, one frame leaves; no video speed-up. */
internal class HyperlapseModule(private val output: File, portrait: Boolean) : Closeable {
    private val frameWidth = if (portrait) HEIGHT else WIDTH
    private val frameHeight = if (portrait) WIDTH else HEIGHT
    private val encoder: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val muxer: MediaMuxer
    private val codecSurface: Surface
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var track = -1
    private var muxerStarted = false
    private var frames = 0L
    private var finished = false
    private var closed = false

    init {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, frameWidth, frameHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codecSurface = encoder.createInputSurface()
            encoder.start()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            initEgl()
        } catch (error: Exception) {
            try { encoder.release() } catch (_: Exception) { }
            throw error
        }
    }

    fun addPhoto(file: File) {
        check(!finished && !closed)
        val photo = decodePhoto(file, WIDTH * HEIGHT * 2) ?: error("Could not decode interval JPEG")
        try {
            drawFrame(photo)
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, frames * 1_000_000_000L / FRAME_RATE)
            check(EGL14.eglSwapBuffers(eglDisplay, eglSurface)) { "Encoder surface swap failed" }
            frames++
            drain(false)
        } finally {
            photo.recycle()
        }
    }

    fun complete() {
        if (finished) return
        check(frames > 0L)
        encoder.signalEndOfInputStream()
        drain(true)
        finished = true
    }

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY)
        val versions = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, versions, 0, versions, 1))
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0)
        eglContext = EGL14.eglCreateContext(
            eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, configs[0], codecSurface, intArrayOf(EGL14.EGL_NONE), 0
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    private fun drawFrame(bitmap: Bitmap) {
        GLES20.glViewport(0, 0, frameWidth, frameHeight)
        val v = compileShader(GLES20.GL_VERTEX_SHADER, """
            attribute vec4 position;
            attribute vec2 uv;
            varying vec2 texCoord;
            void main() { gl_Position = position; texCoord = uv; }
        """.trimIndent())
        val f = compileShader(GLES20.GL_FRAGMENT_SHADER, """
            precision mediump float;
            uniform sampler2D tex;
            varying vec2 texCoord;
            void main() { gl_FragColor = texture2D(tex, texCoord); }
        """.trimIndent())
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, v)
        GLES20.glAttachShader(program, f)
        GLES20.glLinkProgram(program)
        val link = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, link, 0)
        check(link[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(program) }
        GLES20.glUseProgram(program)

        val imageRatio = bitmap.width.toFloat() / bitmap.height
        val targetRatio = frameWidth.toFloat() / frameHeight
        val cropX = if (imageRatio > targetRatio) (1f - targetRatio / imageRatio) / 2f else 0f
        val cropY = if (imageRatio < targetRatio) (1f - imageRatio / targetRatio) / 2f else 0f
        val data = floatArrayOf(
            -1f, -1f, cropX, 1f - cropY,
             1f, -1f, 1f - cropX, 1f - cropY,
            -1f,  1f, cropX, cropY,
             1f,  1f, 1f - cropX, cropY
        )
        val vertices: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(data)
        vertices.position(0)
        val pos = GLES20.glGetAttribLocation(program, "position")
        val uv = GLES20.glGetAttribLocation(program, "uv")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, vertices)
        vertices.position(2)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices)
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        try {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "tex"), 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "OpenGL frame rendering failed" }
        } finally {
            GLES20.glDeleteTextures(1, texture, 0)
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteShader(v)
            GLES20.glDeleteShader(f)
        }
    }

    private fun drain(waitForEnd: Boolean) {
        val info = MediaCodec.BufferInfo()
        var retries = 0
        while (true) {
            val index = encoder.dequeueOutputBuffer(info, if (waitForEnd) 10_000L else 0L)
            when (index) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!waitForEnd) return
                    check(++retries < 3000) { "Video encoder timed out" }
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted)
                    track = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                else -> if (index >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0) {
                        check(muxerStarted)
                        encoder.getOutputBuffer(index)?.let { buffer ->
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                        } ?: error("Encoder output buffer missing")
                    }
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(index, false)
                    if (eos) return
                }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        try { encoder.stop() } catch (_: Exception) { }
        encoder.release()
        codecSurface.release()
        try { if (muxerStarted) muxer.stop() } finally { muxer.release() }
    }

    companion object {
        const val FRAME_RATE = 30
        const val WIDTH = 1280
        const val HEIGHT = 720
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}

/** Decode at bounded size and apply the still JPEG orientation before video composition. */
internal fun decodePhoto(file: File, targetPixels: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while ((bounds.outWidth / sample) * (bounds.outHeight / sample) > targetPixels * 2L) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    ) ?: return null
    val orientation = ExifInterface(file.absolutePath).getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
    )
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> matrix.postRotate(270f)
    }
    if (orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
        orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
        orientation == ExifInterface.ORIENTATION_TRANSVERSE ||
        orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL) matrix.postScale(-1f, 1f)
    if (matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        if (it !== bitmap) bitmap.recycle()
    }
}

internal fun isPortraitPhoto(file: File): Boolean {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val orientation = ExifInterface(file.absolutePath).getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
    )
    val rotated = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
        orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
        orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
        orientation == ExifInterface.ORIENTATION_TRANSVERSE
    return if (rotated) bounds.outWidth > bounds.outHeight else bounds.outHeight > bounds.outWidth
}
