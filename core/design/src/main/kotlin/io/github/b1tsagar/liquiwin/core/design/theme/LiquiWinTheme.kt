package io.github.b1tsagar.liquiwin.core.design.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import io.github.b1tsagar.liquiwin.core.design.style.LocalStyleFacets
import io.github.b1tsagar.liquiwin.core.design.style.StyleFacets

@Composable
fun LiquiWinTheme(style: StyleFacets = StyleFacets.Wp81Basic, content: @Composable () -> Unit) {
    val widthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val units = remember(widthDp) { WpUnits(widthDp) }
    val type = remember(units) { liquiWinTypography(units) }
    // Dark only in this stage.
    val colors = LiquiWinColors(
        background = Color.Black,
        foreground = Color.White,
        subtle = Color(0x99FFFFFF),
        chrome = Color(0xFF1F1F1F),
        accent = style.accent,
    )
    CompositionLocalProvider(
        LocalWpUnits provides units,
        LocalLiquiWinColors provides colors,
        LocalLiquiWinType provides type,
        LocalStyleFacets provides style,
        content = content,
    )
}
