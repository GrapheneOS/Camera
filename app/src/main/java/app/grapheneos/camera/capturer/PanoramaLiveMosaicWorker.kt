package app.grapheneos.camera.capturer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * A single bounded-memory background thread assembles a provisional preview
 * from the original JPEG keyframes while acquisition continues. This is intentionally
 * separate from the high-resolution, foreground-service JPEG output path.
 *
 * A failed optical edge is sensor-ESTIMATED, not labelled matched. Recent placements
 * are revisable using a later n-2 -> n edge. No unbounded ImageProxy references, no
 * stored video, and no changes to the final panorama stitcher.
 */
internal class PanoramaLiveMosaicWorker(
    private val sessionId: String,
    private val onPreview: (Bitmap, PanoramaIncrementalLayout.Stats) -> Unit
) {
    private data class Sample(
        val preview: Bitmap,
        val gray: PanoramaAlignment.GrayFrame,
        val features: List<PanoramaAlignment.Point>,
        val pose: PanoramaVisualInertialFusion.Estimate?
    )

    private val executor = Executors.newSingleThreadExecutor { job ->
        Thread(job, "PanoramaMosaic-$sessionId").apply { priority = Thread.MIN_PRIORITY }
    }
    private val closed = AtomicBoolean(false)
    private val samples = ArrayList<Sample>(30)
    private var layout: PanoramaIncrementalLayout? = null
    private var initialWidth = 0
    private var initialHeight = 0

    /** Only a JPEG path is enqueued, not a megapixel bitmap or an ImageProxy. */
    fun submit(jpeg: File, heading: Float, rotationDegrees: Int,
               pose: PanoramaVisualInertialFusion.Estimate?) {
        if (closed.get()) return
        try {
            executor.execute {
                if (closed.get()) return@execute
                try { append(jpeg, heading, rotationDegrees, pose) }
                catch (error: Exception) {
                    Log.w(TAG, "Live preview skipped frame for session=$sessionId; " +
                        "final JPEG stitching unaffected", error)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { /* session ended */ }
    }

    private fun append(jpeg: File, heading: Float, rotationDegrees: Int,
                       pose: PanoramaVisualInertialFusion.Estimate?) {
        if (!heading.isFinite() || samples.size >= 30) return
        val bitmap = decode(jpeg, rotationDegrees) ?: return
        if (closed.get()) { bitmap.recycle(); return }
        if (initialWidth != 0 && (bitmap.width != initialWidth || bitmap.height != initialHeight)) {
            bitmap.recycle()
            Log.w(TAG, "Orientation/dimensions changed; skip unaligned live preview frame")
            return
        }
        if (initialWidth == 0) { initialWidth = bitmap.width; initialHeight = bitmap.height }
        val projected = try { cylinderWarp(bitmap) } finally { bitmap.recycle() }
        val sample = try {
            val pixels = IntArray(projected.width * projected.height)
            projected.getPixels(pixels, 0, projected.width, 0, 0,
                projected.width, projected.height)
            val gray = PanoramaAlignment.GrayFrame(projected.width,
                projected.height, ByteArray(pixels.size) { i ->
                    val c = pixels[i]
                    (((c ushr 16 and 255) * 77 + (c ushr 8 and 255) * 150 +
                        (c and 255) * 29) ushr 8).toByte()
                })
            Sample(projected, gray, PanoramaAlignment.features(gray), pose)
        } catch (error: Exception) {
            projected.recycle()
            throw error
        }
        val model = layout ?: PanoramaIncrementalLayout(projected.width).also { layout = it }
        val previous = samples.lastOrNull()
        val previousHeading = model.snapshot().lastOrNull()?.heading
        val rawYawDelta = if (previousHeading == null) 0f else heading - previousHeading
        val pairedYaw = if (previous != null) poseDelta(previous.pose, pose) else null
        // Pose history is a cross-check, never a standalone absolute location.
        // Reject incompatible clock domains or inconsistent rotation instead of
        // silently overriding the continuous sensor heading.
        val previewYawDelta = if (pairedYaw != null &&
            abs(pairedYaw - rawYawDelta) <= 0.12f) rawYawDelta * 0.85f + pairedYaw * 0.15f
            else rawYawDelta
        val expected = (previewYawDelta * (projected.width * 1.04f)).coerceIn(
            -projected.width * 0.78f, projected.width * 0.78f)
        val adjacent = if (previous != null && abs(expected) >= projected.width * 0.025f)
            match(previous, sample, expected) else null
        model.append(heading, expected, adjacent)
        samples.add(sample)
        if (samples.size >= 3) {
            val placements = model.snapshot()
            val skipExpected = (placements.last().heading -
                placements[placements.lastIndex - 2].heading) * projected.width * 1.04f
            if (abs(skipExpected) < projected.width * 0.88f) {
                val skip = match(samples[samples.lastIndex - 2], sample, skipExpected)
                model.reviseNewestFromSkip(skip)
            }
        }
        if (closed.get()) return
        val result = render(model.snapshot())
        if (closed.get()) result.recycle()
        else {
            val stats = model.stats()
            Log.i(TAG, "Live mosaic $sessionId frames=${stats.frames} " +
                "matched=${stats.matchedEdges} estimated=${stats.estimatedEdges} " +
                "revisions=${stats.backtrackRevisions} bitmap=${result.width}x${result.height}")
            try { onPreview(result, stats) }
            catch (error: Exception) { result.recycle(); throw error }
        }
    }

    private fun poseDelta(previous: PanoramaVisualInertialFusion.Estimate?,
                          current: PanoramaVisualInertialFusion.Estimate?): Float? {
        if (previous == null || current == null || !previous.synchronizedClocks ||
            !current.synchronizedClocks || previous.confidence < 0.60f ||
            current.confidence < 0.60f) return null
        val elapsed = current.timestampNs - previous.timestampNs
        if (elapsed !in 30_000_000L..2_000_000_000L) return null
        val a = previous.totalSensorHeadingRadians ?: return null
        val b = current.totalSensorHeadingRadians ?: return null
        return (b - a).takeIf { it.isFinite() && abs(it) < 1.2f }
    }

    private fun match(a: Sample, b: Sample, predictedDx: Float): PanoramaIncrementalLayout.Offset? {
        if (!predictedDx.isFinite() || abs(predictedDx) !in
            (a.gray.width * 0.025f)..(a.gray.width * 0.88f)) return null
        val isRight = predictedDx > 0
        val first = if (isRight) a else b
        val second = if (isRight) b else a
        val offset = PanoramaAlignment.register(
            first.gray, second.gray, first.features, second.features, 0.025, 0.88
        ) ?: PanoramaAlignment.registerGuided(
            first.gray, second.gray, abs(predictedDx).toDouble()
        )
        if (offset == null) return null
        val confidence = if (offset.inliers >= 12 && offset.error.isFinite()) 0.85f else 0.64f
        val sign = if (isRight) 1f else -1f
        return PanoramaIncrementalLayout.Offset(
            (offset.dx * sign).toFloat(), (offset.dy * sign).toFloat(), confidence)
    }

    private fun decode(jpeg: File, rotation: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(jpeg.absolutePath, bounds)
        if (bounds.outWidth < 96 || bounds.outHeight < 96) return null
        val angle = ((rotation % 360) + 360) % 360
        val orientedWidth = if (angle == 90 || angle == 270) bounds.outHeight else bounds.outWidth
        val orientedHeight = if (angle == 90 || angle == 270) bounds.outWidth else bounds.outHeight
        val scale = min(1.0, min(320.0 / orientedWidth, 240.0 / orientedHeight))
        val targetWidth = max(96, (orientedWidth * scale).roundToInt())
        val targetHeight = max(96, (orientedHeight * scale).roundToInt())
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth &&
            bounds.outHeight / (sample * 2) >= targetHeight) sample *= 2
        val decoded = BitmapFactory.decodeFile(jpeg.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }) ?: return null
        val oriented = if (angle == 0) decoded else {
            try { Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(angle.toFloat()) }, true) }
            finally { decoded.recycle() }
        }
        if (oriented.width == targetWidth && oriented.height == targetHeight) return oriented
        return try { Bitmap.createScaledBitmap(oriented, targetWidth, targetHeight, true) }
        finally { oriented.recycle() }
    }

    /** Same cylinder projection convention as the final stitcher, at reduced resolution. */
    private fun cylinderWarp(input: Bitmap): Bitmap {
        val w = input.width; val h = input.height
        val focal = w * 1.04
        val angle = atan(w / (2.0 * focal))
        val outW = (2 * focal * angle).roundToInt().coerceAtLeast(96)
        val outH = (h * kotlin.math.cos(angle) * 0.995).roundToInt().coerceAtLeast(96)
        val source = IntArray(w * h)
        input.getPixels(source, 0, w, 0, 0, w, h)
        val result = IntArray(outW * outH)
        for (x in 0 until outW) {
            val theta = (x - (outW - 1) / 2.0) / focal
            val sx = ((w - 1) / 2.0 + focal * tan(theta)).coerceIn(0.0, (w - 2).toDouble())
            val x0 = sx.toInt(); val tx = sx - x0
            val vs = 1.0 / kotlin.math.cos(theta)
            for (y in 0 until outH) {
                val sy = ((h - 1) / 2.0 + (y - (outH - 1) / 2.0) * vs)
                    .coerceIn(0.0, (h - 2).toDouble())
                val y0 = sy.toInt(); val ty = sy - y0
                val a = source[y0 * w + x0]; val b = source[y0 * w + x0 + 1]
                val c = source[(y0 + 1) * w + x0]; val d = source[(y0 + 1) * w + x0 + 1]
                var color = -0x1000000
                for (shift in intArrayOf(0, 8, 16)) {
                    val v = ((a ushr shift and 255) * (1-tx) * (1-ty) +
                        (b ushr shift and 255) * tx * (1-ty) +
                        (c ushr shift and 255) * (1-tx) * ty +
                        (d ushr shift and 255) * tx * ty).roundToInt().coerceIn(0, 255)
                    color = color or (v shl shift)
                }
                result[y * outW + x] = color
            }
        }
        return Bitmap.createBitmap(result, outW, outH, Bitmap.Config.ARGB_8888)
    }

    /** Rebuild the small preview so the latest two placements can move backwards. */
    private fun render(placements: List<PanoramaIncrementalLayout.Placement>): Bitmap {
        require(placements.size == samples.size)
        val minX = placements.minOf { it.x }
        val maxX = placements.maxOf { it.x } + samples[0].preview.width
        val minY = placements.minOf { it.y }
        val maxY = placements.maxOf { it.y }
        val overlapHeight = (samples[0].preview.height - (maxY - minY)).roundToInt()
            .coerceIn(64, samples[0].preview.height)
        val fullWidth = max(1f, maxX - minX)
        val outputScale = min(1f, 2560f / fullWidth)
        val outWidth = max(1, (fullWidth * outputScale).roundToInt())
        val outHeight = max(1, (overlapHeight * outputScale).roundToInt())
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val order = placements.indices.sortedBy { placements[it].x }
            var priorRight = Float.NEGATIVE_INFINITY
            for (index in order) {
                val p = placements[index]
                val bmp = samples[index].preview
                val left = (p.x - minX) * outputScale
                val right = left + bmp.width * outputScale
                val boundary = if (priorRight == Float.NEGATIVE_INFINITY) left else
                    max(left, min(priorRight, (left + priorRight) * 0.5f))
                // Replace only after the middle of overlapping views. Remaining edge
                // is not claimed to be a final seam; final stitcher remains independent.
                val srcLeft = ((boundary - left) / outputScale).roundToInt()
                    .coerceIn(0, bmp.width)
                if (srcLeft < bmp.width) {
                    val srcTop = (maxY - p.y).roundToInt().coerceIn(0, bmp.height - 1)
                    val srcBottom = min(bmp.height, srcTop + overlapHeight)
                    val drawHeight = ((srcBottom - srcTop) * outputScale).coerceAtLeast(1f)
                    canvas.drawBitmap(bmp, Rect(srcLeft, srcTop, bmp.width, srcBottom),
                        RectF(boundary, 0f, right, drawHeight), paint)
                }
                priorRight = max(priorRight, right)
            }
            return output
        } catch (error: Exception) { output.recycle(); throw error }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            executor.execute {
                for (sample in samples) sample.preview.recycle()
                samples.clear()
                layout?.clear()
                layout = null
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { }
        // The closed flag causes pending capture jobs to return immediately; the
        // final cleanup job runs after any active bitmap work, without UI blocking.
        executor.shutdown()
    }

    companion object { private const val TAG = "PanoramaLiveMosaic" }
}
