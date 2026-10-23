package app.grapheneos.camera.ui.common.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier

internal fun Modifier.copyOnLongClick(
    text: String,
    copyLabel: String,
    onCopy: (String) -> Unit,
    onClickLabel: String? = null,
    onClick: () -> Unit = {},
): Modifier {
    return combinedClickable(
        onClickLabel = onClickLabel,
        onLongClickLabel = copyLabel,
        onLongClick = { onCopy(text) },
        onClick = onClick,
    )
}
