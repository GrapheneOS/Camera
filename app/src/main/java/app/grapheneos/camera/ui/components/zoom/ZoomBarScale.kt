package app.grapheneos.camera.ui.components.zoom

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.ui.components.ruler.RulerScale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Each span between neighboring stops gets a whole number of evenly spaced ticks, five per
 * octave (a doubling), so every stop falls exactly on a tick.
 */
@Immutable
internal data class ZoomBarScale(
    val valueRange: ClosedFloatingPointRange<Float>,
    val stops: List<Float>,
) : RulerScale {

    override val lastTick: Int

    val marks: List<Float>
    val markTicks: List<Int>

    private val anchors: FloatArray
    private val anchorTicks: IntArray
    private val ticksPerOctave: FloatArray
    private val majorTicks: BooleanArray

    init {
        require(valueRange.start > 0f && valueRange.endInclusive > valueRange.start) {
            "valueRange must be positive and not empty, was $valueRange"
        }
        require(stops.all { stop -> stop in valueRange }) {
            "stops must be inside $valueRange, were $stops"
        }
        require(stops.zipWithNext().all { (previous, next) -> previous < next }) {
            "stops must be ascending, were $stops"
        }

        anchors = (listOf(valueRange.start) + stops + valueRange.endInclusive)
            .distinct()
            .toFloatArray()
        anchorTicks = IntArray(anchors.size)
        ticksPerOctave = FloatArray(anchors.size - 1)

        for (span in ticksPerOctave.indices) {
            val octaves = log2(anchors[span + 1] / anchors[span])
            val ticks = max(1, (octaves * TICKS_PER_OCTAVE).roundToInt())

            anchorTicks[span + 1] = anchorTicks[span] + ticks
            ticksPerOctave[span] = ticks / octaves
        }

        lastTick = anchorTicks.last()
        marks = (stops + valueRange.endInclusive).distinct()
        markTicks = marks.map { mark ->
            anchorTicks[anchors.indexOfFirst { anchor -> anchor == mark }]
        }
        majorTicks = BooleanArray(lastTick + 1) { tick -> tick in markTicks }
    }

    override fun isMajor(tick: Int): Boolean {
        return majorTicks[tick]
    }

    override fun position(value: Float): Float {
        val clamped = value.coerceIn(valueRange.start, valueRange.endInclusive)
        val span = spanOf { index -> clamped <= anchors[index + 1] }

        return when (clamped) {
            anchors[span + 1] -> anchorTicks[span + 1].toFloat()
            else -> anchorTicks[span] + log2(clamped / anchors[span]) * ticksPerOctave[span]
        }
    }

    override fun value(position: Float): Float {
        val clamped = position.coerceIn(0f, lastTick.toFloat())
        val span = spanOf { index -> clamped <= anchorTicks[index + 1] }

        return when (clamped) {
            anchorTicks[span + 1].toFloat() -> anchors[span + 1]
            else -> anchors[span] * 2f.pow((clamped - anchorTicks[span]) / ticksPerOctave[span])
        }
    }

    fun isStop(position: Float): Boolean {
        val tick = position.toInt()

        return tick.toFloat() == position && majorTicks[tick]
    }

    fun firstStop(
        from: Float,
        to: Float,
    ): Float? {
        val ticks = when {
            to > from -> floor(from).toInt() + 1..floor(to).toInt()
            else -> ceil(from).toInt() - 1 downTo ceil(to).toInt()
        }

        return ticks.firstOrNull { tick -> majorTicks[tick] }?.toFloat()
    }

    private inline fun spanOf(isInSpan: (index: Int) -> Boolean): Int {
        val lastSpan = ticksPerOctave.lastIndex

        for (index in 0 until lastSpan) {
            if (isInSpan(index)) return index
        }
        return lastSpan
    }

    private companion object {
        private const val TICKS_PER_OCTAVE = 5f
    }
}
