package io.github.b1tsagar.liquiwin.core.tiles.source

import io.github.b1tsagar.liquiwin.core.testing.FakeTileSource
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SourceRegistryTest {
    @Test fun eachSourceIsCreatedOnce() {
        var created = 0
        val registry = SourceRegistry { created++; FakeTileSource(it) }
        val id = SourceId("app:com.whatsapp@0")
        assertSame(registry.source(id), registry.source(id))
        assertEquals(1, created)
    }

    @Test fun anIdNoOneCanServeResolvesToNull() {
        assertNull(SourceRegistry { null }.source(SourceId("combined:7")))
    }

    @Test fun appIdsGiveTheirPackageAndUser() {
        assertEquals("com.whatsapp" to 10L, SourceId.forApp("com.whatsapp", 10).appPackageAndSerial())
        assertNull(SourceId("search:apps").appPackageAndSerial())
        assertNull(SourceId("app:broken").appPackageAndSerial())
    }
}
