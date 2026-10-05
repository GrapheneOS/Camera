package app.grapheneos.camera.ui.components.progress.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.semantics.ProgressBarRangeInfo

@Stable
internal sealed interface RingProgress {

    val rangeInfo: ProgressBarRangeInfo?

    data object None : RingProgress {
        override val rangeInfo: ProgressBarRangeInfo? = null
    }

    data object Indeterminate : RingProgress {
        override val rangeInfo: ProgressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }

    class Determinate(
        val fraction: () -> Float,
    ) : RingProgress {

        override val rangeInfo: ProgressBarRangeInfo
            get() {
                return fractionRangeInfo(fraction = fraction())
            }
    }

    class Segmented(
        val segments: Int,
        val fraction: () -> Float,
    ) : RingProgress {

        init {
            require(segments > 0) {
                "segments must be positive, was $segments"
            }
        }

        override val rangeInfo: ProgressBarRangeInfo
            get() {
                return fractionRangeInfo(fraction = fraction())
            }
    }

    private companion object {

        private fun fractionRangeInfo(fraction: Float): ProgressBarRangeInfo {
            return ProgressBarRangeInfo(
                current = fraction.coerceIn(0f, 1f),
                range = 0f..1f,
            )
        }
    }
}
