package io.github.b1tsagar.liquiwin.feature.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.b1tsagar.liquiwin.core.data.apps.AppIcon
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.design.components.IconImage
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.icons.LiquiWinIcons
import io.github.b1tsagar.liquiwin.core.design.motion.tilt
import io.github.b1tsagar.liquiwin.core.design.style.LocalStyleFacets
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits
import java.util.Locale

/** The search page: a query box that takes focus at once, then each provider's results. Go opens the best result. */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    icon: suspend (LauncherApp) -> AppIcon?,
    onOpened: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val units = LocalWpUnits.current
    val colors = LocalLiquiWinColors.current
    val type = LocalLiquiWinType.current
    val side = units.dp(LocalStyleFacets.current.sideMarginWpPx)
    // The field keeps its own text: the view model's copy arrives a frame later and would fight the typing.
    var text by rememberSaveable { mutableStateOf(state.query) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    Column(modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars)) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                viewModel.onQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth().padding(start = side, end = side, top = units.dp(12f)).focusRequester(focus),
            textStyle = type.body.copy(color = colors.foreground),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (viewModel.openFirst()) onOpened() }),
            decorationBox = { field ->
                Row(
                    Modifier.background(colors.chrome).padding(horizontal = units.dp(12f), vertical = units.dp(10f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(LiquiWinIcons.Search, contentDescription = null, modifier = Modifier.size(units.dp(26f)), colorFilter = ColorFilter.tint(colors.subtle))
                    Box(Modifier.padding(start = units.dp(12f))) {
                        if (text.isEmpty()) WpText("search apps", type.body, color = colors.subtle)
                        field()
                    }
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = side,
                end = side,
                top = units.dp(12f),
                bottom = WindowInsets.ime.union(WindowInsets.navigationBars).asPaddingValues().calculateBottomPadding() + units.dp(12f),
            ),
            verticalArrangement = Arrangement.spacedBy(units.dp(12f)),
        ) {
            for (group in state.groups) {
                item(key = "group:${group.providerId}") { WpText(group.title.lowercase(Locale.getDefault()), type.header) }
                items(group.results, key = { it.key }) { result ->
                    when (result) {
                        is SearchResult.App -> ResultRow(result.app, icon) {
                            viewModel.open(result)
                            onOpened()
                        }
                    }
                }
            }
        }
    }
}

// Looks and tilts like an app list row.
@Composable
private fun ResultRow(app: LauncherApp, icon: suspend (LauncherApp) -> AppIcon?, onClick: () -> Unit) {
    val units = LocalWpUnits.current
    val appIcon by produceState<AppIcon?>(null, app.key, app.versionToken) { value = icon(app) }
    Row(
        Modifier.fillMaxWidth().alpha(if (app.isPaused) 0.4f else 1f).tilt().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(units.dp(56f)).background(LocalLiquiWinColors.current.accent)) {
            IconImage(appIcon?.bitmap, units.dp(28f), Modifier.align(Alignment.Center))
            if (app.isWorkProfile) {
                Image(LiquiWinIcons.Briefcase, contentDescription = null, modifier = Modifier.align(Alignment.TopEnd).padding(units.dp(4f)).size(units.dp(14f)))
            }
        }
        WpText(app.label, LocalLiquiWinType.current.listItem, Modifier.padding(start = units.dp(12f)), maxLines = 1)
    }
}
