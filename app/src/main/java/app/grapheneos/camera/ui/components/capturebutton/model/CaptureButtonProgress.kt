package app.grapheneos.camera.ui.components.capturebutton.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.semantics.ProgressBarRangeInfo

@Immutable
internal sealed interface CaptureButtonProgress {

    val rangeInfo: ProgressBarRangeInfo?

    data object None : CaptureButtonProgress {
        override val rangeInfo: ProgressBarRangeInfo? = null
    }

    data object Indeterminate : CaptureButtonProgress {
        override val rangeInfo: ProgressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }

    data class Determinate(
        val fraction: Float,
    ) : CaptureButtonProgress {

        init {
            require(fraction in 0f..1f) {
                "fraction must be within 0..1, was $fraction"
            }
        }

        override val rangeInfo: ProgressBarRangeInfo
            get() {
                return ProgressBarRangeInfo(
                    current = fraction,
                    range = 0f..1f,
                )
            }
    }

    data class Segmented(
        val segments: Int,
        val filled: Int,
    ) : CaptureButtonProgress {

        init {
            require(segments > 0) {
                "segments must be positive, was $segments"
            }
            require(filled in 0..segments) {
                "filled must be within 0..$segments, was $filled"
            }
        }

        val fraction: Float
            get() {
                return filled.toFloat() / segments
            }

        override val rangeInfo: ProgressBarRangeInfo
            get() {
                return ProgressBarRangeInfo(
                    current = filled.toFloat(),
                    range = 0f..segments.toFloat(),
                    steps = segments - 1,
                )
            }
    }
}
