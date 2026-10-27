package io.github.b1tsagar.liquiwin.feature.applist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits

/**
 * The full-screen letter grid: four columns of letters. Letters with apps jump to their section; empty ones can't be
 * tapped. Back closes it.
 */
@Composable
internal fun LetterGrid(letters: List<LetterCell>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val units = LocalWpUnits.current
    val colors = LocalLiquiWinColors.current
    val letterStyle = LocalLiquiWinType.current.pageTitle.copy(fontSize = units.sp(42.667f))
    val cell = units.dp(96f)
    val gap = units.dp(12f)
    // The scroll also keeps touches from reaching the list underneath.
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(vertical = gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (row in letters.chunked(4)) {
            Row(Modifier.width(cell * 4 + gap * 3), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (letter in row) {
                    Box(
                        Modifier
                            .size(cell)
                            .testTag("letter-${letter.label}")
                            .background(if (letter.hasApps) colors.accent else colors.chrome)
                            .then(if (letter.hasApps) Modifier.clickable { onPick(letter.label) } else Modifier),
                    ) {
                        WpText(
                            text = letter.label,
                            style = letterStyle,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = units.dp(12f), bottom = units.dp(8f)),
                            color = if (letter.hasApps) colors.foreground else colors.subtle,
                        )
                    }
                }
            }
        }
    }
}
