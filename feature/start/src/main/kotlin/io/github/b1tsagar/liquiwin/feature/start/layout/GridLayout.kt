package io.github.b1tsagar.liquiwin.feature.start.layout

import io.github.b1tsagar.liquiwin.core.tiles.model.TilePlacement
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize

data class Cell(val x: Int, val y: Int)

data class Placed(val id: Long, val size: TileSize, val cell: Cell)

/**
 * Arranges tiles on a grid `width` units wide (4, 6 or 8). Every function returns a valid arrangement:
 * in bounds, no overlaps, compacted, and sorted in reading order (by y, then x).
 */
object GridLayout {
    private val readingOrder = compareBy<Placed>({ it.cell.y }, { it.cell.x })

    /** First fit for each tile, in the given order, from the top. */
    fun flow(tiles: List<Pair<Long, TileSize>>, width: Int): List<Placed> =
        tiles.fold(emptyList<Placed>()) { placed, (id, size) -> placed + firstFit(placed, id, size, width, fromRow = 0) }
            .sortedWith(readingOrder)

    /** Until nothing moves: in reading order, each tile floats up while the row above is free in all its columns. */
    fun compact(tiles: List<Placed>, width: Int): List<Placed> {
        val out = tiles.sortedWith(readingOrder).toMutableList()
        do {
            var moved = false
            for (i in out.indices) {
                while (out[i].cell.y > 0) {
                    val up = out[i].copy(cell = Cell(out[i].cell.x, out[i].cell.y - 1))
                    if (out.indices.any { it != i && out[it].overlaps(up) }) break
                    out[i] = up
                    moved = true
                }
            }
        } while (moved)
        return out.sortedWith(readingOrder)
    }

    /** First fit from the top row of the lowest tile, then compact. */
    fun append(tiles: List<Placed>, id: Long, size: TileSize, width: Int): List<Placed> =
        compact(tiles + firstFit(tiles, id, size, width, fromRow = tiles.maxOfOrNull { it.cell.y } ?: 0), width)

    fun remove(tiles: List<Placed>, id: Long, width: Int): List<Placed> = compact(tiles.filter { it.id != id }, width)

    /** Fixes the tile at [target], clamped into the grid, and re-flows the others from the first one it collides with. */
    fun move(tiles: List<Placed>, id: Long, target: Cell, width: Int): List<Placed> {
        val tile = tiles.firstOrNull { it.id == id } ?: return tiles
        val cell = Cell(target.x.coerceIn(0, width - tile.size.widthUnits), target.y.coerceAtLeast(0))
        return reflowAround(tile.copy(cell = cell), tiles, width)
    }

    /** Keeps the tile's cell, shifted left if it would stick out, and re-flows the others from the first collision. */
    fun resize(tiles: List<Placed>, id: Long, newSize: TileSize, width: Int): List<Placed> {
        val tile = tiles.firstOrNull { it.id == id } ?: return tiles
        val cell = Cell(tile.cell.x.coerceAtMost(width - newSize.widthUnits), tile.cell.y)
        return reflowAround(Placed(id, newSize, cell), tiles, width)
    }

    /**
     * The arrangement to show for the tiles in [sizes]. Without a [saved] arrangement the tiles flow in [fallbackOrder],
     * then by ascending id. With one, its valid placements stay; placements that are out of bounds, overlap an earlier
     * one or belong to a missing tile are dropped, and tiles without a placement are appended by ascending id.
     */
    fun reconcile(sizes: Map<Long, TileSize>, saved: List<TilePlacement>?, fallbackOrder: List<Long>, width: Int): List<Placed> {
        if (saved == null) {
            val order = fallbackOrder.filter { it in sizes }.distinct() + sizes.keys.filter { it !in fallbackOrder }.sorted()
            return flow(order.map { it to sizes.getValue(it) }, width)
        }
        val kept = mutableListOf<Placed>()
        for (p in saved.sortedWith(compareBy({ it.y }, { it.x }))) {
            val size = sizes[p.tileId] ?: continue
            val candidate = Placed(p.tileId, size, Cell(p.x, p.y))
            val inBounds = p.x >= 0 && p.y >= 0 && p.x + size.widthUnits <= width
            if (inBounds && kept.none { it.id == p.tileId || it.overlaps(candidate) }) kept += candidate
        }
        val missing = sizes.keys.filter { id -> kept.none { it.id == id } }.sorted()
        return compact(missing.fold(kept.toList()) { out, id -> append(out, id, sizes.getValue(id), width) }, width)
    }

    // The fixed tile stays; tiles before the first one it overlaps stay; that tile and all after it take the first fit
    // from its row, around the tiles already placed, so none jumps into a hole above the collision.
    private fun reflowAround(fixed: Placed, tiles: List<Placed>, width: Int): List<Placed> {
        val others = tiles.filter { it.id != fixed.id }.sortedWith(readingOrder)
        val firstHit = others.indexOfFirst { it.overlaps(fixed) }
        if (firstHit < 0) return compact(others + fixed, width)
        val fromRow = others[firstHit].cell.y
        val placed = (others.take(firstHit) + fixed).toMutableList()
        for (t in others.drop(firstHit)) placed += firstFit(placed, t.id, t.size, width, fromRow)
        return compact(placed, width)
    }

    // Rows from fromRow down, x from 0 in each: the first position where the tile is in bounds and overlaps nothing.
    private fun firstFit(placed: List<Placed>, id: Long, size: TileSize, width: Int, fromRow: Int): Placed {
        var y = fromRow
        while (true) {
            for (x in 0..width - size.widthUnits) {
                val candidate = Placed(id, size, Cell(x, y))
                if (placed.none { it.overlaps(candidate) }) return candidate
            }
            y++
        }
    }

    private fun Placed.overlaps(other: Placed): Boolean =
        cell.x < other.cell.x + other.size.widthUnits && other.cell.x < cell.x + size.widthUnits &&
            cell.y < other.cell.y + other.size.heightUnits && other.cell.y < cell.y + size.heightUnits
}
