# LiquiWin Stage 2: The Chat Window — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The four calls the owner made after using part 1 on the Galaxy. Tiles flip more flatly. Every message a tile shows gets a chat button. The button opens a chat window with the whole chat, a scrollbar, and quick reply when the app takes replies. A chat can be hidden from tiles there, and shown again from edit mode.

**Architecture:**
- `:core:data` stores hidden chats in `cache.db` and reads whole conversations.
- `TileSource` gains chats, replies and hiding. `AppNotificationSource` implements them on top of the store and the live action cache.
- `:feature:start` draws the chat buttons, routes their taps, and lists hidden chats in edit mode.
- The new `:feature:hub` holds `ChatViewModel` and `ChatWindow`. `:app` shows the window over the pager.
- Pure logic is unit-tested on the JVM. Room and Compose are tested under Robolectric.

**Tech Stack:** As in part 1, with no new dependencies. `RemoteInput` is the platform's (`android.app.RemoteInput`). The text box is foundation's `BasicTextField` with a `TextFieldState`.

**Spec:** [docs/superpowers/specs/2026-10-27-liquiwin-launcher-start-screen-design.md](../specs/2026-10-27-liquiwin-launcher-start-screen-design.md). This plan covers the owner's calls after part 1 (commit 9fbdbc2): §5.1 (the `FLIP` template and the chat button), §5.5 (taps), §5.7 (the chat window and quick reply), §5.8 (hidden chats), §4.3 (the hidden chats corner) and §3.3 (`hidden_conversation`).
- Windows Phone values come from [docs/reference/windows-phone-ui-reference.md](../../reference/windows-phone-ui-reference.md).
- How to reach and drive the phone is in [the stage 2 handoff](../handoff/2026-10-28-liquiwin-stage-2-handoff.md).

The work continues on `stage-2-tiles-come-alive`, after part 1's last commit, so it lands with part 1 unless the owner lands part 1 first. The rest of stage 2 (combined tiles, the tile editor, hub pages and the other templates) gets its own plan once this one lands.

## Global Constraints

- Android only: `minSdk 29`, `targetSdk 36`, `compileSdk 37`. The test phone is the owner's Galaxy S24, reached as the handoff's "Phone testing" section describes.
- Toolchain and versions are part 1's. Every version lives in `gradle/libs.versions.toml`. This part adds no dependencies.
- Package root: `io.github.b1tsagar.liquiwin`. The new module's namespace is `io.github.b1tsagar.liquiwin.feature.hub`.
- "**No `INTERNET` permission in sub-project 1.** Notification content can't leave the phone, because the app has no network access at all." (§2)
- Logs never include a chat's names, keys, texts or images.
- "**Sending.** Only on the send button." (§5.7) The keyboard's enter key adds a new line.
- "**Read.** Opening the window marks the chat read (§5.3), in LiquiWin only, so no read receipt is sent." LiquiWin never triggers apps' own mark-as-read actions.
- Hidden chats are stored in "`hidden_conversation` in `cache.db` (§3.3), which isn't backed up, because a chat's key can be a phone number." `layout.db` doesn't change.
- "If `cache.db` is ever rebuilt after a failed migration, hidden chats show again." (§5.8) Part 1's destructive fallback already does this.
- No feature module depends on another (§3.1). A chat button's tap leaves `:feature:start` as a callback, which `:app` routes to `:feature:hub`.
- Touch areas are at least 48 dp. The chat button has "a 48 dp touch area" (§5.1).
- "WP px" are Windows Phone pixels: dp = WP px × (screen width in dp ÷ 480).
- Headers and buttons are lowercase. Message texts, names and the failure line keep their own case.
- No unofficial APIs. (§1.5)
- Motion follows Android's remove-animations setting, as in part 1. The "reduced" setting is stage 3's.
- Tests use JUnit 4. Robolectric runs at `sdk=36`, set in each module's `src/test/resources/robolectric.properties`.

## Review Focus

1. **An app that takes a reply but never re-posts it, re-posts it re-worded, or cancels its notification.** "sending…" never lasts past 10 s, a message that comes back shows once, and the window stays usable. Tests in Tasks 5 and 6.
2. **The reply action going away.** This covers a cancelled `PendingIntent`, a launcher restart, a removed notification and a double tap on send. The draft stays, the failure line shows, and nothing is sent twice. Tests in Tasks 3 and 5.
3. **A chat changing while its window is open.** New messages show and are read at once. A chat whose messages are all cleaned away closes its window. Hiding takes the chat off tiles straight away. Tests in Tasks 3 and 5.
4. **The same conversation key in two apps or two profiles.** Hiding WhatsApp's "family" never hides Telegram's, or the work profile's. Tests in Tasks 2 and 3.
5. **TalkBack.** The tiles under the window are hidden from it, and every button is labelled. The window and the hidden-chats list both close without Back. Tests in Tasks 6 and 7.

---

## File structure

| Path | Module | Adds |
|---|---|---|
| `core/design/` | `:core:design` | `tileFlipCameraDistance`, `Swivel`, the Chat and ChatOff icons, and `rememberImage` (moved from start) |
| `core/data/` | `:core:data` | `HiddenConversationEntity`, the conversation and hidden-chat queries, `cache.db` version 3 |
| `core/tiles/` | `:core:tiles` | `Chat`, `ChatMessage`, `HiddenChat`, `ReplyResult`, `TileSource`'s chat members, `TileState.hiddenChats` |
| `core/testing/` | `:core:testing` | The fake source's chat, replies and hidden chats; `testMessage()` and `testChat()` |
| `feature/notifications/` | `:feature:notifications` | Chats, replies and hiding in `AppNotificationSource`; `ItemLauncher.reply` |
| `feature/start/` | `:feature:start` | The flatter flip, chat buttons and their tap areas, the hidden chats corner and `HiddenChatsList` |
| `feature/hub/` | `:feature:hub` (new) | `ChatViewModel`, `ChatWindow`, `scrollThumb` and `timeLabel` |
| `app/` | `:app` | The window over the pager, closed by Home |
| `docs/reference/` | — | The values chosen |

As in part 1, `…/` stands for the module's package directory under `src/main/kotlin/` or `src/test/kotlin/`.

`:feature:hub` depends on `:core:tiles`, which exposes `:core:data` and `:core:design`, the same way `:feature:start` does. `:app` adds it.

**Not in this part:**
- The rest of stage 2, in the next plan:
  - combined tiles and the tile editor (§4.4);
  - hub pages (§5.6), with their chat buttons, swipe and mark all read;
  - the `PEEK`, `CYCLE`, `ICONIC` and `STATIC` templates;
  - the combined front face, and the app icon on combined faces;
  - `TileSource.label` and `icon`;
  - "show previews" and "keep read items";
  - success criterion 2, and the hub half of criterion 3.
- The Action Center and its chat buttons (stage 5).
- The Glass morph of the window (§4.8, stage 4).
- Stage 3: tile pace, "reduced" motion, and the Roborazzi matrix, which will include the chat window. Until then the window's look is checked on the Galaxy.
- Stage 6: the ignore list (§7.2) and "wipe history".

## Choices this plan makes

1. **Spec §3.2's `reply(item, text): ReplyResult`, as written.** A cancelled `PendingIntent` counts as `ActionGone`, since either way the app won't take this reply.
2. **`TileSource.chat(conversationKey)`** gives the whole chat as a flow. Each `ChatMessage` carries its `TileItem`, so the window reuses `open`, `reply` and `markRead`.
3. **A reply goes to the newest message whose notification still has a reply action** (`Chat.replyTo`). Any of a conversation's live notifications takes a reply, and the newest is the one most likely still showing.
4. **Once shown, the text box stays until the window closes.** If the reply action goes while you type, the failure line explains it, rather than the box vanishing under your fingers.
5. **"sending…" ends when one of your messages comes back.** Each new message of yours replaces the oldest reply still pending, whatever its text, because apps re-word what they echo. After 10 s with nothing back, the bubble stays and shows its time instead.
6. **Messages that arrive while the window is open are read too**, because they're on screen.
7. **The header.** A one-to-one chat shows the newest incoming avatar, and a group shows none. The title is the group's name, else the newest incoming sender's name, else the app's label.
8. **Where the chat button sits.** On medium and wide faces it's at the label strip's end, where the front face shows its count. Its 48 dp touch area is the tile's bottom-right corner, so the rest of the strip still opens the app. On a large tile it's at each row's bottom right.
9. **Hand-drawn glyphs**, so nothing is downloaded. Chat is a speech bubble, and ChatOff is the bubble with a slash. **send** is a text button, like the header's.
10. **The flatter flip** keeps part 1's timing and only moves the camera back.
11. **The window lives in `:feature:hub`**, the module §3.1 gives "the quick-reply window". One `ChatViewModel` serves the home screen, and `:app` puts the window over the pager.
12. **Hidden chats reach edit mode through `TileState`**, next to the rotation and count that already come from the tile's sources.
13. **Hidden chats are listed newest hidden first.**
14. **open in app** runs §5.5's chain on the newest message. It closes the window at once, with no swivel, since the app is opening over it.
15. **`cache.db` version 3** uses an auto-migration and keeps part 1's destructive fallback, which §5.8 accepts.
16. **The swivel turns around the window's centre, with Tilt's camera** (12 × density). The reference gives the swivel's angles and timing, but no camera.
17. **The draft lives in a `TextFieldState` in the view model**, so typing never waits on a flow, and the text survives a failed send.

**Downloads to approve:** none.

---

### Task 1: The flatter flip

**Files:**
- Modify: `core/design/src/main/kotlin/…/core/design/motion/TileFlip.kt`, `feature/start/src/main/kotlin/…/feature/start/StartScreen.kt:394-400` (TileSlot's `graphicsLayer`)
- Test: `core/design/src/test/kotlin/…/core/design/motion/TileFlipTest.kt`

**Interfaces:**
- Consumes: part 1's `tileFlipAngle`, and the flip in TileSlot's `graphicsLayer`.
- Produces: `fun tileFlipCameraDistance(heightPx: Float): Float`, the `graphicsLayer` `cameraDistance` for a tile `heightPx` tall.

- [ ] **Step 1: Write the failing test.** Add to `TileFlipTest`:

```kotlin
    // §5.1: at any point in the turn, the nearer edge looks at most 5% wider than the farther one
    @Test fun aTileIsSeenFromFarEnoughThatItsNearerEdgeLooksAtMost5PercentWider() {
        for (height in listOf(150f, 638f, 1_400f)) {
            val cameraPx = tileFlipCameraDistance(height) * 72f // Skia's camera units
            val ratio = (cameraPx + height / 2) / (cameraPx - height / 2) // edge-on, where the widths differ most
            assertTrue("$height px tall: $ratio", ratio in 1.04f..1.05f)
        }
    }
```

- [ ] **Step 2: Run it and watch it fail.**

Run: `./gradlew :core:design:testDebugUnitTest --tests "*TileFlipTest"`
Expected: FAIL to compile, because `tileFlipCameraDistance` doesn't exist.

- [ ] **Step 3: Move the camera back.**
  - `tileFlipCameraDistance(heightPx)` is `21f * heightPx / 72f`. Its comment says why: `graphicsLayer` takes `cameraDistance` in Skia's camera units of 72 px, whatever the density, so this puts the camera 21 tile heights away. Edge-on, the nearer edge is then 21.5 / 20.5 ≈ 1.049 times as wide as the farther one.
  - In TileSlot's `graphicsLayer`, set `cameraDistance = tileFlipCameraDistance(size.height)` inside the same `if` as `rotationX`.

- [ ] **Step 4: Run the tests again.**

Run: `./gradlew :core:design:testDebugUnitTest :feature:start:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add core/design feature/start
git commit -m "Flip tiles as if seen from further away"
```

---

### Task 2: Hidden chats and whole conversations in `cache.db`

**Files:**
- Create: `core/data/src/main/kotlin/…/core/data/db/cache/HiddenConversationEntity.kt`
- Modify: `core/data/src/main/kotlin/…/core/data/db/cache/NotificationDao.kt`, `CacheDatabase.kt`
- Test: `core/data/src/test/kotlin/…/core/data/db/cache/NotificationDaoTest.kt`, `CacheDatabaseMigrationTest.kt`
- Generated: `core/data/schemas/io.github.b1tsagar.liquiwin.core.data.db.cache.CacheDatabase/3.json` (commit it)

**Interfaces:**
- Consumes: part 1's `NotificationDao`, `StoredItem` and `CacheDatabase` version 2.
- Produces:

```kotlin
@Entity(tableName = "hidden_conversation", primaryKeys = ["source_id", "conversation_key"])
data class HiddenConversationEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "conversation_key") val conversationKey: String,
    val name: String,                                 // the chat's title when hidden (§5.8)
    @ColumnInfo(name = "hidden_at") val hiddenAt: Long,
)

// NotificationDao
abstract fun observeItems(sourceId: String): Flow<List<StoredItem>>  // now leaves out hidden chats
/** Oldest first, with your own, removed and hidden messages. */
abstract fun observeConversation(sourceId: String, conversationKey: String): Flow<List<StoredItem>>
abstract fun observeHidden(sourceId: String): Flow<List<HiddenConversationEntity>>  // newest hidden first
@Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun hide(chat: HiddenConversationEntity)
abstract suspend fun showAgain(sourceId: String, conversationKey: String)
```

  - `CacheDatabase` version 3.

- [ ] **Step 1: Write the failing tests.** Add to `NotificationDaoTest`:

```kotlin
    private fun hidden(conversation: String, name: String, at: Long, source: String = whatsapp) =
        HiddenConversationEntity(source, conversation, name, at)

    // Review Focus 4
    @Test fun aHiddenChatLeavesItsSourcesItemsUntilShownAgain() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100)))
        dao.replaceNotification("n2", listOf(item("f", 200, conversation = "family", notification = "n2")))
        dao.replaceNotification("t1", listOf(item("t", 300, conversation = "family", notification = "t1", source = telegram)))
        dao.hide(hidden("family", "Family", at = 400))
        assertEquals(listOf("a"), dao.observeItems(whatsapp).first().map { it.item.key })
        assertEquals(listOf("t"), unread(telegram)) // the same key in another app stays
        assertEquals(listOf("f"), dao.observeConversation(whatsapp, "family").first().map { it.item.key }) // its window still opens
        dao.showAgain(whatsapp, "family")
        assertEquals(listOf("f", "a"), unread())
    }

    @Test fun aConversationIsOldestFirstWithYoursAndRemovedOnes() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100), item("me", 150, self = true)))
        dao.markRemoved("n1", now = 300) // reads alex up to 150
        dao.replaceNotification("n2", listOf(item("b", 200, notification = "n2")))
        dao.replaceNotification("n3", listOf(item("s", 120, conversation = "sam", notification = "n3")))
        val chat = dao.observeConversation(whatsapp, "alex").first()
        assertEquals(listOf("a", "me", "b"), chat.map { it.item.key })
        assertEquals(listOf(false, false, true), chat.map { it.unread })
        assertEquals(300L, chat.first().item.removedAt)
    }

    @Test fun hiddenChatsAreNewestFirstAndHidingAgainRenamesThem() = runTest {
        dao.hide(hidden("alex", "Alex", at = 100))
        dao.hide(hidden("family", "Family", at = 200))
        dao.hide(hidden("family", "Family", at = 50, source = telegram))
        assertEquals(listOf("Family", "Alex"), dao.observeHidden(whatsapp).first().map { it.name })
        dao.hide(hidden("alex", "Alex B", at = 300))
        assertEquals(listOf("Alex B", "Family"), dao.observeHidden(whatsapp).first().map { it.name })
    }
```

  In `CacheDatabaseMigrationTest`, turn `createVersion1(file)` into `createVersion(version: Int, file: File, rows: SQLiteDatabase.() -> Unit)`. It reads `schemas/…/$version.json`, runs `rows` after the setup queries, and sets `db.version = version`. The version 1 test passes its `app_entry` insert as `rows`. Then add:

```kotlin
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
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*NotificationDaoTest" --tests "*CacheDatabaseMigrationTest"`
Expected: FAIL to compile, because `HiddenConversationEntity` and the new queries don't exist.

- [ ] **Step 3: Implement the table and the queries.**
  - **`observeItems`** adds `AND NOT EXISTS (SELECT 1 FROM hidden_conversation h WHERE h.source_id = i.source_id AND h.conversation_key = i.conversation_key)` to its `WHERE`.
  - **`observeConversation`** uses `observeItems`' select and `unread` column, filtered on `i.source_id` and `i.conversation_key`. It has no hidden filter, and it orders by `i.posted_at, i.key`.
  - **`observeHidden`** orders by `hidden_at DESC, conversation_key`.
  - **`showAgain`** deletes the row with both keys.
  - **`cleanUp`** leaves `hidden_conversation` alone: hidden chats are "kept until shown again" (§3.3).
  - **`CacheDatabase`** adds the entity, `version = 3`, and `AutoMigration(from = 2, to = 3)` after part 1's.

- [ ] **Step 4: Run them again.**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS, and `core/data/schemas/…CacheDatabase/3.json` exists.

- [ ] **Step 5: Commit.**

```bash
git add core/data
git commit -m "Store hidden chats and read whole conversations"
```

---

### Task 3: Chats through tile sources

**Files:**
- Create: `core/tiles/src/main/kotlin/…/core/tiles/source/Chat.kt`
- Modify:
  - `core/tiles/src/main/kotlin/…/core/tiles/source/TileSource.kt`, `TileState.kt`
  - `core/testing/src/main/kotlin/…/core/testing/FakeTileSource.kt`
  - `feature/notifications/src/main/kotlin/…/feature/notifications/AppNotificationSource.kt`, `ItemLauncher.kt`
- Test: `core/tiles/src/test/kotlin/…/core/tiles/source/TileStateTest.kt`, `feature/notifications/src/test/kotlin/…/feature/notifications/AppNotificationSourceTest.kt`, `AndroidItemLauncherTest.kt` (new)

**Interfaces:**
- Consumes:
  - Task 2's DAO members and `HiddenConversationEntity`;
  - part 1's `LiveActionCache` (`put(key, LiveActions(contentIntent, reply: Notification.Action?))`, `remove(key)`), `AppCatalog` and `ItemLauncher`.
- Produces:

```kotlin
// core/tiles …/source/Chat.kt
/** A message in a chat (§5.7). [sender] is the name alone, without a group's title. */
data class ChatMessage(val item: TileItem, val sender: String, val isSelf: Boolean)

/** A chat's stored messages, oldest first (§5.7). */
data class Chat(
    val conversationKey: String,
    val title: String,
    val avatar: ImageRef?,
    val isGroup: Boolean,
    val messages: List<ChatMessage>,
) {
    /** The newest message whose notification takes replies right now. */
    val replyTo: TileItem? get() = messages.lastOrNull { it.item.canReply }?.item
}

/** A chat hidden from tiles (§5.8), under the name it had when hidden. */
data class HiddenChat(val sourceId: SourceId, val conversationKey: String, val name: String)

// core/tiles …/source/TileSource.kt
sealed interface ReplyResult {
    data object Sent : ReplyResult
    data object ActionGone : ReplyResult // the app withdrew its reply action
}

interface TileSource {
    val id: SourceId
    val items: Flow<List<TileItem>> // newest first, without hidden chats
    val unreadCount: Flow<Int> // without hidden chats
    val hiddenChats: Flow<List<HiddenChat>> // newest hidden first
    suspend fun open(item: TileItem?) // null opens the app
    suspend fun markRead(items: List<TileItem>) // local read state only
    fun chat(conversationKey: String): Flow<Chat?> // null while nothing of it is stored
    suspend fun reply(item: TileItem, text: String): ReplyResult // §5.7
    suspend fun hide(chat: Chat) // §5.8
    suspend fun showAgain(conversationKey: String)
}

data class TileState(val rotation: List<TileItem>, val unread: Int, val hiddenChats: List<HiddenChat> = emptyList())

// core/testing
class FakeTileSource(override val id: SourceId) : TileSource {
    // Part 1's itemState, opened and read, plus:
    val chatState = MutableStateFlow<Chat?>(null) // chat(key) gives it while its key matches
    val hiddenState = MutableStateFlow<List<HiddenChat>>(emptyList())
    val replies = mutableListOf<Pair<TileItem, String>>()
    var replyResult: ReplyResult = ReplyResult.Sent
    var replyGate: CompletableDeferred<Unit>? = null // when set, reply() records, then waits for it
}
fun testItem(/* part 1's parameters, then */ canReply: Boolean = false): TileItem
/** A message in the chat "alex". */
fun testMessage(
    key: String, text: String? = "see you at 6", at: Long = 0, self: Boolean = false,
    sender: String = if (self) "You" else "Alex", canReply: Boolean = true,
    source: SourceId = SourceId("app:com.example.chat@0"),
): ChatMessage
fun testChat(vararg messages: ChatMessage, title: String = "Alex", isGroup: Boolean = false): Chat // key "alex"

// feature/notifications
interface ItemLauncher {
    // Part 1's send, startShortcut and appGone, plus:
    /** Fills [action]'s free-form inputs with [text] and sends it. False if it takes no text or was cancelled. */
    fun reply(action: Notification.Action, text: String): Boolean
}
```

  - The fake's `hide` puts `HiddenChat(id, chat.conversationKey, chat.title)` first in `hiddenState`, and `showAgain` takes it out. Its items don't change, because tests set them.
  - `testMessage`'s item is unread unless `self`, and its conversation is `ConversationRef("alex", null)`.

- [ ] **Step 1: Write the failing tests.** Add to `TileStateTest`:

```kotlin
    @Test fun theHiddenChatsOfEverySourceAreCollected() = runTest {
        a.hiddenState.value = listOf(HiddenChat(a.id, "family", "Family"))
        b.hiddenState.value = listOf(HiddenChat(b.id, "sam", "Sam"))
        assertEquals(listOf("Family", "Sam"), tileState(listOf(a, b)).first().hiddenChats.map { it.name })
    }
```

  In `AppNotificationSourceTest`, `RecordingLauncher` gains `var replyWorks = true` and `val replies = mutableListOf<Pair<Notification.Action, String>>()`. Then add:

```kotlin
    @Test fun aChatIsItsStoredMessagesOldestFirstWithYours() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100), stored("me", 150).copy(sender = "You", isSelf = true), stored("b", 200)))
        dao.replaceNotification("n2", listOf(stored("s", 120, conversation = "sam", notification = "n2")))
        val chat = source.chat("alex").first()!!
        assertEquals(listOf("a", "me", "b"), chat.messages.map { it.item.key })
        assertEquals(listOf(false, true, false), chat.messages.map { it.isSelf })
        assertEquals("Alex", chat.title)
        assertEquals(ImageRef(File(folder.root, "b.webp")), chat.avatar) // the newest incoming message's
        assertFalse(chat.isGroup)
    }

    @Test fun aGroupChatIsNamedAfterTheGroupWithItsSendersAndNoAvatar() = runTest {
        dao.replaceNotification("n1", listOf(stored("m", 100, conversation = "family", group = "Family")))
        val chat = source.chat("family").first()!!
        assertEquals("Family", chat.title)
        assertTrue(chat.isGroup)
        assertNull(chat.avatar)
        assertEquals("Mum", chat.messages.single().sender)
    }

    @Test fun aChatWithNothingStoredIsNull() = runTest {
        assertNull(source.chat("alex").first())
    }

    @Test fun aChatOfOnlyYourMessagesIsNamedAfterTheApp() = runTest {
        dao.replaceNotification("n1", listOf(stored("me", 100).copy(sender = "You", isSelf = true)))
        assertEquals("WhatsApp", source.chat("alex").first()!!.title)
    }

    // Review Focus 2: after a launcher restart the cache is empty, so nothing takes replies until the app posts again
    @Test fun aChatRepliesThroughItsNewestNotificationWithALiveReplyAction() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        dao.replaceNotification("n2", listOf(stored("b", 200, notification = "n2")))
        assertNull(source.chat("alex").first()!!.replyTo)
        actions.put("n1", LiveActions(intent, reply))
        assertEquals("a", source.chat("alex").first()!!.replyTo?.key)
        actions.put("n2", LiveActions(intent, reply))
        assertEquals("b", source.chat("alex").first()!!.replyTo?.key)
    }

    @Test fun aReplyGoesToItsItemsLiveReplyAction() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        actions.put("n1", LiveActions(intent, reply))
        assertEquals(ReplyResult.Sent, source.reply(source.chat("alex").first()!!.replyTo!!, "on my way"))
        assertEquals(listOf(reply to "on my way"), launcher.replies)
    }

    // Review Focus 2
    @Test fun aReplyWithoutALiveActionIsActionGone() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        actions.put("n1", LiveActions(intent, reply))
        val item = source.chat("alex").first()!!.replyTo!!
        actions.remove("n1")
        assertEquals(ReplyResult.ActionGone, source.reply(item, "hi"))
        actions.put("n1", LiveActions(intent, reply))
        dao.markRemoved("n1", now = 300)
        assertEquals(ReplyResult.ActionGone, source.reply(item, "hi"))
        assertEquals(emptyList<Pair<Notification.Action, String>>(), launcher.replies)
    }

    // Review Focus 2
    @Test fun aReplyThatCantBeSentIsActionGone() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        actions.put("n1", LiveActions(intent, reply))
        launcher.replyWorks = false
        assertEquals(ReplyResult.ActionGone, source.reply(source.chat("alex").first()!!.replyTo!!, "hi"))
    }

    // Review Focus 3 and 4
    @Test fun hidingAChatTakesItOffTheTileAndShowingItAgainBringsItBack() = runTest {
        val work = AppNotificationSource("com.whatsapp", 10, dao, actions, folder.root, catalog, launcher)
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        dao.replaceNotification("n2", listOf(stored("m", 200, conversation = "family", group = "Family", notification = "n2")))
        dao.replaceNotification("w1", listOf(stored("w", 300, conversation = "family", group = "Family", notification = "w1", serial = 10)))
        source.hide(source.chat("family").first()!!)
        assertEquals(listOf("a"), source.items.first().map { it.key })
        assertEquals(1, source.unreadCount.first())
        assertEquals(listOf(HiddenChat(source.id, "family", "Family")), source.hiddenChats.first())
        assertEquals(listOf("w"), work.items.first().map { it.key }) // the work profile's "family" stays
        source.showAgain("family")
        assertEquals(listOf("m", "a"), source.items.first().map { it.key })
        assertEquals(emptyList<HiddenChat>(), source.hiddenChats.first())
    }
```

  Create `AndroidItemLauncherTest`:

```kotlin
@RunWith(AndroidJUnit4::class)
class AndroidItemLauncherTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val launcher = AndroidItemLauncher(context)

    // Explicit, because Android 14+ refuses a mutable PendingIntent around an implicit intent.
    private fun action(input: RemoteInput) = Notification.Action.Builder(
        null, "Reply", PendingIntent.getBroadcast(context, 0, Intent("reply").setPackage(context.packageName), PendingIntent.FLAG_MUTABLE),
    ).addRemoteInput(input).build()

    @Test fun aReplyFillsTheRemoteInputAsTypedTextAndSendsTheAction() {
        assertTrue(launcher.reply(action(RemoteInput.Builder("reply").build()), "on my way"))
        val sent = shadowOf(context).broadcastIntents.single()
        assertEquals("on my way", RemoteInput.getResultsFromIntent(sent)?.getCharSequence("reply")?.toString())
        assertEquals(RemoteInput.SOURCE_FREE_FORM_INPUT, RemoteInput.getResultsSource(sent))
    }

    // Review Focus 2
    @Test fun anActionThatTakesNoTextOrWasCancelledFails() {
        val choicesOnly = RemoteInput.Builder("pick").setChoices(arrayOf("yes")).setAllowFreeFormInput(false).build()
        assertFalse(launcher.reply(action(choicesOnly), "hi"))
        val cancelled = action(RemoteInput.Builder("reply").build()).also { it.actionIntent.cancel() }
        assertFalse(launcher.reply(cancelled, "hi"))
        assertEquals(emptyList<Intent>(), shadowOf(context).broadcastIntents)
    }
}
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :core:tiles:testDebugUnitTest :feature:notifications:testDebugUnitTest`
Expected: FAIL to compile, because the chat types and members don't exist.

- [ ] **Step 3: Implement chats, replies and hiding.**
  - **`tileState`** combines each source's `items`, `unreadCount` and `hiddenChats`. The state's `hiddenChats` is every source's list, in source order.
  - **`AppNotificationSource`:**
    - `items` and `chat` share one `NotificationItemEntity.toItem(unread: Boolean, live: Map<String, LiveActions>): TileItem`, part 1's mapping.
    - `chat(key)` combines `dao.observeConversation(id.value, key)` with `actions.actions`, and gives null for no rows.
      - A chat is a group when any message has a `conversationTitle`.
      - Its title is the newest `conversationTitle`, else the newest incoming sender, else the catalog's label for the app, else the package name.
      - Its avatar is the newest incoming message's, and only one-to-one chats have one.
    - `reply(item, text)` looks up the stored item, if it isn't removed, and its notification's live `reply` action, then calls `launcher.reply`. The result is `Sent` when that returns true, and `ActionGone` otherwise.
    - `hiddenChats` maps `observeHidden`.
    - `hide(chat)` stores `HiddenConversationEntity(id.value, chat.conversationKey, chat.title, System.currentTimeMillis())`.
    - `showAgain(key)` calls the DAO's.
  - **`AndroidItemLauncher.reply`:**
    - It takes the action's `RemoteInput`s that allow free-form input, and returns false if there are none.
    - It builds a fresh `Intent` with `RemoteInput.addResultsToIntent(inputs, intent, results)`, where `results` holds `text` under each input's `resultKey`. Then it calls `RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)`.
    - It sends with `action.actionIntent.send(context, 0, intent)`, and returns false on `PendingIntent.CanceledException`. There are no `ActivityOptions`, since reply actions are broadcasts or services.

- [ ] **Step 4: Run the tests again.**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, every module included, since the fake changed.

- [ ] **Step 5: Commit.**

```bash
git add core/tiles core/testing feature/notifications
git commit -m "Read, reply to and hide chats through tile sources"
```

---

### Task 4: Chat buttons on tiles

**Files:**
- Modify:
  - `core/design/src/main/kotlin/…/core/design/icons/LiquiWinIcons.kt`
  - `feature/start/src/main/kotlin/…/feature/start/live/TileTap.kt`
  - `feature/start/src/main/kotlin/…/feature/start/StartTileView.kt`
  - `feature/start/src/main/kotlin/…/feature/start/StartScreen.kt`
- Test: `feature/start/src/test/kotlin/…/feature/start/live/TileTapTest.kt`, `StartTileViewTest.kt`, `StartScreenTest.kt`

**Interfaces:**
- Consumes:
  - part 1's `TileFace`, `STACK_SIZE`, `tileTapTarget`, `LABEL_STRIP_WP_PX` and `StartController.tap`;
  - Task 3's `testItem`.
- Produces:
  - `LiquiWinIcons.Chat`, a speech bubble drawn here: `M2 3h20v14H9l-5 4v-4H2z`.
  - `internal fun tileChatTarget(face: TileFace, x: Float, y: Float, widthPx: Float, heightPx: Float, labelStripPx: Float, buttonPx: Float): TileItem?`
  - `StartScreen(…, onChat: (TileItem) -> Unit = {})`, which hands over a chat button's item for `:app` to open.

- [ ] **Step 1: Write the failing tests.** Add to `TileTapTest`:

```kotlin
    @Test fun theChatButtonIsTheBottomRightCornerOfAnItemFace() {
        fun at(x: Float, y: Float) =
            tileChatTarget(TileFace.Item(a), x, y, widthPx = 100f, heightPx = 100f, labelStripPx = 30f, buttonPx = 40f)
        assertEquals(a, at(60f, 60f)) // the corner reaches above the label strip
        assertEquals(a, at(99f, 99f))
        assertNull(at(59f, 99f))
        assertNull(at(99f, 59f))
        assertNull(tileChatTarget(TileFace.Front, 99f, 99f, 100f, 100f, 30f, 40f))
    }

    @Test fun eachStackRowHasItsOwnChatButton() {
        // A 90 px body of three 30 px rows, over a 40 px label strip
        fun at(x: Float, y: Float) = tileChatTarget(
            TileFace.Stack(listOf(a, b, c)), x, y, widthPx = 100f, heightPx = 130f, labelStripPx = 40f, buttonPx = 20f,
        )
        assertEquals(listOf(a, b, c), listOf(25f, 55f, 85f).map { at(90f, it) })
        assertNull(at(90f, 5f)) // above the first row's button
        assertNull(at(70f, 25f))
        assertNull(at(90f, 120f)) // the label strip opens the app
    }
```

  Add to `StartTileViewTest`:

```kotlin
    @Test fun contentFacesShowAChatButtonPerItem() {
        var size by mutableStateOf(TileSize.MEDIUM)
        var face: TileFace by mutableStateOf(TileFace.Front)
        compose.setContent { LiquiWinTheme { StartTileView(tile(size), metrics, { null }, face = face) } }
        fun chatButtons() = compose.onAllNodesWithTag("tile-chat", useUnmergedTree = true)
        chatButtons().assertCountEquals(0)
        face = TileFace.Item(testItem("a"))
        chatButtons().assertCountEquals(1)
        size = TileSize.LARGE
        face = TileFace.Stack(listOf(testItem("a"), testItem("b"), testItem("c")))
        chatButtons().assertCountEquals(3)
    }
```

  In `StartScreenTest`, add `private val chats = mutableListOf<TileItem>()`. Then `show` returns its view model and passes `onChat = { chats += it }` to `StartScreen`. Add:

```kotlin
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
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:start:testDebugUnitTest`
Expected: FAIL to compile, because `tileChatTarget` and `onChat` don't exist.

- [ ] **Step 3: Add the buttons.**
  - **`tileChatTarget`:**
    - On an item face, it gives the item when `x >= widthPx - buttonPx` and `y >= heightPx - buttonPx`.
    - On a stack face, inside the body above the label strip, it gives the row under `y`, when `x >= widthPx - buttonPx` and `y` is within `buttonPx` of that row's bottom.
    - Otherwise it gives null.
  - **`StartTileView`** draws a `LiquiWinIcons.Chat` glyph, 16 WP px, in the foreground colour, with `contentDescription = null` and `testTag("tile-chat")`. It goes at the label strip's end on item faces, where the front face shows its count. It also goes at each stack row's bottom end, and that row's text gets 24 WP px of end padding so it never runs under the glyph.
  - **`StartScreen`:**
    - `StartScreen` and `StartController` take `onChat`.
    - `StartController.chat(tile: StartTile, coords: LayoutCoordinates?, editing: Boolean, item: TileItem)` calls `tap` with the same arguments in edit mode or on a paused tile. Otherwise it calls `onChat(item)`.
    - In TileSlot's `pointerInput`, a tap on a tile that isn't the lifted one checks `tileChatTarget` first, on the drawn face as `tapTarget` does, with `buttonPx` set to 48 dp in px. Only then does it check part 1's `tapTarget`.
    - On a content face, the semantics gain a custom action "chat with ${item.title}" for each of `face.shownItems`.
  - **`LiquiWinIcons`:** add `Chat`. The KDoc says Fluent's paths are MIT "except Pause and Chat, drawn here".

- [ ] **Step 4: Run them again.**

Run: `./gradlew :core:design:testDebugUnitTest :feature:start:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add core/design feature/start
git commit -m "Add a chat button to every message on a tile"
```

---

### Task 5: The chat window's state

**Files:**
- Create:
  - `feature/hub/build.gradle.kts`
  - `feature/hub/src/test/resources/robolectric.properties`
  - `feature/hub/src/main/kotlin/…/feature/hub/ChatViewModel.kt`
- Modify: `settings.gradle.kts`
- Test: `feature/hub/src/test/kotlin/…/feature/hub/ChatViewModelTest.kt`

**Interfaces:**
- Consumes:
  - Task 3's `Chat`, `ChatMessage`, `HiddenChat`, `ReplyResult` and `TileSource`'s chat members;
  - the fake's `chatState`, `hiddenState`, `replies`, `replyResult`, `replyGate`, `read` and `opened`;
  - `testMessage`, `testChat`, `testItem`;
  - part 1's `SourceRegistry` and `MainDispatcherRule`.
- Produces:

```kotlin
/** An open chat window (§5.7). */
data class ChatWindowState(
    val sourceId: SourceId,
    val chat: Chat,
    val pending: List<PendingReply>, // sent, and not yet back from the app; oldest first
    val showsReplyBox: Boolean, // since the chat first took replies while open
    val sending: Boolean,
    val failed: Boolean, // "Couldn't send."
)

data class PendingReply(val id: Long, val text: String, val sentAt: Instant, val sending: Boolean)

class ChatViewModel(sources: SourceRegistry, now: () -> Instant = Instant::now) : ViewModel() {
    val window: StateFlow<ChatWindowState?> // null while closed
    val draft: TextFieldState // the text box's
    fun show(item: TileItem) // opens the item's chat
    fun send()
    fun openInApp() // opens the newest message through §5.5's chain, and closes
    fun hide() // hides the chat from tiles; the window closes itself after its swivel
    fun close()
}
```

- [ ] **Step 1: Create the module.**
  - Give `feature/hub/build.gradle.kts` the shape of `feature/start/build.gradle.kts`: namespace `io.github.b1tsagar.liquiwin.feature.hub`, Compose on, `implementation(project(":core:tiles"))`, and the same Compose, activity and lifecycle libraries. For tests it needs `testImplementation(project(":core:testing"))` and start's test libraries.
  - Add `include(":feature:hub")` to `settings.gradle.kts`.
  - Give `robolectric.properties` `sdk=36`.

- [ ] **Step 2: Write the failing test.**

```kotlin
@RunWith(AndroidJUnit4::class) // TextFieldState comes from an Android library
class ChatViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val source = FakeTileSource(SourceId("app:com.example.chat@0"))
    private val vm = ChatViewModel(SourceRegistry { if (it == source.id) source else null }, now = { Instant.ofEpochMilli(5_000) })
    private val a = testMessage("a", at = 1)
    private val b = testMessage("b", "running late", at = 2)
    private val window get() = vm.window.value!!

    private fun open(vararg messages: ChatMessage) {
        source.chatState.value = testChat(*messages)
        vm.show(messages.first().item)
    }

    private fun send(text: String) {
        vm.draft.setTextAndPlaceCursorAtEnd(text)
        vm.send()
    }

    @Test fun openingShowsTheChatAndReadsItUpToItsNewestMessage() {
        open(a, b)
        assertEquals(testChat(a, b), window.chat)
        assertEquals(listOf(b.item), source.read)
    }

    // Review Focus 3
    @Test fun messagesArrivingWhileOpenAreReadToo() {
        open(a, b)
        val c = testMessage("c", at = 3)
        source.chatState.value = testChat(a, b, c)
        source.chatState.value = testChat(a, b, c.copy(item = c.item.copy(canReply = false))) // the same newest: no second read
        assertEquals(listOf(b.item, c.item), source.read)
    }

    // Review Focus 3
    @Test fun aChatWhoseMessagesAreGoneCloses() {
        open(a)
        source.chatState.value = null
        assertNull(vm.window.value)
    }

    @Test fun anItemWithoutAChatOpensNothing() {
        vm.show(testItem("x").copy(conversation = null))
        vm.show(testItem("y", source = SourceId("app:gone@0")))
        assertNull(vm.window.value)
    }

    // Review Focus 2: after a launcher restart, replies come back with the app's next post
    @Test fun theReplyBoxShowsOnceTheChatTakesRepliesAndStays() {
        val quiet = testMessage("a", canReply = false)
        open(quiet)
        assertFalse(window.showsReplyBox)
        source.chatState.value = testChat(testMessage("a"))
        assertTrue(window.showsReplyBox)
        source.chatState.value = testChat(quiet)
        assertTrue(window.showsReplyBox)
    }

    @Test fun sendingRepliesToTheNewestMessageThatTakesRepliesAndClearsTheBox() {
        open(a, testMessage("b", at = 2, canReply = false))
        send("on my way")
        assertEquals(listOf(a.item to "on my way"), source.replies)
        assertEquals("", vm.draft.text.toString())
        val pending = window.pending.single()
        assertEquals("on my way", pending.text)
        assertEquals(Instant.ofEpochMilli(5_000), pending.sentAt)
        assertTrue(pending.sending)
    }

    // Review Focus 1
    @Test fun yourMessageComingBackReplacesThePendingOneWhateverItsText() {
        open(a)
        send("on my way")
        source.chatState.value = testChat(a, testMessage("me", "On my way!", at = 3, self = true))
        assertEquals(emptyList<PendingReply>(), window.pending)
    }

    @Test fun yourMessagesAlreadyThereDontReplaceIt() {
        val earlier = testMessage("me1", "earlier", at = 0, self = true)
        open(earlier, a)
        send("on my way")
        source.chatState.value = testChat(earlier, a, testMessage("c", at = 3))
        assertEquals(listOf("on my way"), window.pending.map { it.text })
    }

    // Review Focus 1
    @Test fun aReplyThatNeverComesBackStopsSayingSendingAfter10Seconds() = runTest {
        open(a)
        send("on my way")
        advanceTimeBy(9_999)
        assertTrue(window.pending.single().sending)
        advanceTimeBy(2)
        assertFalse(window.pending.single().sending)
    }

    // Review Focus 2
    @Test fun sendingAfterTheActionWentKeepsTheTextAndSaysWhy() {
        open(a)
        source.chatState.value = testChat(testMessage("a", at = 1, canReply = false)) // the app removed its notification
        send("on my way")
        assertTrue(window.failed)
        assertEquals("on my way", vm.draft.text.toString())
        assertEquals(emptyList<Pair<TileItem, String>>(), source.replies)
    }

    // Review Focus 2
    @Test fun anActionThatFailsKeepsTheTextAndSaysWhy() {
        source.replyResult = ReplyResult.ActionGone
        open(a)
        send("on my way")
        assertTrue(window.failed)
        assertEquals("on my way", vm.draft.text.toString())
        assertEquals(emptyList<PendingReply>(), window.pending)
        source.replyResult = ReplyResult.Sent
        vm.send()
        assertFalse(window.failed) // the next try clears it
    }

    // Review Focus 2
    @Test fun aSecondTapWhileSendingSendsOnce() {
        val gate = CompletableDeferred<Unit>()
        source.replyGate = gate
        open(a)
        send("on my way")
        vm.send()
        assertTrue(window.sending)
        gate.complete(Unit)
        assertEquals(1, source.replies.size)
        assertEquals(1, window.pending.size)
        assertFalse(window.sending)
    }

    @Test fun aBlankDraftSendsNothing() {
        open(a)
        send("  \n ")
        assertEquals(emptyList<Pair<TileItem, String>>(), source.replies)
        assertFalse(window.failed)
    }

    @Test fun openInAppOpensTheNewestMessageAndCloses() {
        open(a, b)
        vm.openInApp()
        assertEquals(listOf<TileItem?>(b.item), source.opened)
        assertNull(vm.window.value)
    }

    @Test fun hidingHidesTheChatUnderItsTitle() {
        open(a)
        vm.hide()
        assertEquals(listOf(HiddenChat(source.id, "alex", "Alex")), source.hiddenState.value)
    }
}
```

- [ ] **Step 3: Run it and watch it fail.**

Run: `./gradlew :feature:hub:testDebugUnitTest`
Expected: FAIL to compile, because `ChatViewModel` doesn't exist.

- [ ] **Step 4: Implement `ChatViewModel`.**
  - **State.** Keep one `MutableStateFlow<ChatWindowState?>`, and one job collecting the shown chat.
    - `show` ignores an item with no conversation or no source. Otherwise it cancels the last job, clears the draft and the window's own state, and starts collecting `source.chat(key)`. That way, a chat shown before can't leak into this one.
  - **Each chat that arrives:**
    - Null closes the window.
    - When the newest message's key differs from the last one marked read, the source marks that message read (§5.3).
    - The view model keeps the keys of your messages seen since opening. Each new one drops the oldest pending reply.
    - `showsReplyBox` turns true when `chat.replyTo` isn't null, and stays true until the window closes.
  - **`send()`:**
    - It does nothing while closed or sending, or while the draft is blank.
    - With no `replyTo`, it sets `failed`.
    - Otherwise it sets `sending` until `reply` returns, then:
      - On `Sent`, it adds a pending reply with `sentAt = now()` and `sending = true`, clears the draft if it still holds the text sent, and clears `failed`. After 10 s, that reply's `sending` turns false.
      - On `ActionGone`, it sets `failed` and keeps the draft.
  - **`openInApp()`** opens the newest message through its source, which also marks it read, then closes.
  - **`hide()`** calls `source.hide(chat)`. The window stays until `close()`, so it can swivel out.
  - **`close()`** cancels the job, and the window becomes null.

- [ ] **Step 5: Run it again.**

Run: `./gradlew :feature:hub:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add settings.gradle.kts feature/hub
git commit -m "Add the chat window's state"
```

---

### Task 6: The chat window

**Files:**
- Create:
  - `core/design/src/main/kotlin/…/core/design/motion/Swivel.kt`
  - `core/design/src/main/kotlin/…/core/design/components/RememberImage.kt` (moved out of `StartTileView`)
  - `feature/hub/src/main/kotlin/…/feature/hub/ChatWindow.kt`, `ScrollThumb.kt`, `TimeLabel.kt`
- Modify:
  - `core/design/src/main/kotlin/…/core/design/motion/Turnstile.kt` (the shared ease)
  - `feature/start/src/main/kotlin/…/feature/start/StartTileView.kt` (the moved `rememberImage`)
  - `app/build.gradle.kts`, `app/src/main/kotlin/…/home/HomeRoot.kt`
- Test:
  - `core/design/src/test/kotlin/…/core/design/motion/SwivelTest.kt`
  - `feature/hub/src/test/kotlin/…/feature/hub/ScrollThumbTest.kt`, `TimeLabelTest.kt`, `ChatWindowTest.kt`
  - `app/src/test/kotlin/…/home/HomeRootTest.kt`

**Interfaces:**
- Consumes:
  - Task 5's `ChatViewModel` and `ChatWindowState`, and Task 3's chat types and fake;
  - part 1's `AppCatalog` and `FakeAppCatalog`, `SourceId.appPackageAndSerial()`, `LocalLiquiWinColors`, `LocalLiquiWinType`, `LocalWpUnits`, `IconImage` and `WpText`.
- Produces:

```kotlin
// core/design
/** WP's swivel motions (reference §5), turning around the horizontal axis. */
enum class Swivel(val fromDeg: Float, val toDeg: Float, val durationMs: Int, val exponent: Float, val easesIn: Boolean) {
    IN(fromDeg = -45f, toDeg = 0f, durationMs = 350, exponent = 6f, easesIn = false),
    OUT(fromDeg = 0f, toDeg = 90f, durationMs = 250, exponent = 15f, easesIn = true),
}
fun swivelAngle(motion: Swivel, elapsedMs: Float): Float
fun exponentialEase(t: Float, exponent: Float, easesIn: Boolean): Float // WP's ExponentialEase; turnstileProgress uses it too
@Composable fun rememberImage(file: File): ImageBitmap? // decoded off the main thread, as in part 1

// feature/hub
/** The chat window (§5.7) over whatever is beneath it. Draws nothing while closed. */
@Composable fun ChatWindow(viewModel: ChatViewModel, catalog: AppCatalog, modifier: Modifier = Modifier)
internal data class ScrollThumb(val topPx: Float, val lengthPx: Float)
/** Null when everything fits. */
internal fun scrollThumb(viewportPx: Float, contentPx: Float, fromTopPx: Float, minLengthPx: Float): ScrollThumb?
/** The time in [format], after "yesterday " if it's from an earlier day in [format]'s time zone. */
internal fun timeLabel(time: Instant, now: Instant, format: java.text.DateFormat): String
```

- [ ] **Step 1: Write the failing JVM tests.**

```kotlin
class SwivelTest {
    @Test fun swivellingInStartsTiltedAndSettlesEarly() {
        assertEquals(-45f, swivelAngle(Swivel.IN, 0f), 0.001f)
        assertEquals(-2.13f, swivelAngle(Swivel.IN, 175f), 0.05f) // exponent 6, easing out
        assertEquals(0f, swivelAngle(Swivel.IN, 350f), 0.001f)
        assertEquals(0f, swivelAngle(Swivel.IN, 1_000f), 0.001f)
    }

    @Test fun swivellingOutWaitsThenTurnsEdgeOn() {
        assertEquals(0.05f, swivelAngle(Swivel.OUT, 125f), 0.01f) // exponent 15, easing in
        assertEquals(90f, swivelAngle(Swivel.OUT, 250f), 0.001f)
    }
}

class ScrollThumbTest {
    @Test fun noThumbWhenEverythingFits() {
        assertNull(scrollThumb(viewportPx = 100f, contentPx = 100f, fromTopPx = 0f, minLengthPx = 10f))
    }

    @Test fun theThumbShowsWhichPartIsInView() {
        assertEquals(ScrollThumb(topPx = 0f, lengthPx = 50f), scrollThumb(100f, 200f, fromTopPx = 0f, minLengthPx = 10f))
        assertEquals(ScrollThumb(topPx = 50f, lengthPx = 50f), scrollThumb(100f, 200f, fromTopPx = 100f, minLengthPx = 10f))
    }

    @Test fun aLongChatsThumbKeepsItsMinimumLengthAndStaysInside() {
        assertEquals(ScrollThumb(topPx = 90f, lengthPx = 10f), scrollThumb(100f, 10_000f, fromTopPx = 9_900f, minLengthPx = 10f))
        assertEquals(ScrollThumb(topPx = 90f, lengthPx = 10f), scrollThumb(100f, 10_000f, fromTopPx = 12_000f, minLengthPx = 10f))
    }
}

class TimeLabelTest {
    private fun label(time: String, now: String, zone: String = "UTC") = timeLabel(
        Instant.parse(time), Instant.parse(now),
        SimpleDateFormat("H:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(zone) },
    )

    @Test fun todaysMessagesShowTheirTime() {
        assertEquals("9:41", label("2026-10-28T09:41:00Z", now = "2026-10-28T12:00:00Z"))
    }

    @Test fun earlierOnesSayYesterday() {
        assertEquals("yesterday 23:50", label("2026-10-27T23:50:00Z", now = "2026-10-28T00:10:00Z"))
    }

    @Test fun theDayIsTheOneOnThePhone() {
        // In India both are on the morning of the 28th.
        assertEquals("5:20", label("2026-10-27T23:50:00Z", now = "2026-10-28T00:10:00Z", zone = "Asia/Kolkata"))
    }
}
```

- [ ] **Step 2: Write the failing Compose tests.**

```kotlin
@RunWith(AndroidJUnit4::class)
class ChatWindowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val chatApp = testApp("Chat") // com.example.chat, testMessage's default source
    private val source = FakeTileSource(SourceId.forApp(chatApp.key.packageName, 0))
    private val vm = ChatViewModel(SourceRegistry { if (it == source.id) source else null })
    private val a = testMessage("a")

    private fun open(chat: Chat) {
        source.chatState.value = chat
        compose.setContent { LiquiWinTheme { ChatWindow(vm, FakeAppCatalog(apps = listOf(chatApp))) } }
        compose.runOnUiThread { vm.show(chat.messages.first().item) }
        compose.waitForIdle()
    }

    private fun type(text: String) = compose.onNodeWithTag("chat-input").performTextInput(text)

    @Test fun aChatThatTakesRepliesOpensWithTheBoxFocused() {
        open(testChat(a))
        compose.onNodeWithText("Alex").assertIsDisplayed() // the header, and no name over a one-to-one bubble
        compose.onNodeWithText("see you at 6").assertIsDisplayed()
        compose.onNodeWithTag("chat-input").assertIsFocused() // the keyboard itself is checked on the Galaxy
        compose.onNodeWithText("send").assertIsNotEnabled()
    }

    @Test fun aChatThatTakesNoRepliesHasNoBox() {
        val quiet = testMessage("a", canReply = false)
        open(testChat(quiet))
        compose.onNodeWithTag("chat-input").assertDoesNotExist()
        compose.onNodeWithText("open in app").performClick()
        assertEquals(listOf<TileItem?>(quiet.item), source.opened)
        assertNull(vm.window.value)
    }

    // Review Focus 1
    @Test fun sendingShowsYourBubbleUntilTheAppsCopyArrives() {
        open(testChat(a))
        type("on my way")
        compose.onNodeWithText("send").performClick()
        assertEquals(listOf(a.item to "on my way"), source.replies)
        compose.onNodeWithText("sending…").assertIsDisplayed()
        compose.runOnUiThread { source.chatState.value = testChat(a, testMessage("me", "on my way", at = 1, self = true)) }
        compose.onNodeWithText("sending…").assertDoesNotExist()
        compose.onAllNodesWithText("on my way").assertCountEquals(1)
    }

    // Review Focus 2
    @Test fun aFailedSendKeepsTheTextAndOffersTheApp() {
        source.replyResult = ReplyResult.ActionGone
        open(testChat(a))
        type("on my way")
        compose.onNodeWithText("send").performClick()
        compose.onNodeWithText("Couldn't send. Open Chat to reply.").assertIsDisplayed()
        compose.onNodeWithTag("chat-input").assertTextEquals("on my way")
        compose.onNodeWithText("open").performClick()
        assertEquals(listOf<TileItem?>(a.item), source.opened)
        assertNull(vm.window.value)
    }

    @Test fun aLongChatOpensAtTheNewestWithAScrollbar() {
        open(testChat(*Array(40) { testMessage("m$it", "message ${it + 1}", at = it.toLong()) }))
        compose.onNodeWithText("message 40").assertIsDisplayed()
        compose.onNodeWithText("message 1").assertIsNotDisplayed()
        compose.onNodeWithTag("chat-scrollbar").assertIsDisplayed()
    }

    @Test fun aShortChatHasNoScrollbar() {
        open(testChat(a))
        compose.onNodeWithTag("chat-scrollbar").assertDoesNotExist()
    }

    @Test fun aGroupNamesTheSenderAboveEachIncomingBubble() {
        open(
            testChat(
                testMessage("a", sender = "Mum"), testMessage("b", at = 1, sender = "Dad"), testMessage("me", at = 2, self = true),
                title = "Family", isGroup = true,
            ),
        )
        compose.onNodeWithText("Family").assertIsDisplayed()
        compose.onNodeWithText("Mum").assertIsDisplayed()
        compose.onNodeWithText("Dad").assertIsDisplayed()
        compose.onNodeWithText("You").assertDoesNotExist()
    }

    @Test fun hidingTheChatHidesItAndCloses() {
        open(testChat(a))
        compose.onNodeWithText("hide this chat").performClick()
        compose.waitForIdle()
        assertEquals(listOf(HiddenChat(source.id, "alex", "Alex")), source.hiddenState.value)
        assertNull(vm.window.value)
    }

    @Test fun backAndATapOutsideClose() {
        open(testChat(a))
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertNull(vm.window.value)
        compose.runOnUiThread { vm.show(a.item) }
        compose.onRoot().performTouchInput { click(Offset(centerX, 10f)) }
        compose.waitForIdle()
        assertNull(vm.window.value)
    }

    // Review Focus 5
    @Test fun talkBackCanCloseItFromOutside() {
        open(testChat(a))
        compose.onNodeWithContentDescription("close").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertNull(vm.window.value)
    }
}
```

  In `HomeRootTest`, `show` gains `sources: SourceRegistry = SourceRegistry { null }`, which it passes to `HomeDependencies`. Then add:

```kotlin
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
```

- [ ] **Step 3: Run them and watch them fail.**

Run: `./gradlew :core:design:testDebugUnitTest --tests "*SwivelTest" :feature:hub:testDebugUnitTest :app:testDebugUnitTest --tests "*HomeRootTest"`
Expected: FAIL to compile, because `Swivel`, `ChatWindow`, `scrollThumb` and `timeLabel` don't exist, and neither does `show`'s `sources`.

- [ ] **Step 4: Build the window.**
  - **Motion:**
    - Move the turnstile's exponential ease into `exponentialEase`, so `turnstileProgress` and `swivelAngle` share it.
    - `swivelAngle` clamps its time to the motion's duration.
    - `rememberImage` moves to `core:design` as it is, taking the file. `StartTileView` passes it `ref.file`.
  - **`ChatWindow`:**
    - **Layers.** While the window is open, a scrim covers the screen: 60 % black, clickable, and labelled "close". The panel sits at the bottom of it.
    - **The swivel.** A clock (an `Animatable` of elapsed ms with a linear tween, like `TurnstileClock`) sets the panel's `rotationX = swivelAngle(…)`, with `cameraDistance = 12f * density`, around its centre.
      - `IN` plays when the window opens.
      - Back (a `BackHandler` while open), a tap on the scrim, and **hide this chat** (after `viewModel.hide()`) play `OUT`, then call `viewModel.close()`.
      - **open in app** and the failure line's **open** call `viewModel.openInApp()` at once, with no swivel.
    - **The panel.** It's the chrome colour and full width, with `imePadding()` then `navigationBarsPadding()`. It takes at most 80 % of the height left above the keyboard. Its column holds the header, the bubbles (`weight(1f, fill = false)`), the failure line and the reply row.
    - **The header:**
      - the chat's avatar at 40 WP px, when it has one;
      - the title, in `listItem`;
      - the app's icon at 24 WP px. The app is the catalog's app for `sourceId.appPackageAndSerial()`, and its icon comes from `catalog.icon(app)`;
      - the text buttons **open in app** and **hide this chat**. Each is `WpText` in `small` inside a clickable with `Role.Button`, at least 48 dp tall.
    - **The bubbles** sit in a column with `verticalScroll(state, reverseScrolling = true)`, so the window opens at the newest and stays there as messages arrive. The chat's messages come oldest first, followed by the pending replies.
      - An incoming bubble is the accent colour, with a top-left tail. It's aligned left, with 72 WP px free on its right. In a group, the sender's name sits above it, in `small` and subtle.
      - Your bubbles, pending ones included, are `lerp(accent, Color.Black, 0.3f)`, with a bottom-right tail. They're aligned right, with 72 WP px free on their left.
      - Each tail is a 12 WP px right-angled triangle outside its bubble's corner.
      - Bubble text is `body` in the foreground colour, padded 12 WP px across and 8 down. A photo (`rememberImage`) sits inside its bubble, above the text, as wide as the bubble.
      - Under each bubble, in `small` and subtle, is `timeLabel(time, now, DateFormat.getTimeFormat(context))`. A pending reply's time is its `sentAt`, and it shows "sending…" there instead while it's sending.
      - Messages are 12 WP px apart.
    - **The scrollbar.** When `scrollThumb(viewportSize, viewportSize + maxValue, maxValue - value, 24 WP px)` isn't null, a thumb tagged "chat-scrollbar" shows at the column's right edge. It's 4 WP px wide, in the subtle colour.
    - **The failure line.** While `failed`, it reads "Couldn't send. Open ${app?.label ?: "the app"} to reply." in `small`, followed by the text button **open**.
    - **The reply row** shows once `showsReplyBox` is true.
      - The text box is a `BasicTextField(viewModel.draft)` tagged "chat-input", styled like `SearchScreen`'s box. It's on the background colour, with `body` text in the foreground colour. While empty, it shows the hint "type a message" in subtle.
      - It holds up to 4 lines (`TextFieldLineLimits.MultiLine(maxHeightInLines = 4)`), capitalises sentences, and keeps the keyboard's default action, so enter adds a new line.
      - When the row first shows, it takes focus and shows the keyboard, the way `SearchScreen` does (`FocusRequester`, then `LocalSoftwareKeyboardController`). `HomeActivity` already has `adjustResize` and edge-to-edge, so `imePadding()` sees the keyboard.
      - The text button **send** sits at its end. It's enabled while the draft isn't blank and nothing is sending.
  - **`HomeRoot`:**
    - It creates `val chat = viewModel { ChatViewModel(deps.sources) }` and passes `onChat = chat::show` to `StartScreen`.
    - The pager's semantics are cleared while the guide or the chat window is open.
    - Home presses call `chat.close()`, along with the other resets.
    - `ChatWindow(chat, deps.catalog)` is drawn last, over the banners and the other overlays.
    - `:app` adds `implementation(project(":feature:hub"))`.

- [ ] **Step 5: Run the tests again.**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add core/design feature/start feature/hub app
git commit -m "Add the chat window"
```

---

### Task 7: Hidden chats in edit mode

**Files:**
- Create: `feature/start/src/main/kotlin/…/feature/start/HiddenChatsList.kt`
- Modify:
  - `core/design/src/main/kotlin/…/core/design/icons/LiquiWinIcons.kt`
  - `feature/start/src/main/kotlin/…/feature/start/EditControls.kt`, `StartScreen.kt`, `StartViewModel.kt`
- Test: `feature/start/src/test/kotlin/…/feature/start/StartViewModelTest.kt`, `StartScreenTest.kt`

**Interfaces:**
- Consumes:
  - Task 3's `HiddenChat`, `TileState.hiddenChats`, `TileSource.showAgain`, and the fake's `hiddenState`;
  - Task 4's `Chat` icon path;
  - part 1's `EditControls` and `CornerButton`.
- Produces:
  - `LiquiWinIcons.ChatOff`: the Chat path plus a slash, `M1.3 3.4l1.4-1.4 20 20-1.4 1.4z`, filled even-odd so the slash cuts through the bubble. `icon(name, d, fillType = PathFillType.NonZero)` passes the fill type on to `addPath`.
  - `EditControls(tileSize: DpSize, onUnpin: () -> Unit, onResize: () -> Unit, onHiddenChats: (() -> Unit)? = null)`
  - `fun StartViewModel.showAgain(chat: HiddenChat)`
  - `@Composable internal fun HiddenChatsList(chats: List<HiddenChat>, onShowAgain: (HiddenChat) -> Unit, onClose: () -> Unit)`

- [ ] **Step 1: Write the failing tests.** Add to `StartViewModelTest`:

```kotlin
    @Test fun showAgainAsksTheChatsSource() = runTest {
        val source = FakeTileSource(SourceId.forApp("com.example.chat", 0))
        val family = HiddenChat(source.id, "family", "Family")
        source.hiddenState.value = listOf(family)
        val viewModel = StartViewModel(store, catalog, gridWidth = 6, SourceRegistry { if (it == source.id) source else null })
        viewModel.showAgain(family)
        assertEquals(emptyList<HiddenChat>(), source.hiddenState.value)
    }
```

  Add to `StartScreenTest`:

```kotlin
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
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:start:testDebugUnitTest --tests "*StartViewModelTest" --tests "*StartScreenTest"`
Expected: FAIL to compile, because `showAgain` doesn't exist.

- [ ] **Step 3: Add the corner and the list.**
  - **`EditControls`:** with `onHiddenChats`, it adds a third `CornerButton(LiquiWinIcons.ChatOff, "hidden chats", …)` at `Modifier.offset(-24.dp, height - 24.dp)`, the bottom-left corner (§4.3).
  - **`StartScreen`:**
    - `StartController.hiddenChatsOf: Long?` is Compose state: the tile whose list is open.
    - The lifted tile's `EditControls` gets `onHiddenChats` only when its `TileState.hiddenChats` isn't empty.
    - `HiddenChatsList` shows over start while `hiddenChatsOf` is set, edit mode is on, and that tile's list isn't empty. When any of those stops holding, `hiddenChatsOf` goes back to null.
    - Its `BackHandler` is composed after edit mode's, so Back closes the list first.
    - **show again** calls `viewModel.showAgain(chat)`.
  - **`HiddenChatsList`:**
    - A scrim covers start: 60 % black, clickable, and labelled "close".
    - Over it, at the bottom, is a chrome-coloured panel with `navigationBarsPadding()`.
    - The panel has the header "hidden chats" in `header`, then one row per chat. Each row is at least 48 dp tall, with the chat's name in `listItem` and the text button **show again**.
  - **`StartViewModel.showAgain(chat)`** launches `sources.source(chat.sourceId)?.showAgain(chat.conversationKey)`.

- [ ] **Step 4: Run them again.**

Run: `./gradlew :core:design:testDebugUnitTest :feature:start:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add core/design feature/start
git commit -m "Show hidden chats again from edit mode"
```

---

### Task 8: The Galaxy check

**Files:**
- Modify: `docs/reference/windows-phone-ui-reference.md` (record the values chosen)

- [ ] **Step 1: Run the whole suite and build both apps.**

Run: `./gradlew testDebugUnitTest :app:assembleRelease :tools:notifier:assembleDebug`
Expected: BUILD SUCCESSFUL, with `app/build/outputs/apk/release/app-release.apk` and `tools/notifier/build/outputs/apk/debug/notifier-debug.apk`.

- [ ] **Step 2: Install both** with the handoff's helper, or its PowerShell port on a PC whose Bash tool can't name the package.

```bash
a install -r app/build/outputs/apk/release/app-release.apk
a install -r tools/notifier/build/outputs/apk/debug/notifier-debug.apk
```

Expected: `Success` twice, with LiquiWin still the Home app and notification access still on. If either was lost, the owner grants it again, as in part 1's Task 12 Step 3.

- [ ] **Step 3: Pin the notifier's tile** if it isn't on start. It lands as a medium tile.

- [ ] **Step 4: Run the scripted check.**
  - Ask the owner to keep their hands off the phone.
  - Before every step, check with `front` that LiquiWin is in front, and stop if it isn't.
  - Post with part 1's `post` helper.
  - Send the owner the shots that show each result.

  The steps:
  1. **The flip.** Post to alex a few times, taking `screencap` shots in a loop for 30 s. On a shot where the tile is turned, measure the widths of the accent area's top and bottom rows. The wider must be at most 1.05 times the narrower. Ask the owner whether the flip now looks right.
  2. **The chat button.** While the tile shows an alex message, tap the bubble at its bottom right.
     - The window swivels in, oldest message at the top.
     - The header reads "Alex", with the notifier's icon.
     - The text box is focused and the keyboard is up.
     - After the window closes, the tile's count no longer includes alex's messages.
  3. **Sending.** Type with `input text "on%smy%sway"` and tap **send**.
     - Your bubble shows "sending…" until the notifier's re-post replaces it.
     - "you said: on my way" arrives in an incoming bubble.
     - The window stays open.
  4. **A long chat.** Post a 1,500-character message to alex and open its window. The scrollbar shows along the right edge, and moves as the bubbles are dragged.
  5. **A photo.** `post photo`, and open its window. The photo sits inside its bubble, above its caption.
  6. **Failure.**
     1. With alex's window open, run `a shell am start --activity-clear-task -n io.github.b1tsagar.liquiwin.tools.notifier/.NotifierActivity --es chat alex`. The notifier cancels alex's notification.
     2. Press Back to LiquiWin, where the window is still open.
     3. Type and tap **send**. The window shows "Couldn't send. Open notifier to reply.", with your text still in the box.
     4. Tap **open**, and the notifier opens.
  7. **No replies.** `post snap`, and open its window. It has no text box, and **open in app** opens the notifier.
  8. **Hiding.**
     1. `post family "dinner at 7"`, open its window and tap **hide this chat**. The window swivels out, and the tile drops Family's messages and count.
     2. `post family "are you coming?"`. The tile doesn't change.
     3. Long-press the tile. Its **hidden chats** corner lists "Family", and **show again** brings its messages back to the tile.
  9. **Closing.** Back, a tap above the window, and Home each close it.

  TalkBack stays off, since it's a system setting. The Compose tests cover it.

  Note anything that fails as a fix before closing this part.

- [ ] **Step 5: Real apps, with the owner.** These steps are the owner's: the agent never sends a real message.
  - From a WhatsApp message on its tile, the owner opens the chat window and sends a real reply, which must appear in WhatsApp. That's the reply half of success criterion 3.
  - They try Instagram, Messenger and Telegram the same way.
  - They open a Snapchat window, which has no text box (§10).
  - Each chat that looks or behaves wrong gets a fixture shaped like it and a fix, or a note for the next plan.

- [ ] **Step 6: Record the chosen values.** Add a "Values chosen for the chat window" list to the reference doc, after part 1's list. It covers:
  - the flip's camera: 21 tile heights away (`cameraDistance` = 21 × height ÷ 72), so the nearer edge is at most 1.049 times as wide;
  - the chat button: a 16 WP px bubble at the label strip's end, and at the end of each large-tile row, with a 48 dp touch area at the bottom-right corner;
  - the swivel: in from −45° to 0 over 350 ms, exponent 6, easing out; out from 0 to 90° over 250 ms, exponent 15, easing in; around the centre, with Tilt's camera;
  - the window:
    - a 60 % scrim, and at most 80 % of the height above the keyboard;
    - bubbles with 12 WP px tails, 72 WP px left free, padding of 12 across and 8 down, 12 between them, and yours 30 % toward black;
    - a 40 WP px avatar and a 24 WP px app icon;
    - a scrollbar 4 WP px wide and at least 24 long;
    - "sending…" for at most 10 s, and a text box of up to 4 lines;
  - anything changed during Steps 4 and 5.

```bash
git add docs/reference/windows-phone-ui-reference.md
git commit -m "Record the values chosen for the chat window"
```
