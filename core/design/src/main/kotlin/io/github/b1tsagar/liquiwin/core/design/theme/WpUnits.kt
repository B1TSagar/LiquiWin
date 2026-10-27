package io.github.b1tsagar.liquiwin.core.design.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Converts Windows Phone pixels, measured on a 480-px-wide screen, to this screen. */
class WpUnits(val screenWidthDp: Float) {
    fun dp(wpPx: Float): Dp = (wpPx * screenWidthDp / 480f).dp

    // The same number in sp, so the phone's font size setting still applies on top.
    fun sp(wpPx: Float): TextUnit = (wpPx * screenWidthDp / 480f).sp
}

val LocalWpUnits = staticCompositionLocalOf<WpUnits> { error("No WpUnits: wrap the content in LiquiWinTheme") }
