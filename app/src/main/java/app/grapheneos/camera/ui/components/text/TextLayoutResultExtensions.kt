package app.grapheneos.camera.ui.components.text

import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import android.graphics.Typeface
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight

internal fun TextLayoutResult.inkBounds(): Rect {
    val text = layoutInput.text.text
    val glyphBounds = AndroidRect()
    inkPaint(style = layoutInput.style).getTextBounds(text, 0, text.length, glyphBounds)
    val originX = getLineLeft(lineIndex = 0)

    return Rect(
        left = originX + glyphBounds.left,
        top = firstBaseline + glyphBounds.top,
        right = originX + glyphBounds.right,
        bottom = firstBaseline + glyphBounds.bottom,
    )
}

internal fun TextLayoutResult.inkCenteringOffsetY(): Float {
    return size.height / 2f - inkBounds().center.y
}

private fun TextLayoutResult.inkPaint(style: TextStyle): Paint {
    val typeface = layoutInput.fontFamilyResolver
        .resolve(
            fontFamily = style.fontFamily,
            fontWeight = style.fontWeight ?: FontWeight.Normal,
            fontStyle = style.fontStyle ?: FontStyle.Normal,
            fontSynthesis = style.fontSynthesis ?: FontSynthesis.All,
        )
        .value as? Typeface
    val textSizePx = with(layoutInput.density) { style.fontSize.toPx() }
    val letterSpacing = style.letterSpacing

    return Paint().apply {
        this.typeface = typeface
        textSize = textSizePx
        fontFeatureSettings = style.fontFeatureSettings
        this.letterSpacing = when {
            letterSpacing.isEm -> letterSpacing.value
            letterSpacing.isSp -> with(layoutInput.density) { letterSpacing.toPx() } / textSizePx
            else -> 0f
        }
    }
}
