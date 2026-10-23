package app.grapheneos.camera.capturer

import android.graphics.Bitmap
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A conservative optical closure gate for a full revolution.
 * The ordinary 180° path never invokes this module. Reject rather than silently
 * publish a panoramic strip whose first and last photos do not match.
 */
internal object PanoramaCircularClosure {
    data class Result(val dx:Double, val dy:Double, val inliers:Int,
                      val candidates:Int, val error:Double)

    /** Unlike adjacent-pair RANSAC, the closure may have nearly zero displacement. */
    fun estimate(matches:List<PanoramaRobustTranslation.Match>, w:Int, h:Int):Result? {
        if (matches.size < 24 || w <= 0 || h <= 0) return null
        val shifts = matches.map { (it.prevX-it.nextX) to (it.prevY-it.nextY) }
        var best = emptyList<Pair<Double,Double>>()
        val stride = max(1, shifts.size/128)
        for (i in shifts.indices step stride) {
            val (dx,dy) = shifts[i]
            if (abs(dx)>w*0.42 || abs(dy)>h*0.14) continue
            val group = shifts.filter { abs(it.first-dx)<5.0 && abs(it.second-dy)<6.5 }
            if (group.size>best.size) best=group
        }
        if (best.size<20 || best.size<matches.size*0.16) return null
        // A second comparably strong, contradictory displacement means repeated
        // textures (e.g. blinds) rather than a trustworthy loop closure.
        val firstSeed=best.first()
        val alternatives=shifts.filter {
            abs(it.first-firstSeed.first)>8.0 || abs(it.second-firstSeed.second)>10.0
        }
        if (alternatives.isNotEmpty()) {
            val alternativeScore=alternatives.maxOf {seed->alternatives.count {
                abs(it.first-seed.first)<5.0 && abs(it.second-seed.second)<6.5
            }}
            if(alternativeScore>=best.size*0.6) return null
        }
        val dx=median(best.map {it.first})
        val dy=median(best.map {it.second})
        val inlierMatches = matches.filter {
            abs((it.prevX-it.nextX)-dx)<=6.0 && abs((it.prevY-it.nextY)-dy)<=8.0
        }
        // Textureless curtains, repeated fence slats and accidental identical patches
        // are weak evidence of an actual spherical loop closure.
        if (inlierMatches.size<20 || inlierMatches.size<matches.size*0.16) return null
        val horizontalSpan = (inlierMatches.maxOf {it.prevX} - inlierMatches.minOf {it.prevX})
        val verticalSpan = (inlierMatches.maxOf {it.prevY} - inlierMatches.minOf {it.prevY})
        if (horizontalSpan < w*0.16 || verticalSpan < h*0.18) return null
        val err = median(inlierMatches.map { kotlin.math.hypot(
            it.prevX-it.nextX-dx, it.prevY-it.nextY-dy) })
        if (err > 5.5) return null
        return Result(dx,dy,inlierMatches.size,matches.size,err)
    }

    private fun median(vals: List<Double>): Double {
        val s=vals.sorted(); return if(s.size%2!=0) s[s.size/2]
            else (s[s.size/2-1]+s[s.size/2])/2
    }

    /**
     * Remove the duplicated beginning of a completed revolution. The first and last
     * columns represent adjacent orientations; feather them to identical edge colors.
     * Memory: one final-size bitmap, plus O(width of feather) scratch per row.
     */
    fun cropAndBlend(source:Bitmap, circumference:Int, canceled:()->Boolean):Bitmap {
        if (circumference<=source.width/2 || circumference>=source.width)
            throw IOException("Invalid 360° circumference")
        val redundant=source.width-circumference
        if (redundant<12) throw IOException("No 360° overlap for the closing seam")
        // Place the seam slightly inside the optically verified duplicate interval.
        val start=min(48,redundant/4)
        if (start+circumference>source.width) throw IOException("Invalid loop crop")
        val output=Bitmap.createBitmap(source,start,0,circumference,source.height)
        try {
            val band=min(32,(circumference/64).coerceAtLeast(2))
            val l=IntArray(band);val r=IntArray(band)
            for (row in 0 until output.height) {
                if(row%64==0 && canceled()) throw IOException("360° joining canceled")
                output.getPixels(l,0,band,0,row,band,1)
                output.getPixels(r,0,band,circumference-band,row,band,1)
                for(i in 0 until band) {
                    val strength=(1.0-i.toDouble()/band)*0.5
                    l[i]=lerp(l[i],r[band-1-i],strength)
                    r[band-1-i]=lerp(r[band-1-i],l[i],strength)
                }
                // Force exact edge continuity at the wrap.
                val edge=average(l[0],r[band-1]);l[0]=edge;r[band-1]=edge
                output.setPixels(l,0,band,0,row,band,1)
                output.setPixels(r,0,band,circumference-band,row,band,1)
            }
            return output
        } catch(t:Throwable) {output.recycle();throw t}
    }

    private fun average(a:Int,b:Int):Int=lerp(a,b,0.5)
    private fun lerp(a:Int,b:Int,t:Double):Int {
        fun ch(shift:Int):Int=(((a ushr shift) and 255)*(1-t)+
                                  ((b ushr shift) and 255)*t).toInt().coerceIn(0,255)
        return -0x1000000 or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
