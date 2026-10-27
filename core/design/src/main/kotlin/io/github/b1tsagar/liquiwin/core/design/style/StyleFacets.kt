package io.github.b1tsagar.liquiwin.core.design.style

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class StyleFacets(val gridColumns: Int, val accent: Color, val sideMarginWpPx: Float, val gapWpPx: Float) {
    val gridUnits: Int get() = gridColumns * 2

    companion object {
        // WP8 cyan. On the 480 px WP8 start screen 4 × 99 + 3 × 12 = 432 px of tiles leaves 24 px each side.
        val Wp81Basic = StyleFacets(gridColumns = 3, accent = Color(0xFF1BA1E2), sideMarginWpPx = 24f, gapWpPx = 12f)
    }
}

val LocalStyleFacets = staticCompositionLocalOf { StyleFacets.Wp81Basic }
