package io.github.b1tsagar.liquiwin.core.tiles.model

/** Where one tile sits in a grid [gridWidth] units wide. */
data class TilePlacement(val tileId: Long, val gridWidth: Int, val x: Int, val y: Int)
