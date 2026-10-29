package app.grapheneos.camera.ui.components.choicebutton

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

@Immutable
internal data class ChoiceButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val selectedContainerColor: Color,
    val selectedContentColor: Color,
) {

    internal fun containerColor(selected: Boolean): Color {
        return when {
            selected -> selectedContainerColor
            else -> containerColor
        }
    }

    internal fun contentColor(selected: Boolean): Color {
        return when {
            selected -> selectedContentColor
            else -> contentColor
        }
    }

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromSurfaceTheme(): ChoiceButtonColors {
            val colorScheme = MaterialTheme.colorScheme

            return ChoiceButtonColors(
                containerColor = colorScheme.surfaceContainerHighest,
                contentColor = colorScheme.onSurface,
                selectedContainerColor = colorScheme.primary,
                selectedContentColor = colorScheme.onPrimary,
            )
        }
    }
}
