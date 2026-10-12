package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun linkedText(
    text: String,
    link: String,
    onClick: () -> Unit,
): AnnotatedString {
    val linkStart = text.indexOf(link)
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        ),
    )

    return buildAnnotatedString {
        append(text)
        addLink(
            clickable = LinkAnnotation.Clickable(
                tag = link,
                styles = linkStyles,
            ) { onClick() },
            start = linkStart,
            end = linkStart + link.length,
        )
    }
}
