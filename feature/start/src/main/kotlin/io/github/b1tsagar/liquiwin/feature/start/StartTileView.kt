package io.github.b1tsagar.liquiwin.feature.start

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.b1tsagar.liquiwin.core.data.apps.AppIcon
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.design.components.IconImage
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.components.rememberImage
import io.github.b1tsagar.liquiwin.core.design.components.scrollThumb
import io.github.b1tsagar.liquiwin.core.design.icons.LiquiWinIcons
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.source.ImageRef
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import io.github.b1tsagar.liquiwin.feature.start.live.STACK_SIZE
import io.github.b1tsagar.liquiwin.feature.start.live.TileFace
import kotlin.math.roundToInt

internal const val TILE_INSET_WP_PX = 12f // a tile's content inset at its start, top and end

/**
 * One tile as drawn, showing [face]. The front is the accent square with the app's icon, the label on medium and larger
 * tiles, and the [unread] count bottom right, or a pause mark there while live tiles are paused. A content face shows
 * its items above the label. A stack shows them in a list, kept in [stack], that scrolls while [scrollable].
 */
@Composable
internal fun StartTileView(
    tile: StartTile,
    metrics: GridMetrics,
    icon: suspend (LauncherApp) -> AppIcon?,
    modifier: Modifier = Modifier,
    face: TileFace = TileFace.Front,
    unread: Int = 0,
    liveTilesPaused: Boolean = false,
    stack: LazyListState = rememberLazyListState(),
    scrollable: Boolean = true,
) {
    val units = LocalWpUnits.current
    val size = metrics.sizeDp(tile.size)
    val app = tile.app
    val appIcon by produceState<AppIcon?>(null, app?.key, app?.versionToken) { value = app?.let { icon(it) } }
    // A wide tile shows an item's photo across the whole tile, with a one-line caption.
    val photo = (face as? TileFace.Item)?.item?.image?.takeIf { tile.size == TileSize.WIDE }
    Box(modifier.size(size).background(LocalLiquiWinColors.current.accent)) {
        if (face == TileFace.Front) IconImage(appIcon?.bitmap, min(size.width, size.height) * 0.5f, Modifier.align(Alignment.Center))
        if (photo != null) {
            val image = rememberImage(photo.file)
            if (image != null) {
                Image(image, contentDescription = null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.2f)))
            }
        }
        val inset = units.dp(TILE_INSET_WP_PX)
        Column(Modifier.fillMaxSize().padding(start = inset, top = inset, end = inset, bottom = units.dp(8f))) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (face) {
                    TileFace.Front -> Unit
                    is TileFace.Item -> when {
                        photo != null -> TileText(
                            listOfNotNull(face.item.title, face.item.text).joinToString(": "),
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                        tile.size == TileSize.WIDE -> ItemRow(face.item, avatarWpPx = 64f, lines = 3)
                        else -> Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(face.item.avatar, units.dp(32f))
                                TileText(face.item.title, maxLines = 1, sender = true)
                            }
                            face.item.text?.let { TileText(it, maxLines = 2) }
                        }
                    }
                    is TileFace.Stack -> StackList(face.items, stack, scrollable)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { if (tile.size != TileSize.SMALL) TileText(tile.label, maxLines = 1) }
                when (face) {
                    TileFace.Front -> if (liveTilesPaused) {
                        // No description: the slot's state description says it.
                        Image(LiquiWinIcons.Pause, contentDescription = null, Modifier.size(units.dp(16f)).testTag("live-paused"))
                    } else if (unread > 0) {
                        TileText(if (unread > 99) "99+" else unread.toString(), maxLines = 1)
                    }
                    is TileFace.Item -> ChatGlyph()
                    is TileFace.Stack -> Unit
                }
            }
        }
        if (tile.isWork) {
            Image(
                LiquiWinIcons.Briefcase,
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = units.dp(8f), end = units.dp(8f)).size((metrics.unitDp / 4).dp),
            )
        }
    }
}

// A large tile's items, newest first, three rows in view at once (§5.1). A swipe scrolls it before start, and its
// scrollbar sits in the tile's end margin.
@Composable
private fun StackList(items: List<TileItem>, state: LazyListState, scrollable: Boolean) {
    val units = LocalWpUnits.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Whole pixels, so three rows never overflow the list.
        val rowPx = constraints.maxHeight / STACK_SIZE
        val row = with(LocalDensity.current) { rowPx.toDp() }
        LazyColumn(Modifier.fillMaxSize(), state, userScrollEnabled = scrollable, overscrollEffect = null) {
            items(items, key = { it.key }) { item ->
                Box(Modifier.height(row).fillMaxWidth()) {
                    // The end padding keeps the text out from under the row's chat button.
                    ItemRow(item, avatarWpPx = 48f, lines = 2, Modifier.padding(end = units.dp(32f)))
                    ChatGlyph(Modifier.align(Alignment.BottomEnd))
                }
            }
        }
        StackThumb(state, items.size, rowPx, Modifier.align(Alignment.TopEnd).offset(x = units.dp(8f)))
    }
}

// Along the end margin while the list has more rows than show at once.
@Composable
private fun StackThumb(state: LazyListState, rows: Int, rowPx: Int, modifier: Modifier) {
    val units = LocalWpUnits.current
    val density = LocalDensity.current
    val thumb = scrollThumb(
        viewportPx = state.layoutInfo.viewportSize.height.toFloat(),
        contentPx = (rows * rowPx).toFloat(),
        fromTopPx = (state.firstVisibleItemIndex * rowPx + state.firstVisibleItemScrollOffset).toFloat(),
        minLengthPx = with(density) { units.dp(24f).toPx() },
    ) ?: return
    Box(
        modifier
            .offset { IntOffset(0, thumb.topPx.roundToInt()) }
            .size(units.dp(4f), with(density) { thumb.lengthPx.toDp() })
            .background(Color.White)
            .testTag("tile-scrollbar"),
    )
}

// No description: the slot's custom actions say it. The tap target around it is the slot's, 48 dp.
@Composable
private fun ChatGlyph(modifier: Modifier = Modifier) {
    Image(LiquiWinIcons.Chat, contentDescription = null, modifier.size(LocalWpUnits.current.dp(24f)).testTag("tile-chat"))
}

// The avatar on the left, with the sender and up to [lines] lines of text to its right.
@Composable
private fun ItemRow(item: TileItem, avatarWpPx: Float, lines: Int, modifier: Modifier = Modifier) {
    Row(modifier) {
        Avatar(item.avatar, LocalWpUnits.current.dp(avatarWpPx))
        Column {
            TileText(item.title, maxLines = 1, sender = true)
            item.text?.let { TileText(it, maxLines = lines) }
        }
    }
}

// Without an avatar it takes no room, so the text starts at the inset.
@Composable
private fun Avatar(ref: ImageRef?, size: Dp) {
    if (ref == null) return
    val image = rememberImage(ref.file)
    Box(Modifier.padding(end = LocalWpUnits.current.dp(8f)).size(size)) {
        if (image != null) Image(image, contentDescription = null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun TileText(text: String, maxLines: Int, modifier: Modifier = Modifier, sender: Boolean = false) {
    val style = LocalLiquiWinType.current.tileLabel
    WpText(text, if (sender) style.copy(fontWeight = FontWeight.SemiBold) else style, modifier, Color.White, maxLines)
}
