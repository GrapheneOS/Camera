package app.grapheneos.camera.capturer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import app.grapheneos.camera.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Shows thumbnails from captured panorama frames, independently of the final stitch. */
internal class PanoramaProgressStrip(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val thumbnails = ArrayList<Bitmap>()
    /** Optional low-resolution incremental panorama, owned and swapped on UI thread. */
    private var livePreview: Bitmap? = null
    private var streamActive = false
    private var angleFraction = 0f
    private var currentAngle = 0
    private var targetAngle = 180
    private var maximumFrames = MAX_FRAMES
    private var matchedEdges = 0
    private var estimatedEdges = 0
    private var backtrackRevisions = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val panel = RectF()
    private val viewport = RectF()
    private val destination = RectF()
    private val source = Rect()
    private var direction = 0
    private var sweepSteps = 0f
    private var selectedDegrees = 0
    private var verticalDrift = 0f
    private var aligned = true
    private var trackingReliable = false
    private val blue = Color.rgb(93, 191, 255)
    private val muted = Color.rgb(105, 115, 126)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        updateAccessibility()
    }

    fun clear() {
        livePreview?.recycle()
        livePreview = null
        streamActive = false
        angleFraction = 0f
        currentAngle = 0
        targetAngle = 180
        maximumFrames = MAX_FRAMES
        matchedEdges = 0
        estimatedEdges = 0
        backtrackRevisions = 0
        for (bitmap in thumbnails) bitmap.recycle()
        thumbnails.clear()
        direction = 0
        sweepSteps = 0f
        selectedDegrees = 0
        verticalDrift = 0f
        aligned = true
        trackingReliable = false
        updateAccessibility()
        invalidate()
    }

    fun configureLimit(frames:Int) { maximumFrames=frames; updateAccessibility(); invalidate() }

    fun addFrame(bitmap: Bitmap) {
        val factor = min(1f, THUMBNAIL_LONG_EDGE / max(bitmap.width, bitmap.height).toFloat())
        val width = max(1, (bitmap.width * factor).roundToInt())
        val height = max(1, (bitmap.height * factor).roundToInt())
        thumbnails.add(Bitmap.createScaledBitmap(bitmap, width, height, true))
        updateAccessibility()
        invalidate()
    }

    /** Called only from the main thread; accepts ownership of bitmap. */
    fun updateLivePreview(bitmap: Bitmap, stats: PanoramaIncrementalLayout.Stats) {
        // JPEG mosaic is the fallback. Once YUV preview starts, never flash back
        // to an older still-based mosaic while live frames are flowing.
        if (streamActive) { bitmap.recycle(); return }
        val old = livePreview
        livePreview = bitmap
        if (old !== bitmap) old?.recycle()
        matchedEdges = stats.matchedEdges
        estimatedEdges = stats.estimatedEdges
        backtrackRevisions = stats.backtrackRevisions
        updateAccessibility()
        invalidate()
    }

    /** Takes ownership of the new continuous-stream bitmap, on UI thread only. */
    fun updateStreamPreview(bitmap: Bitmap, stats: PanoramaIncrementalLayout.Stats) {
        streamActive = true
        val previous = livePreview
        livePreview = bitmap
        if (previous !== bitmap) previous?.recycle()
        matchedEdges = stats.matchedEdges
        estimatedEdges = stats.estimatedEdges
        backtrackRevisions = stats.backtrackRevisions
        updateAccessibility()
        invalidate()
    }

    /** A monotone, direction-independent UI angle, never raw gyro jitter. */
    fun updateAngularProgress(fraction: Float, degrees: Int, targetDegrees: Int) {
        val nextFraction = fraction.coerceIn(0f, 1f)
        val nextDegrees = degrees.coerceAtLeast(0)
        if (abs(angleFraction - nextFraction) < 0.002f &&
            currentAngle == nextDegrees && targetAngle == targetDegrees) return
        angleFraction = nextFraction
        currentAngle = nextDegrees
        targetAngle = targetDegrees
        updateAccessibility()
        invalidate()
    }

    fun updateMovement(newDirection: Int, steps: Float) {
        val normalized = newDirection.coerceIn(-1, 1)
        val progress = steps.coerceIn(0f, maximumFrames.toFloat())
        if (direction == normalized && abs(sweepSteps - progress) < 0.025f) return
        direction = normalized
        sweepSteps = progress
        invalidate()
    }

    /** Angular extent from the FIRST to LAST stored JPEG, not overall gyro motion. */
    fun updateSelectedCoverage(degrees: Int) {
        val bounded = degrees.coerceIn(0, 360)
        if (selectedDegrees == bounded) return
        selectedDegrees = bounded
        updateAccessibility()
        invalidate()
    }

    /** Yellow moving guide, red when the handset tilts beyond the starting sweep plane. */
    fun updateAlignment(pitchDelta: Float, isAligned: Boolean) {
        val drift = pitchDelta.coerceIn(-0.35f, 0.35f)
        if (abs(drift - verticalDrift) < 0.005f && aligned == isAligned) return
        verticalDrift = drift
        aligned = isAligned
        invalidate()
    }

    /** A small indicator distinguishes visual tracking from sensor-only guidance. */
    fun updateOpticalTracking(reliable: Boolean) {
        if (trackingReliable == reliable) return
        trackingReliable = reliable
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val inset = dp(9f)
        val corner = dp(10f)
        panel.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = 0xCC13181E.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(panel, corner, corner, paint)

        viewport.set(inset, dp(8f), width - inset, height - dp(24f))
        paint.color = 0xFF262C33.toInt()
        canvas.drawRoundRect(viewport, dp(4f), dp(4f), paint)

        canvas.save()
        canvas.clipRect(viewport)
        val growing = livePreview
        if (growing != null && !growing.isRecycled) {
            // Fill the preview rail according to the 180-degree capture sweep.
            val drawWidth = PanoramaStripGeometry.previewFillWidth(
                viewport.width(), angleFraction, dp(62f))
            destination.set(viewport.left, viewport.top,
                viewport.left + drawWidth, viewport.bottom)
            centerCrop(growing, destination, source)
            paint.color = Color.WHITE
            paint.alpha = 255
            canvas.drawBitmap(growing, source, destination, paint)
        } else {
            val tileWidth = min(dp(62f), viewport.width() / 2.5f)
            val overflow = max(0f, thumbnails.size * tileWidth - viewport.width())
            for (index in thumbnails.indices) {
                val left = PanoramaStripGeometry.thumbnailLeft(
                    viewport.left, tileWidth, index, overflow)
                destination.set(left, viewport.top, left + tileWidth, viewport.bottom)
                if (destination.right <= viewport.left || destination.left >= viewport.right) continue
                val bitmap = thumbnails[index]
                centerCrop(bitmap, destination, source)
                paint.color = Color.WHITE
                paint.alpha = 255
                canvas.drawBitmap(bitmap, source, destination, paint)
                paint.color = 0xAAFFFFFF.toInt()
                paint.strokeWidth = dp(0.7f)
                canvas.drawLine(destination.right, viewport.top, destination.right, viewport.bottom, paint)
                if (index == thumbnails.lastIndex) {
                    paint.color = blue
                    paint.strokeWidth = dp(2f)
                    paint.style = Paint.Style.STROKE
                    canvas.drawRect(destination, paint)
                    paint.style = Paint.Style.FILL
                }
            }
        }
        canvas.restore()

        // The moving frame is a guide, not an additional captured JPEG thumbnail.
        val guideWidth = min(dp(48f), viewport.width() / 4f)
        val guideHeight = viewport.height() * 0.70f
        // Display always starts LEFT and moves RIGHT. Signed sweep direction
        // remains available for stitching, but must never mirror UI progress.
        val railInset = dp(4f)
        val guideLeft = PanoramaStripGeometry.guideLeft(
            viewport.left, viewport.width(), guideWidth, railInset, angleFraction)
        val driftPixels = (verticalDrift / 0.20f) * viewport.height() * 0.38f
        destination.set(guideLeft, viewport.centerY() - guideHeight / 2f + driftPixels,
            guideLeft + guideWidth, viewport.centerY() + guideHeight / 2f + driftPixels)
        paint.color = if (aligned) Color.rgb(255, 214, 62) else Color.rgb(244, 97, 81)
        paint.strokeWidth = dp(2f)
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(destination, dp(2f), dp(2f), paint)
        paint.style = Paint.Style.FILL
        paint.color = if (trackingReliable) Color.rgb(94, 218, 149) else muted
        canvas.drawCircle(viewport.left + dp(8f), viewport.top + dp(8f), dp(3f), paint)

        paint.color = muted
        val barTop = height - dp(12f)
        val barLeft = inset
        val barRight = width - dp(110f)
        val barWidth = max(1f, barRight - barLeft)
        val bar = RectF(barLeft, barTop, barRight, barTop + dp(3f))
        canvas.drawRoundRect(bar, dp(2f), dp(2f), paint)

        val count = thumbnails.size
        // YELLOW = measured relative IMU sweep. This is NOT final verified optical
        // coverage; saved keyframe count remains separate in the accessibility text.
        val filledWidth = barWidth * angleFraction
        if (filledWidth > 0f) {
            val filled = RectF(barLeft, bar.top,
                PanoramaStripGeometry.progressRight(barLeft, barWidth, angleFraction), bar.bottom)
            paint.color = if (aligned) Color.rgb(255, 214, 62) else Color.rgb(244, 97, 81)
            canvas.drawRoundRect(filled, dp(2f), dp(2f), paint)
        }

        paint.color = Color.WHITE
        paint.textSize = dp(11f)
        paint.textAlign = Paint.Align.RIGHT
        val baseline = barTop + dp(3f)
        val quality = if (growing != null) " ${matchedEdges}✓/${estimatedEdges}~" else " $count frames"
        val label = "${currentAngle}°/${targetAngle}°" + quality
        canvas.drawText(label, width - inset, baseline, paint)
    }

    private fun centerCrop(bitmap: Bitmap, dest: RectF, out: Rect) {
        val desiredRatio = dest.width() / dest.height()
        val actualRatio = bitmap.width.toFloat() / bitmap.height
        if (actualRatio > desiredRatio) {
            val cropWidth = max(1, (bitmap.height * desiredRatio).roundToInt())
            val x = (bitmap.width - cropWidth) / 2
            out.set(x, 0, x + cropWidth, bitmap.height)
        } else {
            val cropHeight = max(1, (bitmap.width / desiredRatio).roundToInt())
            val y = (bitmap.height - cropHeight) / 2
            out.set(0, y, bitmap.width, y + cropHeight)
        }
    }

    private fun updateAccessibility() {
        contentDescription = context.getString(R.string.panorama_progress, thumbnails.size, maximumFrames) +
            ", sensor sweep $currentAngle of $targetAngle degrees; " +
            "${thumbnails.size} stored JPEG frames, " +
            "$matchedEdges visually matched, $estimatedEdges estimated preview edges; " +
            "final optical coverage pending"
    }

    private fun dp(value: Float) = value * density

    companion object {
        private const val THUMBNAIL_LONG_EDGE = 240f
        const val MAX_FRAMES = 30
    }
}
