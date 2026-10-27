package io.github.b1tsagar.liquiwin.core.tiles.model

import io.github.b1tsagar.liquiwin.core.data.apps.AppKey

data class Tile(
    val id: Long,
    val kind: TileKind,
    val title: String?,
    val size: TileSize,
    val template: TileTemplate,
    val colorOverride: Int?,
    val appKey: AppKey?,
    val sources: List<SourceId>,
)
