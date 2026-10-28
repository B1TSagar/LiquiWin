package io.github.b1tsagar.liquiwin.home

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.FakeTileSource
import io.github.b1tsagar.liquiwin.core.testing.FakeTileStore
import io.github.b1tsagar.liquiwin.core.testing.MainDispatcherRule
import io.github.b1tsagar.liquiwin.core.testing.appTile
import io.github.b1tsagar.liquiwin.core.testing.testApp
import io.github.b1tsagar.liquiwin.core.testing.testChat
import io.github.b1tsagar.liquiwin.core.testing.testMessage
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.source.SourceRegistry
import io.github.b1tsagar.liquiwin.feature.notifications.DisconnectLog
import io.github.b1tsagar.liquiwin.feature.notifications.LiveTilesHealth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeRootTest { // Review Focus 5
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val camera = testApp("Camera"); private val maps = testApp("Maps"); private val mail = testApp("Mail")
    private val store = FakeTileStore()
    private val presses = MutableSharedFlow<HomePress>(extraBufferCapacity = 1)
    private var setHomeCalls = 0
    private var fixCalls = 0
    private var appInfoCalls = 0
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("listener_health", Context.MODE_PRIVATE)
    private fun health(granted: Boolean, log: DisconnectLog = DisconnectLog(prefs)) =
        LiveTilesHealth({ granted }, MutableStateFlow(true), {}, log, { false }, CoroutineScope(Dispatchers.Main))

    private fun show(
        apps: List<LauncherApp> = listOf(camera, maps, mail),
        pinned: List<LauncherApp> = emptyList(),
        isDefaultHome: Boolean = true,
        liveTiles: LiveTilesHealth? = null,
        sources: SourceRegistry = SourceRegistry { null },
    ) {
        store.seed(tiles = pinned.mapIndexed { i, app -> appTile(i + 1L, app.key) }, placements = emptyMap())
        val deps = HomeDependencies(
            store, FakeAppCatalog(apps = apps), { isDefaultHome }, onSetHome = { setHomeCalls++ }, onOpenHomeSettings = {},
            sources = sources, liveTiles = liveTiles, onFixLiveTiles = { fixCalls++ }, onOpenAppInfo = { appInfoCalls++ },
        )
        compose.setContent { LiquiWinTheme { HomeRoot(deps, presses) } }
    }

    private fun swipeToAppList(row: String = "Maps") {
        compose.onRoot().performTouchInput { swipeLeft() }
        waitForAppList(row)
    }

    // The app list groups its apps off the main thread, and the list sits off screen until start has turnstiled away.
    // A tile has no text, so a text match is an app list row.
    private fun waitForAppList(row: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(row).fetchSemanticsNodes().isNotEmpty() && compose.onNodeWithText(row).isDisplayed()
    }

    private fun press(press: HomePress) {
        compose.runOnUiThread { presses.tryEmit(press) }
        compose.waitForIdle()
    }

    @Test fun homeWhileOnHomeReturnsToTheTopOfStart() {
        val apps = listOf(camera) + (1..29).map { testApp("App $it") }
        show(apps = apps, pinned = apps)
        compose.onRoot().performTouchInput { swipeUp() }
        compose.onNodeWithTag("tile-1").assertIsNotDisplayed()
        swipeToAppList(row = "App 1")
        press(HomePress.WHILE_ON_HOME)
        compose.onNodeWithTag("tile-1").assertIsDisplayed()
    }

    @Test fun homeFromAnAppClosesSearchEditModeAndMenus() {
        show(pinned = listOf(camera))
        swipeToAppList()
        compose.onNodeWithText("search apps").performClick()
        compose.onNode(hasSetTextAction()).assertExists()
        press(HomePress.FROM_APP)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithTag("tile-1").assertIsDisplayed()

        compose.onNodeWithTag("tile-1").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("done").assertExists()
        press(HomePress.FROM_APP)
        compose.onNodeWithContentDescription("done").assertDoesNotExist()

        swipeToAppList()
        compose.onNodeWithText("Maps").performTouchInput { longClick() }
        compose.onNodeWithText("pin to start").assertExists()
        press(HomePress.FROM_APP)
        swipeToAppList()
        compose.onNodeWithText("pin to start").assertDoesNotExist()
    }

    @Test fun backFromTheAppListReturnsToStart() {
        show()
        swipeToAppList()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("swipe left for your apps. long-press one and choose pin to start.").assertIsDisplayed()
    }

    @Test fun pinFromTheAppListAppearsOnStart() {
        show()
        swipeToAppList()
        compose.onNodeWithText("Maps").performTouchInput { longClick() }
        compose.onNodeWithText("pin to start").performClick()
        compose.onRoot().performTouchInput { swipeRight() }
        compose.onNode(hasContentDescription("Maps")).assertExists()
    }

    // Start turnstiles away in place once a swipe lets go; until then nothing moves.
    @Test fun aSwipeHeldPartWayLeavesStartWhereItIs() {
        show(pinned = listOf(camera))
        val before = compose.onNodeWithTag("tile-1").getBoundsInRoot().left
        compose.onRoot().performTouchInput { down(center); moveBy(Offset(-width / 3f, 0f)) }
        assertEquals(before, compose.onNodeWithTag("tile-1").getBoundsInRoot().left)
    }

    @Test fun aSwipeWhileRearrangingStaysOnStart() {
        show(pinned = listOf(camera))
        compose.onNodeWithTag("tile-1").performTouchInput { longClick() }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("tile-1").assertIsDisplayed()
        compose.onNodeWithContentDescription("done").assertExists()
    }

    // With no page to scroll, TalkBack reaches the app list through an action.
    @Test fun theAppListActionOpensTheList() {
        show()
        val actions = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNode()
        compose.runOnUiThread { actions.config[SemanticsActions.CustomActions].single { it.label == "app list" }.action() }
        waitForAppList("Maps")
    }

    @Test fun theBannerShowsOnlyWhenNotTheHomeApp() {
        show(isDefaultHome = false)
        compose.onNodeWithText("liquiwin isn't your home app yet").assertExists()
        compose.onNodeWithText("set").performClick()
        assertEquals(1, setHomeCalls)
    }

    // Review Focus 4
    @Test fun thePausedBannersFixOpensNotificationAccess() {
        show(liveTiles = health(granted = false))
        compose.onNodeWithText("live tiles paused · fix").performClick()
        assertEquals(1, fixCalls)
    }

    @Test fun theKeepStoppingBannerOpensTheGuide() {
        var clock = 0L
        val log = DisconnectLog(prefs) { clock }
        repeat(3) { log.record(); clock += 61_000 }
        show(liveTiles = health(granted = true, log))
        compose.onNodeWithText("live tiles keep stopping · fix").performClick()
        compose.onNodeWithText("keep tiles awake").assertExists()
        compose.onNodeWithText("open app info").performClick()
        assertEquals(1, appInfoCalls)
    }

    // Review Focus 5
    @Test fun theChatButtonOpensTheWindowAndHomeClosesIt() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) // the item shows at once
        val chats = FakeTileSource(SourceId.forApp(camera.key.packageName, 0))
        val a = testMessage("a", source = chats.id)
        chats.itemState.value = listOf(a.item)
        chats.chatState.value = testChat(a)
        show(pinned = listOf(camera), sources = SourceRegistry { if (it == chats.id) chats else null })
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(width - 5f, height - 5f)) }
        compose.onNodeWithText("see you at 6").assertIsDisplayed() // a tile has no text, so this is the window
        compose.onNodeWithTag("tile-1").assertDoesNotExist() // hidden from TalkBack under the window
        press(HomePress.FROM_APP)
        compose.onNodeWithText("see you at 6").assertDoesNotExist()
        compose.onNodeWithTag("tile-1").assertExists()
    }
}
