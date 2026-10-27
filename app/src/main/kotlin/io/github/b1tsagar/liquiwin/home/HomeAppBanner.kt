package io.github.b1tsagar.liquiwin.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.style.LocalStyleFacets
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits

/** A slim chrome banner above the first row of tiles while LiquiWin isn't the default Home app. */
@Composable
fun HomeAppBanner(onSet: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val units = LocalWpUnits.current
    val style = LocalStyleFacets.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = units.dp(style.sideMarginWpPx), end = units.dp(style.sideMarginWpPx), bottom = units.dp(style.gapWpPx))
            .background(LocalLiquiWinColors.current.chrome)
            .padding(start = units.dp(12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WpText("liquiwin isn't your home app yet", LocalLiquiWinType.current.small, Modifier.weight(1f))
        BannerButton("set", onSet)
        BannerButton("settings", onSettings)
    }
}

@Composable
private fun BannerButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = LocalWpUnits.current.dp(12f)),
        contentAlignment = Alignment.Center,
    ) {
        WpText(text, LocalLiquiWinType.current.small, color = LocalLiquiWinColors.current.accent)
    }
}
