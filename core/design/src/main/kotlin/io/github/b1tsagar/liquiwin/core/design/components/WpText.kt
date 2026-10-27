package io.github.b1tsagar.liquiwin.core.design.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors

@Composable
fun WpText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalLiquiWinColors.current.foreground,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(text, modifier, style.copy(color = color), overflow = TextOverflow.Ellipsis, maxLines = maxLines)
}
