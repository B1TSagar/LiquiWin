package io.github.b1tsagar.liquiwin.feature.start.layout

import io.github.b1tsagar.liquiwin.core.tiles.model.TilePlacement
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize.LARGE
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize.MEDIUM
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize.SMALL
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize.WIDE
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridLayoutTest {
    private fun p(id: Long, size: TileSize, x: Int, y: Int) = Placed(id, size, Cell(x, y))
    private fun cells(tiles: List<Placed>) = tiles.associate { it.id to it.cell }

    @Test fun flowFillsRowsFromTheTopLeft() {
        val out = GridLayout.flow(listOf(1L to MEDIUM, 2L to WIDE, 3L to SMALL, 4L to SMALL, 5L to MEDIUM), width = 6)
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(2, 0), 3L to Cell(0, 2), 4L to Cell(1, 2), 5L to Cell(2, 2)), cells(out))
    }

    @Test fun flowFillsEarlierHolesWithLaterSmallTiles() {
        val out = GridLayout.flow(listOf(1L to MEDIUM, 2L to WIDE, 3L to SMALL), width = 4)
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(0, 2), 3L to Cell(2, 0)), cells(out))
    }

    @Test fun compactionFloatsTilesUpWithinTheirColumns() {
        val out = GridLayout.compact(listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 2, 4), p(3, SMALL, 0, 5)), width = 4)
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(2, 0), 3L to Cell(0, 2)), cells(out))
    }

    @Test fun appendAddsAfterTheLastRow() {
        val start = listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 2, 0), p(3, MEDIUM, 4, 0), p(4, MEDIUM, 0, 2))
        assertEquals(Cell(2, 2), cells(GridLayout.append(start, 5, MEDIUM, width = 6))[5])
        assertEquals(Cell(0, 0), cells(GridLayout.append(emptyList(), 1, WIDE, width = 6))[1])
    }

    @Test fun removeClosesTheGap() {
        val out = GridLayout.remove(listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 0, 2), p(3, MEDIUM, 2, 2)), id = 1, width = 4)
        assertEquals(mapOf(2L to Cell(0, 0), 3L to Cell(2, 0)), cells(out))
    }

    @Test fun moveOntoATileReflowsFromTheFirstCollision() {
        val out = GridLayout.move(listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 2, 0), p(3, MEDIUM, 0, 2)), id = 3, target = Cell(2, 0), width = 4)
        assertEquals(mapOf(1L to Cell(0, 0), 3L to Cell(2, 0), 2L to Cell(0, 2)), cells(out))
    }

    @Test fun moveDownTheSameColumnSwapsWithTheTileBelow() {
        val out = GridLayout.move(listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 0, 2)), id = 1, target = Cell(0, 2), width = 4)
        assertEquals(mapOf(2L to Cell(0, 0), 1L to Cell(0, 2)), cells(out))
    }

    @Test fun droppingATileWhereItWasChangesNothing() {
        val start = listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 0, 2))
        assertEquals(start, GridLayout.move(start, id = 1, target = Cell(0, 0), width = 4))
    }

    @Test fun moveKeepsWideTilesInsideTheGridAndBelowNothing() {
        assertEquals(Cell(0, 0), cells(GridLayout.move(listOf(p(1, WIDE, 0, 0)), id = 1, target = Cell(3, 5), width = 4))[1])
    }

    @Test fun resizePushesCollidingTilesDown() {
        val out = GridLayout.resize(listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 2, 0), p(3, SMALL, 0, 2)), id = 1, newSize = WIDE, width = 4)
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(0, 2), 3L to Cell(2, 2)), cells(out))
        assertEquals(WIDE, out.single { it.id == 1L }.size)
    }

    @Test fun resizeLeavesTilesThatStillFitInPlace() {
        val out = GridLayout.resize(listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 4, 0)), id = 1, newSize = WIDE, width = 6)
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(4, 0)), cells(out))
    }

    @Test fun resizeAtTheRightEdgeShiftsTheTileLeft() {
        assertEquals(Cell(2, 0), cells(GridLayout.resize(listOf(p(1, SMALL, 3, 0)), id = 1, newSize = MEDIUM, width = 4))[1])
    }

    // The owner's case from the Galaxy check: floating up alone left tiles 3 and 6 stacked down the right edge.
    @Test fun shrinkingATileReflowsTheTilesAfterItIntoTheSpaceItFrees() {
        val start = listOf(p(1, LARGE, 0, 0), p(2, MEDIUM, 4, 0), p(3, MEDIUM, 4, 2), p(4, MEDIUM, 0, 4), p(5, MEDIUM, 2, 4), p(6, MEDIUM, 4, 4))
        val out = GridLayout.resize(start, id = 1, newSize = SMALL, width = 6)
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(1, 0), 3L to Cell(3, 0), 4L to Cell(0, 2), 5L to Cell(2, 2), 6L to Cell(4, 2)), cells(out))
    }

    @Test fun reconcileFlowsAWidthWithNoSavedArrangement() {
        val out = GridLayout.reconcile(mapOf(1L to MEDIUM, 2L to MEDIUM, 3L to SMALL), saved = null, fallbackOrder = listOf(2, 1, 3), width = 4)
        assertEquals(mapOf(2L to Cell(0, 0), 1L to Cell(2, 0), 3L to Cell(0, 2)), cells(out))
    }

    @Test fun reconcileRepairsABrokenSavedArrangement() { // Review Focus 2
        val saved = listOf(TilePlacement(1, 4, 0, 0), TilePlacement(9, 4, 2, 0), TilePlacement(2, 4, 0, 0))
        val out = GridLayout.reconcile(mapOf(1L to MEDIUM, 2L to MEDIUM, 3L to SMALL), saved, fallbackOrder = emptyList(), width = 4)
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(2, 0), 3L to Cell(0, 2)), cells(out))
    }

    @Test fun randomEditsAlwaysLeaveAValidArrangement() { // Review Focus 4
        val random = Random(7)
        for (width in listOf(4, 6, 8)) {
            var tiles = emptyList<Placed>()
            var nextId = 1L
            repeat(300) {
                tiles = when (if (tiles.isEmpty()) 0 else random.nextInt(4)) {
                    0 -> GridLayout.append(tiles, nextId++, TileSize.entries.random(random), width)
                    1 -> GridLayout.remove(tiles, tiles.random(random).id, width)
                    2 -> GridLayout.move(tiles, tiles.random(random).id, Cell(random.nextInt(width + 2), random.nextInt(20)), width)
                    else -> GridLayout.resize(tiles, tiles.random(random).id, TileSize.entries.random(random), width)
                }
                assertValid(tiles, width)
            }
        }
    }

    // Ids unique, reading order, in bounds, no two tiles share a cell, and no tile could move up a row.
    private fun assertValid(tiles: List<Placed>, width: Int) {
        assertEquals(tiles.size, tiles.map { it.id }.toSet().size)
        assertEquals(tiles.sortedWith(compareBy({ it.cell.y }, { it.cell.x })), tiles)
        val taken = mutableSetOf<Cell>()
        for (t in tiles) {
            assertTrue(t.cell.x >= 0 && t.cell.y >= 0 && t.cell.x + t.size.widthUnits <= width)
            for (dx in 0 until t.size.widthUnits) for (dy in 0 until t.size.heightUnits)
                assertTrue(taken.add(Cell(t.cell.x + dx, t.cell.y + dy)))
        }
        for (t in tiles.filter { it.cell.y > 0 })
            assertTrue((0 until t.size.widthUnits).any { Cell(t.cell.x + it, t.cell.y - 1) in taken })
    }
}
