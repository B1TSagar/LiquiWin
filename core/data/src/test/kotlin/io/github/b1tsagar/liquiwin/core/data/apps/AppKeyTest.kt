package io.github.b1tsagar.liquiwin.core.data.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppKeyTest {
    @Test fun parseRoundTripsFlatten() {
        val key = AppKey("com.whatsapp", "com.whatsapp.Main", 10)
        assertEquals("com.whatsapp/com.whatsapp.Main@10", key.flatten())
        assertEquals(key, AppKey.parse(key.flatten()))
    }

    @Test fun parseRejectsMalformedValues() {
        assertNull(AppKey.parse("nonsense"))
        assertNull(AppKey.parse("a/b@x"))
    }
}
