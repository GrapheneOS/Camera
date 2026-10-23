package app.grapheneos.camera.portrait

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import app.grapheneos.camera.R
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Offline portrait processing with local ONNX models and Gaussian bokeh. */
object PortraitEngine {
    const val ENGINE_NAME = "PortraitEngine"
    const val PORTRAIT_REVISION = "1.6"
    private const val TAG = "FoldPortraitAI"
    private const val MATTE = "modnet_photographic_portrait_matting.onnx"
    private const val DEPTH = "depth_anything_v2_small_quantized.onnx"
    // Optional local semantic segmentation for cats and dogs.
    private const val PET_MODEL = "lraspp_voc_pets_320.onnx"
    private const val PET_MANIFEST = "lraspp_voc_pets_320.onnx.sha256"
    private const val PET_SIZE = 320
    private const val PET_CLASSES = 21
    private const val CAT_CLASS = 8
    private const val DOG_CLASS = 12
    private const val PERSON_CLASS = 15
    private const val ANALYSIS_MAX_EDGE = 2048
    // Composite the full-resolution image in tiles to limit memory use.
    private const val COMPOSITOR_MAX_EDGE = 1280
    private const val GAUSSIAN_MAX_EDGE = 960
    private const val FULL_RES_TILE_ROWS = 64
    private const val MODNET_SHA = "07c308cf0fc7e6e8b2065a12ed7fc07e1de8febb7dc7839d7b7f15dd66584df9"
    private const val DEPTH_SHA = "fcf51f1b230362b28690bb9d1809bf0431f29cad20534e3f589bd7285547f20d"
    @Volatile var blurPercent: Int = 55
        private set
    fun setBlurPercent(value: Int) { blurPercent = value.coerceIn(0, 100) }

    private val photoJobs = AtomicInteger(0)
    // Track batch progress on the single-threaded rendering queue.
    private val queueLock = Any()
    private var queueTotal = 0
    private var queueCompleted = 0
    private var queueActive = false
    private var queueProgress = 0

    private fun notifyQueue(context: Context, progress: Int) = synchronized(queueLock) {
        if (!queueActive || queueCompleted >= queueTotal) return@synchronized
        val next = progress.coerceIn(0, 100)
        if (next < queueProgress) return@synchronized
        if (next == queueProgress && next != 0) return@synchronized
        queueProgress = next
        PortraitRenderService.updateProgress(context, queueCompleted + 1, queueTotal, next)
    }

    private val previewBusy = AtomicBoolean(false)
    private val previewWorker = Executors.newSingleThreadExecutor { job ->
        Thread(job, "FoldPortraitLivePreview").apply { priority = Thread.NORM_PRIORITY - 2 }
    }
    // Separate model pipelines for still photo; bounded to one worker per model.
    private val depthWorker = Executors.newSingleThreadExecutor { job ->
        Thread(job, "FoldPortraitDepth").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val sessionInitLock = Any()

    private val worker = Executors.newSingleThreadExecutor { job ->
        Thread(job, "FoldPortraitInference").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    // Reuse verified model sessions within the process.
    private val environment by lazy { OrtEnvironment.getEnvironment() }
    private var matteSession: OrtSession? = null
    private var depthSession: OrtSession? = null
    private var petSessionCache: OrtSession? = null
    private var petSessionAttempted = false

    private fun session(context: Context, isDepth: Boolean): OrtSession = synchronized(sessionInitLock) {
        val existing = if (isDepth) depthSession else matteSession
        if (existing != null) return@synchronized existing
        val file = if (isDepth) ensureModel(context, DEPTH, DEPTH_SHA)
                   else ensureModel(context, MATTE, MODNET_SHA)
        val created = OrtSession.SessionOptions().use { opts ->
            opts.setIntraOpNumThreads(2)
            opts.setInterOpNumThreads(1)
            environment.createSession(file.absolutePath, opts)
        }
        if (isDepth) depthSession = created else matteSession = created
        Log.i(TAG, "ONNX ${if (isDepth) "depth" else "matte"} session initialized once")
        created
    }

    /** Missing pet assets never break human portraits. A failed ONNX load is logged once. */
    private fun optionalPetSession(context: Context): OrtSession? = synchronized(sessionInitLock) {
        if (petSessionAttempted) return@synchronized petSessionCache
        petSessionAttempted = true
        try {
            val declaredHash = context.assets.open("portrait/$PET_MANIFEST").bufferedReader().use {
                it.readText().trim().split(Regex("\\s+")).first()
            }
            require(Regex("[a-f0-9]{64}").matches(declaredHash)) { "Invalid pet model manifest" }
            // ensureModel verifies the ONNX bytes before opening a model session.
            val file = ensureModel(context, PET_MODEL, declaredHash)
            petSessionCache = OrtSession.SessionOptions().use { opts ->
                opts.setIntraOpNumThreads(2)
                opts.setInterOpNumThreads(1)
                environment.createSession(file.absolutePath, opts)
            }
            Log.i(TAG, "offline pet segmentation initialized")
        } catch (e: Exception) {
            Log.w(TAG, "Pet model not installed or invalid; leaving human portraits unchanged", e)
        }
        petSessionCache
    }

    private data class PetSegmentation(val mask: Plane, val includesPerson: Boolean)

    /**
     * TorchVision LR-ASPP / Pascal VOC has classes 8=cat, 12=dog, 15=person.
     * Input is a fixed ImageNet-normalized NCHW 320x320 float tensor.
     * The ONNX export returns semantic logits [1,21,320,320].
     * Unlike MODNet these are class masks, NOT fine-grained fur matting.
     */
    private fun inferPets(context: Context, source: Bitmap): PetSegmentation? {
        val session = optionalPetSession(context) ?: return null
        val pixels = sourcePixels(source, PET_SIZE, PET_SIZE)
        val area = PET_SIZE * PET_SIZE
        val mean = floatArrayOf(.485f, .456f, .406f)
        val std = floatArrayOf(.229f, .224f, .225f)
        val buffer = ByteBuffer.allocateDirect(area * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (c in 0 until 3) for (i in 0 until area) {
            val rgb = pixels[i]
            val v = when (c) {
                0 -> Color.red(rgb)
                1 -> Color.green(rgb)
                else -> Color.blue(rgb)
            } / 255f
            buffer.put((v - mean[c]) / std[c])
        }
        buffer.rewind()
        val logits = OnnxTensor.createTensor(environment, buffer,
            longArrayOf(1, 3, PET_SIZE.toLong(), PET_SIZE.toLong())).use { tensor ->
            synchronized(session) {
                session.run(mapOf(session.inputNames.first() to tensor)).use { results ->
                    val output = results[0] as OnnxTensor
                    val shape = output.info.shape
                    require(shape.contentEquals(longArrayOf(1, PET_CLASSES.toLong(),
                        PET_SIZE.toLong(), PET_SIZE.toLong()))) {
                        "Unexpected pet model output shape: ${shape.contentToString()}"
                    }
                    FloatArray(PET_CLASSES * area).also { arr ->
                        output.floatBuffer.apply { rewind(); get(arr) }
                        require(arr.all { it.isFinite() }) { "Non-finite pet model output" }
                    }
                }
            }
        }
        val mask = FloatArray(area)
        var confidentPetPixels = 0
        var personPixels = 0
        for (i in 0 until area) {
            val pet = max(logits[CAT_CLASS * area + i], logits[DOG_CLASS * area + i])
            var other = Float.NEGATIVE_INFINITY
            val person = logits[PERSON_CLASS * area + i]
            for (cls in 0 until PET_CLASSES) {
                if (cls == CAT_CLASS || cls == DOG_CLASS) continue
                val value = logits[cls * area + i]
                if (value > other) other = value
            }
            // Conservative margin keeps unrelated objects from being mistaken for pets.
            val margin = pet - other
            mask[i] = ((margin + .45f) / 1.45f).coerceIn(0f, 1f)
            if (margin > .45f) confidentPetPixels++
            if (person > pet + .45f && person >= other - .0001f) personPixels++
        }
        if (confidentPetPixels < area / 150 || confidentPetPixels > area * 9 / 10) {
            return null
        }
        Log.i(TAG, "pet pixels=$confidentPetPixels/$area personPixels=$personPixels")
        return PetSegmentation(Plane(mask, PET_SIZE, PET_SIZE), personPixels > area / 150)
    }

    private fun subjectMatte(input: Bitmap, originalPerson: Plane, context: Context): Plane {
        val pets = try { inferPets(context, input) } catch (e: Exception) {
            Log.w(TAG, "Pet detection failed: keep the original MODNet subject", e)
            null
        } ?: return originalPerson
        val w = originalPerson.width
        val h = originalPerson.height
        val combined = FloatArray(w * h)
        for (i in combined.indices) {
            val x = i % w
            val y = i / w
            val pet = pets.mask.atPixel(x, y, w, h).coerceIn(0f, 1f)
            val human = if (pets.includesPerson) originalPerson.data[i] else 0f
            combined[i] = max(pet, human)
        }
        return refineMatte(input, cleanMatte(Plane(combined, w, h)))
    }

    /** Immutable matte data only: never a frozen camera frame. */
    data class LiveSubjectMask(
        val width: Int,
        val height: Int,
        val subjectAlphaArgb: IntArray,
    )

    /** One inference and one compositor frame max in flight; still photos win. */
    private val liveRenderBusy = AtomicBoolean(false)
    private val liveRenderCount = AtomicInteger(0)
    private val liveRenderWorker = Executors.newSingleThreadExecutor { job ->
        Thread(job, "FoldPortraitLiveCompose").apply { priority = Thread.NORM_PRIORITY - 2 }
    }

    fun canSchedulePreview(): Boolean = photoJobs.get() == 0 && !previewBusy.get()
    fun isPhotoProcessing(): Boolean = photoJobs.get() > 0

    /** Runs only subject segmentation. No depth model, no 12MP work, no frame holding. */
    fun requestLiveSubjectMask(context: Context, previewBitmap: Bitmap,
                               callback: (LiveSubjectMask?) -> Unit): Boolean {
        // read-only asynchronous lens discovery, once per app process.
        if (photoJobs.get() > 0 || !previewBusy.compareAndSet(false, true)) {
            previewBitmap.recycle()
            return false
        }
        val app = context.applicationContext
        previewWorker.execute {
            val started = SystemClock.elapsedRealtime()
            var result: LiveSubjectMask? = null
            try {
                if (photoJobs.get() == 0) {
                    // Preview-only fast path. Do not change full-resolution stills.
                    val person = cleanMatte(infer(app, previewBitmap,
                        isDepth = false, livePreview = true))
                    if (photoJobs.get() == 0) {
                        // Combine person matting with optional pet segmentation when available.
                        val matte = subjectMatte(previewBitmap, person, app)
                        val w = previewBitmap.width
                        val h = previewBitmap.height
                        val alpha = IntArray(w * h)
                        for (y in 0 until h) for (x in 0 until w) {
                            val value = (255 * matte.atPixel(x, y, w, h)
                                .coerceIn(0f, 1f)).roundToInt()
                            // PorterDuff DST_OUT reads the alpha, RGB is irrelevant.
                            alpha[y * w + x] = value shl 24
                        }
                        val subjectPixels = alpha.count { (it ushr 24) > 127 }
                        if (subjectPixels > alpha.size / 250 && subjectPixels < alpha.size * 9 / 10) {
                            result = LiveSubjectMask(w, h, alpha)
                            Log.i(TAG, "preview subject mask ready: ${w}x${h}")
                        } else {
                            Log.i(TAG, "no usable live subject; showing raw camera stream")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "mask unavailable; raw preview stays live", e)
            } finally {
                previewBitmap.recycle()
                previewBusy.set(false)
                Log.i(TAG, "mask inference: ${SystemClock.elapsedRealtime() - started}ms")
            }
            val delivery = result
            Handler(Looper.getMainLooper()).post { callback(delivery) }
        }
        return true
    }

    /**
     * Only the BACKGROUND becomes a semi-transparent overlay; the subject is a
     * genuine, moving PreviewView stream. Slider changes never trigger inference.
     * On purpose this is small PreviewView snapshots, not a second CameraX use case.
     */
    fun requestLiveBackground(frame: Bitmap, mask: LiveSubjectMask, strength: Int,
                              shiftX: Int, shiftY: Int,
                              callback: (Bitmap?) -> Unit): Boolean {
        if (photoJobs.get() > 0 || !liveRenderBusy.compareAndSet(false, true)) {
            // Rejected frames remain owned by the controller.
            return false
        }
        liveRenderWorker.execute {
            val started = SystemClock.elapsedRealtime()
            var output: Bitmap? = null
            try {
                if (photoJobs.get() == 0 && strength > 0) {
                    val w = frame.width
                    val h = frame.height
                    // Stronger slider -> bigger bokeh kernel. Rendering stays small.
                    val divider = (2 + strength * 20 / 100).coerceIn(2, 22)
                    val down = Bitmap.createScaledBitmap(frame, max(2, w / divider),
                        max(2, h / divider), true)
                    try {
                        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = Canvas(out)
                            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                            canvas.drawBitmap(down, null, Rect(0, 0, w, h), paint)
                            val cutout = Bitmap.createBitmap(mask.subjectAlphaArgb,
                                mask.width, mask.height, Bitmap.Config.ARGB_8888)
                            try {
                                val erase = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                                }
                                canvas.drawBitmap(cutout, null,
                                    Rect(shiftX, shiftY, w + shiftX, h + shiftY), erase)
                                erase.xfermode = null
                            } finally {
                                cutout.recycle()
                            }
                            output = out
                        } catch (t: Throwable) {
                            out.recycle()
                            throw t
                        }
                    } finally {
                        if (down !== frame) down.recycle()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "background unavailable; raw preview stays live", t)
            } finally {
                frame.recycle()
                liveRenderBusy.set(false)
                if (liveRenderCount.incrementAndGet() % 20 == 0) {
                    Log.i(TAG, "overlay render: ${SystemClock.elapsedRealtime() - started}ms")
                }
            }
            val delivery = output
            Handler(Looper.getMainLooper()).post { callback(delivery) }
        }
        return true
    }

    // Tabulated exponential replaces repeated exp() calls in matte refinement.
    private const val EXP_STEPS = 8192
    private const val EXP_RANGE = 20f
    private val expNeg = FloatArray(EXP_STEPS + 1) { i ->
        exp(-(i * EXP_RANGE / EXP_STEPS).toDouble()).toFloat()
    }
    private fun fastExpMinus(x: Float): Float {
        if (x >= EXP_RANGE) return 0f
        if (x <= 0f) return 1f
        val scaled = x * (EXP_STEPS / EXP_RANGE)
        val i = scaled.toInt()
        val t = scaled - i
        return expNeg[i] + (expNeg[i + 1] - expNeg[i]) * t
    }

    fun enqueue(context: Context, original: Uri, strength: Int = blurPercent) {
        val app = context.applicationContext
        // Portrait AI is single-camera on every supported device.
        // Protect lengthy offline rendering with a foreground service.
        PortraitRenderService.begin(app)
        synchronized(queueLock) {
            if (queueTotal == queueCompleted) {
                queueTotal = 0
                queueCompleted = 0
            }
            queueTotal++
            photoJobs.incrementAndGet()
            // Refresh "1/5" if additional captures arrive while 1/3 is processing.
            if (queueActive) {
                PortraitRenderService.updateProgress(app, queueCompleted + 1, queueTotal, queueProgress)
            }
        }
        worker.execute {
            synchronized(queueLock) {
                queueActive = true
                queueProgress = 0
                PortraitRenderService.updateProgress(app, queueCompleted + 1, queueTotal, 0)
            }
            val start = SystemClock.elapsedRealtime()
            var last = start
            fun stage(name: String) {
                val now = SystemClock.elapsedRealtime()
                Log.i(TAG, "timing $name=${now-last}ms total=${now-start}ms")
                last = now
            }
            var analysisImage: Bitmap? = null
            var fullImage: Bitmap? = null
            var portrait: Bitmap? = null
            try {
                val full = decodeOriginal(app, original)
                val analysis = decodeAnalysisBitmap(app, original)
                fullImage = full
                analysisImage = analysis
                stage("decode")
                notifyQueue(app, 5)
                // Compute MODNet and Depth Anything concurrently on the small analysis bitmap.
                val depthFuture = depthWorker.submit(Callable { infer(app, analysis, isDepth = true) })
                val rawMatte: Plane
                try {
                    rawMatte = infer(app, analysis, isDepth = false)
                } catch (t: Throwable) {
                    depthFuture.cancel(true)
                    throw t
                }
                stage("modnet")
                notifyQueue(app, 22)
                val humanMatte = refineMatte(analysis, cleanMatte(rawMatte))
                val matte = subjectMatte(analysis, humanMatte, app)
                stage("matte_and_pets")
                notifyQueue(app, 38)
                // A failed depth inference must not discard a correctly captured JPEG.
                // A flat relative-depth plane triggers the existing uniform-bokeh
                // fallback in composeWithStrength. Never claim metric depth.
                val rawDepth = try {
                    depthFuture.get()
                } catch (error: Exception) {
                    Log.w(TAG, "depth model failed; using single-camera uniform-bokeh fallback", error)
                    Plane(FloatArray(64 * 64) { .5f }, 64, 64)
                }
                stage("depth_anything")
                notifyQueue(app, 47)
                val depth = refineDepth(analysis, rawDepth)
                stage("depth_refine")
                notifyQueue(app, 55)
                val finished = composeWithStrength(full, matte, depth, strength) { renderingPercent ->
                    notifyQueue(app, 55 + renderingPercent * 42 / 100)
                }
                portrait = finished
                stage("compose")
                notifyQueue(app, 98)
                saveNewImage(app, finished)
                stage("jpeg_save")
                notifyQueue(app, 100)
                Log.i(TAG, "Portrait saved in ${SystemClock.elapsedRealtime() - start}ms; " +
                    "output=${full.width}x${full.height} analysis=${analysis.width}x${analysis.height}")
                notifyUser(app, app.getString(R.string.portrait_saved))
            } catch (t: Throwable) {
                Log.e(TAG, "AI post-processing failed; original JPEG is safe", t)
                notifyUser(app, app.getString(R.string.portrait_failed))
            } finally {
                portrait?.recycle()
                analysisImage?.recycle()
                fullImage?.recycle()
                synchronized(queueLock) {
                    queueCompleted++
                    photoJobs.decrementAndGet()
                    queueActive = false
                    if (queueCompleted >= queueTotal) {
                        // A new burst starts its numbering from 1 again.
                        queueTotal = 0
                        queueCompleted = 0
                        queueProgress = 0
                    } else {
                        // Next job is queued; it will begin on the same worker.
                        queueProgress = 0
                        PortraitRenderService.updateProgress(app, queueCompleted + 1, queueTotal, 0)
                    }
                }
                PortraitRenderService.finishIfIdle(app)
            }
        }
    }

    internal fun hasPendingPortraits(): Boolean = photoJobs.get() > 0

    private fun notifyUser(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun decodeOriginal(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            require(info.size.width > 0 && info.size.height > 0) { "Ongeldige beeldafmetingen" }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        if (decoded.config == Bitmap.Config.ARGB_8888) return decoded
        val copied = decoded.copy(Bitmap.Config.ARGB_8888, false)
        decoded.recycle()
        return copied
    }

    private fun decodeAnalysisBitmap(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            require(w > 0 && h > 0) { "Ongeldige beeldafmetingen" }
            val scale = min(1f, ANALYSIS_MAX_EDGE.toFloat() / max(w, h))
            decoder.setTargetSize(max(2, (w * scale).roundToInt()), max(2, (h * scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        if (decoded.config == Bitmap.Config.ARGB_8888) return decoded
        val copied = decoded.copy(Bitmap.Config.ARGB_8888, false)
        decoded.recycle()
        return copied
    }

    private data class Plane(val data: FloatArray, val width: Int, val height: Int) {
        fun at(x: Float, y: Float): Float {
            val fx = x.coerceIn(0f, (width - 1).toFloat())
            val fy = y.coerceIn(0f, (height - 1).toFloat())
            val x0 = fx.toInt(); val y0 = fy.toInt()
            val x1 = min(x0 + 1, width - 1); val y1 = min(y0 + 1, height - 1)
            val u = fx - x0; val v = fy - y0
            val p = y0 * width + x0; val q = y0 * width + x1
            val r = y1 * width + x0; val s = y1 * width + x1
            return ((data[p] * (1 - u) + data[q] * u) * (1 - v) +
                (data[r] * (1 - u) + data[s] * u) * v)
        }
        fun atPixel(x: Int, y: Int, outW: Int, outH: Int) = at(
            (x + .5f) * width / outW - .5f,
            (y + .5f) * height / outH - .5f)
    }


    private data class ColorPlane(val data: IntArray, val width: Int, val height: Int) {
        fun at(x: Float, y: Float): Int {
            val fx = x.coerceIn(0f, (width - 1).toFloat())
            val fy = y.coerceIn(0f, (height - 1).toFloat())
            val x0 = fx.toInt(); val y0 = fy.toInt()
            val x1 = min(x0 + 1, width - 1); val y1 = min(y0 + 1, height - 1)
            val u = fx - x0; val v = fy - y0
            val p = data[y0 * width + x0]
            val q = data[y0 * width + x1]
            val r = data[y1 * width + x0]
            val t = data[y1 * width + x1]
            fun ch(c00: Int, c10: Int, c01: Int, c11: Int): Int {
                val top = c00 * (1f - u) + c10 * u
                val bottom = c01 * (1f - u) + c11 * u
                return (top * (1f - v) + bottom * v).roundToInt().coerceIn(0, 255)
            }
            val red = ch(Color.red(p), Color.red(q), Color.red(r), Color.red(t))
            val green = ch(Color.green(p), Color.green(q), Color.green(r), Color.green(t))
            val blue = ch(Color.blue(p), Color.blue(q), Color.blue(r), Color.blue(t))
            return Color.rgb(red, green, blue)
        }
        fun atPixel(x: Int, y: Int, outW: Int, outH: Int) = at(
            (x + .5f) * width / outW - .5f,
            (y + .5f) * height / outH - .5f)
    }

    private fun ensureModel(context: Context, name: String, hash: String): File {
        val dir = File(context.filesDir, "portrait-models").apply { mkdirs() }
        val file = File(dir, name)
        if (file.isFile && sha256(file) == hash) return file
        val tmp = File(dir, "$name.part")
        try {
            context.assets.open("portrait/$name").use { source ->
                tmp.outputStream().use { out -> source.copyTo(out) }
            }
            require(sha256(tmp) == hash) { "Model SHA-256 wijkt af: $name" }
            if (file.exists()) require(file.delete()) { "Kan oud model niet vervangen" }
            require(tmp.renameTo(file)) { "Kan model niet installeren" }
            return file
        } finally {
            tmp.delete()
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { f ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val n = f.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun infer(context: Context, source: Bitmap, isDepth: Boolean,
                      livePreview: Boolean = false): Plane {
        val session = session(context, isDepth)
        val env = environment
        val input = session.inputInfo.values.first().info as TensorInfo
        val dimensions = input.shape
        require(dimensions.size == 4) { "Model verwacht geen NCHW" }
        val (w, h) = modelInputSize(dimensions, source.width, source.height,
            isDepth, livePreview)
        if (livePreview) Log.i(TAG, "model input ${w}x${h} fixed=" +
            (dimensions[2] > 0 && dimensions[3] > 0))
        val pixels = sourcePixels(source, w, h)
        val area = w * h
        val buffer = ByteBuffer.allocateDirect(area * 3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        val mean = floatArrayOf(.485f, .456f, .406f)
        val std = floatArrayOf(.229f, .224f, .225f)
        for (channel in 0 until 3) {
            for (p in 0 until area) {
                val pix = pixels[p]
                val v = when (channel) {
                    0 -> Color.red(pix)
                    1 -> Color.green(pix)
                    else -> Color.blue(pix)
                }.toFloat() / 255f
                buffer.put(if (isDepth) (v - mean[channel]) / std[channel] else (v * 2f - 1f))
            }
        }
        buffer.rewind()
        return OnnxTensor.createTensor(env, buffer, longArrayOf(1, 3, h.toLong(), w.toLong())).use { tensor ->
            synchronized(session) {
                session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val out = result[0] as OnnxTensor
                val dims = out.info.shape
                require(dims.size in 3..4 && dims.first() == 1L) {
                    "Ongeldige modeluitvoer ${dims.contentToString()}"
                }
                val oh = dims[dims.size - 2].toInt()
                val ow = dims.last().toInt()
                require(oh >= 2 && ow >= 2 && oh * ow <= 3_000_000) { "Te grote modeluitvoer" }
                val data = FloatArray(oh * ow)
                val outputBuffer = out.floatBuffer
                outputBuffer.rewind()
                outputBuffer.get(data)
                require(data.all { it.isFinite() }) { "Niet-eindige modelwaarden" }
                if (isDepth) {
                    // Depth Anything: high = near. Percentiles reduce outlier impact.
                    val ordered = data.copyOf().apply { sort() }
                    val lo = ordered[(ordered.size * .02).toInt()]
                    val hi = ordered[(ordered.size * .98).toInt().coerceAtMost(ordered.lastIndex)]
                    require(hi - lo > 1e-6f) { "Te vlakke dieptekaart" }
                    for (i in data.indices) data[i] = ((data[i] - lo) / (hi - lo)).coerceIn(0f, 1f)
                } else {
                    for (i in data.indices) data[i] = data[i].coerceIn(0f, 1f)
                }
                Plane(data, ow, oh)
                }
            }
        }
    }

    private fun modelInputSize(shape: LongArray, width: Int, height: Int,
                               isDepth: Boolean, livePreview: Boolean = false): Pair<Int, Int> {
        val fixedH = shape[2].toInt()
        val fixedW = shape[3].toInt()
        if (fixedH > 0 && fixedW > 0) return Pair(fixedW, fixedH)
        // Dynamic MODNet: use a smaller preview tensor (256px short edge),
        // never shrink the still-photo model path. Fixed models keep their fixed size.
        val minSide = if (isDepth) 518.0 else if (livePreview) 256.0 else 512.0
        val patch = if (isDepth) 14 else 32
        val scale = minSide / min(width, height)
        fun rounded(x: Int) = (max(1, (x * scale / patch).roundToInt()) * patch).coerceAtMost(1400)
        return Pair(rounded(width), rounded(height))
    }

    private fun sourcePixels(source: Bitmap, width: Int, height: Int): IntArray {
        val small = Bitmap.createScaledBitmap(source, width, height, true)
        val arr = IntArray(width * height)
        small.getPixels(arr, 0, width, 0, 0, width, height)
        if (small !== source) small.recycle()
        return arr
    }

    /** Component-aware island cleanup on the model grid. */
    private fun cleanMatte(raw: Plane): Plane {
        val w = raw.width; val h = raw.height; val n = w * h
        val ids = IntArray(n)
        val queue = IntArray(n)
        val sizes = ArrayList<Int>()
        val bounds = ArrayList<IntArray>()
        sizes.add(0); bounds.add(intArrayOf(0, 0, 0, 0))
        var count = 0
        for (i in 0 until n) {
            if (ids[i] != 0 || raw.data[i] < .025f) continue
            count++
            var head = 0; var tail = 1; queue[0] = i; ids[i] = count
            var minX = w; var minY = h; var maxX = 0; var maxY = 0
            while (head < tail) {
                val index = queue[head++]
                val x = index % w; val y = index / w
                minX = min(minX, x); minY = min(minY, y)
                maxX = max(maxX, x); maxY = max(maxY, y)
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx !in 0 until w || yy !in 0 until h) continue
                    val j = yy * w + xx
                    if (ids[j] == 0 && raw.data[j] >= .025f) {
                        ids[j] = count; queue[tail++] = j
                    }
                }
            }
            sizes.add(tail); bounds.add(intArrayOf(minX, minY, maxX, maxY))
        }
        if (count < 2) return raw
        val main = (1..count).maxByOrNull { sizes[it] } ?: return raw
        val big = max(16, max(sizes[main] * .03f, n * .0025f).roundToInt())
        val near = max(5f, min(w, h) * .055f)
        val primary = bounds[main]
        val keep = BooleanArray(count + 1) { it == main }
        for (i in 1..count) {
            if (i == main) continue
            val b = bounds[i]
            val dx = max(0, max(primary[0] - b[2], b[0] - primary[2]))
            val dy = max(0, max(primary[1] - b[3], b[1] - primary[3]))
            keep[i] = sizes[i] >= big || kotlin.math.hypot(dx.toDouble(), dy.toDouble()) <= near
        }
        val out = raw.data.copyOf()
        for (i in 0 until n) if (ids[i] > 0 && !keep[ids[i]]) out[i] = 0f
        return Plane(out, w, h)
    }

    /** Conservative RGB/matte smoothing for uncertain model pixels. */
    private fun refineMatte(photo: Bitmap, raw: Plane): Plane {
        val w = raw.width; val h = raw.height
        val rgb = sourcePixels(photo, w, h)
        val out = raw.data.copyOf()
        val invSigma = 1f / (2f * .085f * .085f)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val index = y * w + x
            val a = raw.data[index]
            if (a <= .005f || a >= .995f) continue
            val pix = rgb[index]
            var sum = 0f; var denom = 0f
            for (dy in -1..1) for (dx in -1..1) {
                val j = (y + dy) * w + x + dx
                val other = rgb[j]
                val dr = (Color.red(pix) - Color.red(other)) / 255f
                val dg = (Color.green(pix) - Color.green(other)) / 255f
                val db = (Color.blue(pix) - Color.blue(other)) / 255f
                val spatial = if (dx == 0 && dy == 0) 1f else if (dx == 0 || dy == 0) .67f else .45f
                val wt = fastExpMinus((dr * dr + dg * dg + db * db) * invSigma) * spatial
                sum += raw.data[j] * wt; denom += wt
            }
            val grad = max(kotlin.math.abs(raw.data[index-1] - raw.data[index+1]),
                kotlin.math.abs(raw.data[index-w] - raw.data[index+w]))
            val guard = (1f - 1.8f * grad).coerceIn(.15f, 1f)
            val delta = ((sum / max(denom, 1e-6f) - a) * .35f * guard).coerceIn(-.045f, .045f)
            out[index] = (a + delta).coerceIn(0f, 1f)
        }
        return Plane(out, w, h)
    }

    /** Bounded depth edge correction. Depth remains relative, not metric. */
    private fun refineDepth(photo: Bitmap, raw: Plane): Plane {
        val w = raw.width; val h = raw.height
        val pixels = sourcePixels(photo, w, h)
        val result = raw.data.copyOf()
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val idx = y * w + x
            val c = pixels[idx]
            val gray = (.2126f * Color.red(c) + .7152f * Color.green(c) + .0722f * Color.blue(c)) / 255f
            var numerator = 0f; var denominator = 0f
            for (dy in -1..1) for (dx in -1..1) {
                val j = (y + dy) * w + x + dx
                val p = pixels[j]
                val g = (.2126f * Color.red(p) + .7152f * Color.green(p) + .0722f * Color.blue(p)) / 255f
                val weight = fastExpMinus(kotlin.math.abs(g-gray) * 12f)
                numerator += weight * raw.data[j]; denominator += weight
            }
            val proposal = numerator / max(denominator, 1e-6f)
            result[idx] = (raw.data[idx] + .3f * (proposal - raw.data[idx]).coerceIn(-.07f,.07f)).coerceIn(0f,1f)
        }
        return Plane(result, w, h)
    }

    /**
     * Native capture pixels are preserved in a full-resolution
     * foreground. The bounded depth/blur grid is only used to synthesize the
     * background; final mask sampling and RGB blending happen at every original
     * pixel in 64-row stripes. No full-size alpha or blurred-copy Bitmaps.
     *
     * Gaussian appearance and model inference are otherwise unchanged.
     */
    private fun composeWithStrength(source: Bitmap, matte: Plane, depth: Plane,
                                    blurStrengthPercent: Int, onRenderProgress: ((Int) -> Unit)? = null): Bitmap {
        val started = SystemClock.elapsedRealtime()
        val width = source.width
        val height = source.height
        val ratio = min(1f, COMPOSITOR_MAX_EDGE.toFloat() / max(width, height))
        val w = max(2, (width * ratio).roundToInt())
        val h = max(2, (height * ratio).roundToInt())
        val length = w * h
        val alpha = FloatArray(length)
        val depthNear = FloatArray(length)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            alpha[i] = matte.atPixel(x, y, w, h).coerceIn(0f, 1f)
            depthNear[i] = depth.atPixel(x, y, w, h).coerceIn(0f, 1f)
        }
        val solid = alpha.count { it > .5f }
        require(solid > length / 250 && solid < length * 95 / 100) {
            "Geen bruikbaar persoonsonderwerp gevonden; originele foto blijft behouden"
        }
        onRenderProgress?.invoke(12)
        // Refine the final matte only where it is ambiguous,
        // using photo color edges at the SAME compositor resolution. The original
        // image remains the sharp foreground; the Gaussian kernels are untouched.
        val pixels = sourcePixels(source, w, h)
        val refinedAlpha = PortraitEdgeRefiner.refine(alpha, pixels, w, h)
        val personDepth = ArrayList<Float>()
        val backgroundDepth = ArrayList<Float>()
        for (i in 0 until length step 8) {
            if (refinedAlpha[i] > .70f) personDepth.add(depthNear[i])
            if (refinedAlpha[i] < .12f) backgroundDepth.add(depthNear[i])
        }
        // Use the bounded depth curve and all three Gaussian layers.
        val depthCurve = PortraitDepthCurve.fromSamples(personDepth, backgroundDepth)
        Log.i(TAG, "portrait v$PORTRAIT_REVISION: focus=${depthCurve.focus} " +
            "rearDoF=${depthCurve.rearFocusHalfWidth} " +
            "frontDoF=${depthCurve.frontFocusHalfWidth} " +
            "fallback=${depthCurve.fallbackStrength}")
        Log.i(TAG, "compositor prep ${w}x${h}")
        val weighted = IntArray(length)
        val support = IntArray(length)
        for (i in 0 until length) {
            val backgroundAlpha = 1f - refinedAlpha[i]
            val rgb = pixels[i]
            weighted[i] = Color.rgb(
                (Color.red(rgb) * backgroundAlpha).roundToInt(),
                (Color.green(rgb) * backgroundAlpha).roundToInt(),
                (Color.blue(rgb) * backgroundAlpha).roundToInt())
            val intensity = (255 * backgroundAlpha).roundToInt()
            support[i] = Color.rgb(intensity, intensity, intensity)
        }
        onRenderProgress?.invoke(30)
        // build Gaussian background levels in one lower-resolution
        // pass. Premultiplied color/support prevents dark halos near the subject.
        val gaussianStarted = SystemClock.elapsedRealtime()
        val gaussianLayers = gaussianBackgroundLayers(weighted, support, w, h)
        val near = gaussianLayers[0]
        val mid = gaussianLayers[1]
        val far = gaussianLayers[2]
        Log.i(TAG, "gaussian layers=${SystemClock.elapsedRealtime() - gaussianStarted}ms")
        onRenderProgress?.invoke(60)
        val rawBlurField = FloatArray(length)
        for (i in 0 until length) {
            rawBlurField[i] = depthCurve.blurStrength(depthNear[i])
        }
        val smoothBlurField = smoothBackgroundField(rawBlurField, refinedAlpha, w, h)
        // Identify isolated, low-confidence depth predictions
        // in same-colored background only; never smooth across subject edges.
        // Preserve the standard output when the depth curve uses its fallback.
        val stabilized = PortraitDepthConfidence.stabilize(
            smoothBlurField, depthNear, refinedAlpha, pixels, w, h,
            enabled = depthCurve.fallbackStrength < 0f
        )
        val blurField = stabilized.field
        Log.i(TAG, "portrait depth confidence: outliers=${stabilized.lowConfidenceCells} " +
            "adjustedCells=${stabilized.adjustedCells} " +
            "grid=${stabilized.gridWidth}x${stabilized.gridHeight}")
        Log.i(TAG, "blur field ready in ${SystemClock.elapsedRealtime() - started}ms")
        val blurredBackground = IntArray(length)
        val mix = blurStrengthPercent.coerceIn(0, 100) / 100f
        for (i in 0 until length) {
            // Clamp non-finite blur values before composing portrait colors.
            blurredBackground[i] = PortraitCompositeMath.mixGaussianRgb(
                near[i], mid[i], far[i], blurField[i])
        }
        onRenderProgress?.invoke(85)
        // Render the final portrait at source resolution using row tiles.
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val blurPlane = ColorPlane(blurredBackground, w, h)
            val edgePlane = Plane(refinedAlpha, w, h)
            val rows = FULL_RES_TILE_ROWS.coerceAtMost(height)
            val scan = IntArray(width * rows)
            var y0 = 0
            while (y0 < height) {
                val count = min(rows, height - y0)
                source.getPixels(scan, 0, width, 0, y0, width, count)
                for (row in 0 until count) {
                    val y = y0 + row
                    val offset = row * width
                    for (x in 0 until width) {
                        val i = offset + x
                        val coverage = PortraitFullResolutionComposite.overlayAlpha(
                            matte.atPixel(x, y, width, height),
                            edgePlane.atPixel(x, y, width, height), mix)
                        if (coverage != 0) {
                            val blurredColor = blurPlane.atPixel(x, y, width, height)
                            scan[i] = PortraitFullResolutionComposite.blendOpaque(
                                scan[i], blurredColor, coverage)
                        }
                    }
                }
                result.setPixels(scan, 0, width, 0, y0, width, count)
                y0 += count
                onRenderProgress?.invoke(85 + 15 * y0 / height)
            }
            Log.i(TAG, "full-res portrait mix: ${width}x${height} over gaussian ${w}x${h} " +
                "in ${SystemClock.elapsedRealtime() - started}ms; blur=$blurStrengthPercent%")
            return result
        } catch (t: Throwable) {
            result.recycle()
            throw t
        }
    }

    /**
     * Constant-cost, low-resolution separable smoothing.
     * The former per-pixel 5x5 color-aware loop was prohibitively expensive.
     * Smoothing is only used for background blur *strength*, never for the
     * subject matte. A continuous interpolation prevents depth contour banding.
     */
    private fun smoothBackgroundField(field: FloatArray, alpha: FloatArray,
                                      w: Int, h: Int): FloatArray {
        val factor = 4
        val sw = max(2, (w + factor - 1) / factor)
        val sh = max(2, (h + factor - 1) / factor)
        val small = FloatArray(sw * sh)
        val valid = FloatArray(sw * sh)
        for (y in 0 until sh) for (x in 0 until sw) {
            val ix = min(w - 1, x * factor + factor / 2)
            val iy = min(h - 1, y * factor + factor / 2)
            val i = iy * w + ix
            val background = (1f - alpha[i]).coerceIn(0f, 1f)
            val j = y * sw + x
            small[j] = field[i] * background
            valid[j] = background
        }
        fun blur(src: FloatArray): FloatArray {
            val temp = FloatArray(src.size)
            val result = FloatArray(src.size)
            val radius = 3
            for (y in 0 until sh) {
                var sum = 0f
                for (dx in -radius..radius) sum += src[y * sw + dx.coerceIn(0, sw - 1)]
                for (x in 0 until sw) {
                    temp[y * sw + x] = sum / (radius * 2 + 1)
                    sum += src[y * sw + (x + radius + 1).coerceAtMost(sw - 1)] -
                           src[y * sw + (x - radius).coerceAtLeast(0)]
                }
            }
            for (x in 0 until sw) {
                var sum = 0f
                for (dy in -radius..radius) sum += temp[dy.coerceIn(0, sh - 1) * sw + x]
                for (y in 0 until sh) {
                    result[y * sw + x] = sum / (radius * 2 + 1)
                    sum += temp[(y + radius + 1).coerceAtMost(sh - 1) * sw + x] -
                           temp[(y - radius).coerceAtLeast(0) * sw + x]
                }
            }
            return result
        }
        val smoothValues = blur(small)
        val smoothValid = blur(valid)
        val lowField = FloatArray(sw * sh) { i ->
            if (smoothValid[i] > .05f) (smoothValues[i] / smoothValid[i]).coerceIn(0f, 1f)
            else small[i]
        }
        val plane = Plane(lowField, sw, sh)
        val output = FloatArray(field.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            output[i] = if (alpha[i] < .5f) plane.atPixel(x, y, w, h) else field[i]
        }
        return output
    }

    /**
     * Three Gaussian blurred background layers at a bounded working resolution.
     * Unlike downscale/upscale approximation, this convolves actual pixels.
     * Blur is applied to premultiplied background RGB and coverage together:
     * this avoids pulling dark subject pixels into the background bokeh.
     */
    private fun gaussianBackgroundLayers(weighted: IntArray, support: IntArray,
                                         w: Int, h: Int): Array<IntArray> {
        // Use a high-detail, bounded Gaussian input grid.
        // Keep approximately the same visual blur width after resampling.
        val scale = min(1f, GAUSSIAN_MAX_EDGE.toFloat() / max(w, h))
        val sw = max(2, (w * scale).roundToInt())
        val sh = max(2, (h * scale).roundToInt())
        val color = Bitmap.createBitmap(weighted, w, h, Bitmap.Config.ARGB_8888)
        val weight = Bitmap.createBitmap(support, w, h, Bitmap.Config.ARGB_8888)
        val smallColor = Bitmap.createScaledBitmap(color, sw, sh, true)
        val smallWeight = Bitmap.createScaledBitmap(weight, sw, sh, true)
        color.recycle()
        weight.recycle()
        val c = IntArray(sw * sh)
        val a = IntArray(sw * sh)
        smallColor.getPixels(c, 0, sw, 0, 0, sw, sh)
        smallWeight.getPixels(a, 0, sw, 0, 0, sw, sh)
        smallColor.recycle()
        smallWeight.recycle()
        // Reconstruct a narrow band of hidden background on
        // the Gaussian input grid. The original sharp photo, final alpha mask,
        // depth field, Gaussian kernels and three layer radii are untouched.
        val restored = PortraitBackgroundReconstructor.extend(c, a, sw, sh)
        Log.d(TAG, "background extension: $restored pixels at ${sw}x${sh}")
        val area = sw * sh
        val planes = Array(4) { FloatArray(area) }
        for (i in 0 until area) {
            planes[0][i] = Color.red(c[i]).toFloat()
            planes[1][i] = Color.green(c[i]).toFloat()
            planes[2][i] = Color.blue(c[i]).toFloat()
            planes[3][i] = Color.red(a[i]).toFloat()
        }
        val outputs = Array(3) { IntArray(w * h) }
        val radii = intArrayOf(2, 3, 4) // Original three Gaussian stages, preserved.
        val gaussianScale = max(sw, sh) / 640f
        val scaledRadii = radii.map { max(1, (it * gaussianScale).roundToInt()) }
        for (level in 0..2) {
            for (channel in 0..3) planes[channel] = gaussianSeparable(planes[channel], sw, sh, scaledRadii[level])
            val rgb = IntArray(area)
            for (i in 0 until area) {
                val coverage = max(6f, planes[3][i])
                rgb[i] = Color.rgb(
                    (planes[0][i] * 255f / coverage).roundToInt().coerceIn(0,255),
                    (planes[1][i] * 255f / coverage).roundToInt().coerceIn(0,255),
                    (planes[2][i] * 255f / coverage).roundToInt().coerceIn(0,255))
            }
            val small = Bitmap.createBitmap(rgb, sw, sh, Bitmap.Config.ARGB_8888)
            val large = Bitmap.createScaledBitmap(small, w, h, true)
            large.getPixels(outputs[level], 0, w, 0, 0, w, h)
            small.recycle()
            large.recycle()
        }
        return outputs
    }

    /** Fixed-radius separable Gaussian, O(width * height * radius). */
    private fun gaussianSeparable(data: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val sigma = max(.85, radius * .65)
        val weights = FloatArray(radius * 2 + 1) { index ->
            val d = index - radius
            exp(-(d * d) / (2.0 * sigma * sigma)).toFloat()
        }
        val norm = weights.sum()
        for (i in weights.indices) weights[i] /= norm
        val horizontal = FloatArray(data.size)
        val result = FloatArray(data.size)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0f
            for (d in -radius..radius) sum +=
                data[y * w + (x + d).coerceIn(0, w - 1)] * weights[d + radius]
            horizontal[y * w + x] = sum
        }
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0f
            for (d in -radius..radius) sum +=
                horizontal[(y + d).coerceIn(0, h - 1) * w + x] * weights[d + radius]
            result[y * w + x] = sum
        }
        return result
    }

    private fun saveNewImage(context: Context, bitmap: Bitmap): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "FOLD_PORTRAIT_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Portraits")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert mislukt")
        try {
            resolver.openOutputStream(uri, "w")?.use { stream ->
                require(bitmap.compress(Bitmap.CompressFormat.JPEG, 98, stream)) { "JPEG-compressie mislukt" }
            } ?: error("Output stream ontbreekt")
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            require(resolver.update(uri, done, null, null) > 0) { "MediaStore commit mislukt" }
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
