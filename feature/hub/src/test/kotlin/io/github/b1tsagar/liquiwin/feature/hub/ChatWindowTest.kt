package io.github.b1tsagar.liquiwin.feature.hub

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.FakeTileSource
import io.github.b1tsagar.liquiwin.core.testing.testApp
import io.github.b1tsagar.liquiwin.core.testing.testChat
import io.github.b1tsagar.liquiwin.core.testing.testMessage
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.source.Chat
import io.github.b1tsagar.liquiwin.core.tiles.source.HiddenChat
import io.github.b1tsagar.liquiwin.core.tiles.source.ReplyResult
import io.github.b1tsagar.liquiwin.core.tiles.source.SourceRegistry
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ChatWindowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val chatApp = testApp("Chat") // com.example.chat, testMessage's default source
    private val source = FakeTileSource(SourceId.forApp(chatApp.key.packageName, 0))
    private val vm = ChatViewModel(SourceRegistry { if (it == source.id) source else null })
    private val a = testMessage("a")

    private fun open(chat: Chat) {
        source.chatState.value = chat
        compose.setContent { LiquiWinTheme { ChatWindow(vm, FakeAppCatalog(apps = listOf(chatApp))) } }
        compose.runOnUiThread { vm.show(chat.messages.first().item) }
        compose.waitForIdle()
    }

    private fun type(text: String) = compose.onNodeWithTag("chat-input").performTextInput(text)

    @Test fun aChatThatTakesRepliesOpensWithTheBoxFocused() {
        open(testChat(a))
        compose.onNodeWithText("Alex").assertIsDisplayed() // the header, and no name over a one-to-one bubble
        compose.onNodeWithText("see you at 6").assertIsDisplayed()
        compose.onNodeWithTag("chat-input").assertIsFocused() // the keyboard itself is checked on the Galaxy
        compose.onNodeWithText("send").assertIsNotEnabled()
    }

    @Test fun aChatThatTakesNoRepliesHasNoBox() {
        val quiet = testMessage("a", canReply = false)
        open(testChat(quiet))
        compose.onNodeWithTag("chat-input").assertDoesNotExist()
        compose.onNodeWithText("open in app").performClick()
        assertEquals(listOf<TileItem?>(quiet.item), source.opened)
        assertNull(vm.window.value)
    }

    // The owner's call after using it: the app's icon opens the chat in its app too, as "open in app" does
    @Test fun theAppsIconOpensTheChatInItsApp() {
        val b = testMessage("b", at = 1)
        open(testChat(a, b))
        compose.onNodeWithContentDescription("open in Chat").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf<TileItem?>(b.item), source.opened) // the newest message's link
        assertNull(vm.window.value)
    }

    // Review Focus 1
    @Test fun sendingShowsYourBubbleUntilTheAppsCopyArrives() {
        open(testChat(a))
        type("on my way")
        compose.onNodeWithText("send").performClick()
        assertEquals(listOf(a.item to "on my way"), source.replies)
        compose.onNodeWithText("sending…").assertIsDisplayed()
        compose.runOnUiThread { source.chatState.value = testChat(a, testMessage("me", "on my way", at = 1, self = true)) }
        compose.onNodeWithText("sending…").assertDoesNotExist()
        compose.onAllNodesWithText("on my way").assertCountEquals(1)
    }

    // Review Focus 2
    @Test fun aFailedSendKeepsTheTextAndOffersTheApp() {
        source.replyResult = ReplyResult.ActionGone
        open(testChat(a))
        type("on my way")
        compose.onNodeWithText("send").performClick()
        compose.onNodeWithText("Couldn't send. Open Chat to reply.").assertIsDisplayed()
        compose.onNodeWithTag("chat-input").assertTextEquals("on my way")
        compose.onNodeWithText("open").performClick()
        assertEquals(listOf<TileItem?>(a.item), source.opened)
        assertNull(vm.window.value)
    }

    @Test fun aLongChatOpensAtTheNewestWithAScrollbar() {
        open(testChat(*Array(40) { testMessage("m$it", "message ${it + 1}", at = it.toLong()) }))
        compose.onNodeWithText("message 40").assertIsDisplayed()
        compose.onNodeWithText("message 1").assertIsNotDisplayed()
        compose.onNodeWithTag("chat-scrollbar").assertIsDisplayed()
    }

    @Test fun aShortChatHasNoScrollbar() {
        open(testChat(a))
        compose.onNodeWithTag("chat-scrollbar").assertDoesNotExist()
    }

    // Robolectric draws every line of text 35 px tall, so three bubbles with names need more than its 470 px screen.
    @Config(qualifiers = "h800dp")
    @Test fun aGroupNamesTheSenderAboveEachIncomingBubble() {
        open(
            testChat(
                testMessage("a", sender = "Mum"), testMessage("b", at = 1, sender = "Dad"), testMessage("me", at = 2, self = true),
                title = "Family", isGroup = true,
            ),
        )
        compose.onNodeWithText("Family").assertIsDisplayed()
        compose.onNodeWithText("Mum").assertIsDisplayed()
        compose.onNodeWithText("Dad").assertIsDisplayed()
        compose.onNodeWithText("You").assertDoesNotExist()
    }

    @Test fun hidingTheChatHidesItAndCloses() {
        open(testChat(a))
        compose.onNodeWithText("hide this chat").performClick()
        compose.waitForIdle()
        assertEquals(listOf(HiddenChat(source.id, "alex", "Alex")), source.hiddenState.value)
        assertNull(vm.window.value)
    }

    // Final review: a plain notification's slot can hold an app's unrelated posts, so it isn't hidden as a chat
    @Test fun aPlainNotificationHasNoHideButton() {
        open(testChat(a, canHide = false))
        compose.onNodeWithText("hide this chat").assertDoesNotExist()
        compose.onNodeWithText("open in app").assertIsDisplayed()
    }

    @Test fun backAndATapOutsideClose() {
        open(testChat(a))
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertNull(vm.window.value)
        compose.runOnUiThread { vm.show(a.item) }
        compose.onRoot().performTouchInput { click(Offset(centerX, 10f)) }
        compose.waitForIdle()
        assertNull(vm.window.value)
    }

    // Review Focus 5
    @Test fun talkBackCanCloseItFromOutside() {
        open(testChat(a))
        compose.onNodeWithContentDescription("close").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertNull(vm.window.value)
    }

    // Final review: TalkBack hears the window open, as it would a dialog
    @Test fun talkBackHearsTheWindowOpen() {
        open(testChat(a))
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "chat with Alex")).assertExists()
    }
}
