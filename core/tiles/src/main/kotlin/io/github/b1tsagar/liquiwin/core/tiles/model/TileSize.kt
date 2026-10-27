package io.github.b1tsagar.liquiwin.core.tiles.model

enum class TileSize(val widthUnits: Int, val heightUnits: Int) {
    SMALL(1, 1),
    MEDIUM(2, 2),
    WIDE(4, 2),
    LARGE(4, 4);

    /** Small → medium → wide → large → small. */
    fun next(): TileSize = entries[(ordinal + 1) % entries.size]
}
