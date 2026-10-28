# LiquiWin Stage 2, Part 1: Live App Tiles — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App tiles come alive on the owner's Galaxy. Once the owner grants notification access, LiquiWin stores each app's notifications on the phone, flips the app's tile through its unread messages the way Windows Phone did, opens the message that was tapped, and says when live tiles are paused and how to fix it.

**Architecture:** Spec §3.4's flow. `:feature:notifications` (new) holds the listener, the normaliser, the intake that writes the store, the live action cache and each app's `TileSource`. `:core:tiles` holds the `TileSource` interface, the source registry and tile state. `:feature:start` draws the faces, runs the flip timer and splits taps by region. `:app` wires these together and owns the banners and the guide. `:tools:notifier` (new) is a debug app that posts chat notifications. Pure logic is unit-tested on the JVM. Room, the service and Compose are tested under Robolectric.

**Tech Stack:** As in stage 1: Kotlin 2.4.21, AGP 9.4.1, Gradle 9.8.2, Compose (BOM 2026.09.00, foundation and ui only), Room 2.8.5 with KSP 2.3.12, kotlinx.coroutines 1.11.0, JUnit 4, Robolectric 4.17 and Turbine 1.2.1. `NotificationCompat`, `MessagingStyle`, `Person`, `IconCompat`, `ShortcutManagerCompat` and `BundleCompat` come from androidx.core, which the catalog already has.

**Spec:** [docs/superpowers/specs/2026-10-27-liquiwin-launcher-start-screen-design.md](../specs/2026-10-27-liquiwin-launcher-start-screen-design.md), delivery stage 2 (§11), first of two parts. Windows Phone values come from [docs/reference/windows-phone-ui-reference.md](../../reference/windows-phone-ui-reference.md). How to reach and drive the phone is in [the stage 2 handoff](../handoff/2026-10-28-liquiwin-stage-2-handoff.md).

Stage 2 is split into two plans. This one makes single-app tiles live. Part 2 is written after this part lands, so it builds on the real code and on what real notifications looked like on the phone. It covers combined tiles, the tile editor, the hub, quick reply and the other templates.

## Global Constraints

- Android only: `minSdk 29`, `targetSdk 36`, `compileSdk 37`. The test phone is the owner's Galaxy S24 (SM-S921E), reached over wireless debugging as the handoff's "Phone testing" section describes.
- Toolchain: Gradle 9.8.2, AGP 9.4.1 (built-in Kotlin, so no module applies `org.jetbrains.kotlin.android`), Kotlin 2.4.21, KSP 2.3.12 and JDK 21. Set `JAVA_HOME` and `ANDROID_HOME` per machine (handoff, "Machine setup"). Nothing in the build hard-codes paths.
- Every version lives in `gradle/libs.versions.toml`. This part adds no dependencies.
- Package root: `io.github.b1tsagar.liquiwin`. Module namespaces append the module path, for example `io.github.b1tsagar.liquiwin.feature.notifications`.
- "**No `INTERNET` permission in sub-project 1.** Notification content can't leave the phone, because the app has no network access at all." (§2)
- Never requested: `INTERNET`, `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, any accessibility service, storage, location, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. (§8)
- The owner grants notification access in Android's settings. LiquiWin only opens the page.
- "Notification content is stored only in app-private storage. It's kept for 24 hours by default and excluded from backups. It can be wiped from settings, and never leaves the device." (§8) Logs never include a notification's title, text or images.
- "LiquiWin never triggers apps' own mark-as-read actions, because they would send read receipts." (§5.3)
- The notification tables go in `cache.db`, which the backup rules already exclude. `layout.db` doesn't change.
- "Avatars are downscaled to at most 256 px and photos to at most 720 px. They're stored in an app-private cache and deleted with their items." (§3.3) Storage growth: "50 items per app, 24 h retention, downscaled images, cleaned up hourly while the launcher is running". (§9)
- Single process. "Listener callbacks, parsing, database and image work run on background dispatchers. StrictMode is on in debug builds." (§9) The listener's callbacks arrive on the main thread, so they only queue work.
- Performance: "a tile shows a new item within 1 s". (§9)
- No feature module depends on another. Cross-module actions are callbacks that `:app` wires.
- "WP px" are Windows Phone pixels: dp = WP px × (screen width in dp ÷ 480).
- Page headers, banners and buttons are lowercase.
- No unofficial APIs. (§1.5)
- Tests use JUnit 4. Robolectric runs at `sdk=36`, set in each module's `src/test/resources/robolectric.properties`.

## Review Focus

1. **Malformed, huge or hostile notifications.** Extras of the wrong type, a style that throws while it's read, 100,000 characters of text, an image that can't be read. That notification is trimmed or skipped, the rest keep flowing, and LiquiWin never crashes. As Home, repeated crashes would also push the owner into safe mode. Tests in Tasks 2 and 3.
2. **Events missed while the listener was away.** Samsung stops the listener, or LiquiWin's process restarts. On reconnect, whatever is showing gets stored and whatever went is marked removed. Tests in Tasks 1, 3 and 4.
3. **Apps re-posting a conversation.** WhatsApp re-sends recent history with each new message, and apps edit messages or update a notification in place. A message seen again keeps its read state and images, an edit replaces the old text, nothing counts twice, and a removal right after its post leaves the item removed. Tests in Tasks 1, 2 and 3.
4. **Access revoked, or the listener stopped, while LiquiWin runs.** Tiles show their front faces with the pause mark and no stale counts. LiquiWin doesn't rebind without access, and the "keep stopping" banner only follows real disconnects. Tests in Tasks 7, 8 and 10.
5. **The same app in two profiles.** Personal and work WhatsApp are separate sources with separate counts, and opening goes to the right profile. Tests in Tasks 2 and 5.

---

## File structure

| Path | Module | Adds |
|---|---|---|
| `core/data/` | `:core:data` | Notification entities and DAO, `cache.db` version 2 |
| `core/tiles/` | `:core:tiles` | `TileSource`, `TileItem`, `SourceRegistry`, `TileState` |
| `core/design/` | `:core:design` | `tileFlipAngle`, the Pause icon |
| `core/testing/` | `:core:testing` | `FakeTileSource`, `testItem()` |
| `feature/notifications/` | `:feature:notifications` (new) | `Normaliser`, `LiveActionCache`, `ImageStore`, `NotificationIntake`, `LiquiWinNotificationListener`, `AppNotificationSource`, `ItemLauncher`, `NotificationAccess`, `DisconnectLog`, `LiveTilesHealth` |
| `feature/start/` | `:feature:start` | `live/` (`TileFaces`, `TileTicker`, `TileFlipState`, `TileTap`), live faces in `StartTileView`, flips and tap regions in `StartScreen` |
| `app/` | `:app` | Wiring in `AppGraph`, `LiquiWinApp` as `NotificationsHost`, `LiveTilesBanner`, `KeepAwakeGuide` |
| `tools/notifier/` | `:tools:notifier` (new) | The debug app that posts chats |

Kotlin sources go under `src/main/kotlin/io/github/b1tsagar/liquiwin/<module path>/`, and tests under `src/test/kotlin/` in the same package. Below, `…/` stands for that package directory.

`:feature:notifications` uses `:core:tiles`, and through it `:core:data` and `:core:design`. It has no Compose. `:app` adds it. `:tools:notifier` depends on nothing in LiquiWin.

**Not in this part:**
- Part 2:
  - combined tiles and the tile editor (§4.4), with its "new tile" button;
  - combined-source repairs (§9);
  - the PEEK, CYCLE, ICONIC and STATIC templates;
  - the combined front face, and the app icon on combined faces;
  - the hub and `:feature:hub` (§5.6);
  - quick reply and reply buttons (§5.7);
  - `TileSource.label`, `icon` and `reply`;
  - "show previews" and "keep read items";
  - success criteria 2 and 3 checked on the Galaxy.
- Stage 3: tile pace, the "reduced" motion setting, and the Roborazzi screenshot matrix (§10), which will cover the live faces. Until then, motion follows Android's remove-animations setting, the pace is normal, and the faces are checked by Compose tests and on the phone.
- Stage 6: the ignore list, "wipe history" in settings, and first-time setup's "keep tiles awake" step.

## Choices this plan makes

1. **Two plans.** Each part ships a build for the phone, and the real notifications seen with part 1 inform part 2's hub and quick reply.
2. **Modelled fixtures.** §10 asks for normaliser fixtures recorded on the Galaxy with a debug capture screen. Recording real ones would put the owner's messages in the repository, so this part builds them with `NotificationCompat` in each app's shape, with made-up names and texts. Task 12 checks the real apps on the phone.
3. **One read rule, so no `readAt` column.** An item is read once its conversation's `lastReadAt` reaches its time. Opening, removal and "mark all read" all move `lastReadAt`. A plain (non-messaging) notification is its own conversation, keyed by its notification key.
4. **Columns beyond §3.3:** `notification_key` (the notification an item came from), `conversation_title` (groups only) and `shortcut_id`. The primary key is §5.2's dedupe key.
5. **A notification's new post replaces its old one.** Items stored under a notification key but missing from its new post are deleted rather than marked read, so an edit or an update counts once.
6. **Faces, the flip timer and taps live in `:feature:start`.** Only start draws tiles in sub-project 1, and `:core:tiles` has no Compose.
7. **Paused tiles hide their counts**, which would be stale. The pause mark takes the count's place.
8. **The "keep stopping" banner** counts `onListenerDisconnected`, and resumes that find the listener still unbound 2 s later, at most once a minute. After 3 in 24 h, a "live tiles keep stopping · fix" banner offers the "keep tiles awake" guide, rather than the guide opening over start unasked. The banner goes once LiquiWin's battery use is Unrestricted.
9. **`cache.db` version 2** uses an auto-migration, with `fallbackToDestructiveMigration(dropAllTables = true)` as a safety net, because the file holds only data LiquiWin can rebuild.
10. **Removal marks items read**, even when an app cancels a notification and re-posts the same messages under a new one. That's the spec's rule.

**Downloads to approve:** none.

---

### Task 1: Notification tables in `cache.db`

**Files:**
- Create: `core/data/src/main/kotlin/…/core/data/db/cache/NotificationItemEntity.kt`, `ConversationReadEntity.kt`, `NotificationDao.kt`
- Modify: `core/data/src/main/kotlin/…/core/data/db/cache/CacheDatabase.kt`
- Test: `core/data/src/test/kotlin/…/core/data/db/cache/NotificationDaoTest.kt`, `CacheDatabaseMigrationTest.kt`
- Generated: `core/data/schemas/io.github.b1tsagar.liquiwin.core.data.db.cache.CacheDatabase/2.json` (commit it)

**Interfaces:**
- Consumes: `CacheDatabase` version 1 (tables `app_entry` and `app_launch`) and its `AppDao`, from stage 1.
- Produces:

```kotlin
@Entity(tableName = "notification_item", indices = [Index("source_id"), Index("notification_key")])
data class NotificationItemEntity(
    @PrimaryKey val key: String,                                    // §5.2's dedupe key (Task 2)
    @ColumnInfo(name = "notification_key") val notificationKey: String,
    @ColumnInfo(name = "source_id") val sourceId: String,           // SourceId.value
    @ColumnInfo(name = "conversation_key") val conversationKey: String,
    @ColumnInfo(name = "conversation_title") val conversationTitle: String?,
    @ColumnInfo(name = "shortcut_id") val shortcutId: String?,
    val sender: String,
    val text: String?,
    @ColumnInfo(name = "avatar_file") val avatarFile: String?,
    @ColumnInfo(name = "image_file") val imageFile: String?,
    @ColumnInfo(name = "posted_at") val postedAt: Long,
    @ColumnInfo(name = "removed_at") val removedAt: Long?,
    @ColumnInfo(name = "is_self") val isSelf: Boolean,
)

@Entity(tableName = "conversation_read", primaryKeys = ["source_id", "conversation_key"])
data class ConversationReadEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "conversation_key") val conversationKey: String,
    @ColumnInfo(name = "last_read_at") val lastReadAt: Long,
)

data class StoredItem(@Embedded val item: NotificationItemEntity, val unread: Boolean)

@Dao
abstract class NotificationDao {
    abstract fun observeItems(sourceId: String): Flow<List<StoredItem>>   // newest first, then by key
    abstract suspend fun existingKeys(keys: List<String>): List<String>
    abstract suspend fun item(key: String): NotificationItemEntity?
    /** Stores a notification's items. Returns the image files of items its last post had and this one hasn't. */
    @Transaction open suspend fun replaceNotification(notificationKey: String, items: List<NotificationItemEntity>): List<String>
    @Transaction open suspend fun markRemoved(notificationKey: String, now: Long)
    @Transaction open suspend fun markRemovedExcept(activeKeys: Set<String>, now: Long)
    abstract suspend fun markRead(sourceId: String, conversationKey: String, upTo: Long)
    /** Deletes items older than [cutoff], then all but the newest [perSource] of each source. Returns their image files. */
    @Transaction open suspend fun cleanUp(cutoff: Long, perSource: Int): List<String>
    abstract suspend fun allFiles(): List<String>                        // every avatar and image file an item names
}
```

  - `CacheDatabase` version 2, with `abstract fun notificationDao(): NotificationDao`.

- [ ] **Step 1: Write the failing tests.**

```kotlin
@RunWith(AndroidJUnit4::class)
class NotificationDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CacheDatabase::class.java).build()
    private val dao = db.notificationDao()
    private val whatsapp = "app:com.whatsapp@0"
    private val telegram = "app:org.telegram.messenger@0"

    @After fun tearDown() = db.close()

    private fun item(
        key: String, at: Long, conversation: String = "alex", notification: String = "n1", source: String = whatsapp,
        self: Boolean = false, avatar: String? = null, text: String? = "hi",
    ) = NotificationItemEntity(key, notification, source, conversation, null, null, "Alex", text, avatar, null, at, null, self)

    private suspend fun unread(source: String = whatsapp) = dao.observeItems(source).first().filter { it.unread }.map { it.item.key }

    @Test fun aMessageIsUnreadUntilItsConversationIsReadUpToIt() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100), item("b", 200)))
        assertEquals(listOf("b", "a"), unread())
        dao.markRead(whatsapp, "alex", upTo = 100)
        assertEquals(listOf("b"), unread())
        dao.markRead(whatsapp, "alex", upTo = 50) // a read mark never moves back
        assertEquals(listOf("b"), unread())
    }

    @Test fun yourOwnMessagesAreNeverUnread() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100), item("me", 150, self = true)))
        assertEquals(listOf("a"), unread())
    }

    // Review Focus 3
    @Test fun aRepostUpdatesInPlaceAndKeepsReadStateAndImages() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100, avatar = "a.webp")))
        dao.markRead(whatsapp, "alex", upTo = 100)
        dao.replaceNotification("n2", listOf(item("a", 100, notification = "n2"), item("b", 200, notification = "n2")))
        val a = dao.item("a")!!
        assertEquals("a.webp", a.avatarFile)
        assertEquals("n2", a.notificationKey)
        assertEquals(listOf("b"), unread())
    }

    // Review Focus 3
    @Test fun anEditedMessageReplacesTheOldOneAndCountsOnce() = runTest {
        assertEquals(emptyList<String>(), dao.replaceNotification("n1", listOf(item("a", 100, avatar = "a.webp"))))
        assertEquals(listOf("a.webp"), dao.replaceNotification("n1", listOf(item("a-edited", 100, text = "hi!"))))
        assertEquals(listOf("a-edited"), unread())
    }

    @Test fun removingANotificationReadsItsItemsButNotLaterOnes() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100), item("b", 200)))
        dao.markRemoved("n1", now = 300)
        assertEquals(emptyList<String>(), unread())
        assertEquals(300L, dao.item("b")?.removedAt)
        dao.replaceNotification("n3", listOf(item("b", 200, notification = "n3"), item("c", 400, notification = "n3")))
        assertEquals(listOf("c"), unread())
        assertNull(dao.item("b")?.removedAt) // showing again, but still read
    }

    // Review Focus 2
    @Test fun reconcilingMarksRemovedEveryItemWhoseNotificationIsGone() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100)))
        dao.replaceNotification("n2", listOf(item("b", 200, conversation = "sam", notification = "n2")))
        dao.markRemovedExcept(setOf("n2"), now = 300)
        assertEquals(listOf("b"), unread())
        assertEquals(300L, dao.item("a")?.removedAt)
    }

    @Test fun cleanUpKeepsTheNewest50PerSourceWithinTheWindowAndReturnsTheDeletedFiles() = runTest {
        dao.replaceNotification("n1", (1..52).map { item("w$it", 1_000L + it, avatar = "w$it") })
        dao.replaceNotification("t1", listOf(item("old", 10, notification = "t1", source = telegram, avatar = "old")))
        dao.markRemoved("t1", now = 20) // reads Telegram's conversation up to 10
        assertEquals(setOf("w1", "w2", "old"), dao.cleanUp(cutoff = 500, perSource = 50).toSet())
        assertEquals((52 downTo 3).map { "w$it" }, dao.observeItems(whatsapp).first().map { it.item.key })
        assertEquals(50, dao.allFiles().size)
        // The old read mark went too, so an item older than it counts as unread again.
        dao.replaceNotification("t2", listOf(item("older", 5, notification = "t2", source = telegram)))
        assertEquals(listOf("older"), unread(telegram))
    }
}
```

```kotlin
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
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*NotificationDaoTest" --tests "*CacheDatabaseMigrationTest"`
Expected: FAIL to compile, because the entities and the DAO don't exist.

- [ ] **Step 3: Implement the tables and the DAO.**
  - **Unread rule**, in `observeItems`: `is_self = 0 AND posted_at > COALESCE(last_read_at, 0)`, from a `LEFT JOIN conversation_read` on `source_id` and `conversation_key`. Order by `posted_at DESC, key`. There's no `removed_at` term: removal moves `last_read_at` instead.
  - **`replaceNotification`:**
    1. Delete the rows with this `notification_key` whose key isn't among `items`, collecting their `avatar_file` and `image_file`.
    2. Insert the keys not yet stored.
    3. For keys already stored, update only `notification_key`, `conversation_title` and `shortcut_id`, and clear `removed_at`. The rest stays, because the key already fixes the sender, text and time, and the stored images are reused.
  - **`markRemoved`:** sets `removed_at = now` on this notification's items that aren't removed yet. For each conversation among them, it then calls `markRead` up to the newest such item's `posted_at`.
  - **`markRemovedExcept`:** calls `markRemoved` for every `notification_key` that still has unremoved items and isn't in `activeKeys`. Do the filtering in Kotlin: a `NOT IN` list could pass API 29's 999-variable limit.
  - **`markRead`:** sets `last_read_at` to the larger of the stored value and `upTo`, in one `INSERT OR REPLACE … VALUES (…, MAX(:upTo, COALESCE((SELECT last_read_at …), 0)))` statement.
  - **`cleanUp`:**
    1. Delete the items with `posted_at < cutoff`.
    2. For each source, delete everything after its newest `perSource`, in `observeItems` order. Use `LIMIT -1 OFFSET :perSource` per source.
    3. Delete the `conversation_read` rows with `last_read_at < cutoff`.
    4. Return the deleted items' files.
  - **SQLite on API 29 is 3.22:** it has no `ON CONFLICT … DO UPDATE` clause and no window functions. Room's own `@Insert` and `@Update` are fine.
  - **`CacheDatabase`:**
    - entities `AppEntryEntity`, `AppLaunchEntity`, `NotificationItemEntity` and `ConversationReadEntity`;
    - `version = 2`, `autoMigrations = [AutoMigration(from = 1, to = 2)]`;
    - `open()` adds `.fallbackToDestructiveMigration(dropAllTables = true)`.

- [ ] **Step 4: Run them again.**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS, and `core/data/schemas/…CacheDatabase/2.json` exists.

- [ ] **Step 5: Commit.**

```bash
git add core/data
git commit -m "Add the notification tables to the cache database"
```

---

### Task 2: The normaliser

**Files:**
- Modify: `settings.gradle.kts` (include `:feature:notifications`)
- Create: `feature/notifications/build.gradle.kts`, `feature/notifications/src/test/resources/robolectric.properties` (`sdk=36`), `feature/notifications/src/main/kotlin/…/feature/notifications/Normaliser.kt`
- Test: `feature/notifications/src/test/kotlin/…/feature/notifications/NormaliserTest.kt`, `TestNotifications.kt` (fixtures)

**Interfaces:**
- Consumes: `SourceId` and `SourceId.forApp(packageName, userSerial)` from stage 1.
- Produces:

```kotlin
data class NormalisedItem(
    val key: String,                  // §5.2's dedupe key
    val conversationKey: String,
    val conversationTitle: String?,   // groups only
    val shortcutId: String?,
    val sender: String,
    val text: String?,
    val postedAt: Long,
    val isSelf: Boolean,
    val avatar: IconCompat?,
    val image: IconCompat?,
)

data class Normalised(
    val notificationKey: String,      // StatusBarNotification.key
    val sourceId: SourceId,
    val items: List<NormalisedItem>,  // never empty
    val contentIntent: PendingIntent?,
    val replyAction: Notification.Action?,
)

class Normaliser(context: Context, serialOf: (UserHandle) -> Long) {
    /** Null for a notification LiquiWin ignores (§5.2). */
    fun normalise(sbn: StatusBarNotification): Normalised?
}
```

- [ ] **Step 1: Create the module.** Give `feature/notifications/build.gradle.kts` the shape of `core/tiles/build.gradle.kts`: an Android library with namespace `io.github.b1tsagar.liquiwin.feature.notifications` and no Compose. It needs `implementation(project(":core:tiles"))` and `implementation(libs.androidx.core.ktx)`. For tests it needs `testImplementation(project(":core:testing"))`, plus junit, robolectric, androidx-test-junit, coroutines-test and turbine as `:core:tiles` has them. Add `include(":feature:notifications")` to `settings.gradle.kts`.

- [ ] **Step 2: Write the fixtures.** In `TestNotifications.kt`, build each notification with `NotificationCompat.Builder(context, "c")` and `setSmallIcon(android.R.drawable.sym_action_chat)`. Use made-up names, and give every fixture a content intent (`PendingIntent.getActivity(context, 0, Intent("open"), FLAG_IMMUTABLE)`). `NotificationCompat.Builder` sets `when` to the current time, so a fixture whose time a test checks sets it.

```kotlin
internal val context: Context get() = ApplicationProvider.getApplicationContext()

// The deprecated constructor is the public one; uid 10_123 is any app's.
internal fun sbn(pkg: String, notification: Notification, id: Int = 1, tag: String? = null, postTime: Long = 5_000, user: UserHandle = Process.myUserHandle()) =
    StatusBarNotification(pkg, pkg, id, tag, 10_123, 0, 0, notification, user, postTime)
```

| Fixture | Shape |
|---|---|
| `whatsAppChat(messages: List<Pair<String, Long>> = listOf("are you coming?" to 1_000L, "see you at 6" to 2_000L), mine: List<Pair<String, Long>> = emptyList())` | MessagingStyle with user "You". Each message comes from `Person` "Alex" (key `alex`, a bitmap icon); `mine` messages have no person. Shortcut id `alex`. Actions in this order: "Mark as read" (`SEMANTIC_ACTION_MARK_AS_READ`, no input), then "Reply" (`SEMANTIC_ACTION_REPLY`, `RemoteInput` "reply") |
| `whatsAppGroup()` | MessagingStyle group titled "Family": Mum "on my way" at 1 000, Dad "bring bread" at 2 000. Shortcut id `family` |
| `whatsAppSummary()`, `gmailSummary()` | `setGroupSummary(true)` with a title and text |
| `instagramLike()` | Title "Instagram", text "sam_k liked your photo", `BigPictureStyle` with a 2000 × 1000 bitmap |
| `snapchatSnap()` | Title "Alex", text "sent you a Snap", no actions, `setWhen(0)` |
| `messengerChat()` | MessagingStyle from "Jo". One action "Reply" with a free-form `RemoteInput` and no semantic action |
| `telegramPhoto()` | MessagingStyle message "photo" from "Alex" with data `image/jpeg`, `content://org.telegram.messenger.provider/media/1` |
| `gmailEmail()` | `BigTextStyle`: title "Pat Lee", text "Lunch?", big text "Lunch on Friday? The new place on King St.". Actions "Archive", and "Reply" without a `RemoteInput`. `setWhen(4_000)` |
| `navigation()` | `setOngoing(true)` |
| `musicPlayer()` | The platform `Notification.Builder` with `Notification.MediaStyle()` (androidx's MediaStyle is in a library we don't have) |
| `download()` | `setProgress(100, 40, false)` |
| `ownNotification()` | Any notification. The tests post it from `context.packageName` |

- [ ] **Step 3: Write the failing test.**

```kotlin
@RunWith(AndroidJUnit4::class)
class NormaliserTest {
    private val work = UserHandle.getUserHandleForUid(10 * 100_000 + 10_123)
    private val normaliser = Normaliser(context) { if (it == work) 10L else 0L }
    private fun normalise(pkg: String, notification: Notification, tag: String? = null, user: UserHandle = Process.myUserHandle()) =
        normaliser.normalise(sbn(pkg, notification, tag = tag, user = user))

    @Test fun aWhatsAppChatGivesOneItemPerMessage() {
        val n = normalise("com.whatsapp", whatsAppChat())!!
        assertEquals(SourceId("app:com.whatsapp@0"), n.sourceId)
        assertEquals(listOf("are you coming?", "see you at 6"), n.items.map { it.text })
        assertEquals(listOf(1_000L, 2_000L), n.items.map { it.postedAt })
        for (item in n.items) {
            assertEquals("Alex", item.sender)
            assertEquals("alex", item.conversationKey) // the shortcut id
            assertNull(item.conversationTitle)
            assertFalse(item.isSelf)
            assertNotNull(item.avatar)
        }
        assertNotNull(n.contentIntent)
    }

    @Test fun aGroupMessageNamesItsGroup() {
        val n = normalise("com.whatsapp", whatsAppGroup())!!
        assertEquals(listOf("Mum", "Dad"), n.items.map { it.sender })
        assertEquals(listOf("Family", "Family"), n.items.map { it.conversationTitle })
    }

    @Test fun yourOwnMessagesAreMarkedAsYours() {
        val n = normalise("com.whatsapp", whatsAppChat(mine = listOf("on my way" to 3_000L)))!!
        assertEquals(listOf(false, false, true), n.items.map { it.isSelf })
    }

    @Test fun theReplyActionIsTheSemanticReplyOrAFreeFormInput() {
        assertEquals("Reply", normalise("com.whatsapp", whatsAppChat())!!.replyAction?.title) // never "Mark as read", listed first
        assertEquals("Reply", normalise("com.facebook.orca", messengerChat())!!.replyAction?.title)
        assertNull(normalise("com.google.android.gm", gmailEmail())!!.replyAction) // its Reply takes no text
    }

    @Test fun anEmailIsOnePlainItemWithItsBigText() {
        val n = normalise("com.google.android.gm", gmailEmail(), tag = "pat")!!
        val item = n.items.single()
        assertEquals("Pat Lee", item.sender)
        assertEquals("Lunch on Friday? The new place on King St.", item.text)
        assertEquals(n.notificationKey, item.conversationKey)
        assertEquals(4_000L, item.postedAt) // its `when`
    }

    @Test fun aNotificationWithoutAWhenIsDatedByItsPostTime() {
        assertEquals(5_000L, normalise("com.snapchat.android", snapchatSnap())!!.items.single().postedAt)
    }

    @Test fun summariesOngoingMediaProgressAndOwnNotificationsAreIgnored() {
        assertNull(normalise("com.whatsapp", whatsAppSummary()))
        assertNull(normalise("com.google.android.gm", gmailSummary()))
        assertNull(normalise("com.google.android.apps.maps", navigation()))
        assertNull(normalise("com.spotify.music", musicPlayer()))
        assertNull(normalise("com.android.chrome", download()))
        assertNull(normalise(context.packageName, ownNotification()))
    }

    // Review Focus 3
    @Test fun aRepostedMessageKeepsItsKeyAndAnEditedOneGetsANewKey() {
        val first = normalise("com.whatsapp", whatsAppChat(listOf("see you at 6" to 2_000L)))!!.items.single()
        val again = normaliser.normalise(sbn("com.whatsapp", whatsAppChat(), postTime = 9_000))!!.items.last()
        val edited = normalise("com.whatsapp", whatsAppChat(listOf("see you at 7" to 2_000L)))!!.items.single()
        assertEquals(first.key, again.key)
        assertNotEquals(first.key, edited.key)
    }

    @Test fun aPhotoMessageCarriesItsImage() {
        val photo = normalise("org.telegram.messenger", telegramPhoto())!!.items.single()
        assertEquals("content://org.telegram.messenger.provider/media/1", photo.image?.uri.toString())
        assertNotNull(normalise("com.instagram.android", instagramLike())!!.items.single().image)
        assertNull(normalise("com.snapchat.android", snapchatSnap())!!.items.single().image)
    }

    // Review Focus 1
    @Test fun hugeTextIsCutAndAnEmptyNotificationIsIgnored() {
        val huge = NotificationCompat.Builder(context, "c").setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("A".repeat(100_000)).setContentText("b".repeat(100_000)).build()
        val item = normalise("com.example.spam", huge)!!.items.single()
        assertEquals(200, item.sender.length)
        assertEquals(2_000, item.text?.length)
        assertNull(normalise("com.example.empty", NotificationCompat.Builder(context, "c").setSmallIcon(android.R.drawable.sym_action_chat).build()))
    }

    // Review Focus 1
    @Test fun aMalformedMessagesBundleFallsBackToThePlainNotification() {
        val n = NotificationCompat.Builder(context, "c").setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Alex").setContentText("1 new message").build()
        n.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification\$MessagingStyle")
        n.extras.putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf(Bundle().apply { putInt("text", 5); putLong("time", 1_000) }))
        val item = normalise("com.example.chat", n)!!.items.single()
        assertEquals("Alex", item.sender)
        assertEquals("1 new message", item.text)
    }

    // Review Focus 5
    @Test fun workProfileNotificationsBelongToTheWorkSource() {
        val personal = normalise("com.whatsapp", whatsAppChat())!!
        val workChat = normalise("com.whatsapp", whatsAppChat(), user = work)!!
        assertEquals(SourceId("app:com.whatsapp@10"), workChat.sourceId)
        assertNotEquals(personal.items.first().key, workChat.items.first().key)
    }
}
```

- [ ] **Step 4: Run it and watch it fail.**

Run: `./gradlew :feature:notifications:testDebugUnitTest --tests "*NormaliserTest"`
Expected: FAIL to compile, because `Normaliser` doesn't exist.

- [ ] **Step 5: Implement `Normaliser`.**
  - **Ignored:**
    - LiquiWin's own package (`context.packageName`);
    - `FLAG_GROUP_SUMMARY`;
    - `sbn.isOngoing` or `FLAG_FOREGROUND_SERVICE`;
    - media: `EXTRA_TEMPLATE` is `android.app.Notification$MediaStyle` or `$DecoratedMediaCustomViewStyle`, or `EXTRA_MEDIA_SESSION` is set;
    - progress: `EXTRA_PROGRESS_MAX > 0`, `EXTRA_PROGRESS_INDETERMINATE`, or the `android.app.Notification$ProgressStyle` template;
    - anything left with neither a title nor a text.

    The ignore list is stage 6's.
  - **Messaging notifications** are those where `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification` gives at least one usable message: one with text, or with image data. Reading the style is wrapped in `try`/`catch`, and a style that throws or has no usable message falls back to the plain path. For each message:
    - **one item** per message;
    - **sender:** the person's name, or the style user's name when there's no person;
    - **isSelf:** true when there's no person, or the person matches the style's user (the same key when both have one, otherwise the same name);
    - **avatar:** the person's icon, or else the notification's large icon;
    - **image:** the message's data URI, as `IconCompat.createWithContentUri`, when its MIME type starts with `image/`;
    - **conversationTitle:** the style's conversation title, only when `isGroupConversation`;
    - **shortcutId:** `NotificationCompat.getShortcutId(notification)`;
    - **conversationKey:** the shortcut id, or else `sbn.key`;
    - **postedAt:** the message's timestamp.
  - **Plain notifications:**
    - one item;
    - **sender:** `EXTRA_TITLE`, or "";
    - **text:** `EXTRA_BIG_TEXT`, or else `EXTRA_TEXT`;
    - **postedAt:** `notification.when`, or `sbn.postTime` when that's 0;
    - **conversationKey:** `sbn.key`;
    - **avatar:** the large icon, through `IconCompat.createFromIcon(context, icon)`;
    - **image:** the big picture, from `BundleCompat.getParcelable(extras, EXTRA_PICTURE, Bitmap::class.java)` or, on API 31+, `EXTRA_PICTURE_ICON`.
  - **Every item:**
    - **key:** `listOf(sourceId.value, conversationKey, sender, postedAt, (text ?: "").hashCode()).joinToString("|")`;
    - **limits:** the sender is cut to 200 characters and the text to 2,000, as plain strings without spans;
    - **redacted text** (Android 15+ one-time codes) is stored as Android sent it, with nothing special-cased.
  - **reply:** the first action with `SEMANTIC_ACTION_REPLY` and a `RemoteInput`, or else the first action with a `RemoteInput` that allows free-form input. No other action is ever kept.
  - **sourceId:** `SourceId.forApp(sbn.packageName, serialOf(sbn.user))`.
  - **contentIntent:** `notification.contentIntent`.

- [ ] **Step 6: Run it again.**

Run: `./gradlew :feature:notifications:testDebugUnitTest --tests "*NormaliserTest"`
Expected: PASS.

- [ ] **Step 7: Commit.**

```bash
git add settings.gradle.kts feature/notifications
git commit -m "Add the notification normaliser"
```

---

### Task 3: Storing notifications with their live actions

**Files:**
- Create: `feature/notifications/src/main/kotlin/…/feature/notifications/LiveActionCache.kt`, `ImageStore.kt`, `NotificationIntake.kt`
- Test: `feature/notifications/src/test/kotlin/…/feature/notifications/ImageStoreTest.kt`, `NotificationIntakeTest.kt`

**Interfaces:**
- Consumes: `Normaliser`, `Normalised` and `NormalisedItem` (Task 2). `NotificationDao`, `NotificationItemEntity` and `StoredItem` (Task 1).
- Produces:

```kotlin
data class LiveActions(val contentIntent: PendingIntent?, val reply: Notification.Action?)

/** The actions of the notifications showing now, by notification key. Memory only: a PendingIntent can't be stored. */
class LiveActionCache {
    val actions: StateFlow<Map<String, LiveActions>>
    fun put(notificationKey: String, live: LiveActions)
    fun remove(notificationKey: String)
    fun retainOnly(notificationKeys: Set<String>)
}

class ImageStore(context: Context, val dir: File) {
    /** Saves [icon] as WebP, scaled to fit [maxPx]. Returns the file's name, or null when the icon can't be read. */
    fun save(icon: IconCompat, maxPx: Int): String?
    fun delete(names: Collection<String>)
    companion object { const val AVATAR_PX = 256; const val IMAGE_PX = 720 }
}

/** The size that fits [maxPx] on its longer side, never larger than the original and at least 1 × 1. */
fun fitWithin(width: Int, height: Int, maxPx: Int): Pair<Int, Int>

class NotificationIntake(
    normalise: (StatusBarNotification) -> Normalised?,
    dao: NotificationDao,
    actions: LiveActionCache,
    images: ImageStore,
    scope: CoroutineScope,
    now: () -> Long = System::currentTimeMillis,
) {
    fun posted(sbn: StatusBarNotification)
    fun removed(notificationKey: String)
    fun synced(active: List<StatusBarNotification>)
    fun cleanUp()
    companion object { const val RETENTION_MS = 24 * 60 * 60 * 1000L; const val PER_SOURCE = 50 }
}
```

- [ ] **Step 1: Write the failing tests.**

```kotlin
@RunWith(AndroidJUnit4::class)
class ImageStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val store by lazy { ImageStore(ApplicationProvider.getApplicationContext(), folder.newFolder("images")) }
    private fun bitmap(width: Int, height: Int) = IconCompat.createWithBitmap(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))

    @Test fun fitWithinScalesTheLongerSideDownAndNeverUp() {
        assertEquals(256 to 128, fitWithin(1_000, 500, 256))
        assertEquals(19 to 256, fitWithin(300, 4_000, 256))
        assertEquals(64 to 64, fitWithin(64, 64, 256))
        assertEquals(1 to 256, fitWithin(1, 10_000, 256))
    }

    @Test fun savesABitmapIconAsAFile() {
        val name = store.save(bitmap(1_000, 500), ImageStore.AVATAR_PX)
        assertTrue(File(store.dir, name!!).length() > 0) // Robolectric's legacy graphics: check the file, not its pixels
    }

    // Review Focus 1
    @Test fun anUnreadableIconGivesNull() {
        assertNull(store.save(IconCompat.createWithContentUri("content://nowhere/1"), ImageStore.IMAGE_PX))
        assertEquals(emptyList<String>(), store.dir.list().orEmpty().toList())
    }

    @Test fun deleteRemovesTheFiles() {
        val name = store.save(bitmap(10, 10), ImageStore.AVATAR_PX)!!
        store.delete(listOf(name, "missing.webp"))
        assertFalse(File(store.dir, name).exists())
    }
}
```

```kotlin
@RunWith(AndroidJUnit4::class)
class NotificationIntakeTest {
    @get:Rule val folder = TemporaryFolder()
    private val db = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
    private val dao = db.notificationDao()
    private val actions = LiveActionCache()
    private val images by lazy { ImageStore(context, folder.newFolder("images")) }
    private val normaliser = Normaliser(context) { 0L }
    private var clock = 10_000L

    @After fun tearDown() = db.close()

    private fun TestScope.intake(normalise: (StatusBarNotification) -> Normalised? = normaliser::normalise) =
        NotificationIntake(normalise, dao, actions, images, backgroundScope, now = { clock })

    // Events are handled in order, so seeing a later event's result shows the earlier ones were handled.
    private suspend fun items(source: String = "app:com.whatsapp@0", until: (List<StoredItem>) -> Boolean) = dao.observeItems(source).first(until)

    @Test fun aPostedChatIsStoredWithItsLiveActions() = runTest {
        val chat = sbn("com.whatsapp", whatsAppChat())
        intake().posted(chat)
        val stored = items { it.size == 2 }
        assertEquals(listOf("see you at 6", "are you coming?"), stored.map { it.item.text })
        assertTrue(stored.all { it.unread && it.item.avatarFile != null })
        assertEquals("Reply", actions.actions.value.getValue(chat.key).reply?.title)
    }

    // Review Focus 3
    @Test fun aRemovalRightAfterItsPostLeavesTheItemRemoved() = runTest {
        val chat = sbn("com.whatsapp", whatsAppChat())
        intake().run { posted(chat); removed(chat.key) }
        val stored = items { it.isNotEmpty() && it.all { s -> s.item.removedAt != null } }
        assertTrue(stored.none { it.unread })
        assertFalse(chat.key in actions.actions.value)
    }

    // Review Focus 2
    @Test fun reconnectingStoresWhatIsShowingAndRemovesWhatWentWhileDisconnected() = runTest {
        val gone = sbn("com.whatsapp", whatsAppChat(), id = 1)
        val showing = sbn("com.whatsapp", whatsAppGroup(), id = 2)
        val intake = intake()
        intake.posted(gone)
        items { it.size == 2 }
        intake.synced(listOf(showing))
        val stored = items { it.size == 4 && it.any { s -> s.item.removedAt != null } }
        assertEquals(setOf("Mum", "Dad"), stored.filter { it.unread }.map { it.item.sender }.toSet())
        assertEquals(setOf(showing.key), actions.actions.value.keys)
    }

    // Review Focus 3
    @Test fun aRepostSavesImagesOnlyForNewMessages() = runTest {
        val intake = intake()
        intake.posted(sbn("com.whatsapp", whatsAppChat(listOf("are you coming?" to 1_000L))))
        val first = items { it.size == 1 }.single().item.avatarFile
        intake.posted(sbn("com.whatsapp", whatsAppChat()))
        val stored = items { it.size == 2 }
        assertEquals(first, stored.single { it.item.text == "are you coming?" }.item.avatarFile)
        assertEquals(2, images.dir.list()!!.size)
    }

    @Test fun messagesOlderThan24HoursAreNotStored() = runTest {
        clock = 1_500 + NotificationIntake.RETENTION_MS // "are you coming?" (1 000) is outside the window
        intake().posted(sbn("com.whatsapp", whatsAppChat()))
        assertEquals(listOf("see you at 6"), items { it.isNotEmpty() }.map { it.item.text })
    }

    // Review Focus 1
    @Test fun aNotificationThatFailsIsSkippedAndTheNextOneIsStored() = runTest {
        val intake = intake { if (it.packageName == "com.example.bad") error("unreadable") else normaliser.normalise(it) }
        intake.posted(sbn("com.example.bad", whatsAppChat()))
        intake.posted(sbn("com.whatsapp", whatsAppChat()))
        items { it.size == 2 }
        assertEquals(emptyList<StoredItem>(), dao.observeItems("app:com.example.bad@0").first())
    }

    @Test fun cleanUpDeletesTheImagesOfDeletedItemsAndStrayFiles() = runTest {
        val intake = intake()
        intake.posted(sbn("com.whatsapp", whatsAppChat()))
        items { it.size == 2 }
        File(images.dir, "stray.webp").writeText("left by a write that failed")
        clock += NotificationIntake.RETENTION_MS
        intake.cleanUp()
        intake.posted(sbn("com.whatsapp", whatsAppChat(listOf("later" to clock)), id = 2)) // handled after the clean-up
        items { it.map { s -> s.item.text } == listOf("later") }
        assertEquals(1, images.dir.list()!!.size)
    }
}
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:notifications:testDebugUnitTest --tests "*ImageStoreTest" --tests "*NotificationIntakeTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Implement the cache, the image store and the intake.**
  - **`LiveActionCache`:** a `MutableStateFlow` map, changed with `update`.
  - **`ImageStore.save`:**
    - It creates `dir` if needed.
    - **URI icons** (`TYPE_URI`, `TYPE_URI_ADAPTIVE_BITMAP`) decode through `ImageDecoder`, with `setTargetSize` from `fitWithin` and `ALLOCATOR_SOFTWARE`, so a huge photo never decodes at full size.
    - **Other icons** go through `loadDrawable(context)`, then `toBitmap` at `fitWithin` of the intrinsic size, or `maxPx` square when there isn't one.
    - **Files:** WebP at quality 85 (`WEBP_LOSSY` on API 30+, `WEBP` on 29), named `<random UUID>.webp`.
    - **Failures:** any exception returns null and deletes the partial file.
  - **`fitWithin`:** rounds to the nearest pixel.
  - **`NotificationIntake`:**
    - **Queue:** one unlimited `Channel` of events, drained in order by one coroutine launched in `scope`. The four methods only `trySend`, so the listener's main-thread callbacks return at once.
    - **Failures:** each event runs in `try`/`catch`, and the next event still runs. A failure logs `Log.w("NotificationIntake", "skipped a notification from <package>", e)`. Rethrow `CancellationException`, and never log titles, texts or images.
    - **posted:**
      1. `normalise(sbn)`; null means the notification is ignored.
      2. Drop items older than `now() − RETENTION_MS`. If none are left, stop.
      3. `actions.put(notificationKey, LiveActions(contentIntent, replyAction))`, first, so a stored item never shows without its actions.
      4. For items whose key `existingKeys` doesn't return, save the avatar at `AVATAR_PX` and the image at `IMAGE_PX`.
      5. `replaceNotification(...)`, then delete the files it returns.
    - **removed:** `markRemoved(key, now())`, then `actions.remove(key)`.
    - **synced:** one event. Run posted's steps for each active notification, each in its own `try`/`catch`, so a bad one doesn't stop the rest. Then call `markRemovedExcept` and `actions.retainOnly` with every active key.
    - **cleanUp:** delete the files `dao.cleanUp(now() − RETENTION_MS, PER_SOURCE)` returns. Then delete every file in `images.dir` that `dao.allFiles()` doesn't name. Those were left by a write that failed, or by a database the fallback rebuilt.

- [ ] **Step 4: Run them again.**

Run: `./gradlew :feature:notifications:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add feature/notifications
git commit -m "Store posted notifications with their live actions"
```

---

### Task 4: The notification listener

**Files:**
- Create: `feature/notifications/src/main/AndroidManifest.xml`, `feature/notifications/src/main/kotlin/…/feature/notifications/LiquiWinNotificationListener.kt`, `NotificationsHost.kt`
- Modify: `app/build.gradle.kts` (add `implementation(project(":feature:notifications"))`), `app/src/main/kotlin/…/LiquiWinApp.kt`, `AppGraph.kt`
- Test: `feature/notifications/src/test/kotlin/…/feature/notifications/LiquiWinNotificationListenerTest.kt`, and one test added to `app/src/test/kotlin/…/HomeManifestTest.kt`

**Interfaces:**
- Consumes: `NotificationIntake`, `Normaliser`, `LiveActionCache` and `ImageStore` (Tasks 2 and 3). `CacheDatabase.notificationDao()` (Task 1).
- Produces:

```kotlin
/** What the listener needs from the app. In safe mode the app hands out nothing. */
interface NotificationsHost {
    val notificationIntake: NotificationIntake?
}

/** Whether Android has the listener bound right now. */
object ListenerConnection {
    val connected: StateFlow<Boolean>
    internal fun set(connected: Boolean)
}

open class LiquiWinNotificationListener : NotificationListenerService() // open so a test can set what's showing
```

  - `AppGraph`: `val imageStore: ImageStore`, `val liveActions: LiveActionCache`, `val notificationIntake: NotificationIntake`, and a private `cacheDb` that the app catalog shares.
  - `class LiquiWinApp : Application(), NotificationsHost`.

- [ ] **Step 1: Write the failing tests.**

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(application = TestHost::class)
class LiquiWinNotificationListenerTest {
    private val host get() = ApplicationProvider.getApplicationContext<TestHost>()
    private val listener by lazy { Robolectric.buildService(ShowingListener::class.java).create().get() }

    @After fun tearDown() {
        ListenerConnection.set(false)
        host.close()
    }

    @Test fun postedAndRemovedNotificationsReachTheStore() = runTest {
        val chat = sbn("com.whatsapp", whatsAppChat())
        listener.onNotificationPosted(chat)
        host.items { it.size == 2 }
        listener.onNotificationRemoved(chat)
        host.items { it.isNotEmpty() && it.all { s -> s.item.removedAt != null } }
    }

    // Review Focus 2
    @Test fun connectingMarksTheListenerConnectedAndSyncs() = runTest {
        listener.onNotificationPosted(sbn("com.whatsapp", whatsAppChat()))
        host.items { it.size == 2 }
        listener.showing = emptyArray() // the chat went while LiquiWin wasn't listening
        listener.onListenerConnected()
        assertTrue(ListenerConnection.connected.value)
        host.items { it.isNotEmpty() && it.all { s -> s.item.removedAt != null } }
    }

    @Test fun inSafeModeTheListenerStoresNothing() = runTest {
        host.safeMode = true
        listener.onNotificationPosted(sbn("com.whatsapp", whatsAppChat()))
        listener.onListenerConnected()
        assertEquals(emptyList<StoredItem>(), host.items { true }) // with no intake, nothing was queued
    }

    @Test fun disconnectingMarksTheListenerDisconnected() {
        listener.onListenerConnected()
        listener.onListenerDisconnected()
        assertFalse(ListenerConnection.connected.value)
    }
}

/** An app with an in-memory store. In safe mode it has no intake. */
class TestHost : Application(), NotificationsHost {
    var safeMode = false
    private val db by lazy { Room.inMemoryDatabaseBuilder(this, CacheDatabase::class.java).build() }
    private val intake by lazy {
        NotificationIntake(
            Normaliser(this) { 0L }::normalise, db.notificationDao(), LiveActionCache(), ImageStore(this, File(cacheDir, "images")),
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }
    override val notificationIntake: NotificationIntake? get() = if (safeMode) null else intake
    suspend fun items(until: (List<StoredItem>) -> Boolean) = db.notificationDao().observeItems("app:com.whatsapp@0").first(until)
    fun close() = db.close()
}

class ShowingListener : LiquiWinNotificationListener() {
    var showing: Array<StatusBarNotification> = emptyArray()
    override fun getActiveNotifications(): Array<StatusBarNotification> = showing
}
```

In `HomeManifestTest`, using the `context` its other tests use:

```kotlin
@Test fun theNotificationListenerIsDeclaredAndProtected() {
    val component = ComponentName(context, "io.github.b1tsagar.liquiwin.feature.notifications.LiquiWinNotificationListener")
    val service = context.packageManager.getServiceInfo(component, 0)
    assertEquals(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, service.permission)
    assertTrue(service.exported)
}
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:notifications:testDebugUnitTest --tests "*LiquiWinNotificationListenerTest" :app:testDebugUnitTest --tests "*HomeManifestTest"`
Expected: FAIL. The listener doesn't exist, and the app's manifest has no listener.

- [ ] **Step 3: Implement the listener and wire it in.**
  - **Manifest** (the library's, merged into the app): a `<service android:name=".LiquiWinNotificationListener">` with `android:exported="true"`, `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"`, and an intent filter for `android.service.notification.NotificationListenerService`. No new permissions: `HomeManifestTest.forbiddenPermissionsAreNeverRequested` must still pass.
  - **The service:**
    - Each callback reads `(application as? NotificationsHost)?.notificationIntake`, and does nothing when that's null.
    - `onListenerConnected`: `ListenerConnection.set(true)`, then `intake.synced(activeNotifications.toList())`, in a `try`/`catch` that logs and skips the sync.
    - `onListenerDisconnected`: `ListenerConnection.set(false)`. Task 10 adds the health check here.
    - `onNotificationPosted(sbn)` calls `posted(sbn)`, and `onNotificationRemoved(sbn)` calls `removed(sbn.key)`.
  - **`LiquiWinApp`:** `override val notificationIntake: NotificationIntake? get() = if (crashGuard.shouldStartInSafeMode()) null else graph.notificationIntake`. Safe mode never builds the graph, as before.
  - **`AppGraph`:**
    - `private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
    - `private val cacheDb by lazy { CacheDatabase.open(app) }`. The catalog uses it instead of opening its own, because two Room instances must never open the same file.
    - `val imageStore by lazy { ImageStore(app, File(app.cacheDir, "notification-images")) }`.
    - `val liveActions = LiveActionCache()`.
    - `val notificationIntake by lazy { … }`, built with `Normaliser(app, userManager::getSerialNumberForUser)::normalise` and the `io` scope. It starts `io.launch { while (true) { intake.cleanUp(); delay(1.hours) } }` when it's first built.

- [ ] **Step 4: Run them again.**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, the stage 1 suites included.

- [ ] **Step 5: Commit.**

```bash
git add feature/notifications app
git commit -m "Add the notification listener"
```

---

### Task 5: Tile sources and tile state

**Files:**
- Create: `core/tiles/src/main/kotlin/…/core/tiles/source/TileSource.kt`, `SourceRegistry.kt`, `TileState.kt`
- Modify: `core/tiles/src/main/kotlin/…/core/tiles/model/SourceId.kt` (add `appPackageAndSerial()`)
- Create: `core/testing/src/main/kotlin/…/core/testing/FakeTileSource.kt` (with `testItem()`)
- Create: `feature/notifications/src/main/kotlin/…/feature/notifications/AppNotificationSource.kt`, `ItemLauncher.kt`
- Modify: `app/src/main/kotlin/…/AppGraph.kt`
- Test: `core/tiles/src/test/kotlin/…/core/tiles/source/TileStateTest.kt`, `SourceRegistryTest.kt`, and `feature/notifications/src/test/kotlin/…/feature/notifications/AppNotificationSourceTest.kt`

**Interfaces:**
- Consumes: `NotificationDao` and `StoredItem` (Task 1), `LiveActionCache` (Task 3), and from stage 1 `AppCatalog`, `LauncherApp`, `AppKey` and `SourceId`.
- Produces, in `:core:tiles` (spec §3.2, without part 2's `label`, `icon` and `reply`):

```kotlin
@JvmInline value class ImageRef(val file: File)

data class ConversationRef(val key: String, val shortcutId: String?)

data class TileItem(
    val key: String,
    val sourceId: SourceId,
    val title: String,                 // the sender, or "sender · group"
    val text: String?,
    val avatar: ImageRef?,
    val image: ImageRef?,
    val time: Instant,                 // java.time.Instant
    val unread: Boolean,
    val canReply: Boolean,
    val conversation: ConversationRef?,
)

interface TileSource {
    val id: SourceId
    val items: Flow<List<TileItem>>    // newest first
    val unreadCount: Flow<Int>
    suspend fun open(item: TileItem?)  // null opens the app
    suspend fun markRead(items: List<TileItem>) // local read state only
}

/** Creates each source once, on first use. Gives null for an id nothing serves. Thread-safe. */
class SourceRegistry(create: (SourceId) -> TileSource?) {
    fun source(id: SourceId): TileSource?
}

data class TileState(val rotation: List<TileItem>, val unread: Int)

/** Unread items of all [sources], newest first, at most 5 (§5.3), and their summed counts. */
fun tileState(sources: List<TileSource>): Flow<TileState>

// In SourceId: the package and user serial of an "app:<package>@<serial>" id, or null.
fun appPackageAndSerial(): Pair<String, Long>?
```

- In `:core:testing`:

```kotlin
class FakeTileSource(override val id: SourceId) : TileSource {
    val itemState: MutableStateFlow<List<TileItem>>   // items; unreadCount counts the unread ones
    val opened: MutableList<TileItem?>
    val read: MutableList<TileItem>
}

fun testItem(key: String, title: String = "Alex", text: String? = "see you at 6", at: Long = 0, unread: Boolean = true, source: SourceId = SourceId("app:com.example.chat@0")): TileItem
// with no images, canReply = false and conversation = ConversationRef(title.lowercase(), null)
```

- In `:feature:notifications`:

```kotlin
interface ItemLauncher {
    fun send(intent: PendingIntent): Boolean
    fun startShortcut(packageName: String, shortcutId: String, userSerial: Long): Boolean
    fun appGone()
}

class AndroidItemLauncher(context: Context) : ItemLauncher

class AppNotificationSource(
    packageName: String,
    userSerial: Long,
    dao: NotificationDao,
    actions: LiveActionCache,
    imagesDir: File,
    catalog: AppCatalog,
    launcher: ItemLauncher,
) : TileSource
```

  - `AppGraph`: `val sources: SourceRegistry`.

- [ ] **Step 1: Write the failing tests.**

```kotlin
class TileStateTest {
    private val a = FakeTileSource(SourceId("app:a@0"))
    private val b = FakeTileSource(SourceId("app:b@0"))

    @Test fun theRotationIsUnreadItemsNewestFirstAtMostFive() = runTest {
        a.itemState.value = listOf(testItem("read", at = 99, unread = false, source = a.id)) +
            (7 downTo 1).map { testItem("m$it", at = it * 10L, source = a.id) }
        val state = tileState(listOf(a)).first()
        assertEquals(listOf("m7", "m6", "m5", "m4", "m3"), state.rotation.map { it.key })
        assertEquals(7, state.unread)
    }

    @Test fun twoSourcesInterleaveByTimeAndAddUpTheirCounts() = runTest {
        a.itemState.value = listOf(testItem("a2", at = 30, source = a.id), testItem("a1", at = 10, source = a.id))
        b.itemState.value = listOf(testItem("b1", at = 20, source = b.id))
        val state = tileState(listOf(a, b)).first()
        assertEquals(listOf("a2", "b1", "a1"), state.rotation.map { it.key })
        assertEquals(3, state.unread)
    }

    @Test fun aTileWithNoSourcesHasAnEmptyState() = runTest {
        assertEquals(TileState(emptyList(), 0), tileState(emptyList()).first())
    }
}

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
```

```kotlin
@RunWith(AndroidJUnit4::class)
class AppNotificationSourceTest {
    @get:Rule val folder = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
    private val dao = db.notificationDao()
    private val actions = LiveActionCache()
    private val whatsApp = testApp("WhatsApp", packageName = "com.whatsapp")
    private val workWhatsApp = testApp("WhatsApp", packageName = "com.whatsapp", userSerial = 10)
    private val catalog = FakeAppCatalog(apps = listOf(whatsApp, workWhatsApp))
    private val launcher = RecordingLauncher()
    private val source by lazy { AppNotificationSource("com.whatsapp", 0, dao, actions, folder.root, catalog, launcher) }
    private val intent = PendingIntent.getActivity(context, 0, Intent("chat"), PendingIntent.FLAG_IMMUTABLE)
    private val reply = Notification.Action.Builder(null, "Reply", intent).addRemoteInput(RemoteInput.Builder("reply").build()).build()

    @After fun tearDown() = db.close()

    private fun stored(key: String, at: Long, conversation: String = "alex", group: String? = null, notification: String = "n1", serial: Long = 0, shortcut: String? = "alex") =
        NotificationItemEntity(key, notification, "app:com.whatsapp@$serial", conversation, group, shortcut, if (group == null) "Alex" else "Mum", "hi", "$key.webp", null, at, null, false)

    @Test fun itemsAreNewestFirstWithUnreadAndCanReply() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100), stored("b", 200)))
        dao.markRead("app:com.whatsapp@0", "alex", upTo = 100)
        actions.put("n1", LiveActions(intent, reply))
        val items = source.items.first()
        assertEquals(listOf("b", "a"), items.map { it.key })
        assertEquals(listOf(true, false), items.map { it.unread })
        assertTrue(items.all { it.canReply })
        assertEquals(File(folder.root, "b.webp"), items.first().avatar?.file)
        assertEquals(Instant.ofEpochMilli(200), items.first().time)
        assertEquals(1, source.unreadCount.first())
    }

    @Test fun aGroupMessageIsTitledWithSenderAndGroup() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100, conversation = "family", group = "Family")))
        assertEquals("Mum · Family", source.items.first().single().title)
    }

    @Test fun openingAnItemReadsItAndSendsItsContentIntent() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100), stored("b", 200)))
        actions.put("n1", LiveActions(intent, reply = null))
        source.open(source.items.first().first())
        assertEquals(listOf(intent), launcher.sent)
        assertEquals(0, source.unreadCount.first())
    }

    @Test fun aCancelledContentIntentFallsBackToTheShortcut() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        actions.put("n1", LiveActions(intent, reply = null))
        launcher.sendWorks = false
        source.open(source.items.first().single())
        assertEquals(listOf(Triple("com.whatsapp", "alex", 0L)), launcher.shortcuts)
    }

    @Test fun withNoShortcutItLaunchesTheApp() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100, shortcut = null)))
        source.open(source.items.first().single())
        assertEquals(listOf(whatsApp.key), catalog.launched)
    }

    @Test fun withNothingToOpenItSaysTheAppIsGone() = runTest {
        catalog.apps.value = emptyList()
        dao.replaceNotification("n1", listOf(stored("a", 100, shortcut = null)))
        source.open(source.items.first().single())
        assertEquals(1, launcher.goneCount)
    }

    @Test fun openingTheSourceLaunchesTheApp() = runTest {
        source.open(null)
        assertEquals(listOf(whatsApp.key), catalog.launched)
    }

    @Test fun markReadReadsEachConversationUpToItsNewestGivenItem() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100), stored("b", 200), stored("c", 300)))
        dao.replaceNotification("n2", listOf(stored("s", 150, conversation = "sam", notification = "n2")))
        source.markRead(source.items.first().filter { it.key in setOf("b", "s") })
        assertEquals(listOf("c"), source.items.first().filter { it.unread }.map { it.key })
    }

    // Review Focus 5
    @Test fun itemsOfTheOtherProfileStayOut() = runTest {
        dao.replaceNotification("w1", listOf(stored("work", 100, notification = "w1", serial = 10)))
        assertEquals(emptyList<TileItem>(), source.items.first())
        AppNotificationSource("com.whatsapp", 10, dao, actions, folder.root, catalog, launcher).open(null)
        assertEquals(listOf(workWhatsApp.key), catalog.launched)
    }
}

private class RecordingLauncher : ItemLauncher {
    var sendWorks = true
    val sent = mutableListOf<PendingIntent>()
    val shortcuts = mutableListOf<Triple<String, String, Long>>()
    var goneCount = 0
    override fun send(intent: PendingIntent) = sendWorks.also { if (it) sent += intent }
    override fun startShortcut(packageName: String, shortcutId: String, userSerial: Long) = true.also { shortcuts += Triple(packageName, shortcutId, userSerial) }
    override fun appGone() { goneCount++ }
}
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :core:tiles:testDebugUnitTest :feature:notifications:testDebugUnitTest --tests "*AppNotificationSourceTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Implement the sources.**
  - **`tileState`:** `combine` each source's `items` and `unreadCount`. The rotation is the unread items of all sources, newest first and then by key, at most 5. No sources gives `flowOf(TileState(emptyList(), 0))`.
  - **`AppNotificationSource.items`:** `dao.observeItems(id.value)` combined with `actions.actions`, mapped to `TileItem`:
    - **title:** `"$sender · $conversationTitle"` for a group, otherwise the sender;
    - **images:** `ImageRef(File(imagesDir, name))` for each file it names;
    - **time:** `Instant.ofEpochMilli(postedAt)`;
    - **canReply:** true when the item isn't removed and its notification has a cached reply action;
    - **conversation:** `ConversationRef(conversationKey, shortcutId)`.

    `id` is `SourceId.forApp(packageName, userSerial)`, and `unreadCount` is `items` mapped to its unread count.
  - **`open(item)`:** `markRead(listOf(item))` first. Then it takes the first of these that works:
    1. the cached content intent of the item's notification (`dao.item(item.key)?.notificationKey`), through `launcher.send`;
    2. the item's shortcut, through `launcher.startShortcut`;
    3. `catalog.launch` of the catalog app with this package and user serial;
    4. `launcher.appGone()`.

    `open(null)` starts at step 3.
  - **`markRead(items)`:** for each conversation among `items`, `dao.markRead(id.value, conversation.key, the newest item's time)`.
  - **`AndroidItemLauncher`:**
    - **`send`:** use `ActivityOptions.makeBasic()`, with `setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)` on API 36+ and `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` on 34–35. LiquiWin is in front when a tile is tapped, so that's allowed. Call `intent.send(context, 0, null, null, null, null, options.toBundle())`. `PendingIntent.CanceledException` returns false.
    - **`startShortcut`:** works only while `LauncherApps.hasShortcutHostPermission()` (LiquiWin is the Home app). Get the user from `UserManager.getUserForSerialNumber`, then call `LauncherApps.startShortcut(packageName, shortcutId, null, null, user)`. Any `RuntimeException` (no such shortcut, a locked profile) returns false.
    - **`appGone`:** posts a `Toast` reading "this app isn't installed any more" (§5.5) on the main thread.
  - **`AppGraph`:** `val sources = SourceRegistry { id -> id.appPackageAndSerial()?.let { (pkg, serial) -> AppNotificationSource(pkg, serial, cacheDb.notificationDao(), liveActions, imageStore.dir, catalog, AndroidItemLauncher(app)) } }`.

- [ ] **Step 4: Run them again.**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add core feature/notifications app
git commit -m "Add tile sources and live tile state"
```

---

### Task 6: The tile flip and the flip timer

**Files:**
- Create: `core/design/src/main/kotlin/…/core/design/motion/TileFlip.kt`, `feature/start/src/main/kotlin/…/feature/start/live/TileTicker.kt`
- Test: `core/design/src/test/kotlin/…/core/design/motion/TileFlipTest.kt`, `feature/start/src/test/kotlin/…/feature/start/live/TileTickerTest.kt`

**Interfaces:**
- Produces:

```kotlin
// core/design: reference "every transition 0.85 s, CubicEase-out; flip = RotationX 0->180 with face swap at 0.185 s"
const val TILE_FLIP_MS = 850

/** The flip's angle, 0 to 180 degrees, after [elapsedMs]: cubic ease-out over TILE_FLIP_MS. */
fun tileFlipAngle(elapsedMs: Float): Float

// feature/start: spec §5.4's timer
class TileTicker(random: Random) {           // kotlin.random.Random
    fun nextGapMs(): Long                    // 100 to 3 000, in 100 ms steps
    fun tick(candidates: Collection<Long>): Long?   // one tile to move, or null when every candidate is resting
    fun jumped(tileId: Long, nowMs: Long)    // a new item moved this tile
    fun jumpDelayMs(tileId: Long, nowMs: Long): Long
    companion object { const val FIRST_TICK_MS = 2_500L; const val REST_TICKS = 3; const val JUMP_DEBOUNCE_MS = 600L }
}
```

- [ ] **Step 1: Write the failing tests.**

```kotlin
class TileFlipTest {
    @Test fun turnsHalfWayAtTheFaceSwapAndFullyAtTheEnd() {
        assertEquals(0f, tileFlipAngle(0f), 0.001f)
        assertEquals(180f, tileFlipAngle(850f), 0.001f)
        assertEquals(180f, tileFlipAngle(2_000f), 0.001f)
        assertTrue(tileFlipAngle(170f) < 90f && tileFlipAngle(200f) > 90f) // edge-on near the reference's 0.185 s swap
    }
}

class TileTickerTest {
    @Test fun gapsAreWholeTenthsFromATenthToThreeSeconds() {
        val ticker = TileTicker(Random(1))
        val gaps = List(1_000) { ticker.nextGapMs() }
        assertTrue(gaps.all { it in 100L..3_000L && it % 100 == 0L })
        assertEquals(100L, gaps.min())
        assertEquals(3_000L, gaps.max())
    }

    @Test fun aTileThatMovedSitsOutThreeTicks() {
        val ticker = TileTicker(Random(1))
        assertEquals(listOf(7L, null, null, null, 7L), List(5) { ticker.tick(listOf(7L)) })
    }

    @Test fun eachTickMovesOneTileAndRestingOnesAreSkipped() {
        val ticker = TileTicker(Random(1))
        val moved = List(40) { ticker.tick(listOf(1L, 2L, 3L, 4L, 5L))!! }
        moved.forEachIndexed { i, id -> assertFalse(id in moved.subList(maxOf(0, i - 3), i)) }
    }

    @Test fun aTileThatJumpedRestsToo() {
        val ticker = TileTicker(Random(1))
        ticker.jumped(7, nowMs = 0)
        assertEquals(listOf(null, null, null, 7L), List(4) { ticker.tick(listOf(7L)) })
    }

    @Test fun jumpsAreAtMostOncePer600Ms() {
        val ticker = TileTicker(Random(1))
        assertEquals(0L, ticker.jumpDelayMs(7, nowMs = 1_000))
        ticker.jumped(7, nowMs = 1_000)
        assertEquals(400L, ticker.jumpDelayMs(7, nowMs = 1_200))
        assertEquals(0L, ticker.jumpDelayMs(7, nowMs = 1_600))
        assertEquals(0L, ticker.jumpDelayMs(8, nowMs = 1_200))
    }
}
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :core:design:testDebugUnitTest --tests "*TileFlipTest" :feature:start:testDebugUnitTest --tests "*TileTickerTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Implement them.**
  - **`tileFlipAngle`:** `180 × (1 − (1 − t)³)` with `t = elapsedMs ÷ 850`, clamped to 0..1.
  - **`TileTicker`:**
    - It counts ticks. A tile moved on tick n, by `tick` or by `jumped`, is skipped on ticks n + 1 to n + 3. A jump counts as a move on the latest tick, or on tick 0 before the first.
    - `tick` picks at random among the candidates that aren't resting.
    - `jumpDelayMs` is what's left of the 600 ms since the tile's last jump, or 0.

- [ ] **Step 4: Run them again.**

Run: `./gradlew :core:design:testDebugUnitTest :feature:start:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add core/design feature/start
git commit -m "Add the tile flip and the live tile timer"
```

---

### Task 7: Live tile faces

**Files:**
- Create: `feature/start/src/main/kotlin/…/feature/start/live/TileFaces.kt`
- Modify:
  - `core/design/src/main/kotlin/…/core/design/icons/LiquiWinIcons.kt` (add `Pause`)
  - `feature/start/src/main/kotlin/…/feature/start/StartViewModel.kt`, `StartTileView.kt`, `StartScreen.kt`
  - `app/src/main/kotlin/…/home/HomeRoot.kt`, `app/src/main/kotlin/…/HomeActivity.kt`
- Test: `feature/start/src/test/kotlin/…/feature/start/live/TileFacesTest.kt`, `…/feature/start/StartTileViewTest.kt`, and new tests in `StartViewModelTest.kt` and `StartScreenTest.kt`

**Interfaces:**
- Consumes: `TileItem`, `TileState`, `tileState`, `SourceRegistry` (Task 5), `FakeTileSource` and `testItem` (Task 5), and `AppGraph.sources` (Task 5).
- Produces:

```kotlin
// feature/start …/live/TileFaces.kt
sealed interface TileFace {
    val key: String
    data object Front : TileFace { override val key = "front" }
    data class Item(val item: TileItem) : TileFace { override val key get() = item.key }
    data class Stack(val items: List<TileItem>) : TileFace { override val key get() = "stack" }
}

/** Small: the front. Medium and wide: the front, then one face per item. Large: the front and a stack of the 3 newest. */
internal fun faces(size: TileSize, rotation: List<TileItem>): List<TileFace>

/** The face after [current]. A face that's gone gives the front. */
internal fun nextFace(faces: List<TileFace>, current: String): TileFace

// StartViewModel
class StartViewModel(store: TileStore, catalog: AppCatalog, gridWidth: Int, sources: SourceRegistry = SourceRegistry { null })
val live: StateFlow<Map<Long, TileState>>   // by tile id

// StartTileView
internal fun StartTileView(tile: StartTile, metrics: GridMetrics, icon: suspend (LauncherApp) -> AppIcon?, modifier: Modifier = Modifier,
    face: TileFace = TileFace.Front, unread: Int = 0, liveTilesPaused: Boolean = false)

// StartScreen gains, after its existing parameters:
liveTilesPaused: Boolean = false

// LiquiWinIcons
val Pause: ImageVector

// HomeDependencies gains, last:
val sources: SourceRegistry = SourceRegistry { null }
```

- [ ] **Step 1: Write the failing tests.**

```kotlin
class TileFacesTest {
    private val items = listOf(testItem("c", at = 30), testItem("b", at = 20), testItem("a", at = 10), testItem("z", at = 5))

    @Test fun smallTilesOnlyHaveTheirFront() {
        assertEquals(listOf(TileFace.Front), faces(TileSize.SMALL, items))
    }

    @Test fun mediumAndWideTilesHaveOneFacePerItem() {
        for (size in listOf(TileSize.MEDIUM, TileSize.WIDE)) assertEquals(listOf("front", "c", "b", "a", "z"), faces(size, items).map { it.key })
    }

    @Test fun largeTilesStackTheThreeNewest() {
        assertEquals(listOf(TileFace.Front, TileFace.Stack(items.take(3))), faces(TileSize.LARGE, items))
    }

    @Test fun anEmptyRotationLeavesOnlyTheFront() {
        for (size in TileSize.entries) assertEquals(listOf(TileFace.Front), faces(size, emptyList()))
    }

    @Test fun nextFaceCyclesAndAGoneFaceGivesTheFront() {
        val faces = faces(TileSize.MEDIUM, items.take(2))
        assertEquals(listOf("c", "b", "front"), listOf("front", "c", "b").map { nextFace(faces, it).key })
        assertEquals("front", nextFace(faces, "read-meanwhile").key)
    }
}
```

```kotlin
@RunWith(AndroidJUnit4::class)
class StartTileViewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val metrics = GridMetrics(411f, 6, 24f, 12f)
    private fun tile(size: TileSize) = StartTile(1, size, Cell(0, 0), testApp("Chat"), "Chat", isWork = false, isPaused = false)

    @Test fun theFrontFaceShowsTheCountAndCapsItAt99Plus() {
        var unread by mutableIntStateOf(5)
        compose.setContent { LiquiWinTheme { StartTileView(tile(TileSize.SMALL), metrics, { null }, unread = unread) } }
        compose.onNodeWithText("5").assertExists()
        unread = 120
        compose.onNodeWithText("99+").assertExists()
    }

    // Review Focus 4
    @Test fun noCountShowsAtZeroOrWhilePaused() {
        var unread by mutableIntStateOf(0)
        var paused by mutableStateOf(false)
        compose.setContent { LiquiWinTheme { StartTileView(tile(TileSize.MEDIUM), metrics, { null }, unread = unread, liveTilesPaused = paused) } }
        compose.onNodeWithText("0").assertDoesNotExist()
        unread = 3
        paused = true
        compose.onNodeWithText("3").assertDoesNotExist()
        compose.onNodeWithTag("live-paused").assertExists()
    }

    @Test fun aMediumContentFaceShowsSenderAndText() {
        compose.setContent { LiquiWinTheme { StartTileView(tile(TileSize.MEDIUM), metrics, { null }, face = TileFace.Item(testItem("a"))) } }
        for (text in listOf("Alex", "see you at 6", "Chat")) compose.onNodeWithText(text).assertExists()
    }

    @Test fun aLargeTileStacksThreeItems() {
        val stack = TileFace.Stack(listOf(testItem("a", title = "Alex"), testItem("b", title = "Sam"), testItem("c", title = "Jo")))
        compose.setContent { LiquiWinTheme { StartTileView(tile(TileSize.LARGE), metrics, { null }, face = stack) } }
        for (name in listOf("Alex", "Sam", "Jo")) compose.onNodeWithText(name).assertExists()
    }
}
```

In `StartViewModelTest`, with the file's `MainDispatcherRule`:

```kotlin
@Test fun eachTilesLiveStateComesFromItsSources() = runTest {
    val chat = testApp("Chat")
    val source = FakeTileSource(SourceId.forApp(chat.key.packageName, 0))
    val viewModel = StartViewModel(FakeTileStore(), FakeAppCatalog(apps = listOf(chat)), gridWidth = 6, SourceRegistry { if (it == source.id) source else null })
    viewModel.pin(chat.key)
    source.itemState.value = listOf(testItem("b", at = 20), testItem("a", at = 10))
    val live = viewModel.live.first { it[1L]?.unread == 2 }
    assertEquals(listOf("b", "a"), live.getValue(1L).rotation.map { it.key })
}
```

In `StartScreenTest`, give the camera tile a source and pass `sources` to every `StartViewModel` that `show()` builds:

```kotlin
private val cameraSource = FakeTileSource(SourceId.forApp(camera.key.packageName, 0))
private val sources = SourceRegistry { if (it == cameraSource.id) cameraSource else null }

@Test fun talkBackHearsTheUnreadCount() {
    cameraSource.itemState.value = List(3) { testItem("m$it", at = it.toLong(), source = cameraSource.id) }
    show()
    compose.onNodeWithTag("tile-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 unread"))
}
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:start:testDebugUnitTest`
Expected: FAIL to compile, because `TileFace`, `faces`, `live` and the new parameters don't exist.

- [ ] **Step 3: Implement the faces.**
  - **`StartViewModel.live`:** for each tile, `tileState` over those of its `sources` the registry can serve. Re-subscribe only when the tiles' ids or sources change (`map { it.map { t -> t.id to t.sources } }.distinctUntilChanged()`, then `flatMapLatest`), with an empty map for no tiles. `stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())`. It stays separate from `StartState`, so a new message never re-runs the layout.
  - **`Pause`:** two bars, `M6 4h4v16H6zM14 4h4v16h-4z`, in the same 24 × 24 viewport. It isn't a Fluent path, so update the file's comment: "…except Pause, two plain bars drawn here."
  - **`StartTileView`, front face:** as now, plus the count.
    - The count sits bottom-right, inset 12 WP px from the right and 8 WP px from the bottom, white, in the tile-label style.
    - It shows 1–99 as the number and "99+" above 99. It shows on every size, small included, and not at 0.
    - With `liveTilesPaused`, the `Pause` icon takes the count's place: 16 WP px, tagged `live-paused`, with no content description, because the slot says it.
  - **`StartTileView`, content faces:**
    - **Text:** white, in the tile-label style, inside 12 WP px insets. The sender is SemiBold on one line, and every line ends in an ellipsis when cut.
    - **The label** stays at the bottom left, as on the front face.
    - **Item, medium:** a 32 WP px avatar at the top left with the sender beside it (8 WP px gap), then 2 lines of text.
    - **Item, wide, with an image:** the image fills the tile, cropped, under a 20 % black scrim. One line, "sender: text", sits above the label.
    - **Item, wide, without one:** a 64 WP px avatar on the left, with the sender and 3 lines of text to its right.
    - **Stack (large):** three equal rows above the label. Each has a 48 WP px avatar, the sender and 2 lines of text.
    - **No avatar:** the text starts at the inset.
    - **Images:** decode with `BitmapFactory.decodeFile` on `Dispatchers.IO`, in `produceState` keyed by the file. A file that's missing or won't decode shows nothing.
  - **`StartScreen`:**
    - Collects `viewModel.live` and gives each tile its count. Every tile shows its front face in this task.
    - The slot keeps `contentDescription = tile.label`, which `HomeRootTest` relies on.
    - It adds `stateDescription`: "<n> unread" when the tile has unread items, "live tile paused" while paused, and nothing otherwise.
  - **Wiring:** `HomeDependencies` gains `sources` last, with its default, so `HomeRootTest` compiles unchanged. `HomeRoot` passes it to `StartViewModel`, and `HomeActivity` passes `graph.sources`.

- [ ] **Step 4: Run them again.**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add core/design feature/start app
git commit -m "Draw live tile faces"
```

---

### Task 8: Flipping tiles on start

**Files:**
- Create: `feature/start/src/main/kotlin/…/feature/start/live/TileFlipState.kt`
- Modify: `feature/start/src/main/kotlin/…/feature/start/StartScreen.kt`
- Test: `feature/start/src/test/kotlin/…/feature/start/LiveTilesTest.kt`, `app/src/test/kotlin/…/home/LiveTileEndToEndTest.kt`

**Interfaces:**
- Consumes:
  - `TILE_FLIP_MS`, `tileFlipAngle` and `TileTicker` (Task 6);
  - `TileFace`, `faces`, `nextFace` and `StartViewModel.live` (Task 7);
  - for the end-to-end test, `Normaliser`, `NotificationIntake`, `LiveActionCache`, `ImageStore`, `AppNotificationSource` and `AndroidItemLauncher` (Tasks 2–5).
- Produces:

```kotlin
/** One tile's face: the one showing and, during a flip, the one it's turning from. */
@Stable internal class TileFlipState(face: String) {
    val face: String                  // showing, or being turned to
    val from: String?                 // being turned away from, while a flip runs
    val angle: Float                  // tileFlipAngle of the flip's elapsed time; 180 when still
    suspend fun flipTo(face: String)  // a cancelled flip ends at once on its new face
    fun snapTo(face: String)
}
```

- [ ] **Step 1: Write the failing tests.**

```kotlin
@RunWith(AndroidJUnit4::class)
class LiveTilesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val apps = (1..21).map { testApp("App $it") }
    private val sources = apps.associate { app -> SourceId.forApp(app.key.packageName, 0).let { it to FakeTileSource(it) } }
    private fun sourceOf(tile: Int) = sources.getValue(SourceId.forApp(apps[tile - 1].key.packageName, 0))
    private val chat get() = sourceOf(1)
    private var paused by mutableStateOf(false)
    private lateinit var viewModel: StartViewModel

    // Pins medium tiles, three to a row, for the first [pinned] apps, then shows start with the clock held.
    private fun show(pinned: Int = 1) {
        viewModel = StartViewModel(FakeTileStore(), FakeAppCatalog(apps = apps), gridWidth = 6, SourceRegistry(sources::get))
        apps.take(pinned).forEach { viewModel.pin(it.key) }
        compose.mainClock.autoAdvance = false
        compose.setContent { LiquiWinTheme { StartScreen(viewModel, { null }, rememberScrollState(), liveTilesPaused = paused) } }
    }

    private fun said(tile: Int): String? =
        compose.onNodeWithTag("tile-$tile").fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.StateDescription) { null }

    // What the tile says every 100 ms for [ms].
    private fun saidOver(tile: Int, ms: Long) = (1..ms / 100).map { compose.mainClock.advanceTimeBy(100); said(tile) }

    @Test fun theFirstTickFlipsATileToItsNewestItemAfter2_5Seconds() {
        chat.itemState.value = listOf(testItem("a"))
        show()
        compose.mainClock.advanceTimeBy(2_400)
        assertEquals("1 unread", said(1))
        compose.mainClock.advanceTimeBy(100L + TILE_FLIP_MS)
        assertEquals("1 unread, Alex: see you at 6", said(1))
    }

    @Test fun aNewItemFlipsItsTileWithinASecond() {
        show()
        compose.mainClock.advanceTimeBy(500)
        chat.itemState.value = listOf(testItem("a", text = "running late"))
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("1 unread, Alex: running late", said(1))
    }

    @Test fun onlyTilesOnScreenTakePart() {
        sourceOf(21).itemState.value = listOf(testItem("a", text = "below the fold"))
        show(pinned = 21) // seven rows; the last is below Robolectric's 470 dp screen
        assertTrue(saidOver(21, 20_000).none { it.orEmpty().contains("below the fold") })
        compose.onNodeWithTag("tile-21").performScrollTo()
        assertTrue(saidOver(21, 20_000).any { it.orEmpty().contains("below the fold") })
    }

    @Test fun withAnimationsOffTilesShowTheirNewestItemWithoutATimer() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        chat.itemState.value = listOf(testItem("b", text = "newest", at = 20), testItem("a", text = "older", at = 10))
        show()
        assertEquals(setOf("2 unread, Alex: newest"), saidOver(1, 20_000).toSet())
    }

    @Test fun tilesStayStillInEditMode() {
        chat.itemState.value = listOf(testItem("a"))
        show()
        viewModel.enterEdit(1)
        assertEquals(setOf("1 unread"), saidOver(1, 20_000).toSet())
    }

    // Review Focus 4
    @Test fun pausedTilesShowOnlyTheirFront() {
        paused = true
        chat.itemState.value = listOf(testItem("a"))
        show()
        assertEquals(setOf("live tile paused"), saidOver(1, 20_000).toSet())
    }
}
```

```kotlin
@RunWith(AndroidJUnit4::class)
class LiveTileEndToEndTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val mainDispatcher = MainDispatcherRule()
    @get:Rule val folder = TemporaryFolder()

    // The real pipeline from a posted notification to the tile. The fixtures live in another module's tests, so this builds its own.
    @Test fun aPostedMessageReachesItsTile() {
        val context = compose.activity.applicationContext
        val db = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
        val actions = LiveActionCache()
        val images = ImageStore(context, folder.newFolder("images"))
        val chat = testApp("Chat", packageName = "com.example.chat")
        val catalog = FakeAppCatalog(apps = listOf(chat))
        val sources = SourceRegistry { id ->
            id.appPackageAndSerial()?.let { (pkg, serial) -> AppNotificationSource(pkg, serial, db.notificationDao(), actions, images.dir, catalog, AndroidItemLauncher(context)) }
        }
        val intake = NotificationIntake(Normaliser(context) { 0L }::normalise, db.notificationDao(), actions, images, CoroutineScope(Dispatchers.IO))
        val viewModel = StartViewModel(FakeTileStore(), catalog, gridWidth = 6, sources).apply { pin(chat.key) }
        compose.setContent { LiquiWinTheme { StartScreen(viewModel, { null }, rememberScrollState()) } }

        val now = System.currentTimeMillis()
        val alex = Person.Builder().setName("Alex").setKey("alex").build()
        val message = NotificationCompat.Builder(context, "c").setSmallIcon(android.R.drawable.sym_action_chat)
            .setStyle(NotificationCompat.MessagingStyle(Person.Builder().setName("You").build()).addMessage("see you at 6", now, alex))
            .build()
        intake.posted(StatusBarNotification("com.example.chat", "com.example.chat", 1, null, 10_123, 0, 0, message, Process.myUserHandle(), now))

        compose.waitUntil(5_000) { viewModel.live.value[1L]?.unread == 1 }
        compose.mainClock.autoAdvance = false
        val said = (1..40).map {
            compose.mainClock.advanceTimeBy(100)
            compose.onNodeWithTag("tile-1").fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.StateDescription) { null }
        }
        assertTrue(said.any { it.orEmpty().contains("Alex: see you at 6") })
        db.close()
    }
}
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:start:testDebugUnitTest --tests "*LiveTilesTest" :app:testDebugUnitTest --tests "*LiveTileEndToEndTest"`
Expected: FAIL. The tiles never leave their front faces.

- [ ] **Step 3: Make the tiles move.**
  - **When tiles move:** all of these hold:
    - start is `shown` and `onScreen`;
    - the lifecycle is `RESUMED` (the display is off whenever the activity is stopped);
    - not in edit mode, and not `liveTilesPaused`;
    - animations are on: `Settings.Global.ANIMATOR_DURATION_SCALE` (default 1) isn't 0, read again on every resume.
  - **The timer:** while tiles move, wait `FIRST_TICK_MS`, then repeat:
    1. Gather the candidates: tiles whose slot overlaps the viewport and that have more than one face. Every tile is `FLIP` until part 2's editor, so there's no template filter yet.
    2. `ticker.tick(candidates)`, and flip the result to `nextFace`.
    3. Wait for that flip to end, so ticks move one tile at a time (reference: "1 tile animating at a time").
    4. Wait `nextGapMs()`.

    When tiles stop moving the loop is cancelled, and a flip under way ends on its new face. Keep one `TileTicker(Random.Default)` for the screen's life.
  - **Changing content:** each tile keeps its face key in a `TileFlipState`. When the face showing is no longer among the tile's faces (its item was read, say), the tile snaps to the front without a flip.
  - **New items:** the first live state a tile has after it's composed (the first one that isn't null) is its baseline. A key that appears later is new. On a tile that's on screen while tiles move, the newest new item jumps the queue: the tile flips to it after `ticker.jumpDelayMs`, even while another tile is turning, and is marked `jumped`. A flip already running on that tile finishes first. Otherwise nothing happens until the tile next turns.
  - **Times** come from the frame clock (`withFrameMillis`), so tests drive everything through `mainClock`.
  - **The flip:** `rotationX` in the tile's `graphicsLayer`, with the default camera distance as `Modifier.turnstile` uses. The old face turns from 0° to 90°, then the new face from −90° to 0°, both from `tileFlipAngle` of the elapsed time.
  - **Animations off:** no timer at all. A tile shows the face after its front, if it has one: medium and wide tiles their newest item, large tiles their stack, small tiles their front.
  - **Paused:** the front only.
  - **What TalkBack hears:** after the count, the state description lists "<title>: <text>" for each item on the face showing or being turned to, joined by ", ".

- [ ] **Step 4: Run them again.**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, `StartScreenTest` and `StartTurnstileTest` included.

- [ ] **Step 5: Commit.**

```bash
git add feature/start app
git commit -m "Flip live tiles on the start screen"
```

---

### Task 9: Tap regions

**Files:**
- Create: `feature/start/src/main/kotlin/…/feature/start/live/TileTap.kt`
- Modify: `feature/start/src/main/kotlin/…/feature/start/StartViewModel.kt`, `StartScreen.kt`
- Test: `feature/start/src/test/kotlin/…/feature/start/live/TileTapTest.kt`, and new tests in `StartViewModelTest.kt` and `StartScreenTest.kt`

**Interfaces:**
- Consumes: `TileFace` (Task 7), `TileFlipState` (Task 8), `SourceRegistry` and `TileSource.open` (Task 5).
- Produces:

```kotlin
internal const val LABEL_STRIP_WP_PX = 40f  // the strip along a tile's bottom edge that holds its label

/** The item a tap at [y] opens, or null for the app (§5.5). */
internal fun tileTapTarget(face: TileFace, y: Float, heightPx: Float, labelStripPx: Float): TileItem?

// StartViewModel
fun open(tileId: Long, sourceBounds: Rect?, options: Bundle?, item: TileItem? = null)

// StartController
var launch: (Long, Rect?, TileItem?) -> Unit
```

- [ ] **Step 1: Write the failing tests.**

```kotlin
class TileTapTest {
    private val a = testItem("a"); private val b = testItem("b"); private val c = testItem("c")

    @Test fun theFrontFaceOpensTheApp() {
        assertNull(tileTapTarget(TileFace.Front, y = 10f, heightPx = 100f, labelStripPx = 30f))
    }

    @Test fun aContentFaceBodyOpensItsItemAndTheLabelStripTheApp() {
        assertEquals(a, tileTapTarget(TileFace.Item(a), y = 69f, heightPx = 100f, labelStripPx = 30f))
        assertNull(tileTapTarget(TileFace.Item(a), y = 71f, heightPx = 100f, labelStripPx = 30f))
    }

    @Test fun aStackSplitsItsBodyIntoThreeRows() {
        val stack = TileFace.Stack(listOf(a, b, c))
        assertEquals(listOf(a, b, c, null), listOf(10f, 35f, 60f, 80f).map { tileTapTarget(stack, it, 100f, 30f) })
        assertNull(tileTapTarget(TileFace.Stack(listOf(a)), 35f, 100f, 30f)) // no second row
    }
}
```

In `StartViewModelTest`:

```kotlin
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
```

In `StartScreenTest` (`cameraSource` comes from Task 7):

```kotlin
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
    compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(centerX, height - 5f)) }
    compose.waitUntil(5_000) { catalog.launched.isNotEmpty() }
    assertEquals(listOf(camera.key), catalog.launched)
}

@Test fun talkBackHearsTheItemAndCanOpenTheApp() {
    Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    cameraSource.itemState.value = listOf(testItem("a", source = cameraSource.id))
    show()
    compose.onNodeWithTag("tile-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 unread, Alex: see you at 6"))
    compose.onNodeWithTag("tile-1").performCustomAccessibilityActionWithLabel("open Camera")
    compose.waitUntil(5_000) { catalog.launched.isNotEmpty() }
    assertEquals(listOf(camera.key), catalog.launched)
}
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:start:testDebugUnitTest`
Expected: FAIL to compile, because `tileTapTarget` and the `item` parameter don't exist.

- [ ] **Step 3: Implement the tap regions.**
  - **`tileTapTarget`:**
    - the front face gives null;
    - `y >= heightPx − labelStripPx` (the strip) gives null;
    - an item face gives its item;
    - a stack gives the item in row `(y ÷ ((heightPx − labelStripPx) ÷ 3)).toInt()`, or null when there's no such row.

    A small tile has only its front, so a tap anywhere on it opens the app.
  - **`StartViewModel.open`:**
    1. In edit mode, select the tile.
    2. For a paused work profile, ask to unpause it.
    3. With an item, call `sources.source(item.sourceId)?.open(item)`.
    4. Otherwise, `catalog.launch` as before.
  - **`StartScreen`:**
    - The tap gesture hands the slot the up position's `y`. The slot works out `tileTapTarget(face showing, y, its height, LABEL_STRIP_WP_PX in px)` and passes the item through `controller.tap` to `launch`.
    - The flip-then-launch is unchanged, and hands the item to `viewModel.open`.
    - Edit mode still only selects.
  - **Semantics:**
    - `onClick` opens the face's first item: an item face's item, a stack's first, or the app on the front.
    - While a content face shows, a custom action "open <label>" opens the app.
    - A stack adds "open <title>" custom actions for its 2nd and 3rd items.
    - The label strip is 40 WP px, about 34 dp on the S24, so it's under the 48 dp touch target. The "open <label>" action and the front face, all of which opens the app, cover that.

- [ ] **Step 4: Run them again.**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add feature/start
git commit -m "Open the tapped message from a live tile"
```

---

### Task 10: Paused live tiles and keeping the listener awake

**Files:**
- Create:
  - `feature/notifications/src/main/kotlin/…/feature/notifications/NotificationAccess.kt`, `DisconnectLog.kt`, `LiveTilesHealth.kt`
  - `app/src/main/kotlin/…/home/LiveTilesBanner.kt`, `KeepAwakeGuide.kt`
- Modify:
  - `feature/notifications/src/main/kotlin/…/feature/notifications/NotificationsHost.kt`, `LiquiWinNotificationListener.kt`
  - `app/src/main/kotlin/…/AppGraph.kt`, `LiquiWinApp.kt`, `HomeActivity.kt`, `home/HomeRoot.kt`
- Test: `feature/notifications/src/test/kotlin/…/feature/notifications/DisconnectLogTest.kt`, `LiveTilesHealthTest.kt`, `NotificationAccessTest.kt`, and new tests in `app/src/test/kotlin/…/home/HomeRootTest.kt`

**Interfaces:**
- Consumes: `ListenerConnection`, `NotificationsHost` and `LiquiWinNotificationListener` (Task 4), `StartScreen`'s `liveTilesPaused` (Task 7), and `HomeAppBanner`'s look from stage 1.
- Produces:

```kotlin
class NotificationAccess(context: Context) {
    val component: ComponentName      // the listener
    fun isGranted(): Boolean
    fun openSettings(from: Context)   // the listener's own page, or the list where that page is missing
    fun requestRebind()               // only while access is granted
}

class DisconnectLog(prefs: SharedPreferences, now: () -> Long = System::currentTimeMillis) {
    fun record()                      // a disconnect within a minute of the last one counted is the same one
    fun keepsStopping(): Boolean      // 3 or more in the last 24 h
}

data class LiveTilesStatus(val paused: Boolean = false, val keepsStopping: Boolean = false)

class LiveTilesHealth(
    isGranted: () -> Boolean,
    connected: StateFlow<Boolean>,
    rebind: () -> Unit,
    log: DisconnectLog,
    isUnrestricted: () -> Boolean,
    scope: CoroutineScope,
) {
    val status: StateFlow<LiveTilesStatus>
    fun onResume()
    fun onDisconnected()
    companion object { const val REBIND_GRACE_MS = 2_000L }
}

// NotificationsHost gains, with a default so Task 4's TestHost compiles:
val liveTilesHealth: LiveTilesHealth? get() = null

// HomeDependencies gains, last, with defaults:
val liveTiles: LiveTilesHealth? = null, val onFixLiveTiles: () -> Unit = {}, val onOpenAppInfo: () -> Unit = {}
```

- [ ] **Step 1: Write the failing tests.**

```kotlin
@RunWith(AndroidJUnit4::class)
class DisconnectLogTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("listener_health", Context.MODE_PRIVATE)
    private var clock = 0L
    private val log = DisconnectLog(prefs) { clock }
    private val hour = 60 * 60 * 1000L
    private fun disconnectAt(time: Long) { clock = time; log.record() }

    @Test fun threeDisconnectsInADayMeanTilesKeepStopping() {
        disconnectAt(0); disconnectAt(hour)
        assertFalse(log.keepsStopping())
        disconnectAt(2 * hour)
        assertTrue(log.keepsStopping())
    }

    @Test fun disconnectsOlderThanADayDropOut() {
        disconnectAt(0); disconnectAt(hour); disconnectAt(2 * hour)
        clock = 24 * hour + 1
        assertFalse(log.keepsStopping())
    }

    @Test fun disconnectsWithinAMinuteCountOnce() {
        disconnectAt(0); disconnectAt(30_000); disconnectAt(59_000)
        assertFalse(log.keepsStopping())
    }

    @Test fun theCountSurvivesARestart() {
        disconnectAt(0); disconnectAt(hour); disconnectAt(2 * hour)
        assertTrue(DisconnectLog(prefs) { clock }.keepsStopping())
    }
}

@RunWith(AndroidJUnit4::class)
class LiveTilesHealthTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("listener_health", Context.MODE_PRIVATE)
    private var granted = true
    private var unrestricted = false
    private var rebinds = 0
    private val connected = MutableStateFlow(false)
    private fun TestScope.health() = LiveTilesHealth({ granted }, connected, { rebinds++ }, DisconnectLog(prefs) { currentTime }, { unrestricted }, backgroundScope)

    // Review Focus 4
    @Test fun withoutAccessTilesArePaused() = runTest {
        granted = false
        val health = health()
        health.onResume()
        advanceTimeBy(5_000)
        assertTrue(health.status.value.paused)
        assertEquals(0, rebinds)
    }

    @Test fun aGrantedListenerStillDisconnectedTwoSecondsAfterResumeIsReboundAndCounted() = runTest {
        val health = health()
        health.onResume()
        advanceTimeBy(1_900)
        assertEquals(0, rebinds)
        advanceTimeBy(200)
        assertEquals(1, rebinds)
        assertFalse(health.status.value.paused)
    }

    @Test fun aConnectedListenerIsLeftAlone() = runTest {
        val health = health()
        health.onResume()
        advanceTimeBy(1_000)
        connected.value = true
        advanceTimeBy(5_000)
        assertEquals(0, rebinds)
    }

    // Review Focus 4
    @Test fun aDisconnectAfterAccessIsRevokedNeitherRebindsNorCounts() = runTest {
        val health = health()
        granted = false
        repeat(3) { health.onDisconnected(); advanceTimeBy(61_000) }
        assertEquals(0, rebinds)
        assertTrue(health.status.value.paused)
        granted = true
        connected.value = true
        health.onResume()
        advanceTimeBy(3_000)
        assertEquals(LiveTilesStatus(), health.status.value)
    }

    @Test fun theGuideShowsAfterThreeDisconnectsUntilBatteryIsUnrestricted() = runTest {
        val health = health()
        repeat(2) { health.onDisconnected(); advanceTimeBy(61_000) }
        assertFalse(health.status.value.keepsStopping)
        health.onDisconnected()
        runCurrent()
        assertTrue(health.status.value.keepsStopping)
        assertEquals(3, rebinds)
        unrestricted = true
        connected.value = true
        health.onResume()
        advanceTimeBy(3_000)
        assertFalse(health.status.value.keepsStopping)
    }
}

@RunWith(AndroidJUnit4::class)
class NotificationAccessTest {
    @Test fun opensTheDetailPageForLiquiWin() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val access = NotificationAccess(context)
        access.openSettings(context)
        val opened = shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, opened.action)
        assertEquals(access.component.flattenToString(), opened.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME))
    }
}
```

In `HomeRootTest`, give `show()` a `liveTiles: LiveTilesHealth? = null` parameter, and pass it to `HomeDependencies` with `onFixLiveTiles = { fixCalls++ }` and `onOpenAppInfo = { appInfoCalls++ }`:

```kotlin
private var fixCalls = 0
private var appInfoCalls = 0
private val prefs get() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("listener_health", Context.MODE_PRIVATE)
private fun health(granted: Boolean, log: DisconnectLog = DisconnectLog(prefs)) =
    LiveTilesHealth({ granted }, MutableStateFlow(true), {}, log, { false }, CoroutineScope(Dispatchers.Main))

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
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:notifications:testDebugUnitTest :app:testDebugUnitTest --tests "*HomeRootTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Implement the health checks, the banner and the guide.**
  - **`NotificationAccess`:**
    - **`isGranted`:** `NotificationManager.isNotificationListenerAccessGranted(component)`.
    - **`openSettings`:** on API 30+, `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` with `EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME` set to `component.flattenToString()`. On API 29, or if the detail page throws `ActivityNotFoundException`, `ACTION_NOTIFICATION_LISTENER_SETTINGS`. Always add `FLAG_ACTIVITY_NEW_TASK`.
    - **`requestRebind`:** `NotificationListenerService.requestRebind(component)` while granted, in a `try`/`catch` that logs.
  - **`DisconnectLog`:** keeps the times as comma-separated longs under "disconnects".
    - `record` first drops times older than 24 h, then ignores a disconnect within 60 s of the last one kept.
    - The prefs file "listener_health" isn't backed up, because the backup rules include only `layout.db` and the datastore.
  - **`LiveTilesHealth`:** every call does its work in `scope`, never on the caller's thread.
    - **Status**, recomputed by each call:
      - `paused` = access isn't granted;
      - `keepsStopping` = granted, and `log.keepsStopping()`, and not `isUnrestricted()`.
    - **`onResume`:**
      1. Cancel the previous resume's check.
      2. Recompute the status.
      3. If granted, wait `REBIND_GRACE_MS`. If the listener is still not `connected` and access is still granted, `rebind()`, `log.record()` and recompute.
    - **`onDisconnected`:** if granted, `rebind()` and `log.record()`. Recompute either way.
  - **The listener:** `onListenerDisconnected` also calls `(application as? NotificationsHost)?.liveTilesHealth?.onDisconnected()`.
  - **`AppGraph`:**
    - `val notificationAccess = NotificationAccess(app)`;
    - `val liveTilesHealth by lazy { … }`, built from `notificationAccess::isGranted`, `ListenerConnection.connected`, `notificationAccess::requestRebind`, `DisconnectLog(app.getSharedPreferences("listener_health", MODE_PRIVATE))`, `{ powerManager.isIgnoringBatteryOptimizations(app.packageName) }` (true when LiquiWin is Unrestricted) and the `io` scope.
  - **`LiquiWinApp`:** overrides `liveTilesHealth` like `notificationIntake`, null in safe mode.
  - **`LiveTilesBanner(text: String, onFix: () -> Unit)`:** `HomeAppBanner`'s look (chrome background, side margin, the small style, 12 WP px start padding).
    - The whole banner is one button: `clickable(role = Role.Button)`, at least 48 dp tall.
    - It shows one `Text` with an `AnnotatedString`: "<text> · " in white, then "fix" in the accent colour.
  - **`KeepAwakeGuide(samsung: Boolean, onOpenAppInfo: () -> Unit)`:** a full-screen page over start. It has `PageTitle("keep tiles awake")`, then, in the body style:
    - "Android stopped live tiles 3 times in the last day. To keep them running, open LiquiWin's App info and choose Battery → Unrestricted."
    - On Samsung only: "On a Galaxy you can also go to Settings → Battery → Background usage limits → Never sleeping apps and add LiquiWin. The wording varies with the One UI version."
    - Then an "open app info" button with a 48 dp target.
  - **`HomeRoot`:**
    - Its ON_RESUME also calls `deps.liveTiles?.onResume()`.
    - It collects `deps.liveTiles?.status`. In the banner slot, below `HomeAppBanner`, it shows `LiveTilesBanner`:
      - "live tiles paused", whose fix is `deps.onFixLiveTiles`;
      - otherwise "live tiles keep stopping", whose fix opens the guide.
    - It passes `liveTilesPaused = status.paused` to `StartScreen`.
    - The guide gets `samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)`, and closes on Back and on any Home press.
  - **`HomeActivity`:** passes these to `HomeDependencies`:
    - `liveTiles = graph.liveTilesHealth`;
    - `onFixLiveTiles = { graph.notificationAccess.openSettings(this) }`;
    - `onOpenAppInfo`, which opens `ACTION_APPLICATION_DETAILS_SETTINGS` for `package:<packageName>`.

- [ ] **Step 4: Run them again.**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add feature/notifications app
git commit -m "Show when live tiles are paused and how to keep them awake"
```

---

### Task 11: The notifier debug app

**Files:**
- Modify: `settings.gradle.kts` (include `:tools:notifier`)
- Create:
  - `tools/notifier/build.gradle.kts`, `tools/notifier/src/main/AndroidManifest.xml`, `tools/notifier/src/test/resources/robolectric.properties` (`sdk=36`)
  - `tools/notifier/src/main/kotlin/…/tools/notifier/Chats.kt`, `NotifierActivity.kt`, `PostReceiver.kt`, `ReplyReceiver.kt`
- Test: `tools/notifier/src/test/kotlin/…/tools/notifier/ChatsTest.kt`

**Interfaces:**
- Produces:

```kotlin
/** Posts WhatsApp-like and Instagram-like notifications (spec §10). Keeps no state: each post starts from the one showing. */
object Chats {
    fun receive(context: Context, chat: String, text: String) // chat: "alex" or "family"
    fun reply(context: Context, chat: String, text: String)   // your message, then the echo "you said: <text>"
    fun postPhoto(context: Context)                           // Instagram-like: a big picture
    fun postSnap(context: Context)                            // Snapchat-like: plain, no actions
}
```

  - The adb trigger, which the Galaxy check uses:

```bash
adb shell am broadcast -a io.github.b1tsagar.liquiwin.tools.notifier.POST -n io.github.b1tsagar.liquiwin.tools.notifier/.PostReceiver --es chat alex --es text "see you at 6"
```

    `chat` is `alex`, `family`, `photo` or `snap`.

- [ ] **Step 1: Create the module.** `tools/notifier/build.gradle.kts` applies `android.application` with namespace and applicationId `io.github.b1tsagar.liquiwin.tools.notifier`, the catalog's SDK levels and Java 17. Its only dependency is `implementation(libs.androidx.core.ktx)`, plus junit, robolectric and androidx-test-junit for tests. It's a debug tool: nothing builds a release of it, and no LiquiWin module depends on it. The manifest has:
  - `POST_NOTIFICATIONS`;
  - the application, labelled "notifier", with `@android:drawable/sym_action_chat` as its icon;
  - `NotifierActivity`, exported, as MAIN/LAUNCHER, with `android:noHistory="true"` so every open starts fresh;
  - `PostReceiver`, exported, with `android:permission="android.permission.DUMP"` (only adb's shell holds it) and an intent filter for `io.github.b1tsagar.liquiwin.tools.notifier.POST`;
  - `ReplyReceiver`, not exported.

- [ ] **Step 2: Write the failing test.**

```kotlin
@RunWith(AndroidJUnit4::class)
class ChatsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private fun showing(id: Int) = manager.activeNotifications.single { it.id == id }.notification
    private fun style(id: Int) = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(showing(id))!!
    private fun texts(id: Int) = style(id).messages.map { it.text.toString() }

    @Test fun aReceivedMessageIsPostedAsAConversationWithAReplyAction() {
        Chats.receive(context, "alex", "see you at 6")
        assertEquals("alex", showing(1).shortcutId)
        assertEquals(listOf("see you at 6"), texts(1))
        assertEquals("Alex", style(1).messages.single().person?.name)
        val reply = showing(1).actions.single { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }
        assertEquals("reply", reply.remoteInputs.single().resultKey)
    }

    @Test fun aGroupMessageNamesTheGroup() {
        Chats.receive(context, "family", "dinner at 7")
        assertTrue(style(2).isGroupConversation)
        assertEquals("Family", style(2).conversationTitle)
    }

    @Test fun aReplyAddsYourMessageThenTheEcho() {
        Chats.receive(context, "alex", "see you at 6")
        Chats.reply(context, "alex", "on my way")
        assertEquals(listOf("see you at 6", "on my way", "you said: on my way"), texts(1))
        assertNull(style(1).messages[1].person) // yours
    }

    @Test fun chatsKeepTheirLastFiveMessages() {
        repeat(7) { Chats.receive(context, "alex", "message $it") }
        assertEquals((2..6).map { "message $it" }, texts(1))
    }

    @Test fun onlyTheShellCanTriggerPosts() {
        val receiver = context.packageManager.getReceiverInfo(ComponentName(context, PostReceiver::class.java), 0)
        assertEquals("android.permission.DUMP", receiver.permission)
    }
}
```

- [ ] **Step 3: Run it and watch it fail.**

Run: `./gradlew :tools:notifier:testDebugUnitTest`
Expected: FAIL to compile.

- [ ] **Step 4: Implement the notifier.**
  - **Ids:** notification ids are alex 1, family 2, photo 3 and snap 4, all on one channel, "chats".
  - **No state:** each post reads the chat's notification showing now, through `activeNotifications` and `extractMessagingStyleFromNotification`, adds the message, and keeps the last 5.
  - **People:**
    - Alex (key `alex`), Mum (`mum`) and Dad (`dad`);
    - "family" is a group titled "Family", whose received messages come from Mum;
    - each avatar is a 128 px circle in the person's own colour, with their initial.
  - **Shortcuts:** each chat pushes a long-lived dynamic shortcut whose id is the chat (`ShortcutManagerCompat.pushDynamicShortcut`), and sets it as the notification's shortcut. That also exercises LiquiWin's shortcut fallback.
  - **Content intent:** opens `NotifierActivity` with the chat, which then shows "chat <chat>", for example "chat alex", and cancels that chat's notification, as messaging apps do.
  - **Reply action:** "Reply", with `SEMANTIC_ACTION_REPLY` and `RemoteInput` "reply". Its `FLAG_MUTABLE` PendingIntent is an explicit broadcast to `ReplyReceiver`, which calls `Chats.reply` with the text.
  - **Photo:** title "Instagram", text "alex liked your photo", and `BigPictureStyle` with a generated 1080 × 720 picture.
  - **Snap:** title "Alex", text "sent you a Snap".
  - **`PostReceiver`:** maps `chat` to `receive`, `postPhoto` or `postSnap`, with `text` defaulting to "hi".
  - **`NotifierActivity`:** plain Views, with no Compose. On API 33+ it asks for `POST_NOTIFICATIONS`, and the owner taps Allow. Its buttons "alex", "family", "photo" and "snap" post a sample of each.

- [ ] **Step 5: Run it again.**

Run: `./gradlew :tools:notifier:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add settings.gradle.kts tools
git commit -m "Add the notifier debug app"
```

---

### Task 12: Part 1 check on the Galaxy

**Files:**
- Modify: `docs/reference/windows-phone-ui-reference.md` (record the values chosen)

- [ ] **Step 1: Run the whole suite and build both apps.**

Run: `./gradlew testDebugUnitTest :app:assembleRelease :tools:notifier:assembleDebug`
Expected: BUILD SUCCESSFUL, with `app/build/outputs/apk/release/app-release.apk` and `tools/notifier/build/outputs/apk/debug/notifier-debug.apk`.

- [ ] **Step 2: Install both**, with the handoff's helper (`a`, `nodes`, `spot`, `front`) set up in Git Bash.

```bash
a install -r app/build/outputs/apk/release/app-release.apk
a install -r tools/notifier/build/outputs/apk/debug/notifier-debug.apk
```

Expected: `Success` twice.

- [ ] **Step 3: The owner grants access.** These are the owner's steps, not the agent's:
  1. Open "notifier" and tap Allow for notifications.
  2. On start, the agent taps "fix" in the "live tiles paused · fix" banner. The owner then switches LiquiWin on in Android's notification access page and confirms.

  Then the agent resets to start with the two Home intents.

- [ ] **Step 4: Run the scripted check.** Ask the owner to keep their hands off the phone. Before every step, check with `front` that LiquiWin is in front, and stop if it isn't. Record the timed steps with `screenrecord --bit-rate 4000000`, log `/proc/uptime` right after starting it, and send the owner the frames that show each result. Post with:

```bash
post() { a shell am broadcast -a io.github.b1tsagar.liquiwin.tools.notifier.POST -n io.github.b1tsagar.liquiwin.tools.notifier/.PostReceiver --es chat "$1" --es text "${2:-hi}"; }
```

  1. Pin "notifier" from the app list. It lands as a medium tile.
  2. Log `/proc/uptime`, then `post alex "see you at 6"`. The tile shows the message within 1 s of the broadcast (§9), with count 1.
  3. `post alex "running late"`. The count is 2, and the tile flips to the new message.
  4. `post family "dinner at 7"`. The message reads "Mum · Family".
  5. Resize the tile to wide, then `post photo`. The photo fills the tile under its caption. Resize to large: three messages are stacked.
  6. Back at medium, tap the tile's body while it shows an alex message. The notifier opens on "chat alex" and cancels that notification. Press Home: the alex messages no longer count.
  7. Tap the label strip. The notifier opens on its buttons, with no chat.
  8. `post snap`. The tile shows a plain message with no image.
  9. Pin three more apps, post to two chats, and record 30 s. One tile moves at a time, and a tile that just moved sits out at least the next three ticks.

  Note anything that fails as a follow-up fix before closing this part.

- [ ] **Step 5: Paused tiles.** Ask the owner to switch LiquiWin's notification access off.
  - Back on start, the tiles show their fronts with the pause mark and no counts.
  - The "live tiles paused · fix" banner shows, and "fix" opens LiquiWin's own access page.
  - Then the owner switches access back on. After the next resume, the banner is gone and the counts are back.

- [ ] **Step 6: Real apps, with the owner.** The owner pins the apps they use among WhatsApp, Instagram, Snapchat, Messenger, Telegram and Gmail, and uses the phone as usual. Note every real notification that looks wrong on its tile. Each one gets a fixture shaped like it and a fix, or a note for part 2. If the owner has a work profile with a chat app, its tile counts only that profile's messages, and a tap opens the work app (Review Focus 5).

- [ ] **Step 7: Record the chosen values.** Add a "Values chosen in stage 2, part 1" list to the reference doc, after "Values added after stage 1". It covers:
  - the faces: medium with a 32 WP px avatar and 2 lines; wide with a 64 WP px avatar and 3 lines, or a photo under a 20 % scrim; large with three rows and 48 WP px avatars; the count inset 12 / 8; the pause mark at 16;
  - the label strip at 40 WP px;
  - the flip: 850 ms with cubic ease-out, faces swapping at 90°;
  - the timer: first tick 2.5 s, gaps 0.1–3 s in 100 ms steps, a rest of 3 ticks, one flip at a time, jumps at most once per 600 ms;
  - the rebind grace of 2 s, disconnects counted at most once a minute, and 3 in 24 h for the guide;
  - images at 256 / 720 px, WebP at quality 85; text cut at 200 / 2,000 characters;
  - anything changed during Steps 4–6.

```bash
git add docs/reference/windows-phone-ui-reference.md
git commit -m "Record the values chosen in stage 2's first part"
```

- [ ] **Step 8: A day later, check success criterion 7.** Run `a shell dumpsys notification | grep -i liquiwin`. LiquiWin's listener should be among the live listeners. Then ask the owner whether a "live tiles keep stopping" banner showed. If it did, follow the guide and check again a day later. Add the result to the reference doc's part 1 list, then:

```bash
git add docs/reference/windows-phone-ui-reference.md
git commit -m "Record the day-long live tile check"
```

  Unpin the notifier's tile, and leave the app installed for part 2.

