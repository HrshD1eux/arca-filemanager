package com.Hrshd1eux.filemanager.core.designsystem.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * A responsive single-line or constrained text composable that gracefully scales down
 * font size to prevent clipping in tight chrome (action buttons, chips, badges, headers)
 * when rendered in verbose languages (German, Russian, French, Tamil) or under large font scales.
 *
 * Once [minFontSize] is reached, it safely truncates with [TextOverflow.Ellipsis] without looping.
 */
@Composable
fun ResponsiveText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    minFontSize: TextUnit = 11.sp,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = false,
    maxLines: Int = 1,
    style: TextStyle = LocalTextStyle.current
) {
    val targetStyle = style.copy(
        color = if (color != Color.Unspecified) color else style.color,
        fontSize = if (fontSize.isSpecified) fontSize else style.fontSize,
        fontWeight = fontWeight ?: style.fontWeight,
        textAlign = textAlign ?: style.textAlign,
        lineHeight = if (lineHeight.isSpecified) lineHeight else style.lineHeight,
        fontFamily = fontFamily ?: style.fontFamily,
        textDecoration = textDecoration ?: style.textDecoration,
        fontStyle = fontStyle ?: style.fontStyle,
        letterSpacing = if (letterSpacing.isSpecified) letterSpacing else style.letterSpacing
    )

    var currentFontSize by remember(text, targetStyle.fontSize) {
        mutableStateOf(if (targetStyle.fontSize.isSpecified) targetStyle.fontSize else 14.sp)
    }
    var readyToDraw by remember(text, currentFontSize) { mutableStateOf(false) }

    Text(
        text = text,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        style = targetStyle.copy(fontSize = currentFontSize),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow && currentFontSize.value > minFontSize.value) {
                val nextSize = (currentFontSize.value - 1f).coerceAtLeast(minFontSize.value)
                if (nextSize < currentFontSize.value) {
                    currentFontSize = nextSize.sp
                } else {
                    readyToDraw = true
                }
            } else {
                readyToDraw = true
            }
        }
    )
}
