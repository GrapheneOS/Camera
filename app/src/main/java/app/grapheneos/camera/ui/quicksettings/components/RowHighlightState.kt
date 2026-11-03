package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.grapheneos.camera.ui.core.animation.blink
import app.grapheneos.camera.ui.core.model.QuickSetting

@Stable
internal class RowHighlightState {

    private val alpha = Animatable(initialValue = 0f)

    private var setting by mutableStateOf<QuickSetting?>(null)

    suspend fun blink(setting: QuickSetting) {
        this.setting = setting
        try {
            alpha.blink(times = BLINK_COUNT)
        } finally {
            this.setting = null
        }
    }

    fun alphaOf(row: QuickSetting): Float {
        return when (row) {
            setting -> alpha.value
            else -> 0f
        }
    }

    private companion object {
        const val BLINK_COUNT = 2
    }
}
