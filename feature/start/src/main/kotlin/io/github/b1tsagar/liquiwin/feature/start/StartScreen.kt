package io.github.b1tsagar.liquiwin.feature.start

import android.app.ActivityOptions
import android.graphics.Rect
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import io.github.b1tsagar.liquiwin.core.data.apps.AppIcon
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.motion.Turnstile
import io.github.b1tsagar.liquiwin.core.design.motion.TurnstileClock
import io.github.b1tsagar.liquiwin.core.design.motion.tileFlipCameraDistance
import io.github.b1tsagar.liquiwin.core.design.motion.tilt
import io.github.b1tsagar.liquiwin.core.design.motion.turnstile
import io.github.b1tsagar.liquiwin.core.design.style.LocalStyleFacets
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import io.github.b1tsagar.liquiwin.core.tiles.source.TileState
import io.github.b1tsagar.liquiwin.feature.start.layout.Cell
import io.github.b1tsagar.liquiwin.feature.start.live.FLING_DP_PER_S
import io.github.b1tsagar.liquiwin.feature.start.live.LABEL_STRIP_WP_PX
import io.github.b1tsagar.liquiwin.feature.start.live.MessageSwipe
import io.github.b1tsagar.liquiwin.feature.start.live.STACK_SIZE
import io.github.b1tsagar.liquiwin.feature.start.live.Sideways
import io.github.b1tsagar.liquiwin.feature.start.live.StackLayout
import io.github.b1tsagar.liquiwin.feature.start.live.StackRow
import io.github.b1tsagar.liquiwin.feature.start.live.TileFace
import io.github.b1tsagar.liquiwin.feature.start.live.TileFlipState
import io.github.b1tsagar.liquiwin.feature.start.live.TilePage
import io.github.b1tsagar.liquiwin.feature.start.live.TileTicker
import io.github.b1tsagar.liquiwin.feature.start.live.faces
import io.github.b1tsagar.liquiwin.feature.start.live.nextFace
import io.github.b1tsagar.liquiwin.feature.start.live.pageFaces
import io.github.b1tsagar.liquiwin.feature.start.live.pageFor
import io.github.b1tsagar.liquiwin.feature.start.live.settle
import io.github.b1tsagar.liquiwin.feature.start.live.sideways
import io.github.b1tsagar.liquiwin.feature.start.live.tileChatTarget
import io.github.b1tsagar.liquiwin.feature.start.live.tileTapTarget
import io.github.b1tsagar.liquiwin.feature.start.live.turnsDown
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val EMPTY_HINT = "swipe left for your apps. long-press one and choose pin to start."

/**
 * The start screen: the tile grid with edit mode. Outside edit mode a tap turnstiles the tiles away and then opens the
 * tile's app, and a long-press enters edit mode and lets the finger drag the tile straight away. In edit mode a tap
 * selects a tile, the selected tile moves with a drag, and any other tile moves after a long-press, so a swipe on it
 * still scrolls. When [shown] turns false the tiles turnstile away and then [onHidden] is called. The tiles come back by
 * playing their exit backwards, once start is shown again and LiquiWin is [onScreen], fully back from another app.
 * Each tile shows its unread count, or a pause mark while [liveTilesPaused]. While start is shown, on screen and resumed,
 * outside edit mode and with animations on, the tiles on screen flip through their items one at a time, and a new item
 * flips its tile straight away. With animations off each tile shows the face after its front. Each item showing has a
 * chat button, which hands the item to [onChat] (§5.5). While start is scrolled all the way up, a swipe down turns a tile
 * between its front and its new messages or history (§5.3). In edit mode a tile with hidden chats lists them from its
 * bottom-left corner, each with show again (§5.8).
 */
@Composable
fun StartScreen(
    viewModel: StartViewModel,
    icon: suspend (LauncherApp) -> AppIcon?,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    shown: Boolean = true,
    onScreen: Boolean = true,
    onHidden: () -> Unit = {},
    banner: @Composable () -> Unit = {},
    liveTilesPaused: Boolean = false,
    onChat: (TileItem) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val units = LocalWpUnits.current
    val style = LocalStyleFacets.current
    val density = LocalDensity.current
    val metrics = remember(units, state.gridWidth, style) {
        GridMetrics(units.screenWidthDp, state.gridWidth, style.sideMarginWpPx, style.gapWpPx)
    }
    val sidePx = with(density) { metrics.sideMarginDp.dp.toPx() }
    val stepPx = with(density) { metrics.stepDp.dp.toPx() }
    val controller = remember(viewModel) { StartController(viewModel) }
    val haptic = LocalHapticFeedback.current
    SideEffect {
        controller.update(haptic, sidePx, stepPx, state.gridWidth)
        controller.onChat = onChat
        controller.startAtTop = { scrollState.value == 0 }
    }

    // One clock turns every tile. Going away they turnstile out, a tapped tile last, and its app opens once the others
    // have gone; they come back by rewinding that, so the tapped tile turns back first.
    val flip = remember { TurnstileClock() }
    var tapped by remember { mutableStateOf<Long?>(null) }
    fun lastTurn(tapped: Long?) = startTurnstileOrder(state.tiles.associate { it.id to it.cell }, tapped).values.maxOrNull() ?: 0
    LaunchedEffect(shown) {
        if (shown) return@LaunchedEffect
        tapped = null
        if (flip.motion != Turnstile.OUT) flip.play(Turnstile.OUT, lastTurn(null))
        onHidden()
    }
    LaunchedEffect(shown, onScreen) {
        if (shown && onScreen && flip.motion == Turnstile.OUT) {
            flip.rewind(Turnstile.OUT, lastTurn(tapped))
            tapped = null
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val launching = remember { JobRef() }
    SideEffect {
        controller.launch = { id, bounds, item, button ->
            if (launching.value?.isActive != true) launching.value = scope.launch {
                tapped = id
                flip.play(Turnstile.OUT, lastTurn(id))
                viewModel.open(id, bounds, ActivityOptions.makeCustomAnimation(context, android.R.anim.fade_in, 0).toBundle(), item, button)
                // A launch that fails leaves LiquiWin on screen, so nothing would bring the tiles back: they come back here.
                val left = withTimeoutOrNull(1_500) { lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.RESUMED) } }
                if (left == null) {
                    flip.rewind(Turnstile.OUT, lastTurn(tapped))
                    tapped = null
                }
            }
        }
    }

    val editing by rememberUpdatedState(state.edit != null)
    BackHandler(enabled = state.edit != null) { viewModel.exitEdit() }

    // The open hidden chats list (§4.3). It closes once edit mode ends or its tile has none left.
    val hiddenChats = controller.hiddenChatsOf?.takeIf { state.edit != null }?.let { live[it]?.hiddenChats }.orEmpty()
    LaunchedEffect(hiddenChats.isEmpty()) { if (hiddenChats.isEmpty()) controller.hiddenChatsOf = null }
    // The list covers start, so TalkBack mustn't find the tiles beneath it.
    val underList = if (hiddenChats.isEmpty()) Modifier else Modifier.clearAndSetSemantics {}

    // Live tiles move only while they can be seen: start shown and on screen, LiquiWin resumed, outside edit mode, not
    // paused, and with Android's animations on, which is read again on every resume.
    val lifecycleState by lifecycle.currentStateAsState()
    fun animatorOn() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    var animationsOn by remember { mutableStateOf(animatorOn()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { animationsOn = animatorOn() }
    val moving = shown && onScreen && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && state.edit == null &&
        !liveTilesPaused && animationsOn
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        delay(TileTicker.FIRST_TICK_MS)
        while (true) {
            controller.tickLive()
            delay(controller.ticker.nextGapMs())
        }
    }

    // A hover cell held for 150 ms previews the move.
    val drag = controller.drag
    val hover by remember(controller) { derivedStateOf { controller.drag?.let(controller::hoverCell) } }
    LaunchedEffect(drag?.id, hover) {
        val id = drag?.id ?: return@LaunchedEffect
        val cell = hover ?: return@LaunchedEffect
        delay(150)
        viewModel.previewMove(id, cell)
    }

    // Within 64 dp of the viewport's top or bottom the grid scrolls, by up to 12 dp a frame as the finger goes deeper.
    val edgePx = with(density) { 64.dp.toPx() }
    val maxScrollPx = with(density) { 12.dp.toPx() }
    LaunchedEffect(drag != null) {
        while (true) {
            withFrameNanos { }
            val active = controller.drag ?: break
            val height = controller.viewport?.size?.height?.toFloat() ?: continue
            val y = active.fingerY
            val depth = when {
                y.isNaN() -> 0f
                y < edgePx -> y - edgePx
                y > height - edgePx -> y - (height - edgePx)
                else -> 0f
            }
            if (depth != 0f) active.topLeft += Offset(0f, scrollState.scrollBy(maxScrollPx * (depth / edgePx).coerceIn(-1f, 1f)))
        }
    }

    Box(modifier.fillMaxSize().pointerInput(viewModel) { detectTapGestures { if (editing) viewModel.exitEdit() } }) {
        Column(
            Modifier
                .fillMaxSize()
                .then(underList)
                .onPlaced { controller.viewport = it }
                .verticalScroll(scrollState)
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .padding(top = metrics.gapDp.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(bottom = units.dp(96f)),
        ) {
            banner()
            TileGrid(state, metrics, sidePx, stepPx, controller, icon, flip, tapped, live, liveTilesPaused, moving, animationsOn)
        }
        if (state.loaded && state.tiles.isEmpty()) {
            WpText(
                text = EMPTY_HINT,
                style = LocalLiquiWinType.current.body.copy(textAlign = TextAlign.Center),
                modifier = Modifier.align(Alignment.Center).padding(horizontal = metrics.sideMarginDp.dp),
                color = LocalLiquiWinColors.current.subtle,
            )
        }
        if (state.edit != null) EditBar(onDone = viewModel::exitEdit, modifier = Modifier.align(Alignment.BottomCenter).then(underList))
        if (hiddenChats.isNotEmpty()) {
            // Composed after edit mode's, so Back closes the list first.
            BackHandler { controller.hiddenChatsOf = null }
            HiddenChatsList(hiddenChats, onShowAgain = viewModel::showAgain, onClose = { controller.hiddenChatsOf = null })
        }
    }
}

// Every tile places itself through its animated offset, so the layout only reserves the grid's height.
@Composable
private fun TileGrid(
    state: StartState,
    metrics: GridMetrics,
    sidePx: Float,
    stepPx: Float,
    controller: StartController,
    icon: suspend (LauncherApp) -> AppIcon?,
    flip: TurnstileClock,
    tapped: Long?,
    live: Map<Long, TileState>,
    liveTilesPaused: Boolean,
    moving: Boolean,
    animationsOn: Boolean,
) {
    val cells = state.tiles.associate { it.id to (state.edit?.preview?.get(it.id) ?: it.cell) }
    val rows = state.tiles.maxOfOrNull { cells.getValue(it.id).y + it.size.heightUnits } ?: 0
    val turns = startTurnstileOrder(cells, tapped.takeIf { flip.motion == Turnstile.OUT })
    val density = LocalDensity.current
    val hingePx = with(density) { (-0.2f * metrics.screenWidthDp).dp.toPx() } // WP's hinge, a fifth of a screen left of it
    Layout(
        content = {
            for (tile in state.tiles) key(tile.id) {
                val cell = cells.getValue(tile.id)
                val widthPx = with(density) { metrics.sizeDp(tile.size).width.toPx() }
                val pivotX = (hingePx - (sidePx + cell.x * stepPx)) / widthPx
                TileSlot(
                    tile, cell, metrics, sidePx, stepPx, state.edit, controller, icon, flip, turns.getValue(tile.id), pivotX,
                    live[tile.id], liveTilesPaused, moving, animationsOn,
                )
            }
        },
        modifier = Modifier.fillMaxWidth().height(if (rows == 0) 0.dp else (rows * metrics.stepDp - metrics.gapDp).dp),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) { placeables.forEach { it.place(0, 0) } }
    }
}

@Composable
private fun TileSlot(
    tile: StartTile,
    target: Cell,
    metrics: GridMetrics,
    sidePx: Float,
    stepPx: Float,
    edit: EditState?,
    controller: StartController,
    icon: suspend (LauncherApp) -> AppIcon?,
    flip: TurnstileClock,
    turn: Int,
    pivotX: Float,
    live: TileState?,
    liveTilesPaused: Boolean,
    moving: Boolean,
    animationsOn: Boolean,
) {
    val unread = live?.unread ?: 0
    val targetPx = Offset(sidePx + target.x * stepPx, target.y * stepPx)
    val position = remember { Animatable(targetPx, Offset.VectorConverter) }
    val dragged = controller.drag?.id == tile.id
    LaunchedEffect(targetPx, dragged) {
        if (dragged) snapshotFlow { controller.drag?.topLeft }.collect { if (it != null) position.snapTo(it) }
        else position.animateTo(targetPx, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    val selected = edit?.selectedId == tile.id
    val scale by animateFloatAsState(if (edit == null) 1f else if (selected) 1.04f else 0.92f, label = "tile scale")
    val dim by animateFloatAsState(if (edit != null && !selected) 0.4f else 1f, label = "tile dim")
    val current by rememberUpdatedState(tile)
    val currentLive by rememberUpdatedState(live)
    val editing by rememberUpdatedState(edit != null)
    val selectedNow by rememberUpdatedState(selected)
    val coords = remember { CoordinatesRef() }

    // A swipe down turns the tile between its front and a page (§5.3, §5.5). With animations off, atFront shows the
    // front where the face after it would show. A tile with a finger on it holds still (§5.4), and a sideways swipe
    // moves just the message under the finger.
    var page by remember { mutableStateOf<TilePage?>(null) }
    var atFront by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var swipe by remember { mutableStateOf<MessageSwipe?>(null) }
    val swipes = edit == null && !liveTilesPaused && !tile.isPaused
    val swipesNow by rememberUpdatedState(swipes)

    // The face showing: the front while paused; with animations off the front once swiped to, else a page's first face
    // or the face after the front; otherwise the live face.
    val tileFaces = pageFaces(tile.size, live, page)
    val liveFace = remember { TileFlipState(TileFace.Front.key) }
    val still = liveTilesPaused || !animationsOn
    val face = when {
        liveTilesPaused -> TileFace.Front
        !animationsOn -> if (atFront) TileFace.Front else if (page != null) tileFaces.first() else tileFaces.getOrElse(1) { TileFace.Front }
        else -> tileFaces.firstOrNull { it.key == liveFace.face } ?: tileFaces.first()
    }
    // A flip draws the old face until it's edge-on at 90°. A new messages page's faces are among the front's.
    val firstHalf by remember { derivedStateOf { liveFace.from != null && liveFace.angle < 90f } }
    val history = pageFaces(tile.size, live, TilePage.HISTORY)
    val drawn = if (!still && firstHalf) (faces(tile.size, live?.rotation.orEmpty()) + history).firstOrNull { it.key == liveFace.from } ?: TileFace.Front else face
    // A large tile's list keeps its place while the tile turns. Its rows start below the tile's top inset (§5.1).
    val stack = rememberLazyListState()
    val insetPx = with(LocalDensity.current) { LocalWpUnits.current.dp(TILE_INSET_WP_PX).toPx() }
    val stackLayout = {
        val info = stack.layoutInfo
        StackLayout(
            info.visibleItemsInfo.map { StackRow(it.index, insetPx + it.offset, insetPx + it.offset + it.size) },
            top = insetPx,
            bottom = insetPx + info.viewportSize.height,
        )
    }
    // A tap opens what's under the finger on the face drawn: its label strip and the front open the app (§5.5).
    val labelStripPx = with(LocalDensity.current) { LocalWpUnits.current.dp(LABEL_STRIP_WP_PX).toPx() }
    val tapTarget by rememberUpdatedState { y: Float, heightPx: Float -> tileTapTarget(drawn, y, heightPx, labelStripPx, stackLayout()) }
    val chatButtonPx = with(LocalDensity.current) { 48.dp.toPx() }
    val chatTarget by rememberUpdatedState { x: Float, y: Float, widthPx: Float, heightPx: Float ->
        tileChatTarget(drawn, x, y, widthPx, heightPx, chatButtonPx, stackLayout())
    }
    // TalkBack hears and acts on a list's rows in view, and pages through the rest.
    val first = if (face is TileFace.Stack) stack.firstVisibleItemIndex else 0
    val shown = face.shownItems.drop(first).take(STACK_SIZE)
    val scope = rememberCoroutineScope()
    val pages = if (face !is TileFace.Stack) emptyList() else listOfNotNull(
        CustomAccessibilityAction("older messages") {
            scope.launch { stack.animateScrollToItem((first + STACK_SIZE).coerceAtMost(face.items.lastIndex)) }
            true
        }.takeIf { stack.canScrollForward },
        CustomAccessibilityAction("newer messages") {
            scope.launch { stack.animateScrollToItem((first - STACK_SIZE).coerceAtLeast(0)) }
            true
        }.takeIf { stack.canScrollBackward },
    )
    val lines = shown.map { listOfNotNull(it.title, it.text).joinToString(": ") }
    val said = if (liveTilesPaused) "live tile paused" else {
        (listOfNotNull("history".takeIf { page == TilePage.HISTORY }, "$unread unread".takeIf { unread > 0 }) + lines).joinToString(", ").ifEmpty { null }
    }

    // From the front to its page, or from a page or a content face back to the front (§5.5). The page and the turn
    // change in one step, so the gone-face snap finds the face already turning.
    val turnDown by rememberUpdatedState {
        val to = if (page == null && face == TileFace.Front) pageFor(tile.size, live) else null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            page = to
            if (animationsOn) launch(start = CoroutineStart.UNDISPATCHED) { liveFace.flipTo(pageFaces(tile.size, live, to).first().key) }
            else atFront = to == null
            controller.ticker.jumped(tile.id, withFrameMillis { it }) // a turn counts as a move (§5.4)
        }
    }
    // TalkBack can't swipe, so the swipe down is an action too, whenever swipes are allowed (§5.5).
    val turnAction = when {
        !swipes -> null
        face != TileFace.Front || page != null -> "show front"
        else -> when (pageFor(tile.size, live)) {
            TilePage.NEW -> "show new messages"
            TilePage.HISTORY -> "show history"
            null -> null
        }
    }?.let { CustomAccessibilityAction(it) { turnDown(); true } }

    // The timer reaches the tile through the controller. A face that has gone, such as a read item's, gives way without
    // a flip: a page's to the page's first, and the front's to the front.
    val currentFaces by rememberUpdatedState(tileFaces)
    val listScrolled by rememberUpdatedState(face is TileFace.Stack && stack.canScrollBackward)
    DisposableEffect(tile.id) {
        controller.liveSlots[tile.id] = LiveSlot(liveFace, coords, held = { listScrolled || touched }) { currentFaces }
        onDispose { controller.liveSlots.remove(tile.id) }
    }
    LaunchedEffect(tileFaces) { if (tileFaces.none { it.key == liveFace.face }) liveFace.snapTo(tileFaces.first().key) }

    // A page changes under its items once no finger is on the tile (§5.3). History turning to new messages flips to the
    // new item while tiles move, rather than snapping to it.
    val movingNow by rememberUpdatedState(moving)
    val settled = settle(page, tile.size, live)
    LaunchedEffect(settled) {
        if (settled == page) return@LaunchedEffect
        snapshotFlow { touched }.first { !it }
        val from = page
        page = settle(from, current.size, currentLive) // afresh: the lift may have turned the tile meanwhile
        if (from == TilePage.HISTORY && page == TilePage.NEW && movingNow && controller.isShowing(coords.value)) {
            liveFace.flipTo(pageFaces(current.size, currentLive, page).first().key)
        }
    }

    // A new item flips its tile to it straight away, if the tile is on screen while tiles move, once no finger is on it.
    // Jumps on one tile wait for the ticker's debounce, and newer items replace any still waiting. With animations off,
    // a new item shows even after a swipe to the front, as it would flip in.
    val seen = remember { SeenRef() }
    LaunchedEffect(moving) {
        snapshotFlow { currentLive }.filterNotNull()
            .mapNotNull(seen::newest)
            .onEach { atFront = false }
            .filter { moving && controller.isShowing(coords.value) }
            .conflate()
            .collect { item ->
                delay(controller.ticker.jumpDelayMs(tile.id, withFrameMillis { it }))
                snapshotFlow { touched }.first { !it }
                val to = currentFaces.firstOrNull { f -> f.shownItems.any { it.key == item.key } }
                if (to is TileFace.Stack && !stack.isScrollInProgress) stack.scrollToItem(0) // the new item is the list's top row
                if (to == null || to.key == liveFace.face) return@collect
                controller.ticker.jumped(tile.id, withFrameMillis { it })
                liveFace.flipTo(to.key)
            }
    }

    // Letting go of a sideways swipe (§5.5): left opens the message's chat, or presses a missed call's first button, and
    // right clears it, sliding it out until it has left the tile, for at most 1 s. Then, or short of either, it slides
    // back. The next sideways swipe cuts its slides short, but never the clear.
    val letting = remember { JobRef() }
    val letGo by rememberUpdatedState { message: TileItem, to: Sideways?, widthPx: Float ->
        letting.value = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            suspend fun slide(x: Float) {
                if (animationsOn) animate(swipe?.offsetPx ?: 0f, x, animationSpec = tween(200)) { at, _ -> swipe = MessageSwipe(message.key, at) }
                swipe = MessageSwipe(message.key, x)
            }
            when (to) {
                Sideways.CHAT -> if (message.buttons.isEmpty()) controller.chat(current, coords.value, editing, message)
                else controller.press(current, coords.value, editing, message, 0)
                Sideways.CLEAR -> {
                    try { slide(widthPx) } finally { controller.clear(message) }
                    withTimeoutOrNull(1_000) { snapshotFlow { currentFaces }.first { faces -> faces.none { f -> f.shownItems.any { it.key == message.key } } } }
                }
                null -> Unit
            }
            slide(0f)
            swipe = null
        }
    }

    Box(
        Modifier
            .zIndex(if (selected) 1f else 0f)
            .offset { position.value.round() }
            .turnstile(flip, turn, pivotX = pivotX)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = dim * if (tile.isPaused) 0.4f else 1f
                // The old face turns from 0° to 90°, then the new one from -90° to 0°.
                if (!still && liveFace.from != null) {
                    rotationX = liveFace.angle.let { if (it < 90f) it else it - 180f }
                    cameraDistance = tileFlipCameraDistance(size.height)
                }
            }
            .testTag("tile-${tile.id}")
            .semantics {
                contentDescription = tile.label
                said?.let { stateDescription = it }
                onClick { controller.tap(current, coords.value, editing, shown.firstOrNull()); true }
                onLongClick { controller.longPress(current); true }
                // A content face's tap opens its first item in view, so the app and a list's other rows get actions of their own.
                val actions = if (face == TileFace.Front) emptyList() else {
                    listOf(CustomAccessibilityAction("open ${tile.label}") { controller.tap(current, coords.value, editing); true }) +
                        shown.drop(1).map { item ->
                            CustomAccessibilityAction("open ${item.title}") { controller.tap(current, coords.value, editing, item); true }
                        } +
                        shown.map { item ->
                            CustomAccessibilityAction("chat with ${item.title}") { controller.chat(current, coords.value, editing, item); true }
                        } +
                        // The swipe right, wherever it's allowed: a history page's messages only open (§5.5).
                        shown.takeIf { swipes && page != TilePage.HISTORY }.orEmpty().map { item ->
                            CustomAccessibilityAction("clear ${item.title}") { controller.clear(item); true }
                        } +
                        // A missed call's own buttons, in its app's words.
                        shown.flatMap { item ->
                            item.buttons.mapIndexed { i, button ->
                                CustomAccessibilityAction("${button.label} ${item.title}") { controller.press(current, coords.value, editing, item, i); true }
                            }
                        } +
                        pages
                } + listOfNotNull(turnAction)
                if (actions.isNotEmpty()) customActions = actions
            }
            .onPlaced { coords.value = it }
            .pointerInput(tile.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Only the lifted tile moves at a touch; any other needs a long-press, so a swipe on it scrolls.
                    if (!editing || !selectedNow) {
                        touched = true
                        try {
                            // A large tile's list scrolls back to its top before a swipe down turns the tile (§5.5).
                            val turns = swipesNow && controller.startAtTop() && pageFor(current.size, currentLive) != null && !listScrolled
                            // A sideways swipe takes the message under the finger: there's none on a front or the label strip.
                            val message = if (swipesNow) tapTarget(down.position.y, size.height.toFloat()) else null
                            val velocity = VelocityTracker().apply { addPointerInputChange(down) }
                            var moved = Offset.Zero
                            var claimed = false
                            var sliding = false
                            var cancelled = false
                            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    // Initial: this tile sees the event before a large tile's list, so the list never starts a swipe down it takes.
                                    val early = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                    if (early == null) { cancelled = true; break }
                                    velocity.addPointerInputChange(early)
                                    moved += early.position - early.previousPosition
                                    if (turns && early.pressed && moved.y >= viewConfiguration.touchSlop && moved.y > abs(moved.x)) { // >=, as start's scroll
                                        early.consume()
                                        claimed = true
                                        break
                                    }
                                    // Main and Final: as waitForUpOrCancellation. A child button, start's scroll or the pager may have it.
                                    val change = awaitPointerEvent(PointerEventPass.Main).changes.first { it.id == down.id }
                                    if (change.isConsumed || change.isOutOfBounds(size, extendedTouchPadding)) { cancelled = true; break }
                                    if (change.changedToUp()) return@withTimeoutOrNull change
                                    // Start's scroll and the pager see the Main pass after this tile, so they let a message's sideways swipe go.
                                    if (message != null && change.pressed && abs(moved.x) >= viewConfiguration.touchSlop && abs(moved.x) > abs(moved.y)) { // >=, as the pager's slop
                                        change.consume()
                                        sliding = true
                                        break
                                    }
                                    if (awaitPointerEvent(PointerEventPass.Final).changes.first { it.id == down.id }.isConsumed) { cancelled = true; break }
                                }
                                null
                            }
                            if (cancelled) return@awaitEachGesture
                            if (claimed) {
                                // The tile holds still under the finger, and turns as it lets go: past a third of its height, or with a flick.
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                    change.consume()
                                    velocity.addPointerInputChange(change)
                                    if (change.pressed) continue
                                    val flingPx = FLING_DP_PER_S.dp.toPx()
                                    if (turnsDown(change.position.y - down.position.y, velocity.calculateVelocity().y, size.height.toFloat(), flingPx)) turnDown()
                                    return@awaitEachGesture
                                }
                            }
                            if (sliding && message != null) {
                                // The message follows the finger, only leftward on a history page, and acts as it lets go.
                                val canClear = page != TilePage.HISTORY
                                var drag = moved.x
                                letting.value?.cancel() // this message's slide is the one drawn now
                                while (true) {
                                    swipe = MessageSwipe(message.key, if (canClear) drag else drag.coerceAtMost(0f))
                                    val change = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull { it.id == down.id }
                                        ?: run { letGo(message, null, size.width.toFloat()); return@awaitEachGesture }
                                    change.consume()
                                    velocity.addPointerInputChange(change)
                                    drag = change.position.x - down.position.x
                                    if (change.pressed) continue
                                    val flingPx = FLING_DP_PER_S.dp.toPx()
                                    letGo(message, sideways(drag, velocity.calculateVelocity().x, size.width.toFloat(), flingPx, canClear), size.width.toFloat())
                                    return@awaitEachGesture
                                }
                            }
                            if (up != null) {
                                up.consume()
                                val chat = chatTarget(up.position.x, up.position.y, size.width.toFloat(), size.height.toFloat())
                                if (chat != null) controller.chat(current, coords.value, editing, chat)
                                else controller.tap(current, coords.value, editing, tapTarget(up.position.y, size.height.toFloat()))
                                return@awaitEachGesture
                            }
                        } finally {
                            touched = false
                        }
                        controller.longPress(current)
                        controller.startDrag(current, position.value)
                    } else {
                        val overSlop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (overSlop == null) {
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed && !up.isConsumed) {
                                up.consume()
                                controller.tap(current, coords.value, editing = true)
                            }
                            return@awaitEachGesture
                        }
                        controller.startDrag(current, position.value)
                        coords.value?.let { controller.dragBy(overSlop.position - down.position, overSlop.position, it) }
                    }
                    val lifted = drag(down.id) { change ->
                        coords.value?.let { controller.dragBy(change.positionChange(), change.position, it) }
                        change.consume()
                    }
                    // The lift ends the drag, so the screen's tap-to-leave-edit-mode mustn't see it.
                    currentEvent.changes.firstOrNull { it.id == down.id }?.consume()
                    if (lifted) controller.endDrag() else controller.cancelDrag()
                }
            },
    ) {
        StartTileView(
            tile, metrics, icon, Modifier.clearAndSetSemantics {}.tilt(enabled = edit == null),
            face = drawn, unread = unread, liveTilesPaused = liveTilesPaused, stack = stack, scrollable = edit == null,
            // While a history face is drawn, through a flip's first half too.
            label = if (drawn != TileFace.Front && drawn in history) "history" else tile.label,
            swipe = swipe,
            onButton = { item, i -> controller.press(current, coords.value, editing, item, i) },
        )
        if (selected && controller.drag == null) {
            EditControls(
                metrics.sizeDp(tile.size),
                onUnpin = { controller.unpin(tile.id) },
                onResize = { controller.resize(tile.id) },
                onHiddenChats = if (live?.hiddenChats.isNullOrEmpty()) null else ({ controller.hiddenChatsOf = tile.id }),
            )
        }
    }
}

private class CoordinatesRef {
    var value: LayoutCoordinates? = null
}

private class JobRef {
    var value: Job? = null
}

// The keys of a tile's last rotation, null until its first live state: the baseline.
private class SeenRef {
    private var keys: Set<String>? = null

    /** [state]'s newest item if it's new, not in the last rotation. [state] becomes the last rotation. */
    fun newest(state: TileState): TileItem? {
        val before = keys
        keys = state.rotation.mapTo(HashSet()) { it.key }
        return state.rotation.firstOrNull()?.takeIf { before != null && it.key !in before }
    }
}

/** A tile as the live tiles' timer sees it: its face, where it is, whether it's held where it is, and the faces it has now. */
private class LiveSlot(val face: TileFlipState, val coords: CoordinatesRef, val held: () -> Boolean, val faces: () -> List<TileFace>)

/** The items a face shows. */
private val TileFace.shownItems: List<TileItem>
    get() = when (this) {
        TileFace.Front -> emptyList()
        is TileFace.Item -> listOf(item)
        is TileFace.Stack -> items
    }

/** A tile being dragged: its top-left in grid pixels, and the finger's height in the viewport. */
private class TileDrag(val id: Long, val size: TileSize, topLeft: Offset) {
    var topLeft by mutableStateOf(topLeft)
    var fingerY by mutableFloatStateOf(Float.NaN)
}

/** Turns the tiles' gestures into view model calls, holds the drag in progress, and turns the live tiles. */
private class StartController(private val viewModel: StartViewModel) {
    var drag by mutableStateOf<TileDrag?>(null)
        private set
    var hiddenChatsOf by mutableStateOf<Long?>(null) // the tile whose hidden chats list is open
    var viewport: LayoutCoordinates? = null

    // One ticker for the screen's life, and every tile on the grid.
    val ticker = TileTicker(Random.Default)
    val liveSlots = mutableMapOf<Long, LiveSlot>()

    /** Whether a tile at [coords] overlaps the viewport. */
    fun isShowing(coords: LayoutCoordinates?): Boolean {
        val viewport = viewport ?: return false
        if (coords == null || !coords.isAttached || !viewport.isAttached) return false
        return viewport.localBoundingBoxOf(coords, clipBounds = false).overlaps(viewport.size.toSize().toRect())
    }

    /**
     * One turn of the timer: a tile on screen with more than one face flips to its next face, and the turn ends with it.
     * A tile whose list is scrolled from its top, or with a finger on it, stays as it is.
     */
    suspend fun tickLive() {
        val candidates = liveSlots.filterValues { it.faces().size > 1 && !it.held() && isShowing(it.coords.value) }
        val slot = ticker.tick(candidates.keys)?.let(candidates::getValue) ?: return
        slot.face.flipTo(nextFace(slot.faces(), slot.face.face).key)
    }

    /** Opens a tile's app, or the item given, or presses its button, from its bounds in the window, however the screen wants to. */
    var launch: (Long, Rect?, TileItem?, Int?) -> Unit = { id, bounds, item, button -> viewModel.open(id, bounds, null, item, button) }
    var onChat: (TileItem) -> Unit = {}
    var startAtTop: () -> Boolean = { true } // whether start is scrolled all the way up; StartScreen sets it
    private var haptic: HapticFeedback? = null
    private var sidePx = 0f
    private var stepPx = 1f
    private var gridWidth = 1

    fun update(haptic: HapticFeedback, sidePx: Float, stepPx: Float, gridWidth: Int) {
        this.haptic = haptic
        this.sidePx = sidePx
        this.stepPx = stepPx
        this.gridWidth = gridWidth
    }

    // The dragged tile's top-left in whole cells, kept inside the grid.
    fun hoverCell(d: TileDrag) = Cell(
        ((d.topLeft.x - sidePx) / stepPx).roundToInt().coerceIn(0, (gridWidth - d.size.widthUnits).coerceAtLeast(0)),
        (d.topLeft.y / stepPx).roundToInt().coerceAtLeast(0),
    )

    /**
     * In edit mode this selects the tile; otherwise it hands the tile, its bounds in the window and any [item] and [button]
     * to [launch].
     */
    fun tap(tile: StartTile, coords: LayoutCoordinates?, editing: Boolean, item: TileItem? = null, button: Int? = null) {
        if (editing) return viewModel.open(tile.id, null, null)
        val bounds = coords?.takeIf { it.isAttached }?.boundsInWindow()?.let {
            Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
        }
        launch(tile.id, bounds, item, button)
    }

    /** Opens [item]'s chat. In edit mode, or on a tile whose profile is paused, it taps the tile instead. */
    fun chat(tile: StartTile, coords: LayoutCoordinates?, editing: Boolean, item: TileItem) {
        if (editing || tile.isPaused) tap(tile, coords, editing, item) else onChat(item)
    }

    /** A missed call's button (§5.5): a reply button opens the chat window; in edit mode, or on a paused tile, it's a tap. */
    fun press(tile: StartTile, coords: LayoutCoordinates?, editing: Boolean, item: TileItem, button: Int) = when {
        editing || tile.isPaused -> tap(tile, coords, editing, item)
        item.buttons.getOrNull(button)?.isReply == true -> onChat(item)
        else -> tap(tile, coords, editing, item, button)
    }

    fun clear(item: TileItem) = viewModel.clear(item)

    fun longPress(tile: StartTile) {
        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.enterEdit(tile.id)
    }

    fun startDrag(tile: StartTile, topLeft: Offset) {
        viewModel.select(tile.id)
        drag = TileDrag(tile.id, tile.size, topLeft)
    }

    fun dragBy(delta: Offset, finger: Offset, tileCoords: LayoutCoordinates) {
        val active = drag ?: return
        active.topLeft += delta
        val viewport = viewport
        if (viewport != null && viewport.isAttached && tileCoords.isAttached) active.fingerY = viewport.localPositionOf(tileCoords, finger).y
    }

    fun endDrag() {
        val active = drag ?: return
        val cell = hoverCell(active)
        viewModel.previewMove(active.id, cell) // shows the drop until the move is saved
        viewModel.endDrag(active.id, cell)
        drag = null
    }

    fun cancelDrag() {
        viewModel.cancelDrag()
        drag = null
    }

    fun unpin(tileId: Long) = viewModel.unpin(tileId)

    fun resize(tileId: Long) = viewModel.cycleSize(tileId)
}
