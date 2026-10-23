package app.grapheneos.camera.capturer

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Camera/scene-independent photometric seam selector.
 *
 * The inputs are two regularly sampled, already REGISTERED overlap regions in
 * exactly the same coordinate system. No assumptions about scene content,
 * sweep direction, image names or sensor signs are made here.
 * All arrays are small samples (normally <= 1/16 of the overlap pixels).
 */
internal object PanoramaAdaptiveSeamModel {
    data class Gains(val red: Float, val green: Float, val blue: Float) {
        fun apply(rgb: Int): Int {
            val r = (((rgb ushr 16) and 255) * red).toInt().coerceIn(0, 255)
            val g = (((rgb ushr 8) and 255) * green).toInt().coerceIn(0, 255)
            val b = ((rgb and 255) * blue).toInt().coerceIn(0, 255)
            return (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    data class Seam(val sampleColumns: IntArray, val gains: Gains, val sampleStep: Int)

    private fun channel(rgb: Int, shift: Int): Int = (rgb ushr shift) and 255
    private fun gray(rgb: Int): Int =
        (channel(rgb,16)*77 + channel(rgb,8)*150 + channel(rgb,0)*29) shr 8

    fun gains(previous: IntArray, next: IntArray, width: Int, height: Int): Gains {
        require(width > 4 && height > 4 && previous.size == width*height && next.size == width*height)
        val red = ArrayList<Float>()
        val green = ArrayList<Float>()
        val blue = ArrayList<Float>()
        for (y in 2 until height-2 step 3) for (x in width/5 until width*4/5 step 3) {
            val i = y*width+x
            val a=previous[i]; val b=next[i]
            if (abs(gray(a)-gray(b))>65) continue // motion / misregistration
            for (c in 0..2) {
                val shift = (2-c)*8
                val from=channel(a,shift); val to=channel(b,shift)
                if (from in 35..220 && to in 35..220) {
                    val ratio=(from+8f)/(to+8f)
                    when (c) { 0 -> red.add(ratio); 1 -> green.add(ratio); else -> blue.add(ratio) }
                }
            }
        }
        fun median(values: MutableList<Float>): Float {
            if (values.size < 30) return 1f
            values.sort()
            return values[values.size/2].coerceIn(0.86f,1.14f)
        }
        return Gains(median(red),median(green),median(blue))
    }

    /** Dynamic programming through low-disagreement, low-structure regions. */
    fun select(previous: IntArray, next: IntArray,
               width: Int, height: Int, sampleStep: Int = 4): Seam {
        require(width >= 12 && height >= 5 && previous.size == width*height && next.size == width*height)
        val gains=gains(previous,next,width,height)
        val left = max(2,(width*0.13).toInt())
        val right = min(width-3,(width*0.87).toInt())
        val columns=right-left+1
        var previousCosts=DoubleArray(columns)
        val back=IntArray(columns*height)
        val center=(left+right)/2.0
        for (y in 0 until height) {
            val current=DoubleArray(columns)
            for (col in 0 until columns) {
                val x=left+col
                val i=y*width+x
                val a=previous[i]
                val b=gains.apply(next[i])
                val diff=(abs(channel(a,16)-channel(b,16)) +
                    abs(channel(a,8)-channel(b,8)) +
                    abs(channel(a,0)-channel(b,0)))/3.0
                val xLeft=i-1; val xRight=i+1
                val oldEdge=abs(gray(previous[xLeft])-gray(previous[xRight]))
                val newEdge=abs(gray(next[xLeft])-gray(next[xRight]))
                val oldVertical=if(y>0 && y<height-1)
                    abs(gray(previous[i-width])-gray(previous[i+width])) else 0
                val newVertical=if(y>0 && y<height-1)
                    abs(gray(next[i-width])-gray(next[i+width])) else 0
                // Avoid strong edges and disagreeing structures. A short path
                // through a bland patch is preferable to cutting a doorframe.
                val edges=(oldEdge+newEdge+oldVertical+newVertical)*0.33
                val mismatch=abs(oldEdge-newEdge)*0.40 +
                    abs(oldVertical-newVertical)*0.40
                val centerPenalty=abs(x-center)*3.0/max(1,columns)
                val energy=diff*1.7 + edges + mismatch + centerPenalty
                if (y==0) { current[col]=energy; back[col]=col }
                else {
                    var best=Double.POSITIVE_INFINITY
                    var bestPrev=col
                    for (p in max(0,col-2)..min(columns-1,col+2)) {
                        val trial=previousCosts[p] + abs(col-p)*5.0
                        if (trial<best) {best=trial;bestPrev=p}
                    }
                    current[col]=best+energy
                    back[y*columns+col]=bestPrev
                }
            }
            previousCosts=current
        }
        var col=previousCosts.indices.minByOrNull { previousCosts[it] } ?: columns/2
        val path=IntArray(height)
        for (y in height-1 downTo 0) {
            path[y]=left+col
            col=back[y*columns+col]
        }
        return Seam(path,gains,sampleStep)
    }

    fun seamColumnAt(seam: Seam, fullRow: Int, fullWidth: Int, sampleHeight: Int): Int {
        val scaledRow=(fullRow.toDouble()/seam.sampleStep).coerceIn(0.0, (sampleHeight-1).toDouble())
        val lo=scaledRow.toInt()
        val hi=min(sampleHeight-1,lo+1)
        val f=scaledRow-lo
        return ((seam.sampleColumns[lo]*(1.0-f) + seam.sampleColumns[hi]*f) *
            seam.sampleStep).toInt().coerceIn(0,fullWidth-1)
    }

    /** Narrow feather around high-contrast edges to avoid double contours. */
    fun featherRadius(oldPixel: Int, newPixel: Int, oldEdge: Int, newEdge: Int): Int {
        val discrepancy=abs(gray(oldPixel)-gray(newPixel))
        val texture=max(oldEdge,newEdge)
        return when {
            texture>65 || discrepancy>65 -> 2
            texture>35 || discrepancy>35 -> 5
            texture>15 || discrepancy>20 -> 10
            else -> 20
        }
    }

    fun blend(previous:Int, next:Int, weight:Float):Int {
        val w=weight.coerceIn(0f,1f)
        fun mix(shift:Int): Int {
            val a=channel(previous,shift)
            val b=channel(next,shift)
            return (a+(b-a)*w).toInt().coerceIn(0,255)
        }
        return (0xff shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}
