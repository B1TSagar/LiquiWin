package io.github.b1tsagar.liquiwin.feature.start

import android.app.ActivityOptions
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.b1tsagar.liquiwin.core.data.apps.AppIcon
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.motion.Turnstile
import io.github.b1tsagar.liquiwin.core.design.motion.TurnstileClock
import io.github.b1tsagar.liquiwin.core.design.motion.tilt
import io.github.b1tsagar.liquiwin.core.design.motion.turnstile
import io.github.b1tsagar.liquiwin.core.design.style.LocalStyleFacets
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.feature.start.layout.Cell
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val EMPTY_HINT = "swipe left for your apps. long-press one and choose pin to start."

/**
 * The start screen: the tile grid with edit mode. Outside edit mode a tap turnstiles the tiles away and then opens the
 * tile's app, and a long-press enters edit mode and lets the finger drag the tile straight away. In edit mode a tap
 * selects a tile, the selected tile moves with a drag, and any other tile moves after a long-press, so a swipe on it
 * still scrolls. When [shown] turns false the tiles turnstile away; when it turns true, or LiquiWin comes back after a
 * launch, they swing back in.
 */
@Composable
fun StartScreen(
    viewModel: StartViewModel,
    icon: suspend (LauncherApp) -> AppIcon?,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    shown: Boolean = true,
    banner: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    SideEffect { controller.update(haptic, sidePx, stepPx, state.gridWidth) }

    // One clock turns every tile. A tap's tile goes last, and its app opens once the others have gone.
    val flip = remember { TurnstileClock() }
    var tapped by remember { mutableStateOf<Long?>(null) }
    fun lastTurn(tapped: Long?) = startTurnstileOrder(state.tiles.associate { it.id to it.cell }, tapped).values.maxOrNull() ?: 0
    LaunchedEffect(shown) {
        tapped = null
        if (!shown) flip.play(Turnstile.OUT, lastTurn(null))
        else if (flip.motion == Turnstile.OUT) flip.play(Turnstile.BACK_IN, lastTurn(null))
    }
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (shown && flip.motion == Turnstile.OUT && !flip.isPlaying) scope.launch { flip.play(Turnstile.BACK_IN, lastTurn(null)) }
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val launching = remember { JobRef() }
    SideEffect {
        controller.launch = { id, bounds ->
            if (launching.value?.isActive != true) launching.value = scope.launch {
                tapped = id
                flip.play(Turnstile.OUT, lastTurn(id))
                viewModel.open(id, bounds, ActivityOptions.makeCustomAnimation(context, android.R.anim.fade_in, 0).toBundle())
                // A launch that fails leaves LiquiWin on screen, so nothing would bring the tiles back: they come back here.
                val left = withTimeoutOrNull(1_500) { lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.RESUMED) } }
                if (left == null) flip.play(Turnstile.BACK_IN, lastTurn(null))
            }
        }
    }

    val editing by rememberUpdatedState(state.edit != null)
    BackHandler(enabled = state.edit != null) { viewModel.exitEdit() }

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
                .onPlaced { controller.viewport = it }
                .verticalScroll(scrollState)
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .padding(top = metrics.gapDp.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(bottom = units.dp(96f)),
        ) {
            banner()
            TileGrid(state, metrics, sidePx, stepPx, controller, icon, flip, tapped)
        }
        if (state.loaded && state.tiles.isEmpty()) {
            WpText(
                text = EMPTY_HINT,
                style = LocalLiquiWinType.current.body.copy(textAlign = TextAlign.Center),
                modifier = Modifier.align(Alignment.Center).padding(horizontal = metrics.sideMarginDp.dp),
                color = LocalLiquiWinColors.current.subtle,
            )
        }
        if (state.edit != null) EditBar(onDone = viewModel::exitEdit, modifier = Modifier.align(Alignment.BottomCenter))
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
                TileSlot(tile, cell, metrics, sidePx, stepPx, state.edit, controller, icon, flip, turns.getValue(tile.id), pivotX)
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
) {
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
    val editing by rememberUpdatedState(edit != null)
    val selectedNow by rememberUpdatedState(selected)
    val coords = remember { CoordinatesRef() }
    Box(
        Modifier
            .zIndex(if (selected) 1f else 0f)
            .offset { position.value.round() }
            .turnstile(flip, turn, pivotX = pivotX)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = dim * if (tile.isPaused) 0.4f else 1f
            }
            .testTag("tile-${tile.id}")
            .semantics {
                contentDescription = tile.label
                onClick { controller.tap(current, coords.value, editing); true }
                onLongClick { controller.longPress(current); true }
            }
            .onPlaced { coords.value = it }
            .pointerInput(tile.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Only the lifted tile moves at a touch; any other needs a long-press, so a swipe on it scrolls.
                    if (!editing || !selectedNow) {
                        var cancelled = false
                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation().also { if (it == null) cancelled = true }
                        }
                        if (cancelled) return@awaitEachGesture
                        if (up != null) {
                            up.consume()
                            controller.tap(current, coords.value, editing)
                            return@awaitEachGesture
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
        StartTileView(tile, metrics, icon, Modifier.clearAndSetSemantics {}.tilt(enabled = edit == null))
        if (selected && controller.drag == null) {
            EditControls(metrics.sizeDp(tile.size), onUnpin = { controller.unpin(tile.id) }, onResize = { controller.resize(tile.id) })
        }
    }
}

private class CoordinatesRef {
    var value: LayoutCoordinates? = null
}

private class JobRef {
    var value: Job? = null
}

/** A tile being dragged: its top-left in grid pixels, and the finger's height in the viewport. */
private class TileDrag(val id: Long, val size: TileSize, topLeft: Offset) {
    var topLeft by mutableStateOf(topLeft)
    var fingerY by mutableFloatStateOf(Float.NaN)
}

/** Turns the tiles' gestures into view model calls, and holds the drag in progress. */
private class StartController(private val viewModel: StartViewModel) {
    var drag by mutableStateOf<TileDrag?>(null)
        private set
    var viewport: LayoutCoordinates? = null

    /** Opens a tile's app from its bounds in the window, however the screen wants to. */
    var launch: (Long, Rect?) -> Unit = { id, bounds -> viewModel.open(id, bounds, null) }
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

    /** In edit mode this selects the tile; otherwise it hands the tile and its bounds in the window to [launch]. */
    fun tap(tile: StartTile, coords: LayoutCoordinates?, editing: Boolean) {
        if (editing) return viewModel.open(tile.id, null, null)
        val bounds = coords?.takeIf { it.isAttached }?.boundsInWindow()?.let {
            Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
        }
        launch(tile.id, bounds)
    }

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
