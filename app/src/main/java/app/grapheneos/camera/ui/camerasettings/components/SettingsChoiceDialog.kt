package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private val ROW_SPACING = 16.dp

@Composable
internal fun <T> SettingsChoiceDialog(
    title: String,
    choices: ImmutableList<SettingsChoice<T>>,
    selected: T,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIndex by rememberSaveable {
        mutableIntStateOf(choices.indexOfFirst { it.value == selected })
    }

    SettingsDialogFrame(
        title = title,
        onConfirm = { choices.getOrNull(selectedIndex)?.let { onConfirm(it.value) } },
        onDismiss = onDismiss,
    ) {
        SettingsChoices(
            choices = choices,
            selectedIndex = selectedIndex,
            onSelect = { selectedIndex = it },
        )
    }
}

@Composable
private fun <T> SettingsChoices(
    choices: ImmutableList<SettingsChoice<T>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        choices.forEachIndexed { index, choice ->
            SettingsChoiceRow(
                choice = choice,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    choice: SettingsChoice<*>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(
                horizontal = DIALOG_PADDING,
                vertical = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(modifier = Modifier.width(ROW_SPACING))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = choice.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (choice.summary != null) {
                Text(
                    text = choice.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (choice.trailing != null) {
            Spacer(modifier = Modifier.width(ROW_SPACING))
            choice.trailing.invoke()
        }
    }
}

@Immutable
internal data class SettingsChoice<T>(
    val value: T,
    val label: String,
    val summary: String? = null,
    val trailing: (@Composable () -> Unit)? = null,
)

@PreviewLightDark
@Composable
private fun SettingsChoiceDialogPreview() {
    CameraPreviewTheme {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            SettingsDialogFrameContent(
                title = "Capture mode",
                onConfirm = {},
            ) {
                SettingsChoices(
                    choices = persistentListOf(
                        SettingsChoice(
                            value = 0,
                            label = "Speed",
                            summary = "Takes the photo at once",
                        ),
                        SettingsChoice(
                            value = 1,
                            label = "Quality",
                            summary = "Waits for focus",
                        ),
                    ),
                    selectedIndex = 0,
                    onSelect = {},
                )
            }
        }
    }
}
