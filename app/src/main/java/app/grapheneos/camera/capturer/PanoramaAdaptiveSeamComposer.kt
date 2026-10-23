package app.grapheneos.camera.capturer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.min

/**
 * Bounded-memory adaptive seam compositing for already aligned cylindrical JPEGs.
 * Optical registration remains the authority; this changes ONLY the transition
 * between registered pictures. No scene-dependent templates or gyro shortcuts.
 *
 * 4px samples feed a dynamic-programming seam; final image is rendered in 24-row
 * strips instead of allocating an additional whole-panorama bitmap.
 */
internal object PanoramaAdaptiveSeamComposer {
    private const val SAMPLE=4
    private const val TILE_ROWS=24

    fun overlay(canvas:Canvas, destination:Bitmap, next:Bitmap,
                origin:Int, previousEnd:Int,
                canceled:()->Boolean={false},
                localWarp: PanoramaLocalAlignmentModel.ScaledField?=null) {
        require(next.height==destination.height)
        val overlap=min(previousEnd-origin,next.width)
        require(origin>=0 && overlap>=48 && origin+next.width<=destination.width)
        val h=next.height
        val gridW=(overlap+SAMPLE-1)/SAMPLE
        val gridH=(h+SAMPLE-1)/SAMPLE
        val oldGrid=IntArray(gridW*gridH)
        val newGrid=IntArray(gridW*gridH)
        val oldRow=IntArray(overlap)
        val newRow=IntArray(overlap)
        // Reuse the padded source scanlines for both seam analysis and 24-row
        // composition tiles; no additional large bitmap or per-row allocation.
        val warpScratch=localWarp?.let {
            IntArray(next.width*(TILE_ROWS+2*it.maxShiftY+3))
        }
        // Local offsets are reusable within the current overlap. This keeps the
        // Preserve seam geometry while avoiding millions of
        // short-lived Vector objects and repeated mesh grid reads.
        val rowOffsetX = if (localWarp != null) DoubleArray(overlap) else null
        val rowOffsetY = if (localWarp != null) DoubleArray(overlap) else null
        for (gy in 0 until gridH) {
            if (canceled()) throw CancellationException("Panorama canceled")
            val y=min(h-1,gy*SAMPLE)
            destination.getPixels(oldRow,0,overlap,origin,y,overlap,1)
            if (localWarp == null) next.getPixels(newRow,0,overlap,0,y,overlap,1)
            else correctedPixels(next, newRow, overlap, y, 1, localWarp, warpScratch!!,
                rowOffsetX!!, rowOffsetY!!)
            for (gx in 0 until gridW) {
                val x=min(overlap-1,gx*SAMPLE)
                oldGrid[gy*gridW+gx]=oldRow[x]
                newGrid[gy*gridW+gx]=newRow[x]
            }
        }
        val seam=PanoramaAdaptiveSeamModel.select(oldGrid,newGrid,gridW,gridH,SAMPLE)
        val gains=seam.gains
        val paint=Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        paint.colorFilter=ColorMatrixColorFilter(floatArrayOf(
            gains.red,0f,0f,0f,0f,
            0f,gains.green,0f,0f,0f,
            0f,0f,gains.blue,0f,0f,
            0f,0f,0f,1f,0f
        ))
        // This section has no preceding image. Copy it directly with the same
        // exposure correction used in the overlap, preventing brightness jumps.
        if(overlap<next.width) {
            canvas.drawBitmap(next,Rect(overlap,0,next.width,h),
                Rect(origin+overlap,0,origin+next.width,h),paint)
        }
        paint.colorFilter=null
        val oldTile=IntArray(overlap*TILE_ROWS)
        val newTile=IntArray(overlap*TILE_ROWS)
        val outTile=IntArray(overlap*TILE_ROWS)
        var y=0
        while (y<h) {
            if (canceled()) throw CancellationException("Panorama canceled")
            val rows=min(TILE_ROWS,h-y)
            destination.getPixels(oldTile,0,overlap,origin,y,overlap,rows)
            if (localWarp == null) next.getPixels(newTile,0,overlap,0,y,overlap,rows)
            else correctedPixels(next, newTile, overlap, y, rows, localWarp, warpScratch!!,
                rowOffsetX!!, rowOffsetY!!)
            for (r in 0 until rows) {
                val boundary=PanoramaAdaptiveSeamModel.seamColumnAt(
                    seam,y+r,overlap,gridH)
                for (x in 0 until overlap) {
                    val pos=r*overlap+x
                    val before=oldTile[pos]
                    outTile[pos]=when {
                        x <= boundary-21 -> before
                        x >= boundary+21 -> gains.apply(newTile[pos])
                        else -> {
                            val after=gains.apply(newTile[pos])
                            val xPrev=(x-2).coerceAtLeast(0)
                            val xNext=(x+2).coerceAtMost(overlap-1)
                            val oldEdge=grayDifference(oldTile[r*overlap+xPrev],
                                oldTile[r*overlap+xNext])
                            val newEdge=grayDifference(newTile[r*overlap+xPrev],
                                newTile[r*overlap+xNext])
                            val radius=PanoramaAdaptiveSeamModel.featherRadius(
                                before,after,oldEdge,newEdge)
                            val progress=((x-boundary).toFloat()/radius+1f)*0.5f
                            val smooth=progress.coerceIn(0f,1f).let {it*it*(3f-2f*it)}
                            when {
                                smooth<=0f -> before
                                smooth>=1f -> after
                                else -> PanoramaAdaptiveSeamModel.blend(before,after,smooth)
                            }
                        }
                    }
                }
            }
            // Write directly into the existing mutable ARGB panorama. Creating a
            // new Bitmap for every 24-row tile triggered thousands of native
            // allocations and GC pauses on long 180-degree panoramas.
            destination.setPixels(outTile, 0, overlap, origin, y, overlap, rows)
            y+=rows
        }
    }

    /**
     * Remap one tile in the already registered overlap. Bilinear reads come from
     * a padded row slice, not from per-pixel Bitmap.getPixel JNI calls. The mesh
     * tapers to zero at BOTH overlap boundaries, keeping the untouched portion of
     * the next JPEG continuous with the local correction.
     */
    private fun correctedPixels(
        next: Bitmap, output: IntArray, width: Int, startY: Int, rows: Int,
        warp: PanoramaLocalAlignmentModel.ScaledField, source: IntArray,
        rowOffsetX: DoubleArray, rowOffsetY: DoubleArray
    ) {
        val pad = warp.maxShiftY
        val sourceTop = (startY - pad).coerceAtLeast(0)
        val sourceBottom = (startY + rows + pad + 1).coerceAtMost(next.height)
        val sourceRows = sourceBottom - sourceTop
        val sourceWidth = next.width
        require(source.size >= sourceWidth * sourceRows)
        next.getPixels(source, 0, sourceWidth, 0, sourceTop, sourceWidth, sourceRows)
        fun pix(x: Int, y: Int): Int = source[(y - sourceTop) * sourceWidth + x]
        for (r in 0 until rows) {
            val row = startY + r
            warp.fillRow(row, width, rowOffsetX, rowOffsetY)
            for (x in 0 until width) {
                val sx = (x - rowOffsetX[x]).coerceIn(0.0, (next.width - 1).toDouble())
                val sy = (row - rowOffsetY[x]).coerceIn(sourceTop.toDouble(), (sourceBottom - 1).toDouble())
                val x0 = sx.toInt(); val y0 = sy.toInt()
                val x1 = min(next.width - 1, x0 + 1); val y1 = min(sourceBottom - 1, y0 + 1)
                val fx = sx - x0; val fy = sy - y0
                val p00 = pix(x0, y0); val p10 = pix(x1, y0)
                val p01 = pix(x0, y1); val p11 = pix(x1, y1)
                fun channel(shift: Int): Int {
                    val top = ((p00 ushr shift) and 255) * (1.0 - fx) +
                        ((p10 ushr shift) and 255) * fx
                    val bottom = ((p01 ushr shift) and 255) * (1.0 - fx) +
                        ((p11 ushr shift) and 255) * fx
                    return (top * (1.0 - fy) + bottom * fy).toInt().coerceIn(0, 255)
                }
                output[r * width + x] = (0xff shl 24) or
                    (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
            }
        }
    }

    private fun grayDifference(a:Int,b:Int):Int {
        fun luma(p:Int):Int = (((p ushr 16) and 255)*77 +
            ((p ushr 8) and 255)*150 + (p and 255)*29) shr 8
        return abs(luma(a)-luma(b))
    }
}
