package app.grapheneos.camera.ui.components.gesture

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal class DragEndInteractions(
    private val onStop: () -> Unit,
    private val onCancel: () -> Unit,
    val forwardTo: MutableInteractionSource? = null,
) : MutableInteractionSource {

    override val interactions: Flow<Interaction> = forwardTo?.interactions ?: emptyFlow()

    override suspend fun emit(interaction: Interaction) {
        onInteraction(interaction = interaction)
        forwardTo?.emit(interaction)
    }

    override fun tryEmit(interaction: Interaction): Boolean {
        onInteraction(interaction = interaction)
        return forwardTo?.tryEmit(interaction) ?: true
    }

    private fun onInteraction(interaction: Interaction) {
        when (interaction) {
            is DragInteraction.Stop -> onStop()
            is DragInteraction.Cancel -> onCancel()
        }
    }
}
