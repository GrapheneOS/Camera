package app.grapheneos.camera.capturer

import kotlin.math.abs

/** Deliberately doesn't hard-reject large gaps: gyroscope helps CAPTURE, not registration. */
internal object PanoramaSweepPolicy {
    const val STEP_RADIANS=0.13f
    // 180 degree angular target; always based on first vs last *stored* JPEG.
    const val AUTO_FINISH_RADIANS=3.1415927f
    const val MIN_CAPTURE_INTERVAL_MS=290L

    fun direction(current:Int, sinceLast:Float, level:Boolean):Int = when {
        current!=0 -> current
        !level || abs(sinceLast)<STEP_RADIANS -> 0
        sinceLast>0 -> 1
        else -> -1
    }

    // Denser *bridging* frames on feature-poor scenes; never reject after a 21° gap.
    fun shouldCapture(direction:Int, delta:Float, level:Boolean, elapsedMs:Long,
                      opticalReliable:Boolean=true):Boolean {
        val step = if (opticalReliable) STEP_RADIANS else 0.105f
        return direction!=0 && level && delta*direction>=step &&
            elapsedMs>=MIN_CAPTURE_INTERVAL_MS
    }

    fun selectedSensorCoverage(first:Float?, last:Float?):Float =
        if(first==null || last==null) 0f else abs(last-first)

    const val FULL_CIRCLE_RADIANS = 6.2831855f
    const val FULL_CIRCLE_MIN_RADIANS = 5.55f
    fun mayAutoFinishSelected(first:Float?, last:Float?,
                              targetRadians:Float = AUTO_FINISH_RADIANS):Boolean =
        selectedSensorCoverage(first,last)>=targetRadians
}
