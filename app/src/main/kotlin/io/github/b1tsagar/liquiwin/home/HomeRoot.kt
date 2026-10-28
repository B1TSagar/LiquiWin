package io.github.b1tsagar.liquiwin.home

import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.b1tsagar.liquiwin.core.data.apps.AppCatalog
import io.github.b1tsagar.liquiwin.core.design.style.StyleFacets
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.tiles.TileStore
import io.github.b1tsagar.liquiwin.core.tiles.source.SourceRegistry
import io.github.b1tsagar.liquiwin.feature.applist.AppListScreen
import io.github.b1tsagar.liquiwin.feature.applist.AppListViewModel
import io.github.b1tsagar.liquiwin.feature.hub.ChatViewModel
import io.github.b1tsagar.liquiwin.feature.hub.ChatWindow
import io.github.b1tsagar.liquiwin.feature.notifications.LiveTilesHealth
import io.github.b1tsagar.liquiwin.feature.notifications.LiveTilesStatus
import io.github.b1tsagar.liquiwin.feature.search.AppSearchProvider
import io.github.b1tsagar.liquiwin.feature.search.SearchOpener
import io.github.b1tsagar.liquiwin.feature.search.SearchScreen
import io.github.b1tsagar.liquiwin.feature.search.SearchViewModel
import io.github.b1tsagar.liquiwin.feature.start.StartScreen
import io.github.b1tsagar.liquiwin.feature.start.StartViewModel
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

class HomeDependencies(
    val tileStore: TileStore,
    val catalog: AppCatalog,
    val isDefaultHome: () -> Boolean,
    val onSetHome: () -> Unit,
    val onOpenHomeSettings: () -> Unit,
    val sources: SourceRegistry = SourceRegistry { null },
    val liveTiles: LiveTilesHealth? = null,
    val onFixLiveTiles: () -> Unit = {},
    val onOpenAppInfo: () -> Unit = {},
)

enum class HomePress { FROM_APP, WHILE_ON_HOME }

/**
 * The home screen: the start screen and the app list, with search over both. A swipe, Back or a Home press changes
 * between them in place: the one leaving turnstiles away, then the other turns in. A Home press closes whatever is open
 * and shows the start screen; pressed while already home, it also scrolls the start screen up. [windowEntered] says when
 * Android has finished animating LiquiWin back on screen. A banner on start says when live tiles are paused or keep
 * stopping, and the second opens a guide to keeping them awake. A tile's chat button opens the chat window over it all.
 */
@Composable
fun HomeRoot(deps: HomeDependencies, homePresses: Flow<HomePress>, modifier: Modifier = Modifier, windowEntered: Flow<Unit> = emptyFlow()) {
    val start = viewModel { StartViewModel(deps.tileStore, deps.catalog, StyleFacets.Wp81Basic.gridUnits, deps.sources) }
    val appList = viewModel { AppListViewModel(deps.catalog, start.pinnedApps, start::pin) }
    val search = viewModel { SearchViewModel(listOf(AppSearchProvider(deps.catalog)), SearchOpener(deps.catalog)) }
    val chat = viewModel { ChatViewModel(deps.sources) }
    val chatOpen = chat.window.collectAsStateWithLifecycle().value != null
    val icon = remember(deps.catalog) { deps.catalog::icon }
    val pager = rememberPagerState(pageCount = { 2 })
    val startScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    var resetKey by remember { mutableIntStateOf(0) }
    var isDefaultHome by remember { mutableStateOf(deps.isDefaultHome()) }
    val liveTiles by remember(deps) { deps.liveTiles?.status ?: MutableStateFlow(LiveTilesStatus()) }.collectAsStateWithLifecycle()

    // Whether LiquiWin is fully on screen: not while another app covers it, until Android's animation bringing it back has
    // finished (or 0.7 s after it resumes, on a phone that never says), and not while One UI's close-to-home animation
    // plays after a Home press from an app, which outlasts that signal. Flips that bring a screen back wait for it.
    var covered by remember { mutableStateOf(false) }
    var closingToHome by remember { mutableStateOf(false) }
    val onScreen = !covered && !closingToHome
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { covered = true }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isDefaultHome = deps.isDefaultHome()
        deps.liveTiles?.onResume()
        if (covered) scope.launch { delay(700); covered = false }
    }
    LaunchedEffect(windowEntered) { windowEntered.collect { covered = false } }
    val context = LocalContext.current

    // Each change of screen waits for the leaving screen to report it has gone.
    var tilesShown by remember { mutableStateOf(true) }
    var rowsShown by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    fun toList() {
        if (moving || pager.currentPage != 0 || start.state.value.edit != null) return
        moving = true
        tilesShown = false
    }
    fun toStart() {
        if (moving || pager.currentPage != 1) return
        moving = true
        rowsShown = false
    }

    // Holds focus during a Home press. Focus left in the pager, or restored there when search closes, would bring the
    // app list back into view; this target sits outside the pager.
    val homeFocus = remember { FocusRequester() }
    LaunchedEffect(homePresses) {
        homePresses.collect { press ->
            homeFocus.requestFocus()
            searchOpen = false
            guideOpen = false
            chat.close()
            start.exitEdit()
            resetKey++
            if (press == HomePress.WHILE_ON_HOME && pager.currentPage == 1) {
                toStart()
                startScroll.scrollTo(0)
            } else {
                // From another app, straight to start; its tiles come back once LiquiWin is fully on screen. One UI's
                // close-to-home animation takes about 0.4 s, scaled like Android's own transitions.
                if (press == HomePress.FROM_APP) {
                    closingToHome = true
                    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
                    scope.launch { delay((400 * scale).toLong()); closingToHome = false }
                }
                moving = false
                pager.scrollToPage(0)
                rowsShown = false
                tilesShown = true
                if (press == HomePress.WHILE_ON_HOME) startScroll.animateScrollTo(0)
            }
        }
    }

    // Registered first, so screens' own handlers (edit mode, menus, search) win. On the start screen Back does nothing.
    BackHandler {}
    BackHandler(enabled = pager.currentPage == 1) { toStart() }

    Box(modifier.fillMaxSize().background(LocalLiquiWinColors.current.background).focusRequester(homeFocus).focusTarget()) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = false,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                // The guide and the chat window cover the pager, so TalkBack mustn't find the tiles beneath them.
                .then(if (guideOpen || chatOpen) Modifier.clearAndSetSemantics {} else Modifier)
                .semantics {
                    customActions = listOf(
                        if (pager.currentPage == 0) CustomAccessibilityAction("app list") { toList(); true }
                        else CustomAccessibilityAction("start") { toStart(); true },
                    )
                }
                .pointerInput(Unit) {
                    val fling = 600.dp.toPx()
                    val tracker = VelocityTracker()
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking(); drag = 0f },
                        onDragEnd = {
                            when (swipeTarget(pager.currentPage, drag, tracker.calculateVelocity().x, size.width.toFloat(), fling)) {
                                1 -> toList()
                                0 -> toStart()
                            }
                        },
                    ) { change, amount ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        drag += amount
                        change.consume()
                    }
                },
        ) { page ->
            if (page == 0) {
                StartScreen(
                    start, icon, startScroll, shown = tilesShown, onScreen = onScreen,
                    onHidden = { scope.launch { pager.scrollToPage(1); rowsShown = true; moving = false } },
                    banner = {
                        if (!isDefaultHome) HomeAppBanner(onSet = deps.onSetHome, onSettings = deps.onOpenHomeSettings)
                        if (liveTiles.paused) LiveTilesBanner("live tiles paused", deps.onFixLiveTiles)
                        else if (liveTiles.keepsStopping) LiveTilesBanner("live tiles keep stopping") { guideOpen = true }
                    },
                    liveTilesPaused = liveTiles.paused,
                    onChat = chat::show,
                )
            } else {
                AppListScreen(
                    appList, icon, onSearch = { searchOpen = true }, resetKey = resetKey, shown = rowsShown, onScreen = onScreen,
                    onHidden = { scope.launch { pager.scrollToPage(0); tilesShown = true; moving = false } },
                )
            }
        }
        if (searchOpen) {
            BackHandler { searchOpen = false }
            SearchScreen(search, icon, onOpened = { searchOpen = false })
        }
        if (guideOpen) {
            BackHandler { guideOpen = false }
            KeepAwakeGuide(samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true), onOpenAppInfo = deps.onOpenAppInfo)
        }
        ChatWindow(chat, deps.catalog)
    }
}

/**
 * The page a horizontal swipe on [page] goes to: start (0) sits left of the app list (1). A flick at [fling] or faster
 * counts whatever its length; a slower swipe must cover a fifth of the [width]. Toward the edge, it goes nowhere.
 */
internal fun swipeTarget(page: Int, drag: Float, velocity: Float, width: Float, fling: Float): Int? {
    val leftward = when {
        abs(velocity) >= fling -> velocity < 0
        abs(drag) >= width / 5 -> drag < 0
        else -> return null
    }
    return when {
        page == 0 && leftward -> 1
        page == 1 && !leftward -> 0
        else -> null
    }
}
