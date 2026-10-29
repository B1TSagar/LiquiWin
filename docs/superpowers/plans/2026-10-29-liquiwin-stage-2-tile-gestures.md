# LiquiWin Stage 2: Tile Gestures — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The calls the owner made after using the large tile's list. A missed call shows its own buttons. Quick chat shows a card for a photo LiquiWin can't read. A sideways swipe on a tile's message opens its chat (left) or clears its notification (right). With start at its top, a swipe down turns a tile between its front and its new messages, or its history. TalkBack gets an action for each.

**Architecture:**
- `:core:data` stores which photos couldn't be read (`cache.db` version 4), and reads a whole notification at once.
- The normaliser keeps a missed call's buttons with its live actions. `TileSource` gains `clear` and `press`, and `TileState` gains `history`. `AppNotificationSource` clears through the bound listener.
- `:feature:start` keeps a tile's page, claims its swipes and draws a missed call's buttons, all in `TileSlot` and `StartTileView`. The page rules and swipe thresholds are pure functions, tested on the JVM.
- `:feature:hub` draws the photo card and the call row.
- `:tools:notifier` posts a missed call and a photo LiquiWin can't read.

**Tech Stack:** As in part 1, with no new dependencies. Swipes use Compose's pointer passes and `VelocityTracker`. The notifier's photo uses androidx.core's `FileProvider`, which it already has.

**Spec:** [docs/superpowers/specs/2026-10-27-liquiwin-launcher-start-screen-design.md](../specs/2026-10-27-liquiwin-launcher-start-screen-design.md). This plan covers the owner's calls after the large tile's list (commit 41aa715): §5.1 (missed calls), §5.2 (what's extracted), §5.3 (pages), §5.4 (a touched tile, and pages on the timer), §5.5 (a missed call's buttons, swipes and their TalkBack actions), §5.7 (the photo card and the call row), §3.3 (`notification_item`), §8, §9 and §10.
- Windows Phone values come from [docs/reference/windows-phone-ui-reference.md](../../reference/windows-phone-ui-reference.md).
- How to reach and drive the phone is in [the stage 2 handoff](../handoff/2026-10-28-liquiwin-stage-2-handoff.md).

The work continues on `stage-2-tiles-come-alive`, after 41aa715. The chat-window plan's Task 8 Step 5 (the owner's real apps) is still open. This plan doesn't depend on it, and its own Galaxy check can run beside it.

## Global Constraints

- Android only: `minSdk 29`, `targetSdk 36`, `compileSdk 37`. The test phone is the owner's Galaxy S24, reached as the handoff's "Phone testing" section describes.
- Toolchain and versions are part 1's. Every version lives in `gradle/libs.versions.toml`. This part adds no dependencies.
- Package root: `io.github.b1tsagar.liquiwin`.
- "**No `INTERNET` permission in sub-project 1.**" (§2)
- Logs never include a chat's names, keys, texts or images.
- "Never requested: … `CALL_PHONE`, `SEND_SMS`. A missed call's buttons are its app's own (§5.1)." (§8)
- "**Read state is local.** LiquiWin never triggers apps' own mark-as-read actions, because they would send read receipts." (§5.3) A clear only removes the notification "as a swipe in the panel would" (§5.5).
- Swipes work "outside edit mode and on a tile that isn't paused (§9)" (§5.5). Every other swipe "does what it did before".
- No feature module depends on another (§3.1).
- Touch areas are at least 48 dp. A missed call's buttons have "each … a 48 dp touch area" (§5.1).
- "WP px" are Windows Phone pixels: dp = WP px × (screen width in dp ÷ 480).
- Headers and buttons are lowercase. Names, message texts and TalkBack's button names keep their own case.
- No unofficial APIs. (§1.5)
- Motion follows Android's remove-animations setting, as in part 1. The "reduced" setting is stage 3's.
- Every tile is `FLIP` until the other templates come, so every medium, wide and large tile can have pages.
- Tests use JUnit 4. Robolectric runs at `sdk=36`.

## Review Focus

1. **A swipe that isn't the tile's.** This covers a mostly vertical or short swipe on a message, a sideways swipe on a front or a label strip, and any swipe in edit mode or on a paused tile. Start scrolls, the pager turns and edit mode drags, as before. Tests in Tasks 4 and 5.
2. **A clear Android can't carry out.** This covers a notification its app won't let go, and a listener that isn't bound. The message still leaves the tile as read, and nothing throws. Tests in Tasks 1 and 3.
3. **A missed call's button that has gone or fails.** The item opens through §5.5's chain instead, a reply action is never sent without text, and the buttons go once the notification is removed. Tests in Tasks 3 and 6.
4. **A page whose items change under it.** A new item turns history to new messages. A new messages page whose items are all read or cleared gives way to the front. History that ages out leaves the front, and so does a tile made small. Tests in Task 4.
5. **A finger that stays.** A long-press on a missed call's button lifts the tile and presses nothing. A held tile sits out ticks and new items until it's let go. In edit mode, a button's tap selects the tile. Tests in Tasks 4 and 6.

---

## File structure

| Path | Module | Adds |
|---|---|---|
| `core/data/` | `:core:data` | `photo_unreadable`, `markNotificationRead`, `cache.db` version 4 |
| `core/tiles/` | `:core:tiles` | `ItemButton`, `TileItem.buttons` and `photoUnreadable`, `TileSource.clear` and `press`, `TileState.history` |
| `core/testing/` | `:core:testing` | The fake source's `cleared` and `pressed`; `testCall()` |
| `feature/notifications/` | `:feature:notifications` | A missed call's buttons, the photo flag, `clear` and `press`, `ItemLauncher.cancel`, `ListenerConnection.cancel` |
| `feature/start/` | `:feature:start` | `TilePages.kt` and `TileSwipe.kt`; pages, swipes, a missed call's buttons and their TalkBack actions in `TileSlot` and `StartTileView` |
| `feature/hub/` | `:feature:hub` | The photo card and the call row |
| `app/` | `:app` | Two pager tests |
| `tools/notifier/` | `:tools:notifier` | A missed call, and a photo LiquiWin can't read |
| `docs/reference/` | — | The values chosen |

As in part 1, `…/` stands for the module's package directory under `src/main/kotlin/` or `src/test/kotlin/`.

**Not in this part:**
- The rest of stage 2, in the next plan: hub pages (§5.6) with their swipes and mark all read, combined tiles and their pages, and the `PEEK`, `CYCLE`, `ICONIC` and `STATIC` templates.
- The Action Center (stage 5).
- An undo for clear. The spec has none, and the chat window still shows a cleared message.
- Stage 3: tile pace, "reduced" motion, and the Roborazzi matrix.

## Choices this plan makes

1. **`TileSource.clear(item)` and `press(item, button)`**, beside part 1's `open`. `clear` reads the item's whole notification in LiquiWin, then asks Android to remove it. `press` sends the button's own `PendingIntent`, and falls back to `open`.
2. **A missed call's buttons reach tiles as `ItemButton(label, isReply)`** on `TileItem.buttons`. They're built from the live action cache, so they're empty once the notification is removed (§5.1) and after a launcher restart. The `PendingIntent`s stay in `LiveActionCache` (§3.2).
3. **A reply button is the reply action itself**, the same `Notification.Action` object. `press` never sends a reply action, because it would go without text.
4. **Pressing a missed call's button reads its item**, as opening it does (§5.3).
5. **`TileSource.items` leaves out your own messages.** A tile's history is then just its read items, sorted as the rotation is. The chat window still has your messages through `chat()`.
6. **Clearing reads the notification's messages itself**, before asking Android to remove it. A notification whose app won't let it go still leaves the tile (§9), and Android's removal, when it comes, marks it removed as usual.
7. **`ListenerConnection` holds the bound listener weakly**, and `ItemLauncher.cancel` reaches `cancelNotification` through it, so no constructor changes. While the listener is unbound, a clear only reads.
8. **Who gets a swipe.** A tile claims a swipe down on the Initial pass, before a large tile's list sees it. It claims a sideways swipe on the Main pass, before start's scroll and the pager. Only the dominant direction counts, once past touch slop.
9. **Thresholds:** a third of the tile's width or height (§5.5), or a flick at 600 dp/s, the pager's. A flick decides the direction, as it does on the pager.
10. **What moves.** On a medium or wide tile, the whole face slides inside the tile. On a large tile, only the row slides. Beneath it is the accent 30 % toward black, the shade of your own bubbles, reading **reply** at its end or **clear** at its start.
11. **A cleared message slides out** and stays out until it leaves the tile, or for 1 s if it doesn't, then slides back. Sliding back or out takes 200 ms. With motion off, both are instant.
12. **Nothing turns under a finger.** A touched tile sits out ticks (§5.4). A new item, and the page change it brings, wait until the finger lifts. A swipe's turn counts as a move, so the tile sits out its next 3 ticks.
13. **With motion off**, a swipe down snaps through newest → front → page → front, using an `atFront` flag beside the page. A new item clears the flag, so the tile shows it, as a moving tile would flip to it (§5.4).
14. **History's list is keyed `"history"`**, apart from the rotation's `"stack"`, so a flip from it to the front still draws it in its first half.
15. **A missed call's buttons are child composables** with 48 × 48 dp layouts at the face's bottom end. A tap that lifts within the long-press timeout presses. A longer hold leaves the tile its long-press. Labels are drawn lowercase, and TalkBack names keep the app's case.
16. **A button press turns the tiles away first**, as opening an item does, then sends.
17. **A large tile's missed-call row** has one line of text, with its buttons along the row's bottom.
18. **An item with buttons has no chat corner.** A swipe left still opens its chat, with the call's buttons there.
19. **The window's call row** presses the newest message's button and closes the window at once, as **open in app** does.
20. **The photo card opens its own message** (`openInApp(item)`), not the newest.
21. **`cache.db` version 4** uses an auto-migration (`photo_unreadable` defaults to 0) and keeps part 1's destructive fallback.
22. **The notifier's photo comes from its own `FileProvider`**, granted to no app, as chat apps do.
23. **TalkBack's swipe actions** show whenever swipes are allowed, whatever start's scroll, since TalkBack scrolls start by other means.

**Downloads to approve:** none.

---

### Task 1: The photo flag and reading a whole notification in `cache.db`

**Files:**
- Modify: `core/data/src/main/kotlin/…/core/data/db/cache/NotificationItemEntity.kt`, `NotificationDao.kt`, `CacheDatabase.kt`
- Test: `core/data/src/test/kotlin/…/core/data/db/cache/NotificationDaoTest.kt`, `CacheDatabaseMigrationTest.kt`
- Generated: `core/data/schemas/io.github.b1tsagar.liquiwin.core.data.db.cache.CacheDatabase/4.json` (commit it)

**Interfaces:**
- Consumes: the chat window's `NotificationDao` and `CacheDatabase` version 3.
- Produces:

```kotlin
// NotificationItemEntity, last, so positional calls in tests still compile
@ColumnInfo(name = "photo_unreadable", defaultValue = "0") val photoUnreadable: Boolean = false, // §5.7

// NotificationDao
/** Reads each of the notification's chats up to its newest message there (§5.5's clear). Leaves `removedAt` alone. */
@Transaction open suspend fun markNotificationRead(notificationKey: String)
```

  - `CacheDatabase` version 4.

- [ ] **Step 1: Write the failing tests.** Add to `NotificationDaoTest`:

```kotlin
    // Review Focus 2: a clear reads the notification whether or not Android then removes it
    @Test fun readingANotificationReadsItsChatsUpToItsNewestMessage() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100), item("m", 150, conversation = "mum")))
        dao.replaceNotification("n2", listOf(item("b", 200, notification = "n2")))
        dao.markNotificationRead("n1")
        assertEquals(listOf("b"), unread())
        assertNull(dao.item("a")!!.removedAt)
    }

    @Test fun aRepostedItemKeepsItsPhotoFlag() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100).copy(photoUnreadable = true)))
        dao.replaceNotification("n1", listOf(item("a", 100))) // the intake reads photos only for new messages
        assertTrue(dao.item("a")!!.photoUnreadable)
    }
```

  Add to `CacheDatabaseMigrationTest`:

```kotlin
    @Test fun aVersion3DatabaseKeepsItsNotificationsWithNoPhotoFlags() = runTest {
        createVersion(3, context.getDatabasePath("cache.db")) {
            execSQL(
                "INSERT INTO notification_item (`key`, notification_key, source_id, conversation_key, sender, posted_at, is_self) " +
                    "VALUES ('a', 'n1', 'app:com.whatsapp@0', 'alex', 'Alex', 100, 0)",
            )
        }
        val db = CacheDatabase.open(context)
        try {
            assertFalse(db.notificationDao().item("a")!!.photoUnreadable)
        } finally {
            db.close()
        }
    }
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*NotificationDaoTest" --tests "*CacheDatabaseMigrationTest"`
Expected: FAIL to compile, because `photoUnreadable` and `markNotificationRead` don't exist.

- [ ] **Step 3: Add the column and the query.**
  - **`markNotificationRead`** reads `showingItemsOf(notificationKey)`, since a notification already removed was read by its removal. It groups them by source and conversation, and calls `markRead` up to each group's newest `postedAt`.
  - **`markRemoved`** keeps its empty check and `setRemoved`. Its loop moves to a private helper that both use.
  - **`replaceNotification`'s refresh** already keeps a stored item's other columns, so the flag survives a re-post. Don't add it to `refresh`.
  - **`CacheDatabase`**: `version = 4`, and `AutoMigration(from = 3, to = 4)` after the chat window's.

- [ ] **Step 4: Run them again.**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS, and `core/data/schemas/…CacheDatabase/4.json` exists.

- [ ] **Step 5: Commit.**

```bash
git add core/data
git commit -m "Store which photos LiquiWin couldn't read, and read a whole notification"
```

---

### Task 2: A missed call's buttons and unreadable photos at intake

**Files:**
- Modify: `feature/notifications/src/main/kotlin/…/feature/notifications/Normaliser.kt`, `LiveActionCache.kt`, `NotificationIntake.kt`
- Test: `feature/notifications/src/test/kotlin/…/feature/notifications/TestNotifications.kt`, `NormaliserTest.kt`, `NotificationIntakeTest.kt`

**Interfaces:**
- Consumes: Task 1's `photoUnreadable`.
- Produces:

```kotlin
data class Normalised(
    // … as before …
    val buttons: List<Notification.Action> = emptyList(), // a missed call's, up to two, in the app's order (§5.1)
)
data class LiveActions(val contentIntent: PendingIntent?, val reply: Notification.Action?, val buttons: List<Notification.Action> = emptyList())

// TestNotifications.kt
/** A dialer's missed call from Alex, with [labels] as its buttons, then [replyLabel]'s button that takes a reply. */
internal fun missedCall(labels: List<String> = listOf("Call back", "Message"), replyLabel: String? = null): Notification
```

- [ ] **Step 1: Write the failing tests.** Add `missedCall` to `TestNotifications.kt`: `builder()` with title "Alex", text "Missed call", category `NotificationCompat.CATEGORY_MISSED_CALL`, and an `action(label)` for each label, then `action(replyLabel, Action.SEMANTIC_ACTION_REPLY, RemoteInput.Builder("reply").build())` if there's one. Then add to `NormaliserTest`:

```kotlin
    @Test fun aMissedCallKeepsItsFirstTwoButtonsInTheAppsOrder() {
        val call = normalise("com.samsung.android.dialer", missedCall(listOf("Call back", "Message", "Block")))!!
        assertEquals(listOf("Call back", "Message"), call.buttons.map { it.title.toString() })
    }

    @Test fun aMissedCallsReplyButtonIsItsReplyAction() {
        val call = normalise("com.google.android.dialer", missedCall(listOf("Call back"), replyLabel = "Reply"))!!
        assertSame(call.replyAction, call.buttons[1])
    }

    @Test fun otherNotificationsHaveNoButtons() {
        assertEquals(emptyList<Notification.Action>(), normalise("com.whatsapp", whatsAppChat())!!.buttons)
    }
```

  Add to `NotificationIntakeTest`:

```kotlin
    @Test fun aMissedCallsButtonsAreKeptWithItsLiveActions() = runTest {
        val call = sbn("com.samsung.android.dialer", missedCall())
        intake().posted(call)
        items("app:com.samsung.android.dialer@0") { it.size == 1 }
        assertEquals(listOf("Call back", "Message"), actions.actions.value.getValue(call.key).buttons.map { it.title.toString() })
    }

    @Test fun aPhotoLiquiWinCantReadIsStoredAsOne() = runTest {
        intake().posted(sbn("org.telegram.messenger", telegramPhoto())) // nothing grants LiquiWin its provider
        val stored = items("app:org.telegram.messenger@0") { it.size == 1 }.single().item
        assertTrue(stored.photoUnreadable)
        assertNull(stored.imageFile)
    }

    @Test fun aPhotoLiquiWinCanReadIsStoredAsAnImage() = runTest {
        intake().posted(sbn("com.instagram.android", instagramLike()))
        val stored = items("app:com.instagram.android@0") { it.size == 1 }.single().item
        assertFalse(stored.photoUnreadable)
        assertNotNull(stored.imageFile)
    }
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:notifications:testDebugUnitTest --tests "*NormaliserTest" --tests "*NotificationIntakeTest"`
Expected: FAIL to compile, because `missedCall` and `buttons` don't exist.

- [ ] **Step 3: Keep the buttons and note the photos.**
  - **`normalise`**: for `notification.category == "missed_call"`, `buttons` are the actions with a non-blank title and an `actionIntent`, `take(2)`. The string is a private constant, because `Notification.CATEGORY_MISSED_CALL` is API 30. Other notifications get none.
  - **The intake** puts `LiveActions(contentIntent, replyAction, buttons)`. It sets `photoUnreadable = isNew && item.image != null && imageFile == null`. `ImageStore.save` already returns null for any image it can't read.

- [ ] **Step 4: Run the tests again.**

Run: `./gradlew :feature:notifications:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add feature/notifications
git commit -m "Keep a missed call's buttons and note photos LiquiWin can't read"
```

---

### Task 3: Clearing and pressing through tile sources, and a tile's history

**Files:**
- Modify: `core/tiles/src/main/kotlin/…/core/tiles/source/TileSource.kt`, `core/tiles/src/main/kotlin/…/core/tiles/source/TileState.kt`, `core/testing/src/main/kotlin/…/core/testing/FakeTileSource.kt`, `feature/notifications/src/main/kotlin/…/feature/notifications/AppNotificationSource.kt`, `ItemLauncher.kt`, `LiquiWinNotificationListener.kt`
- Test: `core/tiles/src/test/kotlin/…/core/tiles/source/TileStateTest.kt`, `feature/notifications/src/test/kotlin/…/feature/notifications/AppNotificationSourceTest.kt`, `LiquiWinNotificationListenerTest.kt`

**Interfaces:**
- Consumes: Task 1's `markNotificationRead` and `photoUnreadable`, and Task 2's `LiveActions.buttons`.
- Produces:

```kotlin
/** A missed call's button (§5.1). [isReply] buttons open the chat window instead of being pressed. */
data class ItemButton(val label: String, val isReply: Boolean)

data class TileItem(
    // … as before …
    val buttons: List<ItemButton> = emptyList(), // while its notification is still posted
    val photoUnreadable: Boolean = false, // §5.7's card
)

interface TileSource {
    val items: Flow<List<TileItem>> // newest first, without hidden chats or your own messages
    // … as before …
    suspend fun clear(item: TileItem) // §5.5: reads its notification's messages, then removes it from Android's panel
    suspend fun press(item: TileItem, button: Int) // §5.5: opens the item instead if the button has gone, fails or takes a reply
}

data class TileState(
    val rotation: List<TileItem>,
    val unread: Int,
    val hiddenChats: List<HiddenChat> = emptyList(),
    val history: List<TileItem> = emptyList(), // every source's read items, sorted as the rotation is (§5.3)
)

// ItemLauncher
fun cancel(notificationKey: String) // AndroidItemLauncher: ListenerConnection.cancel

// ListenerConnection
internal fun set(connected: Boolean, listener: NotificationListenerService? = null) // held weakly
/** Removes the notification from Android's panel, as a swipe there would. Does nothing while no listener is bound. */
fun cancel(notificationKey: String)

// core/testing
class FakeTileSource { val cleared: MutableList<TileItem>; val pressed: MutableList<Pair<TileItem, Int>> /* … */ }
fun testCall(
    key: String = "call",
    at: Long = 0,
    buttons: List<ItemButton> = listOf(ItemButton("Call back", false), ItemButton("Message", false)),
    source: SourceId = SourceId("app:com.example.chat@0"),
): TileItem // testItem(key, title = "Alex", text = "missed call", at, source = source) with [buttons]
```

- [ ] **Step 1: Write the failing tests.** Add to `TileStateTest`:

```kotlin
    @Test fun historyIsEverySourcesReadItemsNewestFirst() = runTest {
        a.itemState.value = listOf(testItem("a2", at = 30, unread = false, source = a.id), testItem("a1", at = 10, source = a.id))
        b.itemState.value = listOf(testItem("b1", at = 20, unread = false, source = b.id))
        val state = tileState(listOf(a, b)).first()
        assertEquals(listOf("a2", "b1"), state.history.map { it.key })
        assertEquals(listOf("a1"), state.rotation.map { it.key })
    }
```

  In `AppNotificationSourceTest`, `stored()` gains `self: Boolean = false` and `photoUnreadable: Boolean = false`, and `RecordingLauncher` gains `val cancelled = mutableListOf<String>()`. Add a helper that builds a button with its own intent, `button(title) = Notification.Action.Builder(null, title, PendingIntent.getBroadcast(context, title.hashCode(), Intent(title), FLAG_IMMUTABLE)).build()`, then:

```kotlin
    // Review Focus 2
    @Test fun clearingReadsItsNotificationsMessagesAndAsksAndroidToRemoveIt() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        dao.replaceNotification("n2", listOf(stored("b", 200, notification = "n2")))
        source.clear(source.items.first().single { it.key == "a" })
        assertEquals(listOf("n1"), launcher.cancelled)
        assertEquals(listOf("b"), source.items.first().filter { it.unread }.map { it.key })
        dao.markRemoved("n1", now = 300) // Android's removal, when it comes
        assertEquals(listOf("a", "b"), source.chat("alex").first()!!.messages.map { it.item.key }) // the window still has it
    }

    @Test fun itemsLeaveOutYourOwnMessagesButTheChatKeepsThem() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100), stored("me", 150, self = true)))
        assertEquals(listOf("a"), source.items.first().map { it.key })
        assertEquals(listOf("a", "me"), source.chat("alex").first()!!.messages.map { it.item.key })
    }

    // Review Focus 3
    @Test fun aMissedCallsButtonsShowWhileItsNotificationIsPosted() = runTest {
        dao.replaceNotification("n1", listOf(stored("c", 100)))
        actions.put("n1", LiveActions(intent, reply, listOf(button("Call back"), reply)))
        assertEquals(listOf(ItemButton("Call back", isReply = false), ItemButton("Reply", isReply = true)), source.items.first().single().buttons)
        dao.markRemoved("n1", now = 200)
        assertEquals(emptyList<ItemButton>(), source.items.first().single().buttons)
    }

    @Test fun pressingAButtonSendsItsIntentAndReadsTheItem() = runTest {
        dao.replaceNotification("n1", listOf(stored("c", 100)))
        val callBack = button("Call back")
        actions.put("n1", LiveActions(intent, null, listOf(callBack)))
        source.press(source.items.first().single(), 0)
        assertEquals(listOf(callBack.actionIntent), launcher.sent)
        assertFalse(source.items.first().single().unread)
    }

    // Review Focus 3
    @Test fun aButtonThatFailsOpensTheItemInstead() = runTest {
        dao.replaceNotification("n1", listOf(stored("c", 100)))
        actions.put("n1", LiveActions(intent, null, listOf(button("Call back"))))
        launcher.sendWorks = false
        source.press(source.items.first().single(), 0)
        assertEquals(listOf(Triple("com.whatsapp", "alex", 0L)), launcher.shortcuts)
    }

    // Review Focus 3
    @Test fun aButtonWhoseNotificationHasGoneOpensTheItem() = runTest {
        dao.replaceNotification("n1", listOf(stored("c", 100)))
        val callBack = button("Call back")
        actions.put("n1", LiveActions(intent, null, listOf(callBack)))
        val item = source.items.first().single()
        dao.markRemoved("n1", now = 200)
        actions.remove("n1")
        source.press(item, 0)
        assertEquals(listOf(Triple("com.whatsapp", "alex", 0L)), launcher.shortcuts)
        assertFalse(callBack.actionIntent in launcher.sent)
    }

    // Review Focus 3
    @Test fun aReplyButtonIsNeverSentWithoutText() = runTest {
        dao.replaceNotification("n1", listOf(stored("c", 100)))
        val replyButton = button("Reply") // its own intent, unlike the file's `reply`, which shares the content intent
        actions.put("n1", LiveActions(intent, replyButton, listOf(button("Call back"), replyButton)))
        source.press(source.items.first().single(), 1)
        assertEquals(listOf(intent), launcher.sent) // opened through its content intent instead
        assertEquals(emptyList<Pair<Notification.Action, String>>(), launcher.replies)
    }

    @Test fun anItemSaysWhenItsPhotoCouldntBeRead() = runTest {
        dao.replaceNotification("n1", listOf(stored("p", 100, photoUnreadable = true)))
        assertTrue(source.items.first().single().photoUnreadable)
    }
```

  Add to `LiquiWinNotificationListenerTest`:

```kotlin
    // Review Focus 2. A plain listener, since ShowingListener replaces the active notifications Robolectric's shadow keeps.
    @Test fun aBoundListenerRemovesANotificationAndAnUnboundOneDoesNothing() {
        val bound = Robolectric.buildService(LiquiWinNotificationListener::class.java).create().get()
        bound.onListenerConnected()
        shadowOf(bound).addActiveNotification("com.whatsapp", 1, whatsAppChat())
        ListenerConnection.cancel(bound.activeNotifications.single().key)
        assertEquals(0, bound.activeNotifications.size)
        bound.onListenerDisconnected()
        shadowOf(bound).addActiveNotification("com.whatsapp", 2, whatsAppChat())
        ListenerConnection.cancel(bound.activeNotifications.single().key)
        assertEquals(1, bound.activeNotifications.size)
    }
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :core:tiles:testDebugUnitTest :feature:notifications:testDebugUnitTest`
Expected: FAIL to compile, because `history`, `ItemButton`, `clear`, `press`, `cancelled` and `ListenerConnection.cancel` don't exist.

- [ ] **Step 3: Implement clearing, pressing and history.**
  - **`tileState`** adds `history`: the items that aren't unread, with the rotation's comparator.
  - **`AppNotificationSource`**:
    - `items` drops stored items that are `isSelf`.
    - `toItem` sets `buttons` while `removedAt == null`, from `live[notificationKey]?.buttons`, as `ItemButton(action.title.toString(), action === live.reply)`. It copies `photoUnreadable`.
    - `clear` looks up the stored item's notification key, calls `dao.markNotificationRead` and then `launcher.cancel`.
    - `press` marks the item read. It takes the button's action while the item isn't removed. If there's none, the action is the reply action, or `launcher.send` fails, it calls `open(item)`.
  - **`ListenerConnection`** keeps `@Volatile private var listener: WeakReference<NotificationListenerService>?`. `set` replaces it, and `set(false)` clears it. `cancel` calls `cancelNotification` inside `runCatching`, logging only that it failed.
  - **`LiquiWinNotificationListener`** passes `this` in `onListenerConnected`. `onListenerDisconnected` calls `set(false)`.
  - **Every `ItemLauncher`** gains `cancel`, and every `TileSource` gains `clear` and `press`. The fake's record what they're given and do nothing else.

- [ ] **Step 4: Run the tests again.**

Run: `./gradlew :core:tiles:testDebugUnitTest :core:testing:testDebugUnitTest :feature:notifications:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add core/tiles core/testing feature/notifications
git commit -m "Clear and press items through their sources, and give tiles their history"
```

---

### Task 4: Pages and the swipe down

**Files:**
- Create: `feature/start/src/main/kotlin/…/feature/start/live/TilePages.kt`, `feature/start/src/main/kotlin/…/feature/start/live/TileSwipe.kt`
- Modify: `feature/start/src/main/kotlin/…/feature/start/live/TileFaces.kt`, `StartScreen.kt` (`TileSlot` and `StartController`), `StartTileView.kt`
- Test: `feature/start/src/test/kotlin/…/feature/start/live/TilePagesTest.kt` (new), `TileSwipeTest.kt` (new), `TileFacesTest.kt`, `feature/start/src/test/kotlin/…/feature/start/LiveTilesTest.kt`, `StartScreenTest.kt`

**Interfaces:**
- Consumes: Task 3's `TileState.history`.
- Produces:

```kotlin
// TilePages.kt
enum class TilePage { NEW, HISTORY }
/** The page a swipe down from the front turns to (§5.3), or null for a small tile or one with nothing for either. */
internal fun pageFor(size: TileSize, state: TileState?): TilePage?
/** [page] once the tile's items change: a new item turns history to new messages, and a page with nothing left gives way. */
internal fun settle(page: TilePage?, size: TileSize, state: TileState?): TilePage?
/** The faces on [page], without the front; on the front, part 1's faces. A page with nothing on it shows the front. */
internal fun pageFaces(size: TileSize, state: TileState?, page: TilePage?): List<TileFace>

// TileSwipe.kt
internal const val FLING_DP_PER_S = 600f // the pager's flick
/** Whether letting go of a swipe down of [drag] px at [velocity] px/s turns a tile [heightPx] tall (§5.5). */
internal fun turnsDown(drag: Float, velocity: Float, heightPx: Float, flingPx: Float): Boolean

// TileFaces.kt
data class Stack(val items: List<TileItem>, override val key: String = "stack") : TileFace // history's is "history"

// StartTileView gains, after `tile`'s other parameters
label: String = tile.label, // "history" on the history page (§5.3)

// StartController
var startAtTop: () -> Boolean = { true } // StartScreen sets { scrollState.value == 0 }
```

- [ ] **Step 1: Write the failing JVM tests.** Create `TilePagesTest`:

```kotlin
class TilePagesTest {
    private val new = testItem("n", at = 20)
    private val old = testItem("o", at = 10, unread = false)
    private fun state(rotation: List<TileItem> = emptyList(), history: List<TileItem> = emptyList()) =
        TileState(rotation, rotation.size, history = history)

    @Test fun aSwipeDownTurnsToNewMessagesElseHistoryElseNothing() {
        assertEquals(TilePage.NEW, pageFor(TileSize.MEDIUM, state(listOf(new), listOf(old))))
        assertEquals(TilePage.HISTORY, pageFor(TileSize.WIDE, state(history = listOf(old))))
        assertNull(pageFor(TileSize.LARGE, state()))
        assertNull(pageFor(TileSize.SMALL, state(listOf(new))))
        assertNull(pageFor(TileSize.MEDIUM, null))
    }

    // Review Focus 4
    @Test fun aNewItemTurnsHistoryToNewMessages() {
        assertEquals(TilePage.NEW, settle(TilePage.HISTORY, TileSize.MEDIUM, state(listOf(new), listOf(old))))
        assertEquals(TilePage.HISTORY, settle(TilePage.HISTORY, TileSize.MEDIUM, state(history = listOf(old))))
        assertEquals(TilePage.NEW, settle(TilePage.NEW, TileSize.MEDIUM, state(listOf(new))))
    }

    // Review Focus 4
    @Test fun aPageWithNothingLeftGivesWayToTheFront() {
        assertNull(settle(TilePage.NEW, TileSize.WIDE, state(history = listOf(old)))) // all read or cleared
        assertNull(settle(TilePage.HISTORY, TileSize.WIDE, state())) // aged out
        assertNull(settle(TilePage.NEW, TileSize.SMALL, state(listOf(new)))) // made small
        assertNull(settle(null, TileSize.WIDE, state(listOf(new))))
    }

    @Test fun aPageTurnsThroughItsItemsWithoutTheFront() {
        val read = (7 downTo 1).map { testItem("i$it", at = it.toLong(), unread = false) }
        assertEquals(listOf("i7", "i6", "i5", "i4", "i3"), pageFaces(TileSize.MEDIUM, state(history = read), TilePage.HISTORY).map { it.key })
        assertEquals(listOf("history"), pageFaces(TileSize.LARGE, state(history = read), TilePage.HISTORY).map { it.key })
        assertEquals(listOf("stack"), pageFaces(TileSize.LARGE, state(listOf(new)), TilePage.NEW).map { it.key })
        assertEquals(listOf("front", "n"), pageFaces(TileSize.MEDIUM, state(listOf(new)), null).map { it.key })
        assertEquals(listOf("front"), pageFaces(TileSize.MEDIUM, state(), TilePage.NEW).map { it.key }) // until it settles
    }
}
```

  Create `TileSwipeTest`:

```kotlin
class TileSwipeTest {
    @Test fun aSwipeDownTurnsPastAThirdOfTheTileOrAsAFlick() {
        assertTrue(turnsDown(drag = 100f, velocity = 0f, heightPx = 300f, flingPx = 600f))
        assertFalse(turnsDown(drag = 99f, velocity = 0f, heightPx = 300f, flingPx = 600f))
        assertTrue(turnsDown(drag = 20f, velocity = 600f, heightPx = 300f, flingPx = 600f))
        assertFalse(turnsDown(drag = 200f, velocity = -900f, heightPx = 300f, flingPx = 600f)) // flicked back up
    }
}
```

  Add to `TileFacesTest`:

```kotlin
    @Test fun aGoneFaceGivesWayToThePagesFirst() {
        val page = items.take(2).map { TileFace.Item(it) }
        assertEquals(page.first(), nextFace(page, current = "gone"))
    }
```

- [ ] **Step 2: Write the failing Compose tests.** In `LiveTilesTest`, `show()` passes a `private val scroll = ScrollState(0)` and `onChat = { chats += it }`, with `private val chats = mutableListOf<TileItem>()`. Add:

```kotlin
    // A swipe across [x] of the tile's width and [y] of its height from its centre. 300 ms passes touch slop well
    // inside the 400 ms long-press timeout, even on a short swipe, and is no flick; 20 ms is one.
    private fun swipe(tile: Int, x: Float = 0f, y: Float = 0f, ms: Long = 300) {
        compose.onNodeWithTag("tile-$tile").performTouchInput { swipe(center, center + Offset(x * width, y * height), ms) }
        compose.mainClock.advanceTimeBy(100)
    }

    private fun motionOff() = Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

    // §5.3: a page stays until it's swiped back, where a front with a rotation turns on the timer
    @Test fun aSwipeDownTurnsATileToItsNewMessagesUntilTheNextOne() {
        chat.itemState.value = listOf(testItem("b", text = "running late", at = 20, source = chat.id))
        show()
        swipe(1, y = 0.5f)
        assertTrue(saidOver(1, 30_000).all { it == "1 unread, Alex: running late" })
        swipe(1, y = 0.5f)
        assertEquals("1 unread", said(1))
    }

    // §5.4: a page of two or more items turns on the timer, never to the front
    @Test fun aPageOfTwoTurnsBetweenItsItemsButNeverToTheFront() {
        chat.itemState.value = listOf(testItem("b", text = "running late", at = 20, source = chat.id), testItem("a", at = 10, source = chat.id))
        show()
        swipe(1, y = 0.5f)
        assertEquals(setOf("2 unread, Alex: running late", "2 unread, Alex: see you at 6"), saidOver(1, 30_000).toSet())
    }

    @Test fun withNothingNewASwipeDownShowsTheHistory() {
        chat.itemState.value = listOf(testItem("a", text = "see you", at = 10, unread = false, source = chat.id))
        show()
        assertEquals(listOf("show history"), actions(1))
        swipe(1, y = 0.5f)
        assertEquals("history, Alex: see you", said(1))
        assertEquals(listOf("open App 1", "chat with Alex", "show front"), actions(1))
    }

    // Review Focus 1
    @Test fun withStartScrolledDownASwipeDownScrollsStartInstead() {
        motionOff()
        chat.itemState.value = listOf(testItem("b", at = 20, source = chat.id))
        show(pinned = 21)
        swipe(4, y = -0.3f)
        assertTrue(scroll.value > 0)
        swipe(1, y = 0.5f)
        assertEquals(0, scroll.value)
        assertEquals("1 unread, Alex: see you at 6", said(1)) // still its newest, not turned to its front
    }

    // Review Focus 1
    @Test fun aTileWithNothingForEitherPageHasNone() {
        show()
        assertEquals(emptyList<String>(), actions(1))
        swipe(1, y = 0.5f)
        assertNull(said(1))
    }

    @Test fun aSwipeDownScrollsALargeTilesListBackBeforeItTurnsTheTile() {
        motionOff()
        showLarge(messages = 7)
        page(1, "older messages")
        swipe(1, y = 1f)
        assertEquals("7 unread, P6: t6, P5: t5, P4: t4", said(1)) // back at its top, and still the list
        swipe(1, y = 0.5f)
        assertEquals("7 unread", said(1))
    }

    @Test fun withMotionOffASwipeDownStepsThroughNewestFrontAndPage() {
        motionOff()
        chat.itemState.value = listOf(testItem("b", text = "running late", at = 20, source = chat.id))
        show()
        assertEquals("1 unread, Alex: running late", said(1))
        swipe(1, y = 0.5f)
        assertEquals("1 unread", said(1))
        swipe(1, y = 0.2f, ms = 20) // a flick
        assertEquals("1 unread, Alex: running late", said(1))
        swipe(1, y = 0.2f) // short and slow
        assertEquals("1 unread, Alex: running late", said(1))
    }

    @Test fun withMotionOffANewItemShowsEvenAfterASwipeToTheFront() {
        motionOff()
        chat.itemState.value = listOf(testItem("a", at = 10, source = chat.id))
        show()
        swipe(1, y = 0.5f)
        assertEquals("1 unread", said(1))
        chat.itemState.value = listOf(testItem("b", text = "running late", at = 20, source = chat.id)) + chat.itemState.value
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("2 unread, Alex: running late", said(1))
    }

    @Test fun talkBackCanTurnATileToItsPagesAndBack() {
        motionOff()
        chat.itemState.value = listOf(testItem("a", text = "see you", at = 10, unread = false, source = chat.id))
        show()
        page(1, "show history")
        assertEquals("history, Alex: see you", said(1))
        page(1, "show front")
        assertNull(said(1))
        assertEquals(listOf("show history"), actions(1))
    }

    // Review Focus 4
    @Test fun aNewMessagesPageWhoseItemsAreAllReadGivesWayToTheFront() {
        motionOff()
        chat.itemState.value = listOf(testItem("b", at = 20, source = chat.id))
        show()
        page(1, "show front")
        page(1, "show new messages")
        chat.itemState.value = listOf(testItem("b", at = 20, unread = false, source = chat.id))
        compose.mainClock.advanceTimeBy(1_000)
        assertNull(said(1))
        assertEquals(listOf("show history"), actions(1))
    }

    // Review Focus 5: §5.4's touched tile
    @Test fun aTileSitsOutTheTicksWhileAFingerIsOnIt() {
        chat.itemState.value = listOf(testItem("b", at = 20, source = chat.id), testItem("a", at = 10, source = chat.id))
        show()
        compose.onNodeWithTag("tile-1").performTouchInput { down(center); moveBy(Offset(0f, height / 6f)) }
        assertTrue(saidOver(1, 20_000).all { it == "2 unread" })
        compose.onNodeWithTag("tile-1").performTouchInput { moveBy(Offset(0f, -height / 6f)); up() }
        assertTrue(saidOver(1, 20_000).any { it != "2 unread" })
    }

    // Review Focus 4 and 5: a new item turns history to it, once the finger lifts
    @Test fun aNewItemTurnsAHistoryPageToItOnceTheFingerLifts() {
        chat.itemState.value = listOf(testItem("a", text = "see you", at = 10, unread = false, source = chat.id))
        show()
        page(1, "show history")
        compose.onNodeWithTag("tile-1").performTouchInput { down(center); moveBy(Offset(0f, height / 6f)) }
        chat.itemState.value = listOf(testItem("b", text = "running late", at = 20, source = chat.id)) + chat.itemState.value
        assertTrue(saidOver(1, 3_000).all { it == "history, 1 unread, Alex: see you" })
        compose.onNodeWithTag("tile-1").performTouchInput { moveBy(Offset(0f, -height / 6f)); up() }
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals("1 unread, Alex: running late", said(1))
    }
```

  `talkBackHearsALargeTilesRowsInViewAndPagesThroughTheRest` now ends its list with `"show front"`. `pausedTilesShowOnlyTheirFront` also asserts `actions(1)` is empty, since a paused tile takes no swipes (Review Focus 1). Add to `StartScreenTest`:

```kotlin
    // Review Focus 1: a guard, passing today, that must still pass
    @Test fun inEditModeASwipeDownTurnsNoTile() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        cameraSource.itemState.value = listOf(testItem("a", source = cameraSource.id))
        show()
        compose.onNodeWithTag("tile-2").performTouchInput { longClick() }
        compose.onNodeWithTag("tile-1").performTouchInput { swipeDown() }
        saysCamera("1 unread, Alex: see you at 6")
    }
```

- [ ] **Step 3: Run them and watch them fail.**

Run: `./gradlew :feature:start:testDebugUnitTest`
Expected: FAIL to compile, because `TilePage`, `pageFor`, `settle`, `pageFaces` and `turnsDown` don't exist.

- [ ] **Step 4: Add pages and the swipe down.**
  - **`TileFace.Stack`** takes its key, and **`nextFace`** gives `faces.first()` for a face that has gone.
  - **`pageFaces`** is part 1's `faces()` on the front. On a page it's `faces()` of the page's items with the front dropped, and history's stack is keyed `"history"`. An empty list gives `listOf(TileFace.Front)`.
  - **`StartScreen`** sets `controller.startAtTop` in its `SideEffect`.
  - **`TileSlot`'s state:** it gains `page`, `atFront` and `touched`, each `remember { mutableStateOf(…) }`, and `swipes = edit == null && !liveTilesPaused && !tile.isPaused`.
  - **`TileSlot`'s faces:**
    - `tileFaces = pageFaces(tile.size, live, page)`.
    - With motion off, `face` is the front while `atFront`, else the page's first face on a page, else part 1's `getOrElse(1)`.
    - `drawn` looks `liveFace.from` up in `faces(tile.size, rotation) + pageFaces(tile.size, live, TilePage.HISTORY)`, since a new messages page's faces are among the first.
    - The gone-face snap goes to `tileFaces.first()`. Its comment says a page's faces give way to the page's first, and the front's to the front.
  - **Settling.** `val settled = settle(page, tile.size, live)`. A `LaunchedEffect(settled)` returns at once if `settled == page`. Otherwise it waits for `!touched`, then sets `page = settle(page, current.size, currentLive)` afresh, since the lift may have turned the tile meanwhile. When that's history turning to new messages while the tile moves and is on screen, it flips to the page's first face in the same step. That way the gone-face snap finds the face already there, and the new item flips in rather than snapping.
  - **`turnDown()`** runs on `scope`.
    - From a page or a content face (`page != null || face != TileFace.Front`), it sets `page = null` and goes to the front. From the front, it sets `page = pageFor(…)` and goes to that page's first face.
    - It writes `page` and starts the turn in the same step, before `flipTo`'s first suspension. With motion off, it sets `atFront` instead of turning.
    - It calls `controller.ticker.jumped(tile.id, now)`.
  - **Holding still.** `LiveSlot`'s `held` becomes `{ listScrolled || touched }`. The jump collector waits for `!touched` after its delay. Each new item it sees clears `atFront`, before its `moving` filter.
  - **History.** On the history page, `StartTileView` gets `label = "history"`, and `said` starts with "history".
  - **TalkBack**, only while `swipes`:
    - On the front, with `page == null`, the tile gets **show new messages** or **show history** for `pageFor(…)`.
    - Every other face gets **show front**, after its other actions.
    - Each calls `turnDown()`.
  - **Fresh values.** `pointerInput(tile.id)` keeps its first lambda, so what it reads that changes, `swipes`, `turnDown` and whether motion is on, goes through `rememberUpdatedState`, as `tapTarget` and `chatTarget` do. `page`, `touched` and `listScrolled` are state already.
  - **The gesture.** Outside edit mode, and on a tile that isn't lifted, `TileSlot` replaces `waitForUpOrCancellation` with its own loop. Each event is read on the Initial pass, then the Main pass, then the Final pass:

```kotlin
val down = awaitFirstDown(requireUnconsumed = false)
touched = true
try {
    val turns = swipes && controller.startAtTop() && pageFor(current.size, currentLive) != null &&
        !listScrolled // a large tile's list scrolls back first (§5.5)
    var moved = Offset.Zero
    var claimedDown = false
    var cancelled = false
    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            // Initial: this tile sees the event before a large tile's list, so the list never starts a swipe down it takes.
            val early = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            if (early == null) { cancelled = true; break }
            moved += early.position - early.previousPosition
            if (turns && early.pressed && moved.y >= viewConfiguration.touchSlop && moved.y > abs(moved.x)) { // >=, as start's scroll
                early.consume(); claimedDown = true; break
            }
            // Main and Final: as waitForUpOrCancellation. A child button, start's scroll or the pager may have it.
            val change = awaitPointerEvent(PointerEventPass.Main).changes.first { it.id == down.id }
            if (change.isConsumed || change.isOutOfBounds(size, extendedTouchPadding)) { cancelled = true; break }
            if (change.changedToUp()) return@withTimeoutOrNull change
            if (awaitPointerEvent(PointerEventPass.Final).changes.first { it.id == down.id }.isConsumed) { cancelled = true; break }
        }
        null
    }
    when {
        cancelled -> return@awaitEachGesture
        claimedDown -> { /* follow the finger on the Initial pass, consuming each change and feeding a VelocityTracker;
                            on the lift, turnDown() if turnsDown(drag, velocity, size.height, FLING_DP_PER_S.dp.toPx()) */ }
        up != null -> { /* part 1's tap and chat routing, as before */ }
        else -> { /* part 1's long-press, as before */ }
    }
} finally {
    touched = false
}
```

- [ ] **Step 5: Run the tests again.**

Run: `./gradlew :feature:start:testDebugUnitTest`
Expected: PASS. If a swipe test reads the wrong face or lands in edit mode, check its swipe's speed before the code: a swipe that passes touch slop after the 400 ms long-press timeout is a long-press.

- [ ] **Step 6: Commit.**

```bash
git add feature/start
git commit -m "Turn tiles to their new messages or history with a swipe down"
```

---

### Task 5: Sideways swipes on a message

**Files:**
- Modify: `feature/start/src/main/kotlin/…/feature/start/live/TileSwipe.kt`, `StartScreen.kt`, `StartTileView.kt`, `StartViewModel.kt`
- Test: `feature/start/src/test/kotlin/…/feature/start/live/TileSwipeTest.kt`, `feature/start/src/test/kotlin/…/feature/start/LiveTilesTest.kt`, `StartTileViewTest.kt`, `StartViewModelTest.kt`, `app/src/test/kotlin/…/home/HomeRootTest.kt`

**Interfaces:**
- Consumes: Task 3's `TileSource.clear` and `FakeTileSource.cleared`. Task 4's `TilePage`, `FLING_DP_PER_S`, `touched` and gesture loop.
- Produces:

```kotlin
// TileSwipe.kt
enum class Sideways { CHAT, CLEAR }
/** What letting go of a sideways swipe does (§5.5). Null slides the message back. */
internal fun sideways(drag: Float, velocity: Float, widthPx: Float, flingPx: Float, canClear: Boolean): Sideways?
/** The message under a sideways swipe, moved [offsetPx] from its place (negative is left). */
data class MessageSwipe(val key: String, val offsetPx: Float)

// StartTileView gains
swipe: MessageSwipe? = null,

// StartViewModel
fun clear(item: TileItem) // through its source, on viewModelScope
// StartController
fun clear(item: TileItem)
```

- [ ] **Step 1: Write the failing tests.** Add to `TileSwipeTest`:

```kotlin
    @Test fun aSidewaysSwipeActsPastAThirdOfTheTileOrAsAFlick() {
        fun let(drag: Float, velocity: Float = 0f, canClear: Boolean = true) =
            sideways(drag, velocity, widthPx = 300f, flingPx = 600f, canClear = canClear)
        assertEquals(Sideways.CHAT, let(-100f))
        assertEquals(Sideways.CLEAR, let(100f))
        assertNull(let(-99f))
        assertNull(let(99f))
        assertEquals(Sideways.CHAT, let(-20f, velocity = -600f))
        assertEquals(Sideways.CLEAR, let(-150f, velocity = 900f)) // the flick decides, as on the pager
        assertNull(let(150f, canClear = false)) // a history page's messages only open
        assertEquals(Sideways.CHAT, let(-150f, canClear = false))
    }
```

  Add to `StartTileViewTest`:

```kotlin
    @Test fun aSwipedMessageShowsWhatLettingGoWillDo() {
        var swipe: MessageSwipe? by mutableStateOf(MessageSwipe("a", -60f))
        compose.setContent { LiquiWinTheme { StartTileView(tile(TileSize.MEDIUM), metrics, { null }, face = TileFace.Item(testItem("a")), swipe = swipe) } }
        compose.onNodeWithText("reply").assertIsDisplayed()
        swipe = MessageSwipe("a", 60f)
        compose.onNodeWithText("clear").assertIsDisplayed()
        swipe = null
        compose.onNodeWithText("reply").assertDoesNotExist()
        compose.onNodeWithText("clear").assertDoesNotExist()
    }
```

  Add to `LiveTilesTest`:

```kotlin
    @Test fun aSwipeLeftOnAMessageOpensItsChat() {
        motionOff()
        val b = testItem("b", text = "running late", at = 20, source = chat.id)
        chat.itemState.value = listOf(b)
        show()
        swipe(1, x = -0.5f)
        assertEquals(listOf(b), chats)
        assertEquals("1 unread, Alex: running late", said(1))
    }

    @Test fun aSwipeRightOnAMessageClearsIt() {
        motionOff()
        val b = testItem("b", at = 20, source = chat.id)
        chat.itemState.value = listOf(b)
        show()
        swipe(1, x = 0.5f)
        assertEquals(listOf(b), chat.cleared)
        assertEquals(emptyList<TileItem>(), chats)
    }

    @Test fun aShortSlowSwipeSlidesTheMessageBackAndAFlickActs() {
        motionOff()
        val b = testItem("b", at = 20, source = chat.id)
        chat.itemState.value = listOf(b)
        show()
        swipe(1, x = -0.2f)
        swipe(1, x = 0.2f)
        assertEquals(emptyList<TileItem>(), chats)
        assertEquals(emptyList<TileItem>(), chat.cleared)
        swipe(1, x = -0.2f, ms = 20)
        assertEquals(listOf(b), chats)
    }

    // Review Focus 1
    @Test fun aMostlyUpwardSwipeOnAMessageScrollsStart() {
        motionOff()
        chat.itemState.value = listOf(testItem("b", at = 20, source = chat.id))
        show(pinned = 21)
        swipe(1, x = -0.2f, y = -0.5f)
        assertTrue(scroll.value > 0)
        assertEquals(emptyList<TileItem>(), chats)
    }

    @Test fun onAHistoryPageAMessageCanOnlyOpenItsChat() {
        motionOff()
        val a = testItem("a", text = "see you", at = 10, unread = false, source = chat.id)
        chat.itemState.value = listOf(a)
        show()
        page(1, "show history")
        assertFalse(actions(1).any { it.startsWith("clear") })
        swipe(1, x = 0.5f)
        assertEquals(emptyList<TileItem>(), chat.cleared)
        swipe(1, x = -0.5f)
        assertEquals(listOf(a), chats)
    }

    @Test fun aSwipeOnALargeTilesRowActsOnThatRow() {
        motionOff()
        showLarge(messages = 7) // P6, P5 and P4 in view, and 0.45 of the tile down is the middle row
        compose.onNodeWithTag("tile-1").performTouchInput {
            swipe(Offset(centerX, height * 0.45f), Offset(centerX - width / 2, height * 0.45f), 300)
        }
        compose.mainClock.advanceTimeBy(100)
        assertEquals(listOf("m5"), chats.map { it.key })
    }

    @Test fun talkBackCanClearEachMessageInView() {
        motionOff()
        showLarge(messages = 7)
        page(1, "clear P5")
        assertEquals(listOf("m5"), chat.cleared.map { it.key })
    }
```

  `talkBackHearsALargeTilesRowsInViewAndPagesThroughTheRest`'s list becomes `["open App 1", "open P5", "open P4", "chat with P6", "chat with P5", "chat with P4", "clear P6", "clear P5", "clear P4", "older messages", "show front"]`.

  Add to `StartViewModelTest`:

```kotlin
    @Test fun clearingAnItemClearsItThroughItsSource() {
        val item = testItem("a", source = chatSource.id)
        chatViewModel().clear(item)
        assertEquals(listOf(item), chatSource.cleared)
    }
```

  Add to `HomeRootTest`:

```kotlin
    // Review Focus 1
    @Test fun aSidewaysSwipeOnAMessageOpensItsChatAndStaysOnStart() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) // the message shows at once
        val chats = FakeTileSource(SourceId.forApp(camera.key.packageName, 0))
        val a = testMessage("a", source = chats.id)
        chats.itemState.value = listOf(a.item)
        chats.chatState.value = testChat(a)
        show(pinned = listOf(camera), sources = SourceRegistry { if (it == chats.id) chats else null })
        compose.onNodeWithTag("tile-1").performTouchInput { swipeLeft() }
        compose.onNodeWithText("see you at 6").assertIsDisplayed() // a tile has no text, so this is the window
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() } // Back: Home would bring start back anyway
        compose.onNodeWithTag("tile-1").assertIsDisplayed()
    }

    // Review Focus 1: a guard, passing today, that must still pass
    @Test fun aSidewaysSwipeOnATilesFrontStillTurnsToTheAppList() {
        show(pinned = listOf(camera))
        compose.onNodeWithTag("tile-1").performTouchInput { swipeLeft() }
        waitForAppList("Maps")
    }
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:start:testDebugUnitTest :app:testDebugUnitTest --tests "*HomeRootTest" --continue`
Expected: `:feature:start`'s tests FAIL to compile, because `sideways`, `Sideways`, `MessageSwipe` and `clear` don't exist. In `HomeRootTest`, `aSidewaysSwipeOnAMessageOpensItsChatAndStaysOnStart` fails, because the pager takes the swipe, and the guard passes.

- [ ] **Step 3: Add sideways swipes.**
  - **`sideways`**: a flick (`abs(velocity) >= flingPx`) goes the way it's flicked. Otherwise a drag of a third of `widthPx` goes the way it's dragged, and anything shorter gives null. Left is `CHAT`. Right is `CLEAR`, or null when `!canClear`.
  - **`StartTileView`** draws the swiped message offset by `swipe.offsetPx` over an underlay of `lerp(colors.accent, Color.Black, 0.3f)`. That's the whole face on an `Item` face, and the row whose item has `swipe.key` on a `Stack`. The underlay reads **reply** at its end while the offset is negative, and **clear** at its start while it's positive, in the label strip's text style. With no swipe, there's no underlay.
  - **`StartViewModel.clear`** and **`StartController.clear`** pass the item to its source.
  - **In `TileSlot`**, `var swipe by remember { mutableStateOf<MessageSwipe?>(null) }` goes to `StartTileView`.
  - **The gesture.** At the down, the message is `tapTarget(down.position.y, size.height.toFloat())` while `swipes`, which is null on a front and on the label strip.
    - On the Main pass, once the change isn't consumed, a message with `change.pressed && abs(moved.x) >= touchSlop && abs(moved.x) > abs(moved.y)` is claimed (`>=`, as the pager's slop). Consume the change. Start's scroll and the pager see the Main pass after the tile, so they let it go.
    - Then follow the finger on the Main pass, consuming each change and feeding a `VelocityTracker`. Set `swipe` to the drag, held at 0 or less on the history page.
    - On the lift, `sideways(drag, velocity, size.width, flingPx, canClear = page != TilePage.HISTORY)` decides:
      - `CHAT`: `controller.chat(current, coords.value, editing, message)`, then slide back;
      - `CLEAR`: slide out to the tile's width, `controller.clear(message)`, then wait until the message has left `currentFaces`, for at most 1 s, and set `swipe = null`;
      - null: slide back.
    - Slides are `animate(from, to, animationSpec = tween(200))` on `scope`, writing `swipe`. With motion off, they're instant.
  - **TalkBack.** Each message in view gets **clear \<title\>**, after the **chat with** actions, except on the history page.

- [ ] **Step 4: Run the tests again.**

Run: `./gradlew :feature:start:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add feature/start app
git commit -m "Open a message's chat or clear it with a sideways swipe"
```

---

### Task 6: A missed call's buttons on tiles

**Files:**
- Modify: `feature/start/src/main/kotlin/…/feature/start/live/TileTap.kt`, `StartTileView.kt`, `StartScreen.kt`, `StartViewModel.kt`
- Test: `feature/start/src/test/kotlin/…/feature/start/live/TileTapTest.kt`, `feature/start/src/test/kotlin/…/feature/start/StartTileViewTest.kt`, `StartScreenTest.kt`, `StartViewModelTest.kt`

**Interfaces:**
- Consumes: Task 3's `ItemButton`, `TileItem.buttons`, `TileSource.press`, `FakeTileSource.pressed` and `testCall()`.
- Produces:

```kotlin
// StartTileView gains
onButton: (TileItem, Int) -> Unit = { _, _ -> }, // a missed call's button, by its index in item.buttons

// StartViewModel
fun open(tileId: Long, sourceBounds: Rect?, options: Bundle?, item: TileItem? = null, button: Int? = null) // presses [button] of [item]
// StartController
var launch: (Long, Rect?, TileItem?, Int?) -> Unit
fun tap(tile: StartTile, coords: LayoutCoordinates?, editing: Boolean, item: TileItem? = null, button: Int? = null)
/** A missed call's button (§5.5): a reply button opens the chat window; in edit mode, or on a paused tile, it's a tap. */
fun press(tile: StartTile, coords: LayoutCoordinates?, editing: Boolean, item: TileItem, button: Int)
```

- [ ] **Step 1: Write the failing tests.** Add to `TileTapTest`:

```kotlin
    @Test fun aMissedCallHasNoChatCorner() {
        assertNull(tileChatTarget(TileFace.Item(testCall()), 99f, 99f, widthPx = 100f, heightPx = 100f, buttonPx = 40f))
        assertEquals(a, tileChatTarget(TileFace.Item(a), 99f, 99f, widthPx = 100f, heightPx = 100f, buttonPx = 40f))
        val rows = TileFace.Stack(listOf(a, testCall(), c, d))
        assertNull(tileChatTarget(rows, 90f, 50f, widthPx = 100f, heightPx = 130f, buttonPx = 20f, stack = scrolled)) // the call's row
        assertEquals(c, tileChatTarget(rows, 90f, 70f, widthPx = 100f, heightPx = 130f, buttonPx = 20f, stack = scrolled))
    }
```

  Add to `StartTileViewTest`:

```kotlin
    @Test fun aMissedCallShowsItsButtonsInPlaceOfTheChatButton() {
        val pressed = mutableListOf<Pair<String, Int>>()
        compose.setContent {
            LiquiWinTheme { StartTileView(tile(TileSize.MEDIUM), metrics, { null }, face = TileFace.Item(testCall()), onButton = { item, i -> pressed += item.key to i }) }
        }
        compose.onAllNodesWithTag("tile-chat", useUnmergedTree = true).assertCountEquals(0)
        val buttons = compose.onAllNodesWithTag("tile-call", useUnmergedTree = true).assertCountEquals(2)
        for (i in 0..1) buttons[i].assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("call back", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("message", useUnmergedTree = true).performClick()
        assertEquals(listOf("call" to 1), pressed)
    }

    @Test fun aLargeTilesMissedCallHasItsButtonsInItsRow() {
        compose.setContent {
            LiquiWinTheme { StartTileView(tile(TileSize.LARGE), metrics, { null }, face = TileFace.Stack(listOf(testCall(), testItem("a")))) }
        }
        compose.onAllNodesWithTag("tile-call", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithTag("tile-chat", useUnmergedTree = true).assertCountEquals(1) // the other row's
    }
```

  Add to `StartScreenTest`:

```kotlin
    // Camera's tile with [call] as its newest item, shown at once. On a medium tile, **message** sits at the corner.
    private fun showCall(call: TileItem): StartViewModel {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        cameraSource.itemState.value = listOf(call)
        return show()
    }

    @Test fun aMissedCallsButtonPressesIt() {
        val call = testCall(source = cameraSource.id)
        showCall(call)
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(width - 5f, height - 5f)) }
        compose.waitUntil(5_000) { cameraSource.pressed.isNotEmpty() } // once the tiles have turned away
        assertEquals(listOf(call to 1), cameraSource.pressed)
        assertEquals(emptyList<TileItem>(), chats)
    }

    @Test fun aMissedCallsReplyButtonOpensItsChat() {
        val call = testCall(buttons = listOf(ItemButton("Call back", false), ItemButton("Reply", true)), source = cameraSource.id)
        showCall(call)
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(width - 5f, height - 5f)) }
        assertEquals(listOf(call), chats)
        assertEquals(emptyList<Pair<TileItem, Int>>(), cameraSource.pressed)
    }

    // Review Focus 5
    @Test fun aLongPressOnAMissedCallsButtonLiftsTheTileAndPressesNothing() {
        val viewModel = showCall(testCall(source = cameraSource.id))
        compose.onNodeWithTag("tile-1").performTouchInput { longClick(Offset(width - 5f, height - 5f)) }
        assertEquals(1L, viewModel.state.value.edit?.selectedId)
        assertEquals(emptyList<Pair<TileItem, Int>>(), cameraSource.pressed)
    }

    // Review Focus 5
    @Test fun inEditModeAMissedCallsButtonSelectsTheTile() {
        val viewModel = showCall(testCall(source = cameraSource.id))
        compose.onNodeWithTag("tile-2").performTouchInput { longClick() }
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(width - 5f, height - 5f)) }
        assertEquals(1L, viewModel.state.value.edit?.selectedId)
        assertEquals(emptyList<Pair<TileItem, Int>>(), cameraSource.pressed)
    }

    @OptIn(ExperimentalTestApi::class) // performCustomAccessibilityActionWithLabel
    @Test fun talkBackCanPressAMissedCallsButtons() {
        val call = testCall(source = cameraSource.id)
        showCall(call)
        compose.onNodeWithTag("tile-1").performCustomAccessibilityActionWithLabel("Call back Alex")
        compose.waitUntil(5_000) { cameraSource.pressed.isNotEmpty() }
        assertEquals(listOf(call to 0), cameraSource.pressed)
    }
```

  Add to `StartViewModelTest`:

```kotlin
    @Test fun aMissedCallsButtonIsPressedThroughItsSource() {
        val call = testCall(source = chatSource.id)
        chatViewModel().open(1, null, null, call, button = 0)
        assertEquals(listOf(call to 0), chatSource.pressed)
        assertEquals(emptyList<TileItem?>(), chatSource.opened)
    }
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:start:testDebugUnitTest`
Expected: FAIL to compile, because `onButton` and `open`'s `button` don't exist.

- [ ] **Step 3: Add the buttons.**
  - **`tileChatTarget`** gives null for an item with buttons, on an `Item` face and on a `Stack` row.
  - **`StartTileView`.** An item with buttons shows one `CallButton` per button, in the app's order, in place of its chat glyph.
    - On a medium or wide face, they sit at the label strip's end, and the tile's label keeps the rest of the strip.
    - On a large tile, they sit at the row's bottom end, and that row's text is one line.
    - A `CallButton` is a `Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("tile-call"))` aligned to the bottom, with its label `lowercase(Locale.getDefault())` on the strip's line, in the strip's style.
    - Its gesture is `awaitEachGesture { awaitFirstDown(); withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }?.let { it.consume(); onPress() } }`. A longer hold leaves the up unconsumed for the tile's long-press, and a swipe the tile claims cancels it.
  - **`StartController.press`**: in edit mode or on a paused tile, it taps. For a reply button, it calls `onChat(item)`. Otherwise it calls `tap(tile, coords, editing, item, button)`, whose `launch` passes `button` on. `StartScreen`'s `launch` passes it to `viewModel.open`.
  - **`StartViewModel.open`** keeps the paused-profile branch first. Then an item with a button goes to `source.press(item, button)`, and an item without one to `source.open(item)`, as before.
  - **`TileSlot`** passes `onButton = { item, i -> controller.press(current, coords.value, editing, item, i) }`.
  - **TalkBack.** Each button of each message in view gets **\<label\> \<title\>**, such as **Call back Alex**, after the **clear** actions, for the same press.

- [ ] **Step 4: Run them again.**

Run: `./gradlew :feature:start:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add feature/start
git commit -m "Show a missed call's own buttons on its tile"
```

---

### Task 7: The photo card and the call row in the chat window

**Files:**
- Modify: `feature/hub/src/main/kotlin/…/feature/hub/ChatViewModel.kt`, `ChatWindow.kt`
- Test: `feature/hub/src/test/kotlin/…/feature/hub/ChatWindowTest.kt`

**Interfaces:**
- Consumes: Task 3's `ItemButton`, `TileItem.buttons`, `TileItem.photoUnreadable`, `TileSource.press` and `FakeTileSource.pressed`.
- Produces:

```kotlin
// ChatViewModel
fun openInApp(item: TileItem? = null) // [item], or the newest message; closes the window at once
fun press(button: Int) // the newest message's button (§5.7); closes the window at once
```

- [ ] **Step 1: Write the failing tests.** Add to `ChatWindowTest`:

```kotlin
    private fun TileItem.withButtons(vararg buttons: ItemButton) = copy(buttons = buttons.toList())

    @Test fun aPhotoLiquiWinCouldntReadShowsACardThatOpensIt() {
        val photo = testMessage("p", text = "look").let { it.copy(item = it.item.copy(photoUnreadable = true)) }
        open(testChat(photo, a))
        compose.onNodeWithText("photo · open in Chat").performClick()
        assertEquals(listOf<TileItem?>(photo.item), source.opened)
        assertNull(vm.window.value)
    }

    @Test fun aMissedCallsButtonsSitAboveTheBox() {
        val call = testMessage("c", text = "missed call", at = 9).let {
            it.copy(item = it.item.withButtons(ItemButton("Call back", false), ItemButton("Message", true)))
        }
        open(testChat(a, call))
        compose.onNodeWithText("message").assertDoesNotExist() // the reply button is the box itself
        compose.onNode(hasSetTextAction()).assertExists()
        compose.onNodeWithText("call back").performClick()
        assertEquals(listOf(call.item to 0), source.pressed)
        assertNull(vm.window.value)
    }

    @Test fun aCallThatTakesNoReplyShowsItsButtonsInTheBoxsPlace() {
        val call = testMessage("c", text = "missed call", at = 9, canReply = false).let {
            it.copy(item = it.item.withButtons(ItemButton("Call back", false), ItemButton("Message", false)))
        }
        open(testChat(call))
        compose.onNodeWithText("call back").assertExists()
        compose.onNodeWithText("message").assertExists()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun anOlderCallsButtonsDontShow() {
        val call = testMessage("c", text = "missed call", at = 0).let { it.copy(item = it.item.withButtons(ItemButton("Call back", false))) }
        open(testChat(call, testMessage("b", text = "later", at = 9)))
        compose.onNodeWithText("call back").assertDoesNotExist()
    }
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:hub:testDebugUnitTest --tests "*ChatWindowTest"`
Expected: FAIL. `aPhotoLiquiWinCouldntReadShowsACardThatOpensIt` finds no card, and the call row tests find no **call back**.

- [ ] **Step 3: Add the card and the row.**
  - **`openInApp(item)`** opens `item ?: newest`, closing first, as before.
  - **`press(button)`** takes the newest message's item and closes the window. If the button exists, it then calls `source.press(newest, button)` on `viewModelScope`.
  - **The photo card.** `Bubble` gains `onPhoto: (() -> Unit)?`, and `Bubbles` passes `{ viewModel.openInApp(message.item) }` for an item with `photoUnreadable`. The card sits where the photo would, inside the bubble. It's a `Box` of at least 48 dp height with `clickable(role = Role.Button)`, reading "photo · open in ${app?.label ?: "app"}" in the bubble's text style.
  - **The call row.** It's built from the newest message's buttons that aren't replies, keeping their indices. Each is the file's `TextButton` with its label lowercased, calling `viewModel.press(index)`. The row sits above `ReplyRow`, and shows whether or not `ReplyRow` does.

- [ ] **Step 4: Run the tests again.**

Run: `./gradlew :feature:hub:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add feature/hub
git commit -m "Show a card for photos LiquiWin can't read, and a missed call's buttons, in the chat window"
```

---

### Task 8: The notifier's missed call and unreadable photo

**Files:**
- Create: `tools/notifier/src/main/kotlin/…/tools/notifier/ButtonReceiver.kt`, `tools/notifier/src/main/res/xml/photos.xml`
- Modify: `tools/notifier/src/main/kotlin/…/tools/notifier/Chats.kt`, `PostReceiver.kt`, `NotifierActivity.kt`, `tools/notifier/src/main/AndroidManifest.xml`
- Test: `tools/notifier/src/test/kotlin/…/tools/notifier/ChatsTest.kt`

**Interfaces:**
- Consumes: nothing from the other tasks. LiquiWin reads these as it reads any app's notifications.
- Produces: `post` takes `"call"` and `"picture"`, from adb (`--es chat call`) and from two new buttons in `NotifierActivity`.

```kotlin
// Chats
/** A dialer-like missed call from Alex, whose **Call back** and **Message** only log (§10). */
fun postCall(context: Context)
/** A photo from Alex in the alex chat, served by the notifier's own provider and granted to no app (§10). */
fun postPicture(context: Context)
```

- [ ] **Step 1: Write the failing tests.** Add to `ChatsTest`:

```kotlin
    @Test fun aMissedCallHasTwoButtonsThatOnlyLog() {
        Chats.postCall(context)
        val call = showing(5)
        assertEquals(NotificationCompat.CATEGORY_MISSED_CALL, call.category)
        assertEquals(listOf("Call back", "Message"), call.actions.map { it.title.toString() })
        assertTrue(call.actions.all { it.remoteInputs.isNullOrEmpty() })
    }

    @Test fun aChatPhotoIsServedByTheNotifiersOwnProvider() {
        Chats.postPicture(context)
        val photo = style(1).messages.last()
        assertEquals("image/png", photo.dataMimeType)
        assertEquals("${context.packageName}.photos", photo.dataUri?.authority)
    }
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :tools:notifier:testDebugUnitTest`
Expected: FAIL to compile, because `postCall` and `postPicture` don't exist.

- [ ] **Step 3: Post them.**
  - **`postCall`** uses id 5 (`"call"` in `ids`). It's `show(context, "call")` with title "Alex", text "Missed call", `CATEGORY_MISSED_CALL`, and two actions. Each is a broadcast to `ButtonReceiver`, with its label as an extra and its own request code, and `FLAG_IMMUTABLE`.
  - **`ButtonReceiver`** logs `button: <label>`, and nothing else. In the manifest it isn't exported.
  - **`postPicture`** draws a 720 × 480 PNG like `postPhoto`'s and writes it to `cacheDir/photos/picture.png`. It takes its URI from `FileProvider.getUriForFile(context, "${context.packageName}.photos", file)`. Then it `add`s a message from Alex, "a photo", with `setData("image/png", uri)` to the alex chat. It grants nothing.
  - **The manifest** declares `androidx.core.content.FileProvider` with that authority, `exported="false"`, `grantUriPermissions="true"`, and `@xml/photos` (`<cache-path name="photos" path="photos/"/>`).
  - **`post`**, `PostReceiver`'s doc and `NotifierActivity`'s buttons add "call" and "picture".

- [ ] **Step 4: Run the tests again.**

Run: `./gradlew :tools:notifier:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add tools/notifier
git commit -m "Post a missed call and a photo LiquiWin can't read from the notifier"
```

---

### Task 9: The Galaxy check

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

- [ ] **Step 3: Pin the notifier's tile** if it isn't on start. It's wide since the chat window's check, and stays so.

- [ ] **Step 4: Run the scripted check.**
  - Ask the owner to keep their hands off the phone.
  - Before every step, check with `front` that LiquiWin is in front, and stop if it isn't.
  - Post with part 1's `post` helper.
  - Every tap and swipe lands on the notifier's tile, never on another app's.
  - Send the owner the shots that show each result, cropped to the notifier's tile.

  The steps:
  1. **The buttons.** `post call`. Once the tile shows the call, its strip ends in **call back** and **message**. Tap **call back**: the tiles turn away and come back, and `logcat -d -s` on the notifier's tag shows `button: Call back`.
  2. **A long-press on a button.** Long-press **message**. Start goes into edit mode, and no `button:` line follows. Tap **done**.
  3. **Swipe left.** On an alex message, `input swipe` from the tile's centre to half its width left, over 300 ms. The chat window swivels in. Do the same on the call: its window shows **call back** and **message** in the text box's place.
  4. **Swipe right.** `post alex "zq clear me"`, then swipe right on it. The message slides out and leaves the tile. Counting the notifier's lines in `dumpsys notification`, and printing only the count, shows its alex notification gone.
  5. **Swipe down.**
     1. With start at its top and something unread, swipe down half the tile's height. The tile flips to its new messages and stays there through 30 s of shots.
     2. Swipe down again, and it flips to its front.
     3. With everything read, swipe down. The label strip reads **history**.
  6. **Start scrolled down.** Scroll start a little with a swipe up on another of LiquiWin's tiles, then swipe down on the notifier's tile. Start scrolls back to its top, and the tile doesn't turn.
  7. **The photo card.** `post picture`, and open alex's window. The newest bubble holds **photo · open in notifier**, and tapping it opens the notifier. If the photo shows instead, Android let LiquiWin read the notifier's provider: note it, and the card is checked in Step 5 instead.

  TalkBack stays off, since it's a system setting. The Compose tests cover it.

  Note anything that fails as a fix before closing this part.

- [ ] **Step 5: Real apps, with the owner.** These steps are the owner's. The agent never presses a real call's buttons, never clears a real notification, and takes no shots.
  - With the phone app's tile pinned, the owner lets a call go unanswered. The tile shows the call's own buttons, on most phones **call back** and **message**, and tapping **call back** calls back.
  - The owner says whether the tile's first line names the caller or reads "Missed call". TalkBack names the buttons after it, so a "Missed call" there needs a fixture and a fix.
  - A photo sent in a chat shows inside its bubble in quick chat, or as the card where the app keeps it from LiquiWin. The card opens the message.
  - Each thing that looks or behaves wrong gets a fixture shaped like it and a fix, or a note for the next plan.

- [ ] **Step 6: Record the chosen values.** Add a "Values chosen for tile gestures" list to the reference doc, after the chat window's list. It covers:
  - the swipes: a third of the tile's width sideways, or of its height down, or a 600 dp/s flick, the pager's; and only while start is at its top for a swipe down;
  - the underlay: the accent 30 % toward black, reading **reply** or **clear** in the label strip's style;
  - the slides: 200 ms back or out, with a cleared message out for at most 1 s;
  - a missed call's buttons: 48 × 48 dp at the strip's end, labels lowercase in the strip's style, and on a large tile at the row's bottom end with one line of text;
  - the photo card: **photo · open in \<app\>**, at least 48 dp tall;
  - anything changed during Steps 4 and 5.

```bash
git add docs/reference/windows-phone-ui-reference.md
git commit -m "Record the values chosen for tile gestures"
```
