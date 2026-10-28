package io.github.b1tsagar.liquiwin.feature.start

import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.data.apps.AppProfile
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.FakeTileSource
import io.github.b1tsagar.liquiwin.core.testing.FakeTileStore
import io.github.b1tsagar.liquiwin.core.testing.MainDispatcherRule
import io.github.b1tsagar.liquiwin.core.testing.appTile
import io.github.b1tsagar.liquiwin.core.testing.testApp
import io.github.b1tsagar.liquiwin.core.testing.testItem
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.model.TilePlacement
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.source.HiddenChat
import io.github.b1tsagar.liquiwin.core.tiles.source.SourceRegistry
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import io.github.b1tsagar.liquiwin.feature.start.layout.Cell
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StartViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val camera = testApp("Camera"); private val maps = testApp("Maps"); private val mail = testApp("Mail")
    private val store = FakeTileStore()
    private val catalog = FakeAppCatalog(apps = listOf(camera, maps, mail))
    private fun viewModel() = StartViewModel(store, catalog, gridWidth = 6)

    @Test fun pinnedAppsAppendAsMediumTiles() = runTest {
        val vm = viewModel(); vm.pin(camera.key); vm.pin(maps.key)
        val tiles = vm.state.first { it.tiles.size == 2 }.tiles
        assertEquals(listOf(Cell(0, 0), Cell(2, 0)), tiles.map { it.cell })
        assertTrue(tiles.all { it.size == TileSize.MEDIUM })
        assertEquals(setOf(6), store.savedWidths())
    }

    @Test fun pinningAPinnedAppDoesNothing() = runTest {
        val vm = viewModel(); vm.pin(camera.key); vm.pin(camera.key)
        assertEquals(1, vm.state.first { it.loaded && it.tiles.isNotEmpty() }.tiles.size)
    }

    @Test fun unpinCompactsEverySavedWidth() = runTest {
        store.seed(
            tiles = listOf(appTile(1, camera.key), appTile(2, maps.key)),
            placements = mapOf(4 to listOf(TilePlacement(1, 4, 0, 0), TilePlacement(2, 4, 0, 2)),
                               6 to listOf(TilePlacement(1, 6, 0, 0), TilePlacement(2, 6, 0, 2))),
        )
        viewModel().unpin(1)
        assertEquals(listOf(TilePlacement(2, 4, 0, 0)), store.placements(4).first())
        assertEquals(listOf(TilePlacement(2, 6, 0, 0)), store.placements(6).first())
    }

    @Test fun cycleSizeGoesThroughEverySize() = runTest {
        val vm = viewModel(); vm.pin(camera.key)
        val sizes = (1..4).map { vm.cycleSize(1); store.tiles.first().single().size }
        assertEquals(listOf(TileSize.WIDE, TileSize.LARGE, TileSize.SMALL, TileSize.MEDIUM), sizes)
    }

    @Test fun previewMoveDoesNotWriteButEndDragDoes() = runTest {
        val vm = viewModel(); vm.pin(camera.key); vm.pin(maps.key); vm.enterEdit(1)
        val before = store.commitCount
        vm.previewMove(1, Cell(2, 0))
        assertEquals(before, store.commitCount)
        assertEquals(Cell(0, 0), vm.state.value.edit!!.preview!![2L])
        vm.endDrag(1, Cell(2, 0))
        assertEquals(before + 1, store.commitCount)
        assertEquals(listOf(TilePlacement(2, 6, 0, 0), TilePlacement(1, 6, 2, 0)), store.placements(6).first())
    }

    @Test fun aTileThatWasNeverPlacedIsAppendedOnLoad() = runTest { // Review Focus 2
        store.seed(tiles = listOf(appTile(1, camera.key), appTile(2, maps.key)), placements = mapOf(6 to listOf(TilePlacement(1, 6, 0, 0))))
        val tiles = viewModel().state.first { it.loaded }.tiles
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(2, 0)), tiles.associate { it.id to it.cell })
        assertEquals(2, store.placements(6).first().size)
    }

    @Test fun repairsWaitForAFreshAppList() = runTest { // Review Focus 1
        catalog.isFresh.value = false
        catalog.apps.value = listOf(maps)
        store.seed(tiles = listOf(appTile(1, camera.key)), placements = emptyMap())
        val vm = viewModel()
        assertEquals(1, vm.state.first { it.loaded }.tiles.size)
        catalog.isFresh.value = true
        vm.state.first { it.tiles.isEmpty() }
    }

    @Test fun openingAPausedWorkAppRequestsUnpause() = runTest {
        val slack = testApp("Slack", userSerial = 10, isPaused = true)
        catalog.apps.value = listOf(camera, slack)
        catalog.profiles.value = listOf(AppProfile(0, false, false), AppProfile(10, true, true))
        val vm = viewModel(); vm.pin(slack.key)
        vm.open(1, null, null)
        assertEquals(listOf(10L), catalog.unpauseRequests)
        assertEquals(emptyList<AppKey>(), catalog.launched)
    }

    @Test fun openingInEditModeSelectsInsteadOfLaunching() = runTest {
        val vm = viewModel(); vm.pin(camera.key); vm.pin(maps.key); vm.enterEdit(1)
        vm.open(2, null, null)
        assertEquals(2L, vm.state.value.edit!!.selectedId)
        assertEquals(emptyList<AppKey>(), catalog.launched)
    }

    @Test fun eachTilesLiveStateComesFromItsSources() = runTest {
        val chat = testApp("Chat")
        val source = FakeTileSource(SourceId.forApp(chat.key.packageName, 0))
        val viewModel = StartViewModel(FakeTileStore(), FakeAppCatalog(apps = listOf(chat)), gridWidth = 6, SourceRegistry { if (it == source.id) source else null })
        viewModel.pin(chat.key)
        source.itemState.value = listOf(testItem("b", at = 20), testItem("a", at = 10))
        val live = viewModel.live.first { it[1L]?.unread == 2 }
        assertEquals(listOf("b", "a"), live.getValue(1L).rotation.map { it.key })
    }

    private val chat = testApp("Chat")
    private val chatSource = FakeTileSource(SourceId.forApp(chat.key.packageName, 0))
    private val chatCatalog = FakeAppCatalog(apps = listOf(chat))
    private fun chatViewModel() =
        StartViewModel(FakeTileStore(), chatCatalog, gridWidth = 6, SourceRegistry { if (it == chatSource.id) chatSource else null }).apply { pin(chat.key) }

    @Test fun openingWithAnItemOpensItThroughItsSource() = runTest {
        val item = testItem("a", source = chatSource.id)
        chatViewModel().open(1, null, null, item)
        assertEquals(listOf<TileItem?>(item), chatSource.opened)
        assertEquals(emptyList<AppKey>(), chatCatalog.launched)
    }

    @Test fun openingWithoutAnItemStillLaunchesTheApp() = runTest {
        chatViewModel().open(1, null, null)
        assertEquals(listOf(chat.key), chatCatalog.launched)
        assertEquals(emptyList<TileItem?>(), chatSource.opened)
    }

    @Test fun showAgainAsksTheChatsSource() = runTest {
        val source = FakeTileSource(SourceId.forApp("com.example.chat", 0))
        val family = HiddenChat(source.id, "family", "Family")
        source.hiddenState.value = listOf(family)
        val viewModel = StartViewModel(store, catalog, gridWidth = 6, SourceRegistry { if (it == source.id) source else null })
        viewModel.showAgain(family)
        assertEquals(emptyList<HiddenChat>(), source.hiddenState.value)
    }
}
