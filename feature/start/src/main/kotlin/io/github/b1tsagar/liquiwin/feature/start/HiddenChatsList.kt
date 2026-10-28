package io.github.b1tsagar.liquiwin.feature.start

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.style.LocalStyleFacets
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits
import io.github.b1tsagar.liquiwin.core.tiles.source.HiddenChat

/** A tile's hidden chats (§5.8), each with show again, at the bottom over a scrim that closes the list. */
@Composable
internal fun HiddenChatsList(chats: List<HiddenChat>, onShowAgain: (HiddenChat) -> Unit, onClose: () -> Unit) {
    val units = LocalWpUnits.current
    val type = LocalLiquiWinType.current
    val side = units.dp(LocalStyleFacets.current.sideMarginWpPx)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClose)
                .semantics { contentDescription = "close" },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(LocalLiquiWinColors.current.chrome)
                .pointerInput(Unit) { detectTapGestures {} } // taps on the panel reach neither the scrim nor edit mode
                .semantics { paneTitle = "hidden chats" } // TalkBack says it opened, as it would a dialog
                .navigationBarsPadding()
                .padding(horizontal = side, vertical = units.dp(12f)),
        ) {
            WpText("hidden chats", type.header)
            // Chats stay hidden until shown again, so the list can outgrow the screen.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                for (chat in chats) key(chat.sourceId, chat.conversationKey) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        WpText(chat.name, type.listItem, Modifier.weight(1f), maxLines = 1)
                        Box(
                            Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button) { onShowAgain(chat) },
                            contentAlignment = Alignment.Center,
                        ) {
                            WpText("show again", type.small)
                        }
                    }
                }
            }
        }
    }
}
