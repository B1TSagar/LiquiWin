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
        createVersion1(context.getDatabasePath("cache.db"))
        val db = CacheDatabase.open(context)
        try {
            assertEquals(listOf("WhatsApp"), db.appDao().entries().map { it.label })
            assertEquals(emptyList<StoredItem>(), db.notificationDao().observeItems("app:com.whatsapp@0").first())
        } finally {
            db.close()
        }
    }

    // Builds the version 1 file from its exported schema, as room-testing's helper would, without adding that library.
    // Gradle runs unit tests in the module's directory, so the path is relative to core/data.
    private fun createVersion1(file: File) {
        val schema = JSONObject(File("schemas/${CacheDatabase::class.qualifiedName}/1.json").readText()).getJSONObject("database")
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO app_entry VALUES ('com.whatsapp/com.whatsapp.Main@0', 'WhatsApp', 0, 0, 'v1')")
            db.version = 1
        }
    }
}
