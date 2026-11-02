package app.grapheneos.camera.capturer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Low-resolution color panorama assembled from continuous CameraX YUV previews.
 * Only a detached 160x128 IntArray is handed in. A single-slot mailbox drops superseded
 * frames under CPU pressure: NEVER block CameraX, keep an ImageProxy, or queue video.
 * All bitmap / registration operations happen on the private background executor.
 * This mosaic is advisory; the untouched full-res JPEG stitcher remains authoritative.
 */
internal class PanoramaStreamMosaicWorker(
    private val sessionId: String,
    private val onPreview: (Bitmap, PanoramaIncrementalLayout.Stats) -> Unit
) {
    private data class Incoming(val frame: PanoramaYuvColorSampler.Frame,
                                val heading: Float, val rotation: Int)
    private data class Sample(
        val bitmap: Bitmap,
        val gray: PanoramaAlignment.GrayFrame,
        val features: List<PanoramaAlignment.Point>
    )
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PanoramaStream-$sessionId").apply { priority = Thread.MIN_PRIORITY }
    }
    private val closed = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)
    private val latest = AtomicReference<Incoming?>(null)
    private val samples = ArrayList<Sample>()
    private var model: PanoramaIncrementalLayout? = null
    private var directional: PanoramaDirectionalPreview? = null
    private var rotation: Int? = null

    fun submit(frame: PanoramaYuvColorSampler.Frame, heading: Float, rotationDegrees: Int) {
        if (closed.get() || !heading.isFinite()) return
        latest.set(Incoming(frame, heading, rotationDegrees))
        schedule()
    }

    private fun schedule() {
        if (closed.get() || !busy.compareAndSet(false, true)) return
        try { executor.execute {
            try {
                while (!closed.get()) {
                    val incoming = latest.getAndSet(null) ?: break
                    try { append(incoming) }
                    catch (error: Exception) {
                        Log.w(TAG, "Skipping preview segment session=$sessionId", error)
                    }
                }
            } finally {
                busy.set(false)
                if (!closed.get() && latest.get() != null) schedule()
            }
        } } catch (_: RejectedExecutionException) { busy.set(false) }
    }

    private fun append(incoming: Incoming) {
        if (closed.get() || samples.size >= PanoramaStreamPolicy.MAX_SEGMENTS) return
        if (rotation != null && incoming.rotation != rotation) {
            // A changed orientation invalidates the optical registration coordinates.
            for (sample in samples) sample.bitmap.recycle()
            samples.clear(); model?.clear(); model = null; directional?.reset(); directional = null
        }
        rotation = incoming.rotation
        val projected = project(incoming.frame)
        if (closed.get()) { projected.recycle(); return }
        val pixels = IntArray(projected.width * projected.height)
        projected.getPixels(pixels, 0, projected.width, 0, 0, projected.width, projected.height)
        val gray = PanoramaAlignment.GrayFrame(projected.width,
            projected.height, ByteArray(pixels.size) { index ->
                val c = pixels[index]
                (((c ushr 16 and 255) * 77 + (c ushr 8 and 255) * 150 +
                    (c and 255) * 29) ushr 8).toByte()
            })
        val sample = try { Sample(projected, gray,
            PanoramaAlignment.features(gray)) }
        catch (error: Exception) { projected.recycle(); throw error }
        val layout = model ?: PanoramaIncrementalLayout(projected.width).also { model = it }
        val last = layout.snapshot().lastOrNull()
        // Sensor is a *prediction*, optical match is the only confirmed seam.
        val fallbackFocal = (incoming.frame.width *
            PanoramaFocalCalibration.UNCERTAIN_FALLBACK_WIDTH_RATIO).toFloat()
        val sensorDelta = ((incoming.heading - (last?.heading ?: incoming.heading)) *
            fallbackFocal).coerceIn(-projected.width * 0.78f, projected.width * 0.78f)
        val rawMatch = if (last != null) match(samples.last(), sample, abs(sensorDelta)) else null
        val detector = directional ?: PanoramaDirectionalPreview(projected.width)
            .also { directional = it }
        val decision = detector.update(sensorDelta, incoming.rotation, rawMatch?.dx,
            rawMatch != null && rawMatch.confidence >= .8f)
        val observed = rawMatch?.takeIf { decision.observedDx != null }
        layout.append(incoming.heading, decision.predictedDx, observed)
        samples.add(sample)
        if (samples.size >= 3) {
            val placements = layout.snapshot()
            val predicted = abs((placements.last().heading -
                placements[placements.lastIndex - 2].heading) * fallbackFocal)
            if (predicted in (projected.width * 0.025f)..(projected.width * 0.88f))
                layout.reviseNewestFromSkip(
                    match(samples[samples.lastIndex - 2], sample, predicted))
        }
        if (closed.get()) return
        val preview = render(layout.snapshot())
        if (closed.get()) preview.recycle()
        else {
            val stats = layout.stats()
            if (stats.frames % 8 == 0)
                Log.i(TAG, "Stream mosaic $sessionId frames=${stats.frames} " +
                    "matched=${stats.matchedEdges} estimated=${stats.estimatedEdges} " +
                    "revisions=${stats.backtrackRevisions}")
            try { onPreview(preview, stats) }
            catch (error: Exception) { preview.recycle(); throw error }
        }
    }

    /** The same registration is tried in both image-space directions. */
    private fun match(a: Sample, b: Sample, predictedMagnitude: Float):
        PanoramaIncrementalLayout.Offset? {
        val best = PanoramaBidirectionalMatcher.find(a.gray, b.gray,
            a.features, b.features, predictedMagnitude) ?: return null
        return PanoramaIncrementalLayout.Offset(
            best.dx, best.dy, best.confidence)
    }

    private fun project(frame: PanoramaYuvColorSampler.Frame): Bitmap {
        val width = frame.width; val height = frame.height
        require(width >= 96 && height >= 96 && frame.argb.size == width * height)
        val focal = width * PanoramaFocalCalibration.UNCERTAIN_FALLBACK_WIDTH_RATIO
        val angle = atan(width / (2.0 * focal))
        val outW = (2.0 * focal * angle).roundToInt().coerceAtLeast(96)
        val outH = (height * cos(angle) * 0.995).roundToInt().coerceAtLeast(96)
        val output = IntArray(outW * outH)
        for (x in 0 until outW) {
            val theta = (x - (outW - 1) / 2.0) / focal
            val sx = ((width - 1) / 2.0 + focal * tan(theta)).roundToInt()
                .coerceIn(0, width - 1)
            val correction = 1.0 / cos(theta)
            for (y in 0 until outH) {
                val sy = ((height - 1) / 2.0 + (y - (outH - 1) / 2.0) * correction)
                    .roundToInt().coerceIn(0, height - 1)
                output[y * outW + x] = frame.argb[sy * width + sx]
            }
        }
        return Bitmap.createBitmap(output, outW, outH, Bitmap.Config.ARGB_8888)
    }

    private fun render(placements: List<PanoramaIncrementalLayout.Placement>): Bitmap {
        val leftmost = placements.minOf { it.x }
        val rightmost = placements.maxOf { it.x } + samples.first().bitmap.width
        val topmost = placements.minOf { it.y }
        val bottommost = placements.maxOf { it.y }
        val height = (samples.first().bitmap.height - (bottommost - topmost)).roundToInt()
            .coerceIn(64, samples.first().bitmap.height)
        val scale = min(1f, 2560f / max(1f, rightmost - leftmost))
        val output = Bitmap.createBitmap(max(1, ((rightmost - leftmost) * scale).roundToInt()),
            max(1, (height * scale).roundToInt()), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            var priorRight = Float.NEGATIVE_INFINITY
            for (index in placements.indices.sortedBy { placements[it].x }) {
                val p = placements[index]; val bitmap = samples[index].bitmap
                val left = (p.x - leftmost) * scale
                val right = left + bitmap.width * scale
                val split = if (priorRight == Float.NEGATIVE_INFINITY) left else
                    max(left, min(priorRight, (left + priorRight) * 0.5f))
                val sourceLeft = ((split - left) / scale).roundToInt().coerceIn(0, bitmap.width)
                if (sourceLeft < bitmap.width) {
                    val sourceTop = (bottommost - p.y).roundToInt().coerceIn(0, bitmap.height-1)
                    val sourceBottom = min(bitmap.height, sourceTop + height)
                    canvas.drawBitmap(bitmap,
                        Rect(sourceLeft, sourceTop, bitmap.width, sourceBottom),
                        RectF(split, 0f, right,
                            (sourceBottom - sourceTop) * scale), paint)
                }
                priorRight = max(priorRight, right)
            }
            return output
        } catch (error: Exception) { output.recycle(); throw error }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        latest.set(null)
        try { executor.execute {
            for (sample in samples) sample.bitmap.recycle()
            samples.clear(); model?.clear(); model = null; directional?.reset(); directional = null
        } } catch (_: RejectedExecutionException) { }
        executor.shutdown()
    }

    companion object { private const val TAG = "PanoramaStreamMosaic" }
}
