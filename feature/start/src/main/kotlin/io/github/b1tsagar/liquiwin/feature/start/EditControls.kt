package io.github.b1tsagar.liquiwin.feature.start

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.icons.LiquiWinIcons
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits

/** Unpin on the selected tile's top-right corner and resize on its bottom-right, each centred on the corner. */
@Composable
internal fun EditControls(tileSize: DpSize, onUnpin: () -> Unit, onResize: () -> Unit) {
    CornerButton(LiquiWinIcons.PinOff, "unpin", onUnpin, Modifier.offset(tileSize.width - 24.dp, (-24).dp))
    CornerButton(LiquiWinIcons.Resize, "resize", onResize, Modifier.offset(tileSize.width - 24.dp, tileSize.height - 24.dp))
}

// A 32 dp white circle with a black glyph inside a 48 dp touch target.
@Composable
private fun CornerButton(glyph: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Image(glyph, contentDescription = description, modifier = Modifier.size(18.dp), colorFilter = ColorFilter.tint(Color.Black))
        }
    }
}

/** The edit-mode app bar: 72 WP px of chrome above the navigation bar, holding the done button. */
@Composable
internal fun EditBar(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val units = LocalWpUnits.current
    Box(
        modifier.fillMaxWidth().background(LocalLiquiWinColors.current.chrome).navigationBarsPadding().height(units.dp(72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.clickable(role = Role.Button, onClick = onDone), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(units.dp(48f)).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Image(LiquiWinIcons.Check, contentDescription = "done", modifier = Modifier.size(units.dp(26f)))
            }
            WpText("done", LocalLiquiWinType.current.small, Modifier.clearAndSetSemantics {})
        }
    }
}
