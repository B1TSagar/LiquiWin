package io.github.b1tsagar.liquiwin.core.design.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.b1tsagar.liquiwin.core.design.R

data class LiquiWinTypography(
    val pageTitle: TextStyle,
    val header: TextStyle,
    val listItem: TextStyle,
    val body: TextStyle,
    val small: TextStyle,
    val tileLabel: TextStyle,
)

private val Semilight = FontWeight(350)

val Selawik = FontFamily(
    Font(R.font.selawkl, FontWeight.Light),
    Font(R.font.selawksl, Semilight),
    Font(R.font.selawk, FontWeight.Normal),
    Font(R.font.selawksb, FontWeight.SemiBold),
    Font(R.font.selawkb, FontWeight.Bold),
)

/** The type ramp in WP px. The list item and tile label sizes are starting values: the reference doesn't give them. */
fun liquiWinTypography(units: WpUnits): LiquiWinTypography {
    fun style(wpPx: Float, weight: FontWeight) = TextStyle(fontFamily = Selawik, fontWeight = weight, fontSize = units.sp(wpPx))
    return LiquiWinTypography(
        pageTitle = style(72f, Semilight),
        header = style(32f, FontWeight.Normal),
        listItem = style(25.333f, FontWeight.Normal),
        body = style(20f, FontWeight.Normal),
        small = style(18.667f, FontWeight.Normal),
        tileLabel = style(18.667f, FontWeight.Normal),
    )
}

val LocalLiquiWinType = staticCompositionLocalOf<LiquiWinTypography> { error("No LiquiWinTypography: wrap the content in LiquiWinTheme") }
