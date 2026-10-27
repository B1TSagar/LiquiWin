package io.github.b1tsagar.liquiwin.core.design.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class LiquiWinColors(val background: Color, val foreground: Color, val subtle: Color, val chrome: Color, val accent: Color)

val LocalLiquiWinColors = staticCompositionLocalOf<LiquiWinColors> { error("No LiquiWinColors: wrap the content in LiquiWinTheme") }
