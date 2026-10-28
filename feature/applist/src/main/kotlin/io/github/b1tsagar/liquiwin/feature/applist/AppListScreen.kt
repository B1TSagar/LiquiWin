package io.github.b1tsagar.liquiwin.feature.applist

import android.app.ActivityOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.b1tsagar.liquiwin.core.data.apps.AppIcon
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.design.components.IconImage
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.icons.LiquiWinIcons
import io.github.b1tsagar.liquiwin.core.design.motion.Turnstile
import io.github.b1tsagar.liquiwin.core.design.motion.TurnstileClock
import io.github.b1tsagar.liquiwin.core.design.motion.tilt
import io.github.b1tsagar.liquiwin.core.design.motion.turnstile
import io.github.b1tsagar.liquiwin.core.design.style.LocalStyleFacets
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The app the context menu is open for, and its row's bounds in the screen's pixels. */
private class MenuAnchor(val app: LauncherApp, val bounds: Rect)

private class CoordinatesRef {
    var value: LayoutCoordinates? = null
}

/**
 * The WP8.1-style A–Z app list: a search box, then sections under lowercase headers. A header opens the letter grid,
 * and a long-press on an app opens its context menu. A new [resetKey] closes both. While [shown] is false the list's
 * items are hidden; when it turns true, the items on screen feather in from the top.
 */
@Composable
fun AppListScreen(
    viewModel: AppListViewModel,
    icon: suspend (LauncherApp) -> AppIcon?,
    onSearch: () -> Unit,
    resetKey: Int,
    modifier: Modifier = Modifier,
    shown: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val units = LocalWpUnits.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf<MenuAnchor?>(null) }
    var letterGridOpen by remember { mutableStateOf(false) }
    LaunchedEffect(resetKey) {
        menu = null
        letterGridOpen = false
    }
    val screen = remember { CoordinatesRef() }

    // One clock turns every item on screen, counted from the first visible one; items off screen just appear. The list
    // turnstiles in when it's shown, away before an app opens (the tapped row last), and back when LiquiWin returns.
    val flip = remember { TurnstileClock(hiddenFor = if (shown) null else Turnstile.IN) }
    var flipFirst by remember { mutableIntStateOf(0) }
    var flipOnScreen by remember { mutableIntStateOf(0) }
    var tappedItem by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(shown) {
        if (!shown) {
            flip.hideFor(Turnstile.IN)
        } else if (flip.motion == Turnstile.IN && !flip.isPlaying) {
            flipFirst = listState.firstVisibleItemIndex
            flip.play(Turnstile.IN, listState.layoutInfo.visibleItemsInfo.size - 1)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (shown && flip.motion == Turnstile.OUT && !flip.isPlaying) scope.launch {
            flipFirst = listState.firstVisibleItemIndex
            flip.play(Turnstile.BACK_IN, listState.layoutInfo.visibleItemsInfo.size - 1)
        }
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val launching = remember { mutableStateOf<Job?>(null) }
    fun open(item: Int, app: LauncherApp, bounds: android.graphics.Rect?) {
        if (launching.value?.isActive == true) return
        launching.value = scope.launch {
            flipFirst = listState.firstVisibleItemIndex
            flipOnScreen = listState.layoutInfo.visibleItemsInfo.size
            tappedItem = item
            flip.play(Turnstile.OUT, flipOnScreen)
            viewModel.open(app, bounds, ActivityOptions.makeCustomAnimation(context, android.R.anim.fade_in, 0).toBundle())
            // A launch that fails leaves LiquiWin on screen, so nothing would bring the rows back: they come back here.
            val left = withTimeoutOrNull(1_500) { lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.RESUMED) } }
            if (left == null) flip.play(Turnstile.BACK_IN, flipOnScreen - 1)
        }
    }
    val slideStep = with(LocalDensity.current) { units.dp(50f).toPx() } // WP's slide-in: 50 px further out per line
    fun Modifier.turning(item: Int): Modifier {
        val index = listTurnstileIndex(flip.motion ?: Turnstile.IN, item, flipFirst, tappedItem, flipOnScreen)
        return turnstile(flip, index, slideStep * index)
    }

    // The list index of each section's header; item 0 is the search box.
    val headerItems = remember(state.index) {
        var item = 1
        state.index.sections.associate { section -> (section.label to item).also { item += 1 + section.apps.size } }
    }
    val labels = remember(state.index) { state.index.sections.map { it.label } }
    val jumpTo: (String) -> Unit = { label -> headerItems[label]?.let { scope.launch { listState.scrollToItem(it) } } }
    val side = units.dp(LocalStyleFacets.current.sideMarginWpPx)
    val bars = WindowInsets.systemBars.asPaddingValues()
    val dim = if (menu != null) 0.4f else 1f

    LetterScrubber(labels, enabled = menu == null && !letterGridOpen, onLetter = jumpTo, modifier = modifier.fillMaxSize().onPlaced { screen.value = it }) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = side,
                end = side,
                top = bars.calculateTopPadding() + units.dp(12f),
                bottom = bars.calculateBottomPadding() + units.dp(12f),
            ),
            verticalArrangement = Arrangement.spacedBy(units.dp(12f)),
        ) {
            item(key = "search") { SearchBox(onSearch, Modifier.turning(0).alpha(dim)) }
            // Counted here, from the sections being built: headerItems can be a composition behind the list.
            var next = 1
            for (section in state.index.sections) {
                val header = next
                next += 1 + section.apps.size
                item(key = "header:${section.label}") {
                    SectionHeader(section.label, Modifier.turning(header).alpha(dim)) { letterGridOpen = true }
                }
                itemsIndexed(section.apps, key = { _, app -> app.key.flatten() }) { i, app ->
                    AppRow(
                        app = app,
                        isNew = app.key in state.newApps,
                        icon = icon,
                        modifier = Modifier.turning(header + 1 + i).alpha(if (menu == null || menu?.app?.key == app.key) 1f else 0.4f),
                        onOpen = { bounds ->
                            val window = bounds?.boundsInWindow()
                            val rect = window?.let {
                                android.graphics.Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
                            }
                            open(header + 1 + i, app, rect)
                        },
                        onLongPress = { row -> screen.value?.let { menu = MenuAnchor(app, it.localBoundingBoxOf(row)) } },
                    )
                }
            }
        }
        menu?.let { anchor ->
            AppContextMenu(
                anchor = anchor.bounds,
                showPin = anchor.app.key !in state.pinned,
                onPin = { viewModel.pin(anchor.app) },
                onAppInfo = { viewModel.appInfo(anchor.app) },
                onUninstall = { viewModel.uninstall(anchor.app) },
                onDismiss = { menu = null },
            )
        }
        if (letterGridOpen) {
            LetterGrid(
                letters = state.index.letters,
                onPick = { label ->
                    jumpTo(label)
                    letterGridOpen = false
                },
                onDismiss = { letterGridOpen = false },
            )
        }
    }
}

@Composable
private fun SearchBox(onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val units = LocalWpUnits.current
    val colors = LocalLiquiWinColors.current
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.chrome)
            .clickable(onClick = onSearch)
            .padding(horizontal = units.dp(12f), vertical = units.dp(10f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(LiquiWinIcons.Search, contentDescription = null, modifier = Modifier.size(units.dp(26f)), colorFilter = ColorFilter.tint(colors.subtle))
        WpText("search apps", LocalLiquiWinType.current.body, Modifier.padding(start = units.dp(12f)), color = colors.subtle)
    }
}

// A 56 WP px square with an accent outline and the label at its bottom left.
@Composable
private fun SectionHeader(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val units = LocalWpUnits.current
    Box(
        modifier
            .testTag("header-$label")
            .size(units.dp(56f))
            .border(2.dp, LocalLiquiWinColors.current.accent)
            .clickable(onClick = onClick),
    ) {
        WpText(label, LocalLiquiWinType.current.header, Modifier.align(Alignment.BottomStart).padding(start = units.dp(6f), bottom = units.dp(2f)))
    }
}

@Composable
private fun AppRow(
    app: LauncherApp,
    isNew: Boolean,
    icon: suspend (LauncherApp) -> AppIcon?,
    modifier: Modifier,
    onOpen: (LayoutCoordinates?) -> Unit,
    onLongPress: (LayoutCoordinates) -> Unit,
) {
    val units = LocalWpUnits.current
    val colors = LocalLiquiWinColors.current
    val type = LocalLiquiWinType.current
    val square = remember { CoordinatesRef() }
    val row = remember { CoordinatesRef() }
    val appIcon by produceState<AppIcon?>(null, app.key, app.versionToken) { value = icon(app) }
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (app.isPaused) 0.4f else 1f)
            .onPlaced { row.value = it }
            .tilt()
            .combinedClickable(onClick = { onOpen(square.value) }, onLongClick = { row.value?.let(onLongPress) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(units.dp(56f)).background(colors.accent).onPlaced { square.value = it }) {
            IconImage(appIcon?.bitmap, units.dp(28f), Modifier.align(Alignment.Center))
            if (app.isWorkProfile) {
                Image(LiquiWinIcons.Briefcase, contentDescription = null, modifier = Modifier.align(Alignment.TopEnd).padding(units.dp(4f)).size(units.dp(14f)))
            }
        }
        Column(Modifier.padding(start = units.dp(12f))) {
            WpText(app.label, type.listItem, maxLines = 1)
            if (isNew) WpText("new", type.small, color = colors.accent)
        }
    }
}
