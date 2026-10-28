package io.github.b1tsagar.liquiwin.core.data.db.cache

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CacheDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun tearDown() { context.deleteDatabase("cache.db") }

    @Test fun aVersion1DatabaseKeepsItsAppsAndGainsTheNotificationTables() = runTest {
        createVersion(1, context.getDatabasePath("cache.db")) {
            execSQL("INSERT INTO app_entry VALUES ('com.whatsapp/com.whatsapp.Main@0', 'WhatsApp', 0, 0, 'v1')")
        }
        val db = CacheDatabase.open(context)
        try {
            assertEquals(listOf("WhatsApp"), db.appDao().entries().map { it.label })
            assertEquals(emptyList<StoredItem>(), db.notificationDao().observeItems("app:com.whatsapp@0").first())
        } finally {
            db.close()
        }
    }

    @Test fun aVersion2DatabaseKeepsItsNotificationsAndGainsHiddenChats() = runTest {
        createVersion(2, context.getDatabasePath("cache.db")) {
            execSQL(
                "INSERT INTO notification_item (`key`, notification_key, source_id, conversation_key, sender, posted_at, is_self) " +
                    "VALUES ('a', 'n1', 'app:com.whatsapp@0', 'alex', 'Alex', 100, 0)",
            )
        }
        val db = CacheDatabase.open(context)
        try {
            assertEquals(listOf("a"), db.notificationDao().observeItems("app:com.whatsapp@0").first().map { it.item.key })
            assertEquals(emptyList<HiddenConversationEntity>(), db.notificationDao().observeHidden("app:com.whatsapp@0").first())
        } finally {
            db.close()
        }
    }

    // Builds an old version's file from its exported schema, as room-testing's helper would, without adding that library.
    // Gradle runs unit tests in the module's directory, so the path is relative to core/data.
    private fun createVersion(version: Int, file: File, rows: SQLiteDatabase.() -> Unit) {
        val schema = JSONObject(File("schemas/${CacheDatabase::class.qualifiedName}/$version.json").readText()).getJSONObject("database")
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                // Room checks indices after migrating, so the old file needs them too.
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.rows()
            db.version = version
        }
    }
}
