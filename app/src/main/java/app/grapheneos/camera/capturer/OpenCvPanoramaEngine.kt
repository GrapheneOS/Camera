package app.grapheneos.camera.capturer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.SIFT
import org.opencv.imgproc.Imgproc
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Sequential cylindrical panorama stitching from captured frames.
 *
 * OpenCV's actual native SIFT/descriptor-matching implementation (official Android
 * AAR) replaces the in-house feature registration. Android's standard OpenCV AAR
 * does NOT expose cv::Stitcher, so it must not be described as the global Stitcher.
 *
 * All JPEGs are retained until a verified composition succeeds. No gyro-only
 * overlap is ever certified, and the order is chosen from optical matches only.
 * Registration runs on low-resolution decoded frames; the final mosaic is
 * composed from re-decoded, higher-resolution source JPEGs one at a time.
 */
internal object OpenCvPanoramaEngine {
    private const val TAG = "OpenCvPanorama"
    private const val ANALYSIS_WIDTH = 840
    private const val FOCAL_WIDTH_RATIO = 0.72 // conservative initial estimate; refine with camera metadata later

    private data class Features(val points: Array<org.opencv.core.KeyPoint>, val descriptors: Mat) {
        fun release() = descriptors.release()
    }

    private fun checkCanceled(canceled: () -> Boolean) {
        if (canceled()) throw CancellationException("Panorama canceled")
    }

    fun join(frames: List<PanoramaModule.Frame>,
             maxOutputPixels: Long = 36_000_000L,
             canceled: () -> Boolean = { false },
             fullCircle: Boolean = false): PanoramaModule.Result {
        require(frames.size in 2..60)
        require(maxOutputPixels in 10_000_000L..300_000_000L)
        if (!OpenCVLoader.initLocal()) throw IOException("OpenCV native library failed to load")
        checkCanceled(canceled)
        val (nativeW, nativeH) = PanoramaModule.orientedSize(frames.first())
        require(frames.all { PanoramaModule.orientedSize(it) == (nativeW to nativeH) }) {
            "Panorama JPEG orientation/dimensions changed during capture"
        }
        val analysisH = (ANALYSIS_WIDTH.toDouble() * nativeH / nativeW)
            .roundToInt().coerceAtLeast(128)
        val analysisGeometry = PanoramaModule.Cylinder(
            ANALYSIS_WIDTH, analysisH, ANALYSIS_WIDTH * FOCAL_WIDTH_RATIO)
        val detector = SIFT.create(1300)
        val matcher = BFMatcher.create(Core.NORM_L2, false)
        val offsets = ArrayList<PanoramaRobustTranslation.Result>()
        val pairMatches = ArrayList<List<PanoramaRobustTranslation.Match>>()
        var previous: Features? = null
        var firstFeatures: Features? = null
        var opticalClosure: PanoramaCircularClosure.Result? = null
        try {
            for ((index, frame) in frames.withIndex()) {
                checkCanceled(canceled)
                val image = PanoramaModule.decodeFrame(
                    frame, ANALYSIS_WIDTH, analysisH)
                val projected = try { analysisGeometry.warp(image) } finally { image.recycle() }
                val current = try { extract(detector, projected) } finally { projected.recycle() }
                try {
                    val preceding = previous
                    if (preceding != null) {
                        val matches = match(matcher, preceding, current)
                        val estimate = PanoramaRobustTranslation.estimate(
                            matches, analysisGeometry.width, analysisGeometry.height)
                            ?: throw IOException("OpenCV optical overlap rejected for JPEG " +
                                "${index + 1}/${frames.size}: ${matches.size} descriptor pairs")
                        offsets.add(estimate)
                        pairMatches.add(matches)
                        Log.i(TAG, "Optical pair $index/${frames.size - 1}: " +
                            "shift=${estimate.dx.roundToInt()},${estimate.dy.roundToInt()} " +
                            "inliers=${estimate.inliers}/${estimate.candidates} " +
                            "residual=${"%.1f".format(java.util.Locale.US, estimate.medianError)}px")
                    }
                } catch (error: Throwable) {
                    current.release()
                    throw error
                }
                if (index == 0 && fullCircle) firstFeatures = current
                if (previous !== firstFeatures) previous?.release()
                previous = current
            }
            if (fullCircle) {
                val first = firstFeatures ?: throw IOException("360° has no first frame features")
                val last = previous ?: throw IOException("360° has no final frame features")
                opticalClosure = PanoramaCircularClosure.estimate(
                    match(matcher, last, first), analysisGeometry.width, analysisGeometry.height)
                    ?: throw IOException("360° first/last optical closure rejected; no circular photo saved")
            }
        } finally {
            if (previous !== firstFeatures) previous?.release()
            firstFeatures?.release()
            detector.clear()
            matcher.clear()
        }
        val reversed = PanoramaRobustTranslation.reverseOrder(offsets)
            ?: throw IOException("Optical capture direction is ambiguous; no panorama published")
        Log.i(TAG, "Panorama registration: verified=${offsets.size}/${frames.size - 1} " +
            "reverse=$reversed engine=OpenCV-native-SIFT")
        val orderedFrames = if (reversed) frames.asReversed() else frames
        val circularOffset = opticalClosure?.let { if (reversed) -it.dx else it.dx }
        val orderedOffsets = if (reversed) offsets.asReversed().map {
            it.copy(dx = -it.dx, dy = -it.dy)
        } else offsets
        // Canonicalize temporal match direction along with the JPEGs. As a result,
        // the local seam correction sees identical physical pairs for either
        // sweep direction; it never reads a sensor sign or file-order flag.
        val orderedMatches = if (reversed) pairMatches.asReversed().map {
            PanoramaLocalAlignmentModel.canonicalMatches(it, true)
        } else pairMatches
        val localFields = orderedOffsets.indices.map { i ->
            PanoramaLocalAlignmentModel.estimate(
                orderedMatches[i], orderedOffsets[i],
                analysisGeometry.width, analysisGeometry.height)
        }
        Log.i(TAG, "Panorama local alignment: enabled=${localFields.count { it != null }}/" +
            "${orderedOffsets.size} optical pairs; bounded local mesh, canonical spatial order")
        if (orderedOffsets.any { it.dx <= analysisGeometry.width * 0.018 }) {
            throw IOException("Conflicting panorama sweep direction in optical matches")
        }

        // Estimate the ORIGINAL-pixel panorama footprint from the verified SIFT
        // offsets. Only scale down when the memory-dependent budget or JPEG's
        // 65535-pixel side limit would make native-pixel output unsafe.
        val projectedAtNative = PanoramaModule.Cylinder(
            nativeW, nativeH, nativeW * FOCAL_WIDTH_RATIO)
        val analysisXSpan = analysisGeometry.width + orderedOffsets.sumOf { it.dx }
        val analysisVerticalSpan = orderedOffsets.runningFold(0.0) { a, v -> a + v.dy }
        val analysisCommonH = analysisGeometry.height -
            ((analysisVerticalSpan.maxOrNull() ?: 0.0) -
             (analysisVerticalSpan.minOrNull() ?: 0.0))
        if (analysisCommonH < analysisGeometry.height * 0.67)
            throw IOException("Too much vertical drift for a single-row panorama")
        val fullEstimatedWidth = analysisXSpan * projectedAtNative.width / analysisGeometry.width
        val fullEstimatedHeight = analysisCommonH * projectedAtNative.height / analysisGeometry.height
        var scaleFactor = PanoramaOutputBudget.initialScale(
            fullEstimatedWidth, fullEstimatedHeight, maxOutputPixels)
        data class CompositionSize(
            val geometry:PanoramaModule.Cylinder,
            val x:IntArray,val y:IntArray,
            val minY:Int,val maxY:Int,val commonH:Int,val mosaicW:Int)
        fun layout(scale:Double):CompositionSize {
            val outputW = (nativeW * scale).roundToInt().coerceAtLeast(128)
            val outputH = (nativeH * scale).roundToInt().coerceAtLeast(128)
            val geometry = PanoramaModule.Cylinder(
                outputW, outputH, outputW * FOCAL_WIDTH_RATIO)
            val xScale = geometry.width.toDouble() / analysisGeometry.width
            val yScale = geometry.height.toDouble() / analysisGeometry.height
            val x = IntArray(frames.size); val y = IntArray(frames.size)
            for (i in orderedOffsets.indices) {
                x[i+1] = x[i] + (orderedOffsets[i].dx*xScale).roundToInt()
                y[i+1] = y[i] + (orderedOffsets[i].dy*yScale).roundToInt()
            }
            val minY = y.minOrNull() ?: 0
            val maxY = y.maxOrNull() ?: 0
            return CompositionSize(geometry,x,y,minY,maxY,
                geometry.height - (maxY-minY),x.last()+geometry.width)
        }
        var size = layout(scaleFactor)
        repeat(6) {
            if (size.mosaicW.toLong()*size.commonH in 1L..maxOutputPixels &&
                size.mosaicW <= PanoramaOutputBudget.MAX_JPEG_SIDE &&
                size.commonH <= PanoramaOutputBudget.MAX_JPEG_SIDE) return@repeat
            val actualPixels = size.mosaicW.toDouble()*size.commonH
            val pixelScale = sqrt(maxOutputPixels.toDouble()/max(1.0,actualPixels))*0.985
            val sideScale = min(PanoramaOutputBudget.MAX_JPEG_SIDE.toDouble()/size.mosaicW,
                PanoramaOutputBudget.MAX_JPEG_SIDE.toDouble()/size.commonH)*0.985
            scaleFactor *= min(0.95,min(pixelScale,sideScale))
            size = layout(scaleFactor)
        }
        val outputGeometry=size.geometry
        val x=size.x; val y=size.y
        val minY=size.minY; val maxY=size.maxY
        val commonH=size.commonH; val mosaicW=size.mosaicW
        if (commonH < outputGeometry.height * 0.67 ||
            mosaicW <= outputGeometry.width ||
            mosaicW > PanoramaOutputBudget.MAX_JPEG_SIDE ||
            mosaicW.toLong()*commonH > maxOutputPixels)
            throw IOException("Unable to fit panorama safely within device memory")
        val xScale=outputGeometry.width.toDouble()/analysisGeometry.width
        val yScale=outputGeometry.height.toDouble()/analysisGeometry.height
        Log.i(TAG,"Panorama output: source=${nativeW}x$nativeH " +
            "scale=${"%.3f".format(java.util.Locale.US,scaleFactor)} " +
            "panorama=${mosaicW}x$commonH maxPixels=$maxOutputPixels")
        val composeStartedNs = System.nanoTime()
        val bitmap = Bitmap.createBitmap(mosaicW, commonH, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            orderedFrames.forEachIndexed { index, frame ->
                checkCanceled(canceled)
                val original = PanoramaModule.decodeFrame(frame, outputGeometry.inputWidth, outputGeometry.inputHeight)
                val warped = try { outputGeometry.warp(original) } finally { original.recycle() }
                try {
                    val sourceTop = maxY - y[index]
                    if (index == 0) {
                        canvas.drawBitmap(warped,
                            Rect(0, sourceTop, warped.width, sourceTop + commonH),
                            Rect(0, 0, warped.width, commonH), paint)
                    } else {
                        val previousEnd = x[index - 1] + outputGeometry.width
                        val overlap = previousEnd - x[index]
                        if (overlap <= outputGeometry.width * 0.14)
                            throw IOException("Insufficient real overlap near JPEG ${index + 1}")
                        val clipped = Bitmap.createBitmap(warped, 0, sourceTop,
                            warped.width, commonH)
                        try {
                            PanoramaAdaptiveSeamComposer.overlay(
                                canvas, bitmap, clipped, x[index], previousEnd, canceled,
                                localFields[index - 1]?.scaled(xScale, yScale, sourceTop))
                        } finally { clipped.recycle() }
                    }
                } finally { warped.recycle() }
            }
            val resultBitmap = if (fullCircle) {
                val closure = opticalClosure ?: throw IOException("Missing 360° optical closure")
                val offset = circularOffset ?: throw IOException("Missing 360° direction")
                val circumference = x.last() + (offset * xScale).roundToInt()
                if (circumference < mosaicW * 0.60 ||
                    circumference > mosaicW - (outputGeometry.width * 0.02).roundToInt())
                    throw IOException("360° circumference not optically consistent")
                val joined = PanoramaCircularClosure.cropAndBlend(
                    bitmap, circumference, canceled)
                Log.i(TAG, "360° closure: inliers=${closure.inliers}, " +
                    "error=${closure.error}, offset=${offset}, width=$circumference")
                joined
            } else bitmap
            Log.i(TAG, "OpenCV-SIFT panorama: ${resultBitmap.width}x${resultBitmap.height} " +
                "frames=${frames.size} composeMs=${(System.nanoTime() - composeStartedNs) / 1_000_000} " +
                "noGyroFallbacks=true localWarpRowsCached=true zeroTileBitmaps=true")
            if (resultBitmap !== bitmap) bitmap.recycle()
            return PanoramaModule.Result(resultBitmap, 0)
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    private fun extract(detector: SIFT, bitmap: Bitmap): Features {
        val rgba = Mat()
        val gray = Mat()
        val mask = Mat()
        val keypoints = MatOfKeyPoint()
        val descriptors = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            // Exclude narrow borders of cylindrical images to avoid artificial corners.
            mask.create(gray.rows(), gray.cols(), CvType.CV_8UC1)
            mask.setTo(Scalar(0.0))
            Imgproc.rectangle(mask, Point(12.0, 12.0),
                Point((gray.cols() - 13).toDouble(), (gray.rows() - 13).toDouble()),
                Scalar(255.0), -1)
            detector.detectAndCompute(gray, mask, keypoints, descriptors)
            if (descriptors.empty()) throw IOException("No usable features in JPEG")
            return Features(keypoints.toArray(), descriptors)
        } catch (error: Throwable) {
            descriptors.release()
            throw error
        } finally {
            rgba.release()
            gray.release()
            mask.release()
            keypoints.release()
        }
    }

    private fun match(matcher: BFMatcher, before: Features, after: Features):
        List<PanoramaRobustTranslation.Match> {
        val knn = ArrayList<MatOfDMatch>()
        try {
            matcher.knnMatch(before.descriptors, after.descriptors, knn, 2)
            val result = ArrayList<PanoramaRobustTranslation.Match>()
            for (pairMat in knn) {
                val pair = pairMat.toArray()
                if (pair.size < 2 || pair[0].distance >= 0.76 * pair[1].distance) continue
                val earlier = before.points[pair[0].queryIdx].pt
                val later = after.points[pair[0].trainIdx].pt
                result.add(PanoramaRobustTranslation.Match(
                    earlier.x, earlier.y, later.x, later.y))
            }
            return result
        } finally {
            knn.forEach { it.release() }
        }
    }
}
