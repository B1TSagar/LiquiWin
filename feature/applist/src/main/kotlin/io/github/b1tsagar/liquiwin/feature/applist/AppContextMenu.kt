package io.github.b1tsagar.liquiwin.feature.applist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.style.LocalStyleFacets
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits
import kotlin.math.roundToInt

/**
 * The app context menu: a chrome panel under the row at [anchor] (in the screen's pixels), or above it when there's
 * no room below. A tap outside it, or Back, closes it.
 */
@Composable
internal fun AppContextMenu(
    anchor: Rect,
    showPin: Boolean,
    onPin: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().pointerInput(onDismiss) { detectTapGestures { onDismiss() } })
    Layout(
        content = {
            Column(Modifier.fillMaxWidth().background(LocalLiquiWinColors.current.chrome).padding(vertical = LocalWpUnits.current.dp(12f))) {
                if (showPin) MenuEntry("pin to start") { onPin(); onDismiss() }
                MenuEntry("app info") { onAppInfo(); onDismiss() }
                MenuEntry("uninstall") { onUninstall(); onDismiss() }
            }
        },
    ) { measurables, constraints ->
        val panel = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val top = if (anchor.bottom + panel.height <= constraints.maxHeight) anchor.bottom else (anchor.top - panel.height).coerceAtLeast(0f)
        layout(constraints.maxWidth, constraints.maxHeight) { panel.place(0, top.roundToInt()) }
    }
}

@Composable
private fun MenuEntry(text: String, onClick: () -> Unit) {
    val units = LocalWpUnits.current
    WpText(
        text = text,
        style = LocalLiquiWinType.current.listItem,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = units.dp(LocalStyleFacets.current.sideMarginWpPx), vertical = units.dp(12f)),
    )
}
