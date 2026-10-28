package io.github.b1tsagar.liquiwin.feature.start

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.b1tsagar.liquiwin.core.data.apps.AppIcon
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.design.components.IconImage
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.icons.LiquiWinIcons
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.source.ImageRef
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import io.github.b1tsagar.liquiwin.feature.start.live.STACK_SIZE
import io.github.b1tsagar.liquiwin.feature.start.live.TileFace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One tile as drawn, showing [face]. The front is the accent square with the app's icon, the label on medium and larger
 * tiles, and the [unread] count bottom right, or a pause mark there while live tiles are paused. A content face shows
 * its items above the label.
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
            val image = rememberImage(photo)
            if (image != null) {
                Image(image, contentDescription = null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.2f)))
            }
        }
        Column(Modifier.fillMaxSize().padding(start = units.dp(12f), top = units.dp(12f), end = units.dp(12f), bottom = units.dp(8f))) {
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
                    is TileFace.Stack -> Column(Modifier.fillMaxSize()) {
                        repeat(STACK_SIZE) { i ->
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                face.items.getOrNull(i)?.let {
                                    // The end padding keeps the text out from under the row's chat button.
                                    ItemRow(it, avatarWpPx = 48f, lines = 2, Modifier.padding(end = units.dp(24f)))
                                    ChatGlyph(Modifier.align(Alignment.BottomEnd))
                                }
                            }
                        }
                    }
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

// No description: the slot's custom actions say it. The tap target around it is the slot's, 48 dp.
@Composable
private fun ChatGlyph(modifier: Modifier = Modifier) {
    Image(LiquiWinIcons.Chat, contentDescription = null, modifier.size(LocalWpUnits.current.dp(16f)).testTag("tile-chat"))
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
    val image = rememberImage(ref)
    Box(Modifier.padding(end = LocalWpUnits.current.dp(8f)).size(size)) {
        if (image != null) Image(image, contentDescription = null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun TileText(text: String, maxLines: Int, modifier: Modifier = Modifier, sender: Boolean = false) {
    val style = LocalLiquiWinType.current.tileLabel
    WpText(text, if (sender) style.copy(fontWeight = FontWeight.SemiBold) else style, modifier, Color.White, maxLines)
}

// Null while it decodes, and for a file that's gone or won't decode. Keyed by the file, so a new item never shows the
// last one's image.
@Composable
private fun rememberImage(ref: ImageRef): ImageBitmap? {
    val image by key(ref.file) {
        produceState<ImageBitmap?>(null) { value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(ref.file.path) }?.asImageBitmap() }
    }
    return image
}
