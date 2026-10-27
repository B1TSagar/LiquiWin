package io.github.b1tsagar.liquiwin.feature.start

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.data.db.layout.LayoutDatabase
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.MainDispatcherRule
import io.github.b1tsagar.liquiwin.core.testing.testApp
import io.github.b1tsagar.liquiwin.core.tiles.RoomTileStore
import io.github.b1tsagar.liquiwin.feature.start.layout.Cell
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** StartViewModel over the real Room store, which publishes a commit a moment after it returns, unlike FakeTileStore. */
@RunWith(AndroidJUnit4::class)
class StartViewModelRoomTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LayoutDatabase::class.java).build()
    private val camera = testApp("Camera"); private val maps = testApp("Maps")

    @After fun tearDown() = db.close()

    // runBlocking, not runTest: runTest jumps virtual time while Room works on real threads, which would fire the view
    // model's one-second safety timeout at once.
    @Test fun aDropNeverShowsTheTilesBackInTheirOldCells() = runBlocking {
        val viewModel = StartViewModel(RoomTileStore(db.tileDao()), FakeAppCatalog(apps = listOf(camera, maps)), gridWidth = 6)
        viewModel.pin(camera.key)
        withTimeout(5_000) { viewModel.state.first { it.tiles.size == 1 } }
        viewModel.pin(maps.key)
        val before = mapOf(1L to Cell(0, 0), 2L to Cell(2, 0))
        val after = mapOf(1L to Cell(2, 0), 2L to Cell(0, 0))
        withTimeout(5_000) { viewModel.state.first { shown(it) == before } }

        viewModel.enterEdit(1)
        viewModel.previewMove(1, Cell(2, 0))
        val drawn = CopyOnWriteArrayList<Map<Long, Cell>>()
        val recorder = launch(Dispatchers.Unconfined) { viewModel.state.collect { drawn += shown(it) } }
        viewModel.endDrag(1, Cell(2, 0))
        withTimeout(5_000) { viewModel.state.first { it.edit?.preview == null && shown(it) == after } }
        recorder.cancel()

        assertEquals(listOf(after), drawn.distinct())
    }

    // Where the screen draws each tile: its preview cell while a move is previewed, otherwise its saved cell.
    private fun shown(state: StartState) = state.tiles.associate { it.id to (state.edit?.preview?.get(it.id) ?: it.cell) }
}
