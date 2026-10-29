package io.github.b1tsagar.liquiwin.feature.start

import android.graphics.Rect
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.b1tsagar.liquiwin.core.data.apps.AppCatalog
import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.tiles.LayoutEdit
import io.github.b1tsagar.liquiwin.core.tiles.TileStore
import io.github.b1tsagar.liquiwin.core.tiles.model.Tile
import io.github.b1tsagar.liquiwin.core.tiles.model.TileKind
import io.github.b1tsagar.liquiwin.core.tiles.model.TilePlacement
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.source.HiddenChat
import io.github.b1tsagar.liquiwin.core.tiles.source.SourceRegistry
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import io.github.b1tsagar.liquiwin.core.tiles.source.TileState
import io.github.b1tsagar.liquiwin.core.tiles.source.tileState
import io.github.b1tsagar.liquiwin.feature.start.layout.Cell
import io.github.b1tsagar.liquiwin.feature.start.layout.GridLayout
import io.github.b1tsagar.liquiwin.feature.start.layout.Placed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class StartState(val gridWidth: Int, val tiles: List<StartTile>, val edit: EditState?, val loaded: Boolean)

data class StartTile(
    val id: Long,
    val size: TileSize,
    val cell: Cell,
    val app: LauncherApp?,
    val label: String,
    val isWork: Boolean,
    val isPaused: Boolean,
)

data class EditState(val selectedId: Long?, val preview: Map<Long, Cell>?)

/**
 * The start screen on a grid [gridWidth] units wide. Every edit, every repair and every commit of a repaired
 * arrangement runs under one [Mutex] and reads the latest store state. [sources] serves the tiles' live sources.
 */
class StartViewModel(
    private val store: TileStore,
    private val catalog: AppCatalog,
    private val gridWidth: Int,
    private val sources: SourceRegistry = SourceRegistry { null },
) : ViewModel() {
    private val mutex = Mutex()
    private val edit = MutableStateFlow<EditState?>(null)

    val state: StateFlow<StartState> =
        combine(store.tiles, store.placements(gridWidth), catalog.apps, catalog.profiles, edit) { tiles, saved, apps, profiles, edit ->
            val byKey = apps.associateBy { it.key }
            val byId = tiles.associateBy { it.id }
            val mainSerial = profiles.firstOrNull { !it.isWork }?.userSerial
            val shown = arrange(tiles, saved, gridWidth).map { placed ->
                val tile = byId.getValue(placed.id)
                val key = tile.appKey
                val app = key?.let(byKey::get)
                StartTile(
                    id = tile.id,
                    size = tile.size,
                    cell = placed.cell,
                    app = app,
                    label = app?.label ?: key?.packageName ?: tile.title.orEmpty(),
                    isWork = key != null && mainSerial != null && key.userSerial != mainSerial,
                    isPaused = key != null && profiles.any { it.userSerial == key.userSerial && it.isPaused },
                )
            }
            StartState(gridWidth, shown, edit, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, StartState(gridWidth, emptyList(), edit = null, loaded = false))

    val pinnedApps: StateFlow<Set<AppKey>> = store.tiles
        .map { tiles -> tiles.mapNotNullTo(HashSet()) { if (it.kind == TileKind.APP) it.appKey else null } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Each tile's live state, by tile id. It's apart from [state], so a new message never re-runs the layout. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val live: StateFlow<Map<Long, TileState>> = store.tiles
        .map { tiles -> tiles.map { it.id to it.sources } }
        .distinctUntilChanged()
        .flatMapLatest { tiles ->
            if (tiles.isEmpty()) flowOf(emptyMap<Long, TileState>())
            else combine(tiles.map { (id, ids) -> tileState(ids.mapNotNull(sources::source)).map { id to it } }) { it.toMap() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        // A saved arrangement that needs repair (a tile never placed, say) is committed once, repaired.
        viewModelScope.launch {
            combine(store.tiles, store.placements(gridWidth)) { tiles, saved -> arrange(tiles, saved, gridWidth).toPlacements(gridWidth) != saved }
                .collect { if (it) repairArrangement() }
        }
        // App tiles are repaired only against an app list read in this process: a stale or empty one never removes tiles.
        viewModelScope.launch {
            combine(store.tiles, catalog.apps, catalog.profiles, catalog.isFresh) { tiles, apps, profiles, fresh ->
                fresh && planAppTileRepairs(tiles, apps, profiles) != AppTileRepairs()
            }.collect { if (it) repairAppTiles() }
        }
    }

    fun pin(app: AppKey) = locked {
        val tiles = store.tiles.first()
        if (tiles.any { it.kind == TileKind.APP && it.appKey == app }) return@locked
        val before = arrangements(tiles.sizes())
        val id = store.insertAppTile(app, TileSize.MEDIUM)
        store.commit(LayoutEdit(placements = before.mapValues { (width, placed) -> GridLayout.append(placed, id, TileSize.MEDIUM, width).toPlacements(width) }))
    }

    fun unpin(tileId: Long) = locked {
        val tiles = store.tiles.first()
        if (tiles.none { it.id == tileId }) return@locked
        val placements = arrangements(tiles.sizes()).mapValues { (width, placed) -> GridLayout.remove(placed, tileId, width).toPlacements(width) }
        store.commit(LayoutEdit(deleted = setOf(tileId), placements = placements))
        edit.update { if (it?.selectedId == tileId) it.copy(selectedId = null, preview = null) else it }
    }

    fun cycleSize(tileId: Long) = locked {
        val tiles = store.tiles.first()
        val newSize = tiles.firstOrNull { it.id == tileId }?.size?.next() ?: return@locked
        val placements = arrangements(tiles.sizes()).mapValues { (width, placed) -> GridLayout.resize(placed, tileId, newSize, width).toPlacements(width) }
        store.commit(LayoutEdit(sizes = mapOf(tileId to newSize), placements = placements))
    }

    fun enterEdit(tileId: Long) {
        edit.value = EditState(selectedId = tileId, preview = null)
    }

    fun select(tileId: Long) = edit.update { it?.copy(selectedId = tileId) }

    fun previewMove(tileId: Long, cell: Cell) {
        val placed = state.value.tiles.map { Placed(it.id, it.size, it.cell) }
        edit.update { it?.copy(preview = GridLayout.move(placed, tileId, cell, gridWidth).associate { p -> p.id to p.cell }) }
    }

    fun endDrag(tileId: Long, cell: Cell) {
        viewModelScope.launch {
            val moved = mutex.withLock {
                val tiles = store.tiles.first()
                if (tiles.none { it.id == tileId }) return@withLock null
                GridLayout.move(arrangement(gridWidth, tiles.sizes()), tileId, cell, gridWidth)
                    .also { store.commit(LayoutEdit(placements = mapOf(gridWidth to it.toPlacements(gridWidth)))) }
            } ?: return@launch
            // The store publishes the saved move a moment after the commit. Until then the preview shows it, so the
            // tiles don't jump back to their old cells; a newer drag's preview is left alone.
            val dropped = edit.value?.preview
            val cells = moved.associate { it.id to it.cell }
            withTimeoutOrNull(1_000) { state.first { s -> s.tiles.all { cells[it.id] == it.cell } } }
            edit.update { if (it != null && it.preview === dropped) it.copy(preview = null) else it }
        }
    }

    fun cancelDrag() = edit.update { it?.copy(preview = null) }

    fun exitEdit() {
        edit.value = null
    }

    /**
     * In edit mode this selects the tile. A tile of a paused profile asks to unpause it; any other presses [item]'s
     * [button] or opens [item] through its source or, with no item, launches its app.
     */
    fun open(tileId: Long, sourceBounds: Rect?, options: Bundle?, item: TileItem? = null, button: Int? = null) {
        if (edit.value != null) return select(tileId)
        viewModelScope.launch {
            val key = store.tiles.first().firstOrNull { it.id == tileId }?.appKey ?: return@launch
            when {
                catalog.profiles.value.any { it.userSerial == key.userSerial && it.isPaused } -> catalog.requestUnpause(key.userSerial)
                item != null && button != null -> sources.source(item.sourceId)?.press(item, button)
                item != null -> sources.source(item.sourceId)?.open(item)
                else -> catalog.launch(key, sourceBounds, options)
            }
        }
    }

    /** Clears [item] through its source: its notification leaves Android's panel, and its messages the tile (§5.5). */
    fun clear(item: TileItem) {
        viewModelScope.launch { sources.source(item.sourceId)?.clear(item) }
    }

    /** Puts [chat] back on tiles (§5.8). */
    fun showAgain(chat: HiddenChat) {
        viewModelScope.launch { sources.source(chat.sourceId)?.showAgain(chat.conversationKey) }
    }

    private suspend fun repairArrangement() = mutex.withLock {
        val tiles = store.tiles.first()
        val saved = store.placements(gridWidth).first()
        val repaired = arrange(tiles, saved, gridWidth).toPlacements(gridWidth)
        if (repaired != saved) store.commit(LayoutEdit(placements = mapOf(gridWidth to repaired)))
    }

    private suspend fun repairAppTiles() = mutex.withLock {
        if (!catalog.isFresh.value) return@withLock
        val tiles = store.tiles.first()
        val repairs = planAppTileRepairs(tiles, catalog.apps.value, catalog.profiles.value)
        if (repairs == AppTileRepairs()) return@withLock
        val kept = tiles.filter { it.id !in repairs.remove }.sizes()
        val placements = store.savedWidths().associateWith { width -> arrangement(width, kept).toPlacements(width) }
        store.commit(LayoutEdit(deleted = repairs.remove, appKeys = repairs.repoint, placements = placements))
    }

    private fun locked(block: suspend () -> Unit) {
        viewModelScope.launch { mutex.withLock { block() } }
    }

    // The current arrangement of every saved width and this one, as an edit starts from.
    private suspend fun arrangements(sizes: Map<Long, TileSize>): Map<Int, List<Placed>> =
        (store.savedWidths() + gridWidth).associateWith { arrangement(it, sizes) }

    private suspend fun arrangement(width: Int, sizes: Map<Long, TileSize>): List<Placed> =
        GridLayout.reconcile(sizes, store.placements(width).first().ifEmpty { null }, fallbackOrder = emptyList(), width)
}

// fallbackOrder is empty: stage 1 has one width. Stage 3 passes the most recently used width's reading order.
private fun arrange(tiles: List<Tile>, saved: List<TilePlacement>, width: Int): List<Placed> =
    GridLayout.reconcile(tiles.sizes(), saved.ifEmpty { null }, fallbackOrder = emptyList(), width)

private fun List<Tile>.sizes(): Map<Long, TileSize> = associate { it.id to it.size }

private fun List<Placed>.toPlacements(width: Int): List<TilePlacement> = map { TilePlacement(it.id, width, it.cell.x, it.cell.y) }
