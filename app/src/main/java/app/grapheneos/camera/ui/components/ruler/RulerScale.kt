package app.grapheneos.camera.ui.components.ruler

import androidx.compose.runtime.Immutable

@Immutable
internal interface RulerScale {

    val lastTick: Int

    fun isMajor(tick: Int): Boolean

    fun position(value: Float): Float

    fun value(position: Float): Float
}
