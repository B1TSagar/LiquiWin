package io.github.b1tsagar.liquiwin.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.b1tsagar.liquiwin.core.design.style.LocalStyleFacets
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits

/** A chrome banner above the tiles saying live tiles have stopped. The whole banner is one button, which fixes that. */
@Composable
fun LiveTilesBanner(text: String, onFix: () -> Unit) {
    val units = LocalWpUnits.current
    val style = LocalStyleFacets.current
    val colors = LocalLiquiWinColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = units.dp(style.sideMarginWpPx), end = units.dp(style.sideMarginWpPx), bottom = units.dp(style.gapWpPx))
            .background(colors.chrome)
            .clickable(role = Role.Button, onClick = onFix)
            .heightIn(min = 48.dp)
            .padding(start = units.dp(12f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        val line = buildAnnotatedString {
            append("$text · ")
            withStyle(SpanStyle(color = colors.accent)) { append("fix") }
        }
        BasicText(line, style = LocalLiquiWinType.current.small.copy(color = colors.foreground))
    }
}
