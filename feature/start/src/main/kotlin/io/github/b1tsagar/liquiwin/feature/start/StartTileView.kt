package io.github.b1tsagar.liquiwin.feature.start

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.b1tsagar.liquiwin.core.data.apps.AppIcon
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.design.components.IconImage
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.icons.LiquiWinIcons
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize

/** One tile as drawn: the accent square, the app's icon, and the label on medium and larger tiles. */
@Composable
internal fun StartTileView(tile: StartTile, metrics: GridMetrics, icon: suspend (LauncherApp) -> AppIcon?, modifier: Modifier = Modifier) {
    val units = LocalWpUnits.current
    val size = metrics.sizeDp(tile.size)
    val app = tile.app
    val appIcon by produceState<AppIcon?>(null, app?.key, app?.versionToken) { value = app?.let { icon(it) } }
    Box(modifier.size(size).background(LocalLiquiWinColors.current.accent)) {
        IconImage(appIcon?.bitmap, min(size.width, size.height) * 0.5f, Modifier.align(Alignment.Center))
        if (tile.size != TileSize.SMALL) {
            WpText(
                text = tile.label,
                style = LocalLiquiWinType.current.tileLabel,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = units.dp(12f), bottom = units.dp(8f)),
                color = Color.White,
                maxLines = 1,
            )
        }
        if (tile.isWork) {
            Image(
                LiquiWinIcons.Briefcase,
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = units.dp(8f), end = units.dp(8f)).size((metrics.unitDp / 4).dp),
            )
        }
    }
}
