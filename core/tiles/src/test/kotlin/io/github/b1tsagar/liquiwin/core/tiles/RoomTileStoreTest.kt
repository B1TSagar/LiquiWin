package io.github.b1tsagar.liquiwin.core.tiles

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.data.db.layout.LayoutDatabase
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.model.Tile
import io.github.b1tsagar.liquiwin.core.tiles.model.TileKind
import io.github.b1tsagar.liquiwin.core.tiles.model.TilePlacement
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.model.TileTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTileStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LayoutDatabase::class.java).build()
    private val store = RoomTileStore(db.tileDao())
    private val whatsapp = AppKey("com.whatsapp", "com.whatsapp.Main", 0)

    @After fun tearDown() = db.close()

    @Test fun insertAppTileStoresTheTileAndItsAppSource() = runTest {
        val id = store.insertAppTile(whatsapp, TileSize.MEDIUM)
        val tile = store.tiles.first().single()
        assertEquals(id, tile.id)
        assertEquals(TileKind.APP, tile.kind)
        assertEquals(TileTemplate.FLIP, tile.template)
        assertEquals(whatsapp, tile.appKey)
        assertEquals(listOf(SourceId("app:com.whatsapp@0")), tile.sources)
    }

    @Test fun commitReplacesOnlyTheWidthsItNames() = runTest {
        val id = store.insertAppTile(whatsapp, TileSize.MEDIUM)
        store.commit(LayoutEdit(placements = mapOf(4 to listOf(TilePlacement(id, 4, 0, 0)), 6 to listOf(TilePlacement(id, 6, 0, 0)))))
        store.commit(LayoutEdit(placements = mapOf(6 to listOf(TilePlacement(id, 6, 2, 0)))))
        assertEquals(listOf(TilePlacement(id, 4, 0, 0)), store.placements(4).first())
        assertEquals(listOf(TilePlacement(id, 6, 2, 0)), store.placements(6).first())
        assertEquals(setOf(4, 6), store.savedWidths())
    }

    @Test fun commitChangesSizesAndAppKeys() = runTest {
        val id = store.insertAppTile(whatsapp, TileSize.MEDIUM)
        val alias = whatsapp.copy(className = "com.whatsapp.AltIcon")
        store.commit(LayoutEdit(sizes = mapOf(id to TileSize.WIDE), appKeys = mapOf(id to alias)))
        val tile = store.tiles.first().single()
        assertEquals(TileSize.WIDE, tile.size)
        assertEquals(alias, tile.appKey)
    }

    @Test fun deletingATileRemovesItsSourcesAndPlacements() = runTest {
        val id = store.insertAppTile(whatsapp, TileSize.MEDIUM)
        store.commit(LayoutEdit(placements = mapOf(6 to listOf(TilePlacement(id, 6, 0, 0)))))
        store.commit(LayoutEdit(deleted = setOf(id)))
        assertEquals(emptyList<Tile>(), store.tiles.first())
        assertEquals(emptyList<TilePlacement>(), store.placements(6).first())
        assertEquals(0, db.tileDao().countSources())
    }
}
