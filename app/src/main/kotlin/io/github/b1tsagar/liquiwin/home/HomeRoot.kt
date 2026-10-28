package io.github.b1tsagar.liquiwin.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.b1tsagar.liquiwin.core.data.apps.AppCatalog
import io.github.b1tsagar.liquiwin.core.design.style.StyleFacets
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.tiles.TileStore
import io.github.b1tsagar.liquiwin.feature.applist.AppListScreen
import io.github.b1tsagar.liquiwin.feature.applist.AppListViewModel
import io.github.b1tsagar.liquiwin.feature.search.AppSearchProvider
import io.github.b1tsagar.liquiwin.feature.search.SearchOpener
import io.github.b1tsagar.liquiwin.feature.search.SearchScreen
import io.github.b1tsagar.liquiwin.feature.search.SearchViewModel
import io.github.b1tsagar.liquiwin.feature.start.StartScreen
import io.github.b1tsagar.liquiwin.feature.start.StartViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HomeDependencies(
    val tileStore: TileStore,
    val catalog: AppCatalog,
    val isDefaultHome: () -> Boolean,
    val onSetHome: () -> Unit,
    val onOpenHomeSettings: () -> Unit,
)

enum class HomePress { FROM_APP, WHILE_ON_HOME }

/**
 * The home screen: the start screen and the app list side by side, with search over both. A Home press closes
 * whatever is open and shows the start screen; pressed while already home, it also scrolls the start screen up.
 */
@Composable
fun HomeRoot(deps: HomeDependencies, homePresses: Flow<HomePress>, modifier: Modifier = Modifier) {
    val start = viewModel { StartViewModel(deps.tileStore, deps.catalog, StyleFacets.Wp81Basic.gridUnits) }
    val appList = viewModel { AppListViewModel(deps.catalog, start.pinnedApps, start::pin) }
    val search = viewModel { SearchViewModel(listOf(AppSearchProvider(deps.catalog)), SearchOpener(deps.catalog)) }
    val icon = remember(deps.catalog) { deps.catalog::icon }
    val pager = rememberPagerState(pageCount = { 2 })
    val startScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var resetKey by remember { mutableIntStateOf(0) }
    var isDefaultHome by remember { mutableStateOf(deps.isDefaultHome()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefaultHome = deps.isDefaultHome() }

    val dragging = pager.interactionSource.collectIsDraggedAsState()
    var rowsShown by remember { mutableStateOf(false) }
    var tilesShown by remember { mutableStateOf(true) }
    LaunchedEffect(pager) {
        snapshotFlow { Triple(dragging.value, pager.targetPage, pager.settledPage) }.collect { (held, target, settled) ->
            rowsShown = appListShown(rowsShown, held, target, settled)
            tilesShown = startShown(tilesShown, held, target)
        }
    }

    // Holds focus during a Home press. Focus left in the pager, or restored there when search closes, would bring the
    // app list back into view; this target sits outside the pager.
    val homeFocus = remember { FocusRequester() }
    LaunchedEffect(homePresses) {
        homePresses.collect { press ->
            homeFocus.requestFocus()
            searchOpen = false
            start.exitEdit()
            resetKey++
            pager.scrollToPage(0)
            if (press == HomePress.WHILE_ON_HOME) startScroll.animateScrollTo(0)
        }
    }

    // Registered first, so screens' own handlers (edit mode, menus, search) win. On the start screen Back does nothing.
    BackHandler {}
    BackHandler(enabled = pager.currentPage == 1) { scope.launch { pager.animateScrollToPage(0) } }

    Box(modifier.fillMaxSize().background(LocalLiquiWinColors.current.background).focusRequester(homeFocus).focusTarget()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            if (page == 0) {
                StartScreen(start, icon, startScroll, shown = tilesShown, banner = {
                    if (!isDefaultHome) HomeAppBanner(onSet = deps.onSetHome, onSettings = deps.onOpenHomeSettings)
                })
            } else {
                AppListScreen(appList, icon, onSearch = { searchOpen = true }, resetKey = resetKey, shown = rowsShown)
            }
        }
        if (searchOpen) {
            BackHandler { searchOpen = false }
            SearchScreen(search, icon, onOpened = { searchOpen = false })
        }
    }
}

/**
 * Whether the app list's rows are in. They come in when a swipe lets go heading for the app list (page 1), or the pager
 * gets there another way, and go out once start (page 0) has settled. A drag on the list that springs back changes nothing.
 */
internal fun appListShown(shown: Boolean, dragging: Boolean, targetPage: Int, settledPage: Int): Boolean = when {
    !dragging && targetPage == 1 -> true
    settledPage == 0 && targetPage == 0 -> false
    else -> shown
}

/** Whether start's tiles are in: they follow wherever a swipe lets go, and stay as they are while one is held. */
internal fun startShown(shown: Boolean, dragging: Boolean, targetPage: Int): Boolean = if (dragging) shown else targetPage == 0
