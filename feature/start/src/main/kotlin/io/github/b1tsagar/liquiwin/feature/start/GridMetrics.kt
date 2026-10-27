package io.github.b1tsagar.liquiwin.feature.start

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize

/** The start grid in dp: [units] columns between the side margins, with gaps, on a screen [screenWidthDp] wide. */
data class GridMetrics(val screenWidthDp: Float, val units: Int, val sideMarginWpPx: Float, val gapWpPx: Float) {
    val sideMarginDp: Float = sideMarginWpPx * screenWidthDp / 480f
    val gapDp: Float = gapWpPx * screenWidthDp / 480f
    val unitDp: Float = (screenWidthDp - 2 * sideMarginDp - (units - 1) * gapDp) / units
    val stepDp: Float = unitDp + gapDp

    fun sizeDp(size: TileSize): DpSize = DpSize((size.widthUnits * stepDp - gapDp).dp, (size.heightUnits * stepDp - gapDp).dp)
}
