package io.github.b1tsagar.liquiwin.core.design.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits
import java.util.Locale

@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    val units = LocalWpUnits.current
    // The TitlePanel margin 12,17,0,28 plus the page-title margin 9,−7,0,0.
    WpText(
        text = text.lowercase(Locale.getDefault()),
        style = LocalLiquiWinType.current.pageTitle,
        modifier = modifier.padding(start = units.dp(21f), top = units.dp(10f), bottom = units.dp(28f)),
    )
}
