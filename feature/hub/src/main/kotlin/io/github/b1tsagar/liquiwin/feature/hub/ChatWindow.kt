package io.github.b1tsagar.liquiwin.feature.hub

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.b1tsagar.liquiwin.core.data.apps.AppCatalog
import io.github.b1tsagar.liquiwin.core.data.apps.AppIcon
import io.github.b1tsagar.liquiwin.core.design.components.IconImage
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.components.rememberImage
import io.github.b1tsagar.liquiwin.core.design.components.scrollThumb
import io.github.b1tsagar.liquiwin.core.design.motion.Swivel
import io.github.b1tsagar.liquiwin.core.design.motion.swivelAngle
import io.github.b1tsagar.liquiwin.core.design.style.LocalStyleFacets
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits
import io.github.b1tsagar.liquiwin.core.tiles.source.ImageRef
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The chat window (§5.7) over whatever is beneath it. Draws nothing while closed. */
@Composable
fun ChatWindow(viewModel: ChatViewModel, catalog: AppCatalog, modifier: Modifier = Modifier) {
    val window by viewModel.window.collectAsStateWithLifecycle()
    val shown = window ?: return
    val units = LocalWpUnits.current
    val colors = LocalLiquiWinColors.current
    val type = LocalLiquiWinType.current
    val side = units.dp(LocalStyleFacets.current.sideMarginWpPx)
    val apps by catalog.apps.collectAsStateWithLifecycle()
    val app = shown.sourceId.appPackageAndSerial()?.let { (packageName, serial) ->
        apps.firstOrNull { it.key.packageName == packageName && it.key.userSerial == serial }
    }
    val appIcon by produceState<AppIcon?>(null, app?.key, app?.versionToken) { value = app?.let { catalog.icon(it) } }

    // It swivels in on opening. Back, a tap outside and hiding the chat swivel it out, then close it.
    val scope = rememberCoroutineScope()
    val clock = remember { Animatable(0f) }
    var motion by remember { mutableStateOf(Swivel.IN) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { clock.animateTo(Swivel.IN.durationMs.toFloat(), tween(Swivel.IN.durationMs, easing = LinearEasing)) }
    fun leave() {
        if (leaving) return
        leaving = true
        scope.launch {
            clock.snapTo(0f)
            motion = Swivel.OUT
            clock.animateTo(Swivel.OUT.durationMs.toFloat(), tween(Swivel.OUT.durationMs, easing = LinearEasing))
            viewModel.close()
        }
    }
    BackHandler { leave() }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = null, indication = null, role = Role.Button) { leave() }
                .semantics { contentDescription = "close" },
        )
        // Measures the height left above the keyboard.
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationX = swivelAngle(motion, clock.value)
                        cameraDistance = 12f * density
                    }
                    .background(colors.chrome)
                    .pointerInput(Unit) {} // taps on the panel stay off the scrim beneath
                    .semantics { paneTitle = "chat with ${shown.chat.title}" } // TalkBack says it opened, as it would a dialog
                    .navigationBarsPadding()
                    .heightIn(max = maxHeight * 0.8f),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = side, end = side, top = units.dp(12f)), verticalAlignment = Alignment.CenterVertically) {
                    shown.chat.avatar?.let { Avatar(it) }
                    WpText(shown.chat.title, type.listItem, Modifier.weight(1f), maxLines = 1)
                    // The app's icon opens the chat in its app, as "open in app" does.
                    Box(
                        Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(role = Role.Button, onClick = viewModel::openInApp)
                            .semantics { contentDescription = "open in ${app?.label ?: "app"}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        IconImage(appIcon?.bitmap, units.dp(24f))
                    }
                }
                Row(Modifier.padding(horizontal = side), horizontalArrangement = Arrangement.spacedBy(units.dp(24f))) {
                    TextButton("open in app", onClick = viewModel::openInApp)
                    if (shown.chat.canHide) {
                        TextButton("hide this chat") {
                            viewModel.hide()
                            leave()
                        }
                    }
                }
                Bubbles(shown, app?.label ?: "app", side, Modifier.weight(1f, fill = false), onPhoto = viewModel::openInApp)
                // A missed call's buttons, but for its reply button, which the text box is (§5.7).
                val calls = shown.chat.messages.lastOrNull()?.item?.buttons.orEmpty().withIndex().filterNot { it.value.isReply }
                if (calls.isNotEmpty()) {
                    Row(Modifier.padding(horizontal = side), horizontalArrangement = Arrangement.spacedBy(units.dp(24f))) {
                        for ((i, button) in calls) TextButton(button.label.lowercase(Locale.getDefault())) { viewModel.press(i) }
                    }
                }
                if (shown.failed) {
                    Row(Modifier.padding(horizontal = side), verticalAlignment = Alignment.CenterVertically) {
                        WpText("Couldn't send. Open ${app?.label ?: "the app"} to reply.", type.small, Modifier.weight(1f, fill = false))
                        TextButton("open", onClick = viewModel::openInApp)
                    }
                }
                if (shown.showsReplyBox) ReplyRow(viewModel, shown.sending, side)
            }
        }
    }
}

// Oldest at the top, then the replies on their way. It opens at the newest and stays there as messages arrive.
@Composable
private fun Bubbles(window: ChatWindowState, app: String, side: Dp, modifier: Modifier, onPhoto: (TileItem) -> Unit) {
    val units = LocalWpUnits.current
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    val now = Instant.now()
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll, reverseScrolling = true).padding(horizontal = side, vertical = units.dp(12f)),
            verticalArrangement = Arrangement.spacedBy(units.dp(12f)),
        ) {
            for (message in window.chat.messages) key(message.item.key) {
                Bubble(
                    message.item.text, message.item.image, mine = message.isSelf,
                    sender = message.sender.takeIf { window.chat.isGroup && !message.isSelf },
                    time = timeLabel(message.item.time, now, format),
                    photoCard = "photo · open in $app".takeIf { message.item.photoUnreadable },
                    onPhoto = { onPhoto(message.item) },
                )
            }
            for (reply in window.pending) key(reply.id) {
                Bubble(reply.text, image = null, mine = true, sender = null, time = if (reply.sending) "sending…" else timeLabel(reply.sentAt, now, format))
            }
        }
        Thumb(scroll, Modifier.align(Alignment.TopEnd))
    }
}

// Incoming bubbles sit left in the accent colour with a top-left tail; yours sit right in a darker shade with a
// bottom-right tail. TalkBack reads each, with its sender and time, as one item. A photo LiquiWin couldn't read is a
// [photoCard] in its place, a button of its own, as a moving photo is.
@Composable
private fun Bubble(text: String?, image: ImageRef?, mine: Boolean, sender: String?, time: String, photoCard: String? = null, onPhoto: () -> Unit = {}) {
    val units = LocalWpUnits.current
    val colors = LocalLiquiWinColors.current
    val type = LocalLiquiWinType.current
    val fill = if (mine) lerp(colors.accent, Color.Black, 0.3f) else colors.accent
    val tail = Modifier.size(units.dp(12f)).background(fill, if (mine) BottomRightTail else TopLeftTail)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = if (mine) units.dp(72f) else 0.dp, end = if (mine) 0.dp else units.dp(72f))
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (sender != null) WpText(sender, type.small, color = colors.subtle)
        if (!mine) Box(tail)
        Column(Modifier.background(fill)) {
            image?.let { Photo(it.file) }
            photoCard?.let {
                Box(
                    Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onPhoto).padding(horizontal = units.dp(12f)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    WpText(it, type.body)
                }
            }
            text?.let { WpText(it, type.body, Modifier.padding(horizontal = units.dp(12f), vertical = units.dp(8f))) }
        }
        if (mine) Box(tail)
        WpText(time, type.small, color = colors.subtle)
    }
}

// Right-angled triangles with the right angle on the bubble's corner.
private val TopLeftTail = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(0f, size.height)
    lineTo(size.width, size.height)
    close()
}
private val BottomRightTail = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    close()
}

// Along the right edge whenever the bubbles don't all fit; reverse scrolling counts from the bottom.
@Composable
private fun Thumb(scroll: ScrollState, modifier: Modifier) {
    val units = LocalWpUnits.current
    val density = LocalDensity.current
    val thumb = scrollThumb(
        viewportPx = scroll.viewportSize.toFloat(),
        contentPx = scroll.viewportSize.toFloat() + scroll.maxValue,
        fromTopPx = (scroll.maxValue - scroll.value).toFloat(),
        minLengthPx = with(density) { units.dp(24f).toPx() },
    ) ?: return
    Box(
        modifier
            .offset { IntOffset(0, thumb.topPx.roundToInt()) }
            .size(units.dp(4f), with(density) { thumb.lengthPx.toDp() })
            .background(LocalLiquiWinColors.current.subtle)
            .testTag("chat-scrollbar"),
    )
}

// The text box takes focus with the keyboard up as soon as it shows. Enter adds a line; only send sends.
@Composable
private fun ReplyRow(viewModel: ChatViewModel, sending: Boolean, side: Dp) {
    val units = LocalWpUnits.current
    val colors = LocalLiquiWinColors.current
    val type = LocalLiquiWinType.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Row(Modifier.fillMaxWidth().padding(start = side, end = side, bottom = units.dp(12f)), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            viewModel.draft,
            Modifier.weight(1f).testTag("chat-input").focusRequester(focus),
            textStyle = type.body.copy(color = colors.foreground),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
            decorator = { field ->
                Box(Modifier.background(colors.background).padding(horizontal = units.dp(12f), vertical = units.dp(10f))) {
                    if (viewModel.draft.text.isEmpty()) WpText("type a message", type.body, color = colors.subtle)
                    field()
                }
            },
        )
        TextButton("send", Modifier.padding(start = units.dp(12f)), enabled = !sending && viewModel.draft.text.isNotBlank(), onClick = viewModel::send)
    }
}

@Composable
private fun Avatar(ref: ImageRef) {
    val units = LocalWpUnits.current
    val image = rememberImage(ref.file)
    Box(Modifier.padding(end = units.dp(12f)).size(units.dp(40f))) {
        if (image != null) Image(image, contentDescription = null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

// A label in small, in a touch target at least 48 dp each way.
@Composable
private fun TextButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalLiquiWinColors.current
    Box(
        modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        WpText(label, LocalLiquiWinType.current.small, color = if (enabled) colors.foreground else colors.subtle)
    }
}
