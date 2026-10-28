package io.github.b1tsagar.liquiwin.feature.start.live

import kotlin.random.Random

/**
 * The live tiles' flip timer (spec §5.4). Each tick moves one tile, picked at random, and a tile that moved sits out
 * the next [REST_TICKS] ticks, whether a tick or a new item moved it.
 */
class TileTicker(private val random: Random) {
    private var ticks = 0
    private val movedOn = mutableMapOf<Long, Int>()
    private val jumpedAt = mutableMapOf<Long, Long>()

    fun nextGapMs(): Long = random.nextInt(1, 31) * 100L

    /** Counts a tick and picks the tile it moves, or null when every candidate is resting. */
    fun tick(candidates: Collection<Long>): Long? {
        ticks++
        val moved = candidates.filter { id -> movedOn[id].let { it == null || ticks - it > REST_TICKS } }.randomOrNull(random)
        if (moved != null) movedOn[moved] = ticks
        return moved
    }

    /** A new item moved [tileId], which counts as a move on the latest tick. */
    fun jumped(tileId: Long, nowMs: Long) {
        movedOn[tileId] = ticks
        jumpedAt[tileId] = nowMs
    }

    /** How long [tileId] waits before its next jump. */
    fun jumpDelayMs(tileId: Long, nowMs: Long): Long =
        jumpedAt[tileId]?.let { (it + JUMP_DEBOUNCE_MS - nowMs).coerceAtLeast(0L) } ?: 0L

    companion object {
        const val FIRST_TICK_MS = 2_500L
        const val REST_TICKS = 3
        const val JUMP_DEBOUNCE_MS = 600L
    }
}
