package app.grapheneos.camera.capturer

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


/**
 * Conservative, direction-neutral local refinement of an ALREADY VERIFIED SIFT
 * translation. Local offsets are derived from the same matched image points used
 * for registration, never from sensor angle or recording order.
 *
 * The field is deliberately bounded. Unsupported areas remain unchanged; strong
 * parallax/occlusions cannot be made correct by guessing a warp.
 */
internal object PanoramaLocalAlignmentModel {
    private const val GRID_X = 8
    private const val GRID_Y = 7
    private const val MAX_RESIDUAL = 10.0 // analysis pixels; registration itself stays untouched
    private const val MAX_WARP = 5.0     // analysis pixels; prevents uncontrolled distortion

    data class Vector(val x: Double, val y: Double)

    data class Field(val width: Int, val height: Int, val dx: DoubleArray, val dy: DoubleArray) {
        init { require(width > 0 && height > 0 && dx.size == GRID_X * GRID_Y && dy.size == dx.size) }
        fun at(x: Double, y: Double): Vector {
            val gx = (x.coerceIn(0.0, width.toDouble()) / width * (GRID_X - 1)).coerceIn(0.0, (GRID_X - 1).toDouble())
            val gy = (y.coerceIn(0.0, height.toDouble()) / height * (GRID_Y - 1)).coerceIn(0.0, (GRID_Y - 1).toDouble())
            val x0 = gx.toInt(); val y0 = gy.toInt()
            val x1 = min(GRID_X - 1, x0 + 1); val y1 = min(GRID_Y - 1, y0 + 1)
            val tx = gx - x0; val ty = gy - y0
            fun interp(grid: DoubleArray): Double {
                val top = grid[y0 * GRID_X + x0] * (1.0 - tx) + grid[y0 * GRID_X + x1] * tx
                val bottom = grid[y1 * GRID_X + x0] * (1.0 - tx) + grid[y1 * GRID_X + x1] * tx
                return top * (1.0 - ty) + bottom * ty
            }
            return Vector(interp(dx), interp(dy))
        }

        /** Coordinates of a cropped, cylindrical output bitmap, including scaling. */
        fun scaled(scaleX: Double, scaleY: Double, sourceTop: Int): ScaledField =
            ScaledField(this, scaleX, scaleY, sourceTop)
    }

    class ScaledField internal constructor(
        private val field: Field, private val scaleX: Double,
        private val scaleY: Double, private val sourceTop: Int
    ) {
        /** Source sample should be at (x - delta.x, y - delta.y). */
        fun at(x: Int, y: Int, overlap: Int): Vector {
            if (overlap <= 1) return Vector(0.0, 0.0)
            val fadeLength = max(8.0, overlap * 0.14)
            val fade = (min(x.toDouble(), (overlap - 1 - x).toDouble()) / fadeLength)
                .coerceIn(0.0, 1.0).let { it * it * (3.0 - 2.0 * it) }
            val offset = field.at(x / scaleX, (y + sourceTop) / scaleY)
            return Vector(offset.x * scaleX * fade, offset.y * scaleY * fade)
        }
        /**
         * Exact per-row equivalent of [at], without a Vector allocation or a
         * second 8x7 mesh lookup for each output pixel. The grid interpolation
         * and edge fade are still evaluated at EACH pixel: no resampling or
         * approximation is introduced into the geometrical correction.
         * Caller owns and reuses arrays of at least [overlap] elements.
         */
        fun fillRow(row: Int, overlap: Int, offsetX: DoubleArray, offsetY: DoubleArray) {
            require(offsetX.size >= overlap && offsetY.size >= overlap)
            if (overlap <= 1) {
                for (i in 0 until overlap) { offsetX[i] = 0.0; offsetY[i] = 0.0 }
                return
            }
            val gy = (((row + sourceTop) / scaleY).coerceIn(0.0, field.height.toDouble()) /
                field.height * (GRID_Y - 1)).coerceIn(0.0, (GRID_Y - 1).toDouble())
            val y0 = gy.toInt()
            val y1 = min(GRID_Y - 1, y0 + 1)
            val ty = gy - y0
            val fadeLength = max(8.0, overlap * 0.14)
            var previousCell = -1
            var x0Dx = 0.0; var x1Dx = 0.0
            var x0Dy = 0.0; var x1Dy = 0.0
            for (x in 0 until overlap) {
                val gx = ((x / scaleX).coerceIn(0.0, field.width.toDouble()) /
                    field.width * (GRID_X - 1)).coerceIn(0.0, (GRID_X - 1).toDouble())
                val x0 = gx.toInt()
                if (x0 != previousCell) {
                    val x1 = min(GRID_X - 1, x0 + 1)
                    fun vertical(grid: DoubleArray, column: Int): Double {
                        return grid[y0 * GRID_X + column] * (1.0 - ty) +
                            grid[y1 * GRID_X + column] * ty
                    }
                    x0Dx = vertical(field.dx, x0)
                    x1Dx = vertical(field.dx, x1)
                    x0Dy = vertical(field.dy, x0)
                    x1Dy = vertical(field.dy, x1)
                    previousCell = x0
                }
                val tx = gx - x0
                val fade = (min(x.toDouble(), (overlap - 1 - x).toDouble()) / fadeLength)
                    .coerceIn(0.0, 1.0).let { it * it * (3.0 - 2.0 * it) }
                offsetX[x] = (x0Dx * (1.0 - tx) + x1Dx * tx) * scaleX * fade
                offsetY[x] = (x0Dy * (1.0 - tx) + x1Dy * tx) * scaleY * fade
            }
        }

        val maxShiftY: Int get() = (MAX_WARP * scaleY + 3.0).toInt()
    }

    /** Flip temporal matches when the sequence is reversed into left-to-right spatial order. */
    fun canonicalMatches(matches: List<PanoramaRobustTranslation.Match>, reversed: Boolean):
        List<PanoramaRobustTranslation.Match> = if (!reversed) matches else matches.map {
            PanoramaRobustTranslation.Match(it.nextX, it.nextY, it.prevX, it.prevY)
        }

    fun estimate(
        matches: List<PanoramaRobustTranslation.Match>,
        global: PanoramaRobustTranslation.Result, width: Int, height: Int
    ): Field? {
        if (width < 64 || height < 64 || matches.size < 25 || global.inliers < 20) return null
        data class Anchor(val x: Double, val y: Double, val dx: Double, val dy: Double)
        val anchors = matches.mapNotNull { m ->
            val dx = m.prevX - m.nextX - global.dx
            val dy = m.prevY - m.nextY - global.dy
            if (!dx.isFinite() || !dy.isFinite() || abs(dx) > MAX_RESIDUAL || abs(dy) > MAX_RESIDUAL ||
                m.nextX !in 0.0..width.toDouble() || m.nextY !in 0.0..height.toDouble()) null
            else Anchor(m.nextX, m.nextY, dx, dy)
        }
        if (anchors.size < 20) return null
        val sx = width / 3.0
        val sy = height / 3.0
        val dx = DoubleArray(GRID_X * GRID_Y)
        val dy = DoubleArray(GRID_X * GRID_Y)
        val reliable = BooleanArray(dx.size)
        for (gy in 0 until GRID_Y) for (gx in 0 until GRID_X) {
            val x = width.toDouble() * gx / (GRID_X - 1)
            val y = height.toDouble() * gy / (GRID_Y - 1)
            val neighbors = anchors.mapNotNull { a ->
                val normalized = ((a.x - x) / sx) * ((a.x - x) / sx) +
                    ((a.y - y) / sy) * ((a.y - y) / sy)
                if (normalized > 1.6) null else a to (1.0 / (1.0 + normalized * 3.0))
            }
            val index = gy * GRID_X + gx
            if (neighbors.size < 6) continue
            val weightSum = neighbors.sumOf { it.second }
            if (weightSum < 2.5) continue
            // Resistant to a second moving plane: a local median provides a robust
            // centre; outlying displacements are not used to bend the geometry.
            fun median(values: List<Double>): Double {
                val s = values.sorted(); return (s[(s.size - 1)/2] + s[s.size/2]) / 2.0
            }
            val centerX = median(neighbors.map { it.first.dx })
            val centerY = median(neighbors.map { it.first.dy })
            val consistent = neighbors.filter {
                abs(it.first.dx - centerX) <= 3.0 && abs(it.first.dy - centerY) <= 3.0
            }
            if (consistent.size < 5 || consistent.size * 2 < neighbors.size) continue
            val sum = consistent.sumOf { it.second }
            val iDx = consistent.sumOf { it.first.dx * it.second } / sum
            val iDy = consistent.sumOf { it.first.dy * it.second } / sum
            // Regularization toward the globally verified motion. Regions with no
            // strong evidence are not permitted to invent large local deviations.
            val strength = (sum / (sum + 2.5)).coerceIn(0.0, 1.0)
            dx[index] = (iDx * strength).coerceIn(-MAX_WARP, MAX_WARP)
            dy[index] = (iDy * strength).coerceIn(-MAX_WARP, MAX_WARP)
            reliable[index] = true
        }
        if (reliable.count { it } < 3) return null
        // A single smoothing pass makes mesh gradients continuous and reduces
        // pressure to bend a straight structure at an isolated noisy match.
        fun smooth(grid: DoubleArray): DoubleArray = DoubleArray(grid.size) { index ->
            if (!reliable[index]) 0.0 else {
                val gx = index % GRID_X; val gy = index / GRID_X
                var total = grid[index] * 4.0; var weight = 4.0
                for ((nx, ny) in arrayOf(gx - 1 to gy, gx + 1 to gy, gx to gy - 1, gx to gy + 1)) {
                    if (nx in 0 until GRID_X && ny in 0 until GRID_Y) {
                        val n = ny * GRID_X + nx
                        if (reliable[n]) { total += grid[n]; weight += 1.0 }
                    }
                }
                (total / weight).coerceIn(-MAX_WARP, MAX_WARP)
            }
        }
        val finalX = smooth(dx); val finalY = smooth(dy)
        if (finalX.indices.maxOf { max(abs(finalX[it]), abs(finalY[it])) } < 0.30) return null
        return Field(width, height, finalX, finalY)
    }
}
