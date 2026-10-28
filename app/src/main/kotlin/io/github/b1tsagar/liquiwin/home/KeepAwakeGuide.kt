package io.github.b1tsagar.liquiwin.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.b1tsagar.liquiwin.core.design.components.PageTitle
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.style.LocalStyleFacets
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits

/**
 * A full-screen page over start on keeping live tiles running once Android keeps stopping them; [samsung] adds One UI's
 * own setting. Its scroll covers the whole page, so touches never reach the tiles beneath.
 */
@Composable
fun KeepAwakeGuide(samsung: Boolean, onOpenAppInfo: () -> Unit) {
    val units = LocalWpUnits.current
    val type = LocalLiquiWinType.current
    val side = units.dp(LocalStyleFacets.current.sideMarginWpPx)
    Column(
        Modifier
            .fillMaxSize()
            .background(LocalLiquiWinColors.current.background)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(start = side, end = side, bottom = side),
        verticalArrangement = Arrangement.spacedBy(units.dp(12f)),
    ) {
        PageTitle("keep tiles awake")
        WpText(
            "Android stopped live tiles 3 times in the last day. To keep them running, open LiquiWin's App info and choose " +
                "Battery → Unrestricted.",
            type.body,
        )
        if (samsung) {
            WpText(
                "On a Galaxy you can also go to Settings → Battery → Background usage limits → Never sleeping apps and add " +
                    "LiquiWin. The wording varies with the One UI version.",
                type.body,
            )
        }
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .border(2.dp, Color.White)
                .clickable(role = Role.Button, onClick = onOpenAppInfo)
                .padding(horizontal = units.dp(12f), vertical = units.dp(8f)),
            contentAlignment = Alignment.Center,
        ) {
            WpText("open app info", type.body)
        }
    }
}
