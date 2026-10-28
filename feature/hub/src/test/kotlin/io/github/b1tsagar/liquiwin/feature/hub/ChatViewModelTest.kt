package io.github.b1tsagar.liquiwin.feature.hub

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.testing.FakeTileSource
import io.github.b1tsagar.liquiwin.core.testing.MainDispatcherRule
import io.github.b1tsagar.liquiwin.core.testing.testChat
import io.github.b1tsagar.liquiwin.core.testing.testItem
import io.github.b1tsagar.liquiwin.core.testing.testMessage
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.source.ChatMessage
import io.github.b1tsagar.liquiwin.core.tiles.source.HiddenChat
import io.github.b1tsagar.liquiwin.core.tiles.source.ReplyResult
import io.github.b1tsagar.liquiwin.core.tiles.source.SourceRegistry
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class) // TextFieldState comes from an Android library
class ChatViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val source = FakeTileSource(SourceId("app:com.example.chat@0"))
    private val vm = ChatViewModel(SourceRegistry { if (it == source.id) source else null }, now = { Instant.ofEpochMilli(5_000) })
    private val a = testMessage("a", at = 1)
    private val b = testMessage("b", "running late", at = 2)
    private val window get() = vm.window.value!!

    private fun open(vararg messages: ChatMessage) {
        source.chatState.value = testChat(*messages)
        vm.show(messages.first().item)
    }

    private fun send(text: String) {
        vm.draft.setTextAndPlaceCursorAtEnd(text)
        vm.send()
    }

    @Test fun openingShowsTheChatAndReadsItUpToItsNewestMessage() {
        open(a, b)
        assertEquals(testChat(a, b), window.chat)
        assertEquals(listOf(b.item), source.read)
    }

    // Review Focus 3
    @Test fun messagesArrivingWhileOpenAreReadToo() {
        open(a, b)
        val c = testMessage("c", at = 3)
        source.chatState.value = testChat(a, b, c)
        source.chatState.value = testChat(a, b, c.copy(item = c.item.copy(canReply = false))) // the same newest: no second read
        assertEquals(listOf(b.item, c.item), source.read)
    }

    // Review Focus 3
    @Test fun aChatWhoseMessagesAreGoneCloses() {
        open(a)
        source.chatState.value = null
        assertNull(vm.window.value)
    }

    @Test fun anItemWithoutAChatOpensNothing() {
        vm.show(testItem("x").copy(conversation = null))
        vm.show(testItem("y", source = SourceId("app:gone@0")))
        assertNull(vm.window.value)
    }

    // Review Focus 2: after a launcher restart, replies come back with the app's next post
    @Test fun theReplyBoxShowsOnceTheChatTakesRepliesAndStays() {
        val quiet = testMessage("a", canReply = false)
        open(quiet)
        assertFalse(window.showsReplyBox)
        source.chatState.value = testChat(testMessage("a"))
        assertTrue(window.showsReplyBox)
        source.chatState.value = testChat(quiet)
        assertTrue(window.showsReplyBox)
    }

    @Test fun sendingRepliesToTheNewestMessageThatTakesRepliesAndClearsTheBox() {
        open(a, testMessage("b", at = 2, canReply = false))
        send("on my way")
        assertEquals(listOf(a.item to "on my way"), source.replies)
        assertEquals("", vm.draft.text.toString())
        val pending = window.pending.single()
        assertEquals("on my way", pending.text)
        assertEquals(Instant.ofEpochMilli(5_000), pending.sentAt)
        assertTrue(pending.sending)
    }

    // Review Focus 1
    @Test fun yourMessageComingBackReplacesThePendingOneWhateverItsText() {
        open(a)
        send("on my way")
        source.chatState.value = testChat(a, testMessage("me", "On my way!", at = 3, self = true))
        assertEquals(emptyList<PendingReply>(), window.pending)
    }

    @Test fun yourMessagesAlreadyThereDontReplaceIt() {
        val earlier = testMessage("me1", "earlier", at = 0, self = true)
        open(earlier, a)
        send("on my way")
        source.chatState.value = testChat(earlier, a, testMessage("c", at = 3))
        assertEquals(listOf("on my way"), window.pending.map { it.text })
    }

    // Review Focus 1
    @OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy
    @Test fun aReplyThatNeverComesBackStopsSayingSendingAfter10Seconds() = runTest {
        open(a)
        send("on my way")
        advanceTimeBy(9_999)
        assertTrue(window.pending.single().sending)
        advanceTimeBy(2)
        assertFalse(window.pending.single().sending)
    }

    // Review Focus 2
    @Test fun sendingAfterTheActionWentKeepsTheTextAndSaysWhy() {
        open(a)
        source.chatState.value = testChat(testMessage("a", at = 1, canReply = false)) // the app removed its notification
        send("on my way")
        assertTrue(window.failed)
        assertEquals("on my way", vm.draft.text.toString())
        assertEquals(emptyList<Pair<TileItem, String>>(), source.replies)
    }

    // Review Focus 2
    @Test fun anActionThatFailsKeepsTheTextAndSaysWhy() {
        source.replyResult = ReplyResult.ActionGone
        open(a)
        send("on my way")
        assertTrue(window.failed)
        assertEquals("on my way", vm.draft.text.toString())
        assertEquals(emptyList<PendingReply>(), window.pending)
        source.replyResult = ReplyResult.Sent
        vm.send()
        assertFalse(window.failed) // the next try clears it
    }

    // Review Focus 2
    @Test fun aSecondTapWhileSendingSendsOnce() {
        val gate = CompletableDeferred<Unit>()
        source.replyGate = gate
        open(a)
        send("on my way")
        vm.send()
        assertTrue(window.sending)
        gate.complete(Unit)
        assertEquals(1, source.replies.size)
        assertEquals(1, window.pending.size)
        assertFalse(window.sending)
    }

    @Test fun aBlankDraftSendsNothing() {
        open(a)
        send("  \n ")
        assertEquals(emptyList<Pair<TileItem, String>>(), source.replies)
        assertFalse(window.failed)
    }

    @Test fun openInAppOpensTheNewestMessageAndCloses() {
        open(a, b)
        vm.openInApp()
        assertEquals(listOf<TileItem?>(b.item), source.opened)
        assertNull(vm.window.value)
    }

    @Test fun hidingHidesTheChatUnderItsTitle() {
        open(a)
        vm.hide()
        assertEquals(listOf(HiddenChat(source.id, "alex", "Alex")), source.hiddenState.value)
    }
}
