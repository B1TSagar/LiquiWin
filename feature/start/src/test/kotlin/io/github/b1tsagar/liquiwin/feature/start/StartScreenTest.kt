package io.github.b1tsagar.liquiwin.feature.start

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.FakeTileSource
import io.github.b1tsagar.liquiwin.core.testing.FakeTileStore
import io.github.b1tsagar.liquiwin.core.testing.MainDispatcherRule
import io.github.b1tsagar.liquiwin.core.testing.testApp
import io.github.b1tsagar.liquiwin.core.testing.testCall
import io.github.b1tsagar.liquiwin.core.testing.testItem
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.model.TilePlacement
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.source.HiddenChat
import io.github.b1tsagar.liquiwin.core.tiles.source.ItemButton
import io.github.b1tsagar.liquiwin.core.tiles.source.SourceRegistry
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>() // Back: compose.activity.onBackPressedDispatcher
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val camera = testApp("Camera"); private val maps = testApp("Maps"); private val mail = testApp("Mail")
    private val store = FakeTileStore()
    private val catalog = FakeAppCatalog(apps = listOf(camera, maps, mail))
    private val cameraSource = FakeTileSource(SourceId.forApp(camera.key.packageName, 0))
    private val sources = SourceRegistry { if (it == cameraSource.id) cameraSource else null }
    private val chats = mutableListOf<TileItem>()

    // Pins the apps (medium tiles at (0,0), (2,0) and (4,0) by default), then shows the start screen.
    private fun show(pinned: List<LauncherApp> = listOf(camera, maps, mail), scroll: ScrollState? = null): StartViewModel {
        val viewModel = StartViewModel(store, catalog, gridWidth = 6, sources)
        pinned.forEach { viewModel.pin(it.key) }
        compose.setContent { LiquiWinTheme { StartScreen(viewModel, { null }, scroll ?: rememberScrollState(), onChat = { chats += it }) } }
        return viewModel
    }

    private fun longPressCamera() = compose.onNodeWithTag("tile-1").performTouchInput { longClick() }

    // Camera large, with [messages] unread messages from P(n-1), the newest, down to P0, then [more] medium tiles.
    private fun showLarge(messages: Int, more: Int = 0, scroll: ScrollState? = null) {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) // the list shows at once
        cameraSource.itemState.value = (messages - 1 downTo 0).map { testItem("m$it", title = "P$it", text = "t$it", at = it.toLong(), source = cameraSource.id) }
        val apps = listOf(camera) + (1..more).map { testApp("App $it") }
        catalog.apps.value = apps
        val viewModel = show(pinned = apps, scroll = scroll)
        repeat(2) { viewModel.cycleSize(1) } // medium, wide, large
        compose.waitForIdle()
    }

    private fun saysCamera(said: String) = compose.onNodeWithTag("tile-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, said))

    // The tiles turnstile away first (0.4 s for three columns and then the tapped tile), and only then does the app open.
    @Test fun tappingATileFlipsTheTilesAwayThenOpensItsApp() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tile-1").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertEquals(emptyList<AppKey>(), catalog.launched)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf(camera.key), catalog.launched)
    }

    @Test fun longPressShowsTheEditControls() {
        show()
        longPressCamera()
        for (control in listOf("unpin", "resize", "done")) compose.onNodeWithContentDescription(control).assertExists()
    }

    @Test fun resizeMakesTheTileWide() {
        show()
        longPressCamera()
        compose.onNodeWithContentDescription("resize").performClick()
        compose.waitForIdle()
        assertEquals(TileSize.WIDE, runBlocking { store.tiles.first() }.single { it.id == 1L }.size)
    }

    @Test fun unpinRemovesTheTile() {
        show()
        longPressCamera()
        compose.onNodeWithContentDescription("unpin").performClick()
        compose.onNodeWithContentDescription("Camera").assertDoesNotExist()
    }

    @Test fun draggingSwapsTilesAfterTheDwell() {
        show()
        val resources = compose.activity.resources
        val stepPx = GridMetrics(resources.configuration.screenWidthDp.toFloat(), 6, 24f, 12f).stepDp * resources.displayMetrics.density
        compose.onNodeWithTag("tile-1").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(2 * stepPx, 0f))
            advanceEventTime(200)
            up()
        }
        compose.waitForIdle()
        assertEquals(
            listOf(TilePlacement(2, 6, 0, 0), TilePlacement(1, 6, 2, 0), TilePlacement(3, 6, 4, 0)),
            runBlocking { store.placements(6).first() },
        )
    }

    // Spec §4.3 moves the lifted tile; a swipe anywhere else still scrolls the one vertical scroll (§4.2).
    @Test fun inEditModeASwipeOnAnotherTileScrollsInsteadOfMovingIt() {
        val apps = listOf(camera, maps, mail) + (1..15).map { testApp("App $it") }
        catalog.apps.value = apps
        val scroll = ScrollState(0)
        show(pinned = apps, scroll = scroll)
        longPressCamera()
        val before = runBlocking { store.placements(6).first() }
        compose.onNodeWithTag("tile-5").performTouchInput { swipe(center, center - Offset(0f, 140.dp.toPx())) }
        compose.waitForIdle()
        assertTrue(scroll.value > 0)
        assertEquals(before, runBlocking { store.placements(6).first() })
    }

    @Test fun backAndEmptySpaceLeaveEditMode() {
        show()
        longPressCamera()
        compose.onNodeWithContentDescription("done").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("done").assertDoesNotExist()

        longPressCamera()
        compose.onNodeWithContentDescription("done").assertExists()
        compose.onRoot().performTouchInput { click(Offset(centerX, height * 0.6f)) }
        compose.onNodeWithContentDescription("done").assertDoesNotExist()
    }

    @Test fun anEmptyStartScreenShowsTheHint() {
        show(pinned = emptyList())
        compose.onNodeWithText("swipe left for your apps. long-press one and choose pin to start.").assertExists()
    }

    @Test fun talkBackHearsTheUnreadCount() {
        cameraSource.itemState.value = List(3) { testItem("m$it", at = it.toLong(), source = cameraSource.id) }
        show()
        compose.onNodeWithTag("tile-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 unread"))
    }

    @Test fun tappingAContentFaceOpensTheItemAndTheLabelStripTheApp() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) // the item shows at once
        val item = testItem("a", source = cameraSource.id)
        cameraSource.itemState.value = listOf(item)
        show()
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(centerX, 10f)) }
        compose.waitUntil(5_000) { cameraSource.opened.isNotEmpty() }
        assertEquals(listOf<TileItem?>(item), cameraSource.opened)
        assertEquals(emptyList<AppKey>(), catalog.launched)
        compose.mainClock.advanceTimeBy(2_000) // LiquiWin stayed in front, so the tiles come back
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(10f, height - 5f)) } // the strip's end is the chat button
        compose.waitUntil(5_000) { catalog.launched.isNotEmpty() }
        assertEquals(listOf(camera.key), catalog.launched)
    }

    @OptIn(ExperimentalTestApi::class) // performCustomAccessibilityActionWithLabel
    @Test fun talkBackHearsTheItemAndCanOpenTheApp() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        cameraSource.itemState.value = listOf(testItem("a", source = cameraSource.id))
        show()
        compose.onNodeWithTag("tile-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 unread, Alex: see you at 6"))
        compose.onNodeWithTag("tile-1").performCustomAccessibilityActionWithLabel("open Camera")
        compose.waitUntil(5_000) { catalog.launched.isNotEmpty() }
        assertEquals(listOf(camera.key), catalog.launched)
    }

    // Review Focus 1: a guard, passing today, that must still pass
    @Test fun inEditModeASwipeDownTurnsNoTile() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        cameraSource.itemState.value = listOf(testItem("a", source = cameraSource.id))
        show()
        compose.onNodeWithTag("tile-2").performTouchInput { longClick() }
        compose.onNodeWithTag("tile-1").performTouchInput { swipeDown() }
        saysCamera("1 unread, Alex: see you at 6")
    }

    // §5.5: a chat button opens the item's chat, not the item
    @Test fun theChatButtonOpensTheItemsChat() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) // the item shows at once
        val a = testItem("a", source = cameraSource.id)
        cameraSource.itemState.value = listOf(a)
        show(pinned = listOf(camera))
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(width - 5f, height - 5f)) }
        assertEquals(listOf(a), chats)
        assertEquals(emptyList<TileItem?>(), cameraSource.opened)
    }

    // Passes before the change too: it pins edit mode's tap ahead of the chat corner.
    @Test fun inEditModeTheChatCornerSelectsTheTile() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        cameraSource.itemState.value = listOf(testItem("a", source = cameraSource.id))
        val viewModel = show(pinned = listOf(camera, maps))
        compose.onNodeWithTag("tile-2").performTouchInput { longClick() }
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(width - 5f, height - 5f)) }
        assertEquals(1L, viewModel.state.value.edit?.selectedId)
        assertEquals(emptyList<TileItem>(), chats)
    }

    @OptIn(ExperimentalTestApi::class) // performCustomAccessibilityActionWithLabel
    @Test fun talkBackCanOpenTheChat() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val a = testItem("a", source = cameraSource.id)
        cameraSource.itemState.value = listOf(a)
        show(pinned = listOf(camera))
        compose.onNodeWithTag("tile-1").performCustomAccessibilityActionWithLabel("chat with Alex")
        assertEquals(listOf(a), chats)
    }

    // The owner's call: a swipe on a large tile scrolls its list first, and start only once the list ends
    @Test fun aSwipeOnALargeTileScrollsItsListBeforeStart() {
        val scroll = ScrollState(0)
        showLarge(messages = 4, more = 15, scroll = scroll)
        compose.onNodeWithTag("tile-1").performTouchInput { swipe(center, center - Offset(0f, 140.dp.toPx())) }
        compose.waitForIdle()
        saysCamera("4 unread, P2: t2, P1: t1, P0: t0") // the list is at its end
        assertTrue(scroll.value > 0) // and start took the rest
    }

    // Passes before the change too: in edit mode the list keeps out of the way, so a swipe on the tile scrolls start (§4.3).
    @Test fun inEditModeASwipeOnALargeTileScrollsStartAndNotItsList() {
        val scroll = ScrollState(0)
        showLarge(messages = 4, more = 15, scroll = scroll)
        compose.onNodeWithTag("tile-2").performTouchInput { longClick() }
        compose.onNodeWithTag("tile-1").performTouchInput { swipe(center, center - Offset(0f, 140.dp.toPx())) }
        compose.waitForIdle()
        saysCamera("4 unread, P3: t3, P2: t2, P1: t1")
        assertTrue(scroll.value > 0)
    }

    @OptIn(ExperimentalTestApi::class) // performCustomAccessibilityActionWithLabel
    @Test fun aScrolledLargeTileOpensAndChatsWithTheRowUnderTheFinger() {
        showLarge(messages = 7)
        compose.onNodeWithTag("tile-1").performCustomAccessibilityActionWithLabel("older messages")
        compose.waitForIdle()
        // P3 is the top row in view now: its chat button at the row's end, and the rest of the row opens it.
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(width - 5f, 30f)) }
        assertEquals(listOf("m3"), chats.map { it.key })
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(10f, 30f)) }
        compose.waitUntil(5_000) { cameraSource.opened.isNotEmpty() }
        assertEquals(listOf("m3"), cameraSource.opened.map { it?.key })
    }

    // Camera's tile with [call] as its newest item, shown at once. On a medium tile, **message** sits at the corner.
    private fun showCall(call: TileItem): StartViewModel {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        cameraSource.itemState.value = listOf(call)
        return show()
    }

    @Test fun aMissedCallsButtonPressesIt() {
        val call = testCall(source = cameraSource.id)
        showCall(call)
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(width - 5f, height - 5f)) }
        compose.waitUntil(5_000) { cameraSource.pressed.isNotEmpty() } // once the tiles have turned away
        assertEquals(listOf(call to 1), cameraSource.pressed)
        assertEquals(emptyList<TileItem>(), chats)
    }

    @Test fun aMissedCallsReplyButtonOpensItsChat() {
        val call = testCall(buttons = listOf(ItemButton("Call back", false), ItemButton("Reply", true)), source = cameraSource.id)
        showCall(call)
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(width - 5f, height - 5f)) }
        assertEquals(listOf(call), chats)
        assertEquals(emptyList<Pair<TileItem, Int>>(), cameraSource.pressed)
    }

    // Review Focus 5
    @Test fun aLongPressOnAMissedCallsButtonLiftsTheTileAndPressesNothing() {
        val viewModel = showCall(testCall(source = cameraSource.id))
        compose.onNodeWithTag("tile-1").performTouchInput { longClick(Offset(width - 5f, height - 5f)) }
        assertEquals(1L, viewModel.state.value.edit?.selectedId)
        assertEquals(emptyList<Pair<TileItem, Int>>(), cameraSource.pressed)
    }

    // Review Focus 5
    @Test fun inEditModeAMissedCallsButtonSelectsTheTile() {
        val viewModel = showCall(testCall(source = cameraSource.id))
        compose.onNodeWithTag("tile-2").performTouchInput { longClick() }
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(width - 5f, height - 5f)) }
        assertEquals(1L, viewModel.state.value.edit?.selectedId)
        assertEquals(emptyList<Pair<TileItem, Int>>(), cameraSource.pressed)
    }

    @OptIn(ExperimentalTestApi::class) // performCustomAccessibilityActionWithLabel
    @Test fun talkBackCanPressAMissedCallsButtons() {
        val call = testCall(source = cameraSource.id)
        showCall(call)
        compose.onNodeWithTag("tile-1").performCustomAccessibilityActionWithLabel("Call back Alex")
        compose.waitUntil(5_000) { cameraSource.pressed.isNotEmpty() }
        assertEquals(listOf(call to 0), cameraSource.pressed)
    }

    // §4.3 and §5.8; Review Focus 5
    @Test fun aTileWithHiddenChatsListsThemAndShowsThemAgain() {
        cameraSource.hiddenState.value = listOf(HiddenChat(cameraSource.id, "family", "Family"))
        show(pinned = listOf(camera, maps))
        compose.onNodeWithTag("tile-2").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("hidden chats").assertDoesNotExist() // Maps has none
        compose.onNodeWithTag("tile-1").performClick()
        compose.onNodeWithContentDescription("hidden chats").performClick()
        compose.onNodeWithText("Family").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() } // the list goes, edit mode stays
        compose.onNodeWithText("Family").assertDoesNotExist()
        compose.onNodeWithContentDescription("hidden chats").performClick()
        compose.onNodeWithContentDescription("close").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Family").assertDoesNotExist()
        compose.onNodeWithContentDescription("hidden chats").performClick()
        compose.onNodeWithText("show again").performClick()
        assertEquals(emptyList<HiddenChat>(), cameraSource.hiddenState.value)
        compose.onNodeWithText("Family").assertDoesNotExist()
        compose.onNodeWithContentDescription("hidden chats").assertDoesNotExist()
    }

    // Final review: TalkBack hears the list open, as it would a dialog
    @Test fun talkBackHearsTheHiddenChatsListOpen() {
        cameraSource.hiddenState.value = listOf(HiddenChat(cameraSource.id, "family", "Family"))
        show(pinned = listOf(camera))
        compose.onNodeWithTag("tile-1").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("hidden chats").performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "hidden chats")).assertExists()
    }
}
