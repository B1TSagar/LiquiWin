# LiquiWin Launcher — Sub-project 1: launcher shell, start screen and live tiles

**Date:** 2026-10-27
**Status:** Approved 2026-10-27
**Author:** Sagar, with Claude
**Working name:** LiquiWin, matching the repository [B1TSagar/LiquiWin](https://github.com/B1TSagar/LiquiWin). Check the name before a store release, because names that echo "Windows" have drawn Microsoft trademark challenges before (Lindows became Linspire).

---

## 1. Context

### 1.1 What we're building

A modern Android launcher that recreates the Windows Phone ("Metro") interface on today's phones: live tiles, the A–Z app list, pivots and hub pages, and Windows Phone motion. It also has an original **Glass** style: etched, accent-tinted glass that refracts the background and morphs whenever the layout changes. LiquiWin's own Mail, Photos and Music apps follow in later sub-projects.

References:
- *Windows Phone 7 Interface Overview* (zollotech, YouTube `XPr0vKQmEA0`).
- WP8.1 and Windows 10 Mobile behaviour, collected with exact values in the [Windows Phone UI reference](../../reference/windows-phone-ui-reference.md).

### 1.2 Purpose and audience

The first audience is the owner, using it every day on their Samsung Galaxy. It is built so it can be published on Google Play later without a redesign. That rules out three things from day one: unofficial APIs, misuse of accessibility services, and Microsoft branding.

### 1.3 The programme

| # | Sub-project | Summary | Depends on |
|---|---|---|---|
| 1 | **Launcher shell, start screen and live tiles** (this spec) | Everything in this document | — |
| 2 | Music | Now playing for any player; Spotify, Apple Music and YouTube Music connectors; neutral recommendations | 1 (tile sources, design system) |
| 3 | Mail | Accounts, linked inbox, Windows Phone pivots, live tile | 1 |
| 4 | Photos | On-device gallery, albums, cycling live tile | 1 |
| 5 | Other Windows Phone-style apps | People, Calendar, Messaging, Weather, Alarms and others, scoped later | 1 |

Each sub-project gets its own spec, plan and build. Notes carried forward for sub-projects 2–4 are in Appendix B.

### 1.4 Success criteria

All checked on the owner's Galaxy:

1. LiquiWin can be set as the Home app and is used daily. Pressing Home shows the start screen within 300 ms when the launcher is already running, and within 1.5 s from cold.
2. A "Social" tile fed by Instagram, WhatsApp and Snapchat shows a new WhatsApp message (sender and text) within 1 s of its arrival, with a combined unread count.
3. Tapping that update opens WhatsApp at that chat. Tapping the tile's label strip opens the hub page. The reply button sends a real reply that appears in WhatsApp.
4. All five presets can be applied, and every style setting can be mixed freely. Switching column counts never scrambles a saved arrangement.
5. The app list (three styles), universal search, live folders, widgets in tiles and the Action Center panel work as specified.
6. First-time setup takes a new user from install to a live start screen.
7. After a day of normal use under Samsung's battery management, live tiles are still working. If they have paused, the launcher says so and how to fix it.
8. Scrolling the start screen drops no frames at the phone's refresh rate.
9. The Glass preset:
   - refracts the background at tile edges
   - takes the accent colour as its tint, or stays neutral with white
   - never looks fully clear
   - morphs on pin, unpin, resize, drag, folder, quick reply and Action Center
   - scrolls on the Galaxy without dropped frames

### 1.5 Out of scope

- Replacing the lock screen, Glance (always-on display) or Android's own notification shade. A launcher can't do these without fragile workarounds that Play would reject.
- Taking over Samsung's recent-apps screen and gesture animations. One UI keeps these in its own launcher, so with LiquiWin as Home, swiping up and holding to see recent apps takes about a second longer. LiquiWin can't change this.
- Reading app feeds or messages through private or unofficial APIs. Tiles only know what apps put in their notifications.
- Sending read receipts, or marking messages read inside other apps.
- Music, Mail, Photos, People and the other built-in apps (sub-projects 2–5).
- An assistant, Kids Corner, icon packs, cloud sync of layouts, layouts tuned for tablets or foldables, and iOS.

---

## 2. Constraints

- **Platform.** Android only, because iOS doesn't allow replacement home screens. `minSdk 29` (Android 10), `targetSdk 36` (Android 16). The main test device is a Samsung Galaxy on One UI.
- **Stack.** Kotlin, Jetpack Compose, coroutines and Flow, Room, DataStore, and Gradle (Kotlin DSL) with a version catalog. Developed in Android Studio, which is already installed, with the SDK at `C:\Android` and JDK 21.
- **Play-safe from day one:**
  - Apps are listed through `LauncherApps` plus a `<queries>` MAIN/LAUNCHER intent filter. No `QUERY_ALL_PACKAGES`.
  - No accessibility service.
  - Battery exemption goes through the settings screen. The launcher never shows the direct `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` prompt.
  - No `SYSTEM_ALERT_WINDOW`. All floating UI (quick reply, Action Center) lives inside the launcher's own window.
  - **No `INTERNET` permission in sub-project 1.** Notification content can't leave the phone, because the app has no network access at all.
- **Branding.** No Microsoft names, logos or trademarks in the product name, icon or store listing, and no Segoe fonts. No Apple trademarks either: the Glass style is never called "Liquid Glass".
  - **Preset names.** WP7, WP8.1, W10M and Modern Metro are working names for personal use. They get neutral names before a store release: "WP" stands for Windows Phone, and Microsoft itself stopped using "Metro" in 2012 over a trademark concern.
  - **Fonts and icons.** LiquiWin bundles Selawik (an open Segoe UI substitute) and open icon sets, and lists their licences in About. The reference document records the exact licences.
  - **Selawik covers Latin scripts only.** Text in other scripts, such as Hindi or Russian app names, uses the phone's own font.
  - **Selawik ships unmodified.** Its licence (SIL OFL 1.1) counts subsetting or converting the font files as modification, which would require renaming the font.

---

## 3. Architecture

### 3.1 Modules

| Module | Responsibility | Depends on |
|---|---|---|
| `:app` | Home activity; navigation and routing between start, app list, hub, quick reply, Action Center and settings; first-time setup; crash safe mode; dependency wiring | all modules below |
| `:core:design` | The Metro design system in Compose: type ramp, accent palettes, theme, style facets and presets, controls (Pivot, AppBar, tilt), motion (turnstile, continuum, flip, peek, swivel), and the Glass material (shaders, morph system, built-in backgrounds) | — |
| `:core:data` | Room databases (`layout.db` for tiles; `cache.db` for the cached app list and the notification store), DataStore preferences, `AppRepository` (launchable apps through `LauncherApps`, icon cache, launch stats), migrations, export and import | — |
| `:core:tiles` | Tile model, `TileSource` interface, `SourceRegistry`, tile-state aggregation, tile templates and renderers | `:core:design`, `:core:data` |
| `:feature:notifications` | `NotificationListenerService`, normaliser, live action cache, `AppNotificationSource` (implements `TileSource`, including open and reply) | `:core:tiles`, `:core:data` |
| `:feature:start` | Start grid, layout engine, edit mode, tile editor, live folders, widget tiles, pin requests | `:core:tiles` |
| `:feature:applist` | A–Z app list in three styles, alphabet grid, app actions | `:core:design`, `:core:data` |
| `:feature:hub` | Hub pages and the quick-reply window | `:core:tiles`, `:core:data` |
| `:feature:actioncenter` | Action Center panel and quick actions | `:core:tiles`, `:core:data` |
| `:feature:search` | Search UI, the `SearchProvider` interface and the built-in providers | `:core:design`, `:core:data` |
| `:tools:notifier` | Debug-only app that posts fake chat notifications with reply actions | — |

No feature module depends on another feature module:
- Features talk through `:core:tiles` and `:core:data`.
- Cross-screen jumps (open the hub for a tile, open quick reply for an item, open the Action Center) are navigation events that `:app` routes.
- `SourceRegistry` resolves any `SourceId` to its source, creating app sources on demand. That's how the hub and the Action Center reach items from any app.

Later sub-projects add `:feature:music`, `:feature:mail` and `:feature:photos` the same way.

### 3.2 Key interfaces

```kotlin
// Stable id for anything that can feed a tile.
//   "app:<package>@<userSerial>"                all notifications from one app
//   "app:<package>@<userSerial>#<shortcutId>"   one conversation (a pinned chat)
//   later: "music", "mail:<accountId>", "photos"
@JvmInline value class SourceId(val value: String)

interface TileSource {
    val id: SourceId
    val label: String
    val icon: TileIcon
    /** Newest first. */
    val items: Flow<List<TileItem>>
    val unreadCount: Flow<Int>
    /** item == null opens the source itself (the app). */
    suspend fun open(item: TileItem?)
    /** Only valid when item.canReply; see §5.7. */
    suspend fun reply(item: TileItem, text: String): ReplyResult
    /** Local read state only; never touches the source app. */
    suspend fun markRead(items: List<TileItem>)
}

sealed interface ReplyResult {
    data object Sent : ReplyResult
    data object ActionGone : ReplyResult      // the app withdrew its reply action
}

data class TileItem(
    val key: String,
    val sourceId: SourceId,
    val title: String,              // sender or headline
    val text: String?,              // preview
    val avatar: ImageRef?,
    val image: ImageRef?,
    val time: Instant,
    val unread: Boolean,
    val canReply: Boolean,          // a live reply action exists right now
    val conversation: ConversationRef?,
)

interface SearchProvider {
    val id: String
    val title: String
    fun search(query: String): Flow<List<SearchResult>>
}
```

`PendingIntent`s for taps and replies can't be persisted. They live in an in-memory `LiveActionCache` keyed by notification key, and `TileItem.canReply` reflects whether the cache holds a reply action.

### 3.3 Data model

**Room tables:**

| Table | Contents |
|---|---|
| `tile` | id; kind (`APP`, `COMBINED`, `FOLDER`, `WIDGET`, `SHORTCUT`, `SEARCH`, `ACTION_CENTER`); title; size (`SMALL`, `MEDIUM`, `WIDE`, `LARGE`); template (`FLIP`, `PEEK`, `CYCLE`, `ICONIC`, `STATIC`); colour override; surface override; `showPreviews`; `keepReadItems24h`; `parentFolderId`; `appWidgetId`; shortcut reference; package reference |
| `tile_source` | `tileId`, `sourceId`, `order` |
| `tile_placement` | `tileId`, `gridWidth` (4, 6 or 8 units), `x`, `y`. One row per tile per grid width that has been arranged. |
| `notification_item` | `key`, `sourceId`, `conversationKey`, `sender`, `text`, avatar file, image file, `postedAt`, `removedAt`, `readAt`, `isSelf`. Keeps 50 items per app and 24 hours by default. |
| `conversation_read` | `sourceId`, `conversationKey`, `lastReadAt` |

**Other storage:**
- Style settings, onboarding progress and per-feature settings live in DataStore.
- Avatars are downscaled to at most 256 px and photos to at most 720 px. They're stored in an app-private cache and deleted with their items.
- Android Auto Backup includes the tile tables and DataStore. It excludes `notification_item`, `conversation_read` and the image cache.
- Auto Backup works on whole files, so the tables live in two Room databases. `layout.db` holds the tile tables and is backed up. `cache.db` holds the notification tables and the cached app list, and isn't.

### 3.4 Data flow

```
App posts a notification
  → NotificationListenerService.onNotificationPosted       (moved to the IO dispatcher)
  → Normaliser: filter, extract, dedupe                    (§5.2)
  → NotificationStore (Room)  +  LiveActionCache (memory: tap and reply PendingIntents)
  → AppNotificationSource(sourceId).items / unreadCount    (Flow)
  → TileStateAggregator: merges all sources of a tile → TileState(rotation, unread, faces)
  → TileRenderer + TileAnimator                            (only tiles on screen)
```

`onNotificationRemoved` sets `removedAt`, and an item with `removedAt` counts as read.

### 3.5 Process and threading

LiquiWin runs in a single process. The notification listener lives in the launcher's process, so the system's binding to it also keeps the launcher warm. All parsing, database and image work runs off the main thread, which only renders. The app list and icon cache load from disk at start and refresh in the background through `LauncherApps.Callback`.

---

## 4. Start screen and styles

### 4.1 Style facets and presets

Every facet is independent. A preset is a named set of facet values. Changing any facet after applying a preset shows the label "Custom (from \<preset\>)". Facets apply live, without a restart.

| Facet | Options | WP7 | WP8.1 | W10M | Modern Metro | Glass |
|---|---|---|---|---|---|---|
| Grid columns (medium tiles) | 2 · 3 · 4 | 2 | 3 | 4 | 3 | 3 |
| WP7 right gutter with app-list arrow | on · off | on | off | off | off | off |
| Default size for new tiles | small · medium · wide · large | medium | medium | medium | medium | medium |
| Material | flat · glass (§4.8) | flat | flat | flat | flat | glass |
| Tile surface (flat material only) | solid accent · see-through to the background · translucent accent (opacity slider) | solid | see-through | translucent 70% | translucent 85% | — |
| Tile corners | square · rounded (4–28 dp) | square | square | square | square | rounded 16 dp |
| Background | theme colour · photo behind tiles with parallax · full-screen wallpaper | theme colour | photo with parallax | full-screen | full-screen | full-screen, photo or built-in |
| Accent colour | WP7 set · WP8 set of 20 · white · any colour · from wallpaper | WP7 blue | WP8 cyan | WP8 cobalt | from wallpaper | white (neutral glass) |
| Theme | dark · light · follow phone | dark | dark | dark | follow phone | follow phone |
| App list style | WP7 · WP8.1 · W10M | WP7 | WP8.1 | W10M | W10M | W10M |
| App list entry | arrow · swipe · both | both | swipe | swipe | swipe | swipe |
| Status bar | hidden until tapped · always shown | hidden | hidden | shown | shown | shown |
| Motion | full · reduced · off, plus tile pace: calm · normal · lively (§5.4) | full, normal | full, normal | full, normal | full, normal | full, normal |
| App launch animation | turnstile · zoom from tile | turnstile | turnstile | turnstile | turnstile | zoom from tile |
| Font | Selawik · phone font | Selawik | Selawik | Selawik | Selawik | Selawik |
| Big lowercase page headers | on · off | on | on | on | on | on |
| Tile icons | single-colour where available · always full colour | single-colour | single-colour | single-colour | single-colour | single-colour |
| Action Center layout | 8.1 row of four · W10M expandable grid | 8.1 | 8.1 | W10M | W10M | W10M |
| Glass tint strength (glass only) | 10–60% | — | — | — | — | 25% |
| Glass frost (glass only) | 1–100, never 0 | — | — | — | — | 40 |
| Light follows tilt (glass only) | on · off | — | — | — | — | on |
| Colour fringe (glass only) | on · off | — | — | — | — | on |

The surface options mean:
- **Solid:** filled with the accent colour or the tile's own override.
- **See-through:** the tile is a window onto the background photo, lightly tinted. The gaps between tiles show the theme colour, as in WP8.1.
- **Translucent:** the accent colour at the slider's opacity, laid over whatever background is set, as in W10M.

Where the backgrounds come from:
- **Background photos** are always chosen through the Android photo picker. LiquiWin keeps its own downscaled copy.
- **Full-screen wallpaper** can use that photo, or the phone's own wallpaper. Android draws the phone's wallpaper behind the launcher window, so no permission is needed.

How surfaces and backgrounds combine:
- **See-through over a full-screen background:** each tile tints its window onto the photo with 20% of the accent colour, so tiles stay distinct while the gaps show the photo untinted.
- **See-through over the theme colour:** there's no photo to show, so tiles fall back to solid.
- **The phone's own wallpaper:** Android doesn't let apps read the phone's wallpaper image. With it as the background, see-through behaves like translucent. "From wallpaper" accent uses Android's wallpaper colours (`WallpaperManager.getWallpaperColors`). With a LiquiWin photo, it uses the photo's dominant colour.

The exact values per Windows Phone version (accent hex codes, margins and gaps, type ramp, animation timings) are in the [reference](../../reference/windows-phone-ui-reference.md). Presets use those values.

Each tile can override its colour, surface, template, `showPreviews` and `keepReadItems24h`.

The status bar's "hidden until tapped" option hides Android's status bar on the start screen. Tapping the top 24 dp shows it for 3 s, and Android's own swipe-down still works as normal.

### 4.2 Grid model

- **Unit and sizes.** The grid unit is one small tile. Small is 1×1 units, medium 2×2, wide 4×2 and large 4×4. Large is LiquiWin's own addition and works in every style: as far as the research shows, no Windows Phone release had large tiles on phones.
- **Grid width.** The grid is the column count × 2 units wide, so 4, 6 or 8 units. Every tile size fits every width.
- **Unit size.** The edge of one unit = (screen width − 2 × side margin − (units − 1) × gap) / units. Margin and gap come from the active preset's reference values.
- **Arrangements.** Each grid width keeps its own arrangement in `tile_placement`. The first time a width is shown, tiles flow in the order of the most recently used width: row by row, top-left first, each placed in the first space it fits. Once the user edits at that width, the positions are saved.
- **Compaction.** After every edit, tiles float upward until blocked, keeping their columns. This closes vertical holes. Holes at the end of a row next to wider tiles may stay, as on Windows Phone. The exception is a tile that shrinks: at every width, the tiles after it re-flow from its row, so they fill the space it frees, even across columns. (The owner's call at stage 1's Galaxy check: floating up alone left tiles stacked down one edge.)
- **Size.** Size belongs to the tile, not to one width. Growing a tile at one width leaves the other widths' positions where they still fit. Tiles that now collide are re-flowed from the first collision onward. Shrinking re-flows as under Compaction.
- **Scrolling.** The start screen is one vertical scroll. The app list is a page to the right, reached by swipe, by the WP7 arrow, or both.

### 4.3 Edit mode

- Long-press a tile to enter edit mode. That tile lifts (scale 1.04) and the rest step back (scale 0.92, 40% opacity), with a haptic tick.
- Drag to move the tile.
  - When the dragged tile's centre moves into a different grid cell and stays there for 150 ms, the tiles in the way slide aside as a live preview.
  - If the centre instead rests over the middle third of another tile for 400 ms, that tile highlights. Dropping then makes a live folder (§6.3), and moving away cancels the folder.
  - Dropping anywhere else commits the move.
- Corner buttons on the lifted tile:
  - top-left: tile editor (§4.4)
  - top-right: unpin
  - bottom-right: resize, cycling small → medium → wide → large and skipping sizes the tile can't take (§6.4)
- Bottom bar: **widget**, **new tile** (a blank combined tile), **style**, **done**. Back, or a tap on empty space, also leaves edit mode.
- Settings → start screen → **lock layout** disables edit mode.

### 4.4 Tile editor

- **Name:** combined tiles only. App tiles use the app's name.
- **Feeds:** a multi-select list of apps. Apps that posted notifications in the last 7 days come first, then the rest A–Z. An app can feed any number of tiles.
- **Colour, surface, animation (template):** per-tile overrides.
- **Message previews:** shown or hidden.
- **Keep read items for 24 h.**
- **Save** and **cancel**.

Adding a second feed to an app tile turns it into a combined tile.

### 4.5 Pinning

- From the app list (long-press → **pin to start**): an `APP` tile with that app as its only source.
- From other apps' pin requests (`LauncherApps.PinItemRequest`, for example a chat or a web page): a `SHORTCUT` tile. A conversation shortcut becomes a tile fed only by that conversation (`app:<pkg>@<user>#<shortcutId>`).
- Widgets: from edit mode, or from apps' pin-widget requests. Both create a `WIDGET` tile.

### 4.6 Icons

LiquiWin uses the single-colour glyph from the adaptive icon's monochrome layer (Android 13+), tinted white. If the app has none, it uses the full-colour icon, centred at 50% of the tile's shorter side. The icon cache is keyed by package, version and user, and refreshed through `LauncherApps.Callback`.

### 4.7 Motion

| Motion | Used for |
|---|---|
| Tilt | Pressing any tile or list item tilts it toward the finger in 3D and depresses it slightly; it springs back on release |
| Turnstile | Launching an app from Start: tiles swing away around the left edge column by column with a staggered "feather", the tapped tile last, then the app opens with a fade. Coming back home, they swing back in. The app list's rows do the same, top to bottom, when an app opens from the list; start's tiles also swing away when a swipe lets go toward the app list, and back when one lets go toward start. (Built after stage 1, ahead of stage 3, at the owner's request.) With app launch animation set to "zoom from tile", Android's own launch animation grows the app out of the tile's bounds instead (`ActivityOptions.makeScaleUpAnimation`). |
| Glass morphs | Every layout change while the material is glass (§4.8) |
| Continuum | Going from a list item into its page (for example a hub row title flying into the next page's header) |
| Slide / turnstile | Moving between settings and other pages |
| Swivel | Dialogs and the quick-reply window |
| Flip, peek, cycle | Tile templates (§5.1) |
| Pivot header slide | Swiping between hub tabs |

With motion set to "reduced", flips and peeks become crossfades and page transitions become fades. "Off" removes motion entirely and follows Android's remove-animations setting automatically. Durations, angles and stagger delays come from the reference.

### 4.8 Glass

Glass is the fifth preset, and the only style that isn't a Windows Phone recreation. It's LiquiWin's own take on "liquid" glass interfaces:
- etched rather than clear
- tinted by the accent colour
- morphing whenever the layout changes

It's built from the **Material** facet. With material set to glass, these surfaces are glass:
- tiles
- the bottom bar
- the Action Center
- the quick-reply window
- open folder bands
- the search box
- dialogs

A single tile can still be overridden back to flat solid. Glass-only facets (tint, frost, light follows tilt, colour fringe) appear in settings only while the material is glass. Tile corners are a facet in every style.

**Look**

| Property | How it works |
|---|---|
| Etched, never clear | Every glass surface samples a pre-blurred copy of the background (the frost) and adds a fine static grain. Frost has a minimum above zero, so glass never looks clear. |
| Tint | The frosted sample is mixed with the accent colour at the tint strength. White means no colour (neutral glass). In the dark theme glass is slightly smoky; in the light theme it's slightly milky. |
| Refraction | Each shape has a flat centre and a curved rim, 30% of its shorter side wide, up to 20 dp. Inside the rim, the background sample is pushed along the surface normal (taken from the gradient of the shape's signed distance field) by an amount that follows the rim's slope. The result bends like a lens, strongest at the edge; it's modelled on an index of refraction of about 1.5. |
| Colour fringe | Inside the rim, red, green and blue are sampled at slightly different offsets, which gives subtle dispersion. |
| Light | A specular highlight runs along the part of the rim that faces the light. With "light follows tilt" on, the light direction follows the rotation-vector sensor (at most 30 Hz, and only while the start screen is visible). Otherwise the light is fixed at the top left. |
| Legibility | Text and icons on glass go light or dark depending on the average brightness of the frosted sample behind them, keeping at least 4.5:1 contrast. If neither passes, the tint deepens behind the text. |

**Morphing**
- **One shader pass.** All visible glass is drawn by a single shader pass over the background. Each glass element is a rounded rectangle described by a signed distance field, and elements are combined with a smooth union.
- **Blend radius.** At rest the blend radius is 0, so every tile is a separate piece. During a change it animates up, but only between the elements involved.
- **Springs.** Shapes move and resize on slightly underdamped springs (damping ratio 0.65), so they overshoot a little and settle.

| Event | Morph |
|---|---|
| Pin or add | A droplet buds from its source (the app list row, the new-tile button or a pin request) and flows into the slot, overshooting and then settling |
| Unpin or remove | The tile pinches into a droplet and merges into its nearest neighbour, which wobbles once. Tiles moving up to fill the gap stretch vertically while they move. |
| Resize | The growing edge bulges outward and a shrinking edge dips inward, then both settle |
| Drag | The tile stretches along the drag velocity, and neighbours dent as it nears them. Hovering over a tile makes the two pieces of glass neck together; dropping merges them into a folder. |
| Folder open or close | The folder's glass pours down into the band below it and flows back up |
| Quick reply | The reply button's droplet expands into the window. On send or close, the window flows back into the tile. |
| Action Center | The sheet pours down from the top with a curved leading edge and retracts upward |
| New notification | A refraction ripple crosses the tile, and the count bubble drips out of the corner |
| Count cleared | The bubble is reabsorbed |
| Edit mode on or off | Tiles soften (the rim widens), or firm up again |

- **Taps during a morph** always hit-test against the tiles' final layout positions, never the wobbling shapes.
- **Motion "reduced":** morphs become 150 ms fades and size tweens.
- **Motion "off":** changes are instant.

**Requirements and fallbacks**

| Situation | What Glass does |
|---|---|
| Android 13 and later | The full effect: refraction, colour fringe and morph blending all run on an AGSL `RuntimeShader` |
| Android 10–12 | Frost, tint, grain and rounded shapes, all from the pre-blurred background. No lens edges. Morphs become plain position and size springs, without merging. |
| Background | Needs a LiquiWin background: the user's photo, or one of the built-in backgrounds. The built-in ones are original abstract gradients generated by a shader at the screen's resolution, so there are no image licences and Glass works out of the box. |
| Phone's own wallpaper | Android doesn't expose the wallpaper's pixels, so glass falls back to etched panes with 70% accent tint and grain. Settings says why and offers to pick a background. |
| Performance | The background is pre-blurred once each time it changes. The glass shader only redraws during scrolling, morphs, ripples or tilt lighting. If frame time stays over budget for 10 frames, quality steps down in this order: drop the colour fringe, halve the refraction samples, pause tilt lighting. It steps back up when there's headroom again. |

---

## 5. Live tiles

### 5.1 Faces and templates

**Front face:**
- icon or icons
- name label (medium size and up)
- unread count at bottom right, shown as "99+" above 99
- On a combined tile: up to four app icons, with per-app counts on wide and large tiles, plus the total.

**Content faces.** One per item in the rotation (§5.3). Layout depends on size:

| Size | Content face |
|---|---|
| Small | None. Only the count changes. |
| Medium | Avatar, sender, 2 lines of text |
| Wide | Avatar, sender, 3 lines, or the item's photo with a caption |
| Large | The three newest items stacked, each with its own tap area and reply button |

On a combined tile, each content face also shows the item's app icon at top right.

**Templates.** New tiles default to `FLIP`.

| Template | Behaviour |
|---|---|
| `FLIP` | Rotates 180° around the horizontal axis between faces |
| `PEEK` | The content face slides up over the front face, then back |
| `CYCLE` | Photos crossfade; items without a photo fall back to `FLIP` |
| `ICONIC` | No motion. Shows the front face; wide and large tiles list the latest lines. |
| `STATIC` | Front face only; the count is optional |

### 5.2 What counts as an update

**The normaliser ignores:**
- LiquiWin's own notifications
- group summaries
- ongoing and foreground-service notifications
- media-style notifications (set aside for sub-project 2)
- progress notifications
- apps on the ignore list

**It extracts:**
- title, text and big text
- MessagingStyle messages: sender, sender icon, text, timestamp, whether it's from you, and an image URI when readable
- conversation title, whether it's a group, and the shortcut id
- the large icon and big picture
- the content intent
- the reply action: `SEMANTIC_ACTION_REPLY`, or any action with a free-form `RemoteInput`

**Dedupe.** A message is identified by package, conversation key, sender, timestamp and a hash of the text. When an app re-posts the same conversation, it's updated in place.

**Your own messages** (`isSelf`) appear in the quick-reply window but never count as unread.

**One-time codes.** Android 15+ hides one-time codes from notification listeners. Those notifications arrive redacted and are shown that way.

### 5.3 Rotation, unread and read

- **Rotation.** A tile's rotation is its unread items, newest first, up to 5. With "keep read items for 24 h" on, read items follow the unread ones. If the rotation is empty, the tile shows its front face.
- **Unread.** A MessagingStyle message counts as unread if it's newer than its conversation's `lastReadAt`. Any other notification counts as 1 while it's present and its `readAt` is empty.
- **An item becomes read when:**
  - its notification is removed, because it was opened or swiped away elsewhere
  - the user opens it from a tile, the hub or the Action Center, which sets `lastReadAt`
  - the user taps **mark all read** in the hub
- **Read state is local.** LiquiWin never triggers apps' own mark-as-read actions, because they would send read receipts.

### 5.4 Animation scheduling

Tiles animate the way Windows Phone's did: a single timer for the whole start screen starts one tile at a time.

- Only tiles on screen animate, and only while the start screen is resumed and the display is on.
- **The timer.** The first tick comes 2.5 s after the start screen appears, then every 0.1–3 s at random, in 100 ms steps. Each tick moves one random tile on screen to its next face. Only tiles with a non-empty rotation (§5.3) and a moving template (not `ICONIC` or `STATIC`) take part. A tile that has just moved sits out the next 3 ticks, and if every such tile is sitting out, the tick does nothing.
- **Tile pace** (part of the motion facet) scales the gaps between ticks: normal is the Windows Phone timing above, calm doubles the gaps and lively halves them.
- **New items jump the queue.** A new item makes its tile flip to it within 1 s, even while another tile is moving, debounced to once per 600 ms per tile.
- With motion off, a tile shows its newest item statically and there's no timer.

### 5.5 Taps

| Where | Single-app tile | Combined tile |
|---|---|---|
| Content face body | Open that item | Open that item |
| Reply button | Quick reply | Quick reply |
| Label strip, or anywhere on the front face | Open the app | Open the hub page |
| Small tile | Open the app | Open the hub page |
| Long-press | Edit mode | Edit mode |

"Open that item" tries each of these in turn:
1. the notification's content intent
2. the conversation shortcut (`LauncherApps.startShortcut`)
3. launching the app
4. a "this app isn't installed any more" message

Opening an item marks it read.

### 5.6 Hub page

- A pivot page titled with the tile's name.
- **Tabs:** "all", then one per source, ordered by latest activity, each showing its unread count.
- **Rows:** avatar, sender, preview, time, the app icon (on "all"), and the reply button when available.
- **Tap** opens the item using the fallback in §5.5.
- **Swipe** dismisses the row locally and cancels the Android notification (`cancelNotification(key)`).
- **App bar:** mark all read, open the app (on an app tab), edit tile.
- History covers the retention window, 24 h by default.

### 5.7 Quick reply

- **When it shows.** The reply button appears only when `canReply` is true. After a launcher restart, it comes back once the app posts its notification again.
- **The window.** An overlay inside the launcher, anchored above the keyboard, in the style of Windows Phone Messaging. It opens with the swivel animation and contains:
  - a header with avatar, name, app icon and **open in app**
  - bubbles for the conversation's known messages: incoming in the accent colour with a top-left tail, yours in a darker shade with a bottom-right tail
  - a text box that's already focused, with the keyboard showing
  - a send button
- **Sending.** LiquiWin fills the action's `RemoteInput` (`RemoteInput.addResultsToIntent`, source set to free-form input) and sends its `PendingIntent`. Your bubble appears straight away and is replaced when the app re-posts the conversation including your message.
- **If sending fails** (`PendingIntent.CanceledException`): the text is kept, and the window shows "Couldn't send. Open \<app\> to reply." with an open button.
- **Where it's reachable:** tile content faces, hub rows and Action Center notifications.

---

## 6. App list, search, folders, widgets and Action Center

### 6.1 App list

**Styles:**

| Style | Behaviour |
|---|---|
| WP7 | Opened with the arrow button or a flick left; a plain A–Z list with icons. With 45 or more apps it gains letter headers and the alphabet grid, as WP7.5 did. |
| WP8.1 | Swipe left to open; search box at the top; letter headers; a "new" label on apps installed in the last 3 days |
| W10M | Everything in WP8.1, plus "recently added" (the last 3 installs) at the top |

- **Letter grouping** uses ICU `AlphabeticIndex` for the phone's language. Digits and symbols go under "#", and other scripts get their own groups.
- **Letter headers.** Tap one to open the alphabet grid, with empty letters dimmed, and tap a letter to jump to it.
- **Opening.** When a swipe toward the app list lets go, the items on screen feather in from the top. Each swings in around a hinge 20% of its width to its left (the turnstile feather, forward in) while sliding in from the right, 50 WP px further out per line, starting 40 ms after the item above and taking 350 ms. Items stay hidden while the swipe is held, a drag on the list that springs back doesn't replay it, and Android's remove-animations setting makes it instant. (The owner's call after stage 1, ahead of stage 3's motion set.)
- **Letter scrubber.** A drag that starts within 32 dp of the right edge and moves up or down scrubs through the letters that have apps, spread over the screen's height. Each new letter jumps the list there with a haptic tick and shows in a large accent square beside the finger. Nothing is drawn at rest, and taps and sideways swipes there still reach the rows. Windows Phone had no scrubber; it's LiquiWin's own, alongside the alphabet grid. (The owner's call after stage 1.)
- **Long-press an app** to pin it to start, open its app info, uninstall it, or hide it. Hidden apps are managed in settings and don't appear in search.
- **Work-profile apps** carry a badge, and are greyed out while work apps are paused.

### 6.2 Universal search

- **Entry points:** the search box at the top of the app list (in every style), and a pinnable `SEARCH` tile.
- **Providers** run in parallel with a 120 ms debounce, and results stream in grouped in this order:
  1. apps
  2. app shortcuts, available only while LiquiWin is the default Home app (an Android rule)
  3. contacts, which asks for `READ_CONTACTS` on first use and is skipped if refused
  4. recent updates, from an on-device full-text search of hub history
  5. Android settings pages, from a built-in table of settings intents
  6. "search the web for …", which opens the default browser
- **App ranking:** exact prefix first, then word start, then initials ("wa" finds WhatsApp), then contains. Ties go to the most recently launched.
- Enter opens the first result.

### 6.3 Live folders

- **Create** one in edit mode by dropping a tile onto another and holding for 400 ms. The default name is "folder", and you can rename it.
- **Folder tile:**
  - medium size and up: up to 9 mini previews of the tiles inside, in a 3×3 grid
  - small: the first tile's icon
  - the total unread count
  - flips to its newest inner update, like a combined tile
- **Tap** to open it in place. The tiles inside appear in a band below the folder's row and push the rest down, and they stay fully live and tappable. Tap the folder again, or press Back, to close it.
- **Dissolving.** Dragging tiles out until one is left dissolves the folder, and that tile takes its place. Folders can't contain folders.

### 6.4 Widgets in tiles

- **Adding.** Edit mode → **widget** opens a picker of every installed widget provider, grouped by app, with previews.
- **Binding.** LiquiWin tries `bindAppWidgetIdIfAllowed`, otherwise shows Android's bind dialog, then the provider's configure activity if it has one. Cancelling at any step adds nothing.
- **Sizes.** Only tile sizes between the provider's minimum and maximum are offered. The widget is told its tile's size through `updateAppWidgetSize` and the widget options.
- **Rendering.** An `AppWidgetHostView` inside Compose (`AndroidView`), with the host listening between the activity's start and stop. Long-press is intercepted for edit mode; all other touches go to the widget.
- **Look.** The widget keeps its own look. A per-tile toggle sets whether the tile background shows behind it.
- **Failure.** If the provider is gone or fails to inflate, the tile shows a "widget unavailable" face with reconfigure and remove.

### 6.5 Action Center panel

- **Opening and closing.** Pull down on the start screen while it's scrolled to the top, or tap a pinnable `ACTION_CENTER` tile. Back, swiping up, or tapping outside closes it. The panel exists only inside the launcher, because Android keeps the swipe from the top edge for its own shade.
- **Header:** the date, battery percentage, and **all settings**.
- **Quick actions.** Which ones appear, and in what order, is set in settings. Layout follows the Action Center facet: an 8.1 row of four, or a W10M row of four that expands to all.

  | Action | What it does |
  |---|---|
  | Flashlight | Switches directly |
  | Do Not Disturb | Switches directly after notification-policy access is granted |
  | Brightness slider | Changes directly after "modify system settings" is granted |
  | Auto-rotate | Switches directly after "modify system settings" is granted |
  | Camera | Opens the camera |
  | Wi-Fi | Opens Android's Wi-Fi panel |
  | Mobile data | Opens Android's internet panel |
  | Bluetooth, location, hotspot | Opens their settings pages |
  | Volume | Opens Android's volume panel |

- **Notifications.** Every app's current notifications, not just tile sources, grouped by app with headers, newest first.
  - Tap to open it.
  - Swipe right to dismiss it, which also cancels it in Android.
  - **Clear all** clears the clearable ones.
  - The reply button appears where available.

---

## 7. First-time setup and settings

### 7.1 First-time setup

Windows Phone-style pages with big lowercase headers. Every step can be skipped, and progress is saved, so leaving mid-way resumes at the same step. Setup can be rerun from settings.

1. **hello:** a one-line welcome.
2. **pick a style:** the five presets (WP7, WP8.1, W10M, Modern Metro, Glass) as live previews. Tap one to preview it full screen, or tap **customise** to open the style facets.
3. **accent and theme:** the colour sets for the chosen style, any colour or from wallpaper, plus dark, light or follow phone.
4. **background:** a photo through the Android photo picker, so no storage permission is needed. This is skipped when the background is the theme colour.
5. **make it live:** explains notification access and the privacy promise (no internet permission, nothing leaves the phone), then opens the notification-access settings for LiquiWin. If skipped, tiles show icons only until access is granted.
6. **keep tiles awake:** shown only on Samsung. Explains Samsung's sleeping apps and opens LiquiWin's App info, with the instruction "Battery → Unrestricted". It also shows the alternative route: Settings → Battery → Background usage limits → Never sleeping apps (wording varies by One UI version).
7. **your tiles:** a suggested starting layout built from installed apps:
   - Phone, Messages, Camera, the gallery app, the music app, Settings
   - a *Social* combined tile from any installed Instagram, Snapchat and Facebook
   - a *Chats* combined tile from any installed WhatsApp, Messenger and Telegram

   The user can untick suggestions, edit the combinations, or start empty.
8. **set as home:** Android's `ROLE_HOME` request dialog, then the turnstile into the new start screen.

### 7.2 Settings

The standard Windows Phone settings list: each entry shows a title with its current value underneath in the accent colour. Sections:

| Section | Contents |
|---|---|
| style | Presets and every facet (§4.1) |
| start screen | Lock layout, tile pace |
| live tiles | Notification access health, ignored apps, keep read items, hide previews, history length (24 h default), clear history |
| app list | Style, entry, hidden apps, "new" label |
| search | Which providers are on, web search engine |
| action center | Quick actions shown and their order, layout |
| backup | Export and import the layout and settings as a JSON file (Storage Access Framework) |
| about | Version, open-source licences, rerun setup |

---

## 8. Permissions and privacy

| Capability | Android permission or role | Asked | If refused |
|---|---|---|---|
| Being the Home app | `ROLE_HOME` via `RoleManager` | Setup step 8; settings | LiquiWin works as a normal app, and Home still goes to the old launcher |
| Live tiles, hub, Action Center notifications, quick reply | Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`, granted in Settings) | Setup step 5; the paused banner | Tiles show icons only; hub, Action Center notifications and reply are hidden |
| Listing apps | `<queries>` MAIN/LAUNCHER + `LauncherApps` | — | — |
| Uninstalling from the app list | `REQUEST_DELETE_PACKAGES` (normal permission); Android still confirms each uninstall | — | — |
| Shortcut search results, pinned shortcuts | Default-home status (shortcut host permission) | — | Those features are unavailable |
| Contacts in search | `READ_CONTACTS` | First contact search | Contacts are skipped |
| Widgets | Android's widget bind consent, once per provider | When adding a widget | The widget isn't added |
| Do Not Disturb action | Notification policy access | First use | Opens Do Not Disturb settings instead |
| Brightness and auto-rotate actions | `WRITE_SETTINGS` ("Modify system settings") | First use | Opens display settings instead |
| Flashlight | None (torch API) | — | — |
| Glass "light follows tilt" | None (rotation-vector sensor) | — | — |
| Background photo | Android photo picker (no permission) | Setup step 4; settings | Theme-colour background |
| Staying awake on Samsung | None; the user sets App info → Battery → Unrestricted | Setup step 6 | The paused banner appears if tiles pause |
| Haptics | `VIBRATE` (normal permission) | — | — |

**Never requested:** `INTERNET`, `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, any accessibility service, storage, location, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

**Privacy rules:**
- Notification content is stored only in app-private storage.
- It's kept for 24 hours by default and excluded from backups.
- It can be wiped from settings, and never leaves the device.

---

## 9. Error handling and reliability

| Situation | Behaviour |
|---|---|
| Notification access off or revoked | Checked on every resume. Tiles drop back to front faces with a small "paused" mark, and a slim banner reading "live tiles paused · fix" opens the exact settings page. |
| Listener disconnected or killed (Samsung) | `onListenerDisconnected` → `requestRebind`. On every resume, if access is granted but the listener isn't connected, LiquiWin rebinds. After 3 disconnects in 24 h it shows the "keep tiles awake" guide. |
| Tap target gone | The fallback chain in §5.5 |
| Quick reply fails | The text is kept, and an "open \<app\> to reply" button appears (§5.7) |
| App uninstalled | Its `APP` tile is removed and its source is taken out of combined tiles. A combined tile left with no sources is removed. Folders update. |
| App updated, or its icon changed | The icon cache refreshes through `LauncherApps.Callback` |
| Work apps paused | Those apps and their tiles are greyed out |
| Widget provider missing, or inflate error | "widget unavailable" face with reconfigure and remove (§6.4) |
| Crash loop | 3 crashes within 60 s of start → safe mode: a plain app list with an "exit safe mode" button. The user can always switch back in Settings → Apps → Choose default apps → Home app. |
| Database migration fails | The old file is copied aside, LiquiWin starts with the default layout, and the user is told. It never crash-loops. |
| Storage growth | 50 items per app, 24 h retention, downscaled images, cleaned up hourly while the launcher is running |
| Main-thread safety | Listener callbacks, parsing, database and image work run on background dispatchers. StrictMode is on in debug builds. |
| Glass too heavy for the GPU, or unsupported Android version | The quality step-down and fallbacks in §4.8 |

**Performance targets:**
- the start screen appears within 300 ms of pressing Home when the launcher is running, and within 1.5 s from cold
- no dropped frames while scrolling the start screen
- a tile shows a new item within 1 s

---

## 10. Testing

- **Unit tests** (JVM, with Robolectric where Android classes are needed):
  - Layout engine: first-fit flow, compaction, per-width arrangements, collision re-flow after a resize, folder create and dissolve.
  - Normaliser: run against fixture notifications from WhatsApp, Instagram, Snapchat, Messenger, Telegram and Gmail. The fixtures are recorded once on the owner's Galaxy with a debug-only capture screen, which strips names and text before saving.
  - Unread counting and dedupe, the tap fallback chain, the animation timer (one tile per tick, the 3-tick rest, new items jumping the queue), search ranking, and facet/preset resolution (including when the label switches to "Custom").
- **Database tests:** Room DAO tests and a migration test for every schema version.
- **Screenshot tests** (Roborazzi): every flat preset in light and dark × every tile size × every template, the hub, quick reply, the app list styles and the Action Center layouts, plus app names in Devanagari and Cyrillic to check the font fallback. A change to one style can't silently break another.
- **Glass tests:** shaders need a GPU, which JVM screenshot tests don't have.
  - Instrumented screenshot tests run the Glass preset on an Android 14+ emulator with GPU rendering, and the results are reviewed on the Galaxy.
  - The morph state machine (which elements merge, spring targets, hit-test positions) and the signed-distance geometry are unit-tested on the JVM.
  - Macrobenchmark runs the Glass preset separately, covering scrolling plus pin and unpin morphs.
- **Compose UI tests:** edit mode (move, resize, unpin, making a folder), the tile editor, hub tabs and swipe, quick reply (focus and the keyboard appearing, sending, the failure state), and first-time setup.
- **`:tools:notifier`:** a debug app that posts WhatsApp-like and Instagram-like MessagingStyle notifications, with reply actions that echo the reply back. It allows end-to-end tests of tiles and quick reply on the emulator without real accounts.
- **Macrobenchmark:** cold and warm start, start screen scroll jank, and a Baseline Profile.
- **Manual checklist on the Galaxy:**
  - setting it as Home, gesture navigation and back (expect the longer hold for recent apps, §1.5)
  - granting and revoking notification access
  - a day under battery management with the sleeping-apps settings
  - real messages and quick replies in WhatsApp, Instagram, Messenger and Telegram
  - Snapchat updates (open the app, no reply button expected)
  - Samsung widgets
  - a work profile (apps in Samsung's Secure Folder are invisible to all third-party launchers, which is expected)
  - Settings panels (Wi-Fi, internet, volume) on One UI

---

## 11. Delivery stages

The implementation plan follows these stages. Each one ends with an installable build on the Galaxy.

| Stage | Contents |
|---|---|
| 1. Usable home screen | Project and module setup; basic design system (one preset); grid with static app tiles; pin, unpin, move and resize; WP8.1 app list with app search; Home role; crash safe mode |
| 2. Tiles come alive | Notification listener, normaliser, store, live action cache; templates; combined tiles and the tile editor; tap regions; hub page; quick reply; `:tools:notifier`; paused banner and Samsung handling |
| 3. Every Windows Phone style | All flat facets and the four Windows Phone presets; backgrounds, parallax, transparency; the full motion set; the three app list styles; status bar behaviour; screenshot test matrix |
| 4. Glass | First a throwaway shader prototype on the Galaxy, as a go/no-go on full-refresh-rate performance. Then the material and glass facets, refraction, the morph system, fallbacks, built-in backgrounds and the Glass tests. |
| 5. Extras | Live folders; widgets in tiles; pinned shortcuts and conversation tiles; all search providers; Action Center |
| 6. Setup and polish | First-time setup; settings pages; backup and export; Baseline Profile and macrobenchmarks; the Galaxy checklist |

---

## 12. Decisions log

Decisions made with the owner during brainstorming on 2026-10-27:

| Question | Decision |
|---|---|
| Purpose | Personal daily driver now, publishable on the store later |
| First sub-project | Start screen and live tiles |
| Look | All four looks (WP7, WP8.1, W10M, Modern Metro) selectable at setup and in settings, and then free rein: every facet can be mixed independently, and presets only fill them in |
| Tapping a combined tile | The update shown opens that app at that item; anywhere else opens the hub page |
| Extras in the first build | Live folders, Action Center panel, universal search, widgets in tiles |
| Quick reply | Added: a reply button at the bottom-right of an update opens a Windows Phone-style floating window with the keyboard up |
| Test phone | Samsung Galaxy (One UI) |
| Approach | Native Kotlin + Jetpack Compose, chosen over Flutter + Kotlin and over forking Launcher3/Lawnchair |
| Glass style | Added as a fifth preset (§4.8): etched and never clear, tinted by the accent (white is neutral), refracting at the rim, and morphing on every layout change. Rounded corners by default, with square available. Named "Glass" to stay clear of Apple's trademark. |
| Repository and name | [github.com/B1TSagar/LiquiWin](https://github.com/B1TSagar/LiquiWin); working name LiquiWin |

---

## Appendix A — Windows Phone reference

Exact per-version values are kept in [`docs/reference/windows-phone-ui-reference.md`](../../reference/windows-phone-ui-reference.md), with sources: column counts, tile sizes and gaps, accent colour sets with hex codes, type ramp, animation timings, Action Center layouts, and font and icon licences. Presets and motion in this spec take their numbers from there. Where the reference marks a value unverified, the implementation picks the closest value that looks right and records it in the reference.

## Appendix B — Notes carried forward to later sub-projects

These are not part of sub-project 1. They record what was learned while scoping, so the later specs start from facts.

**Music (sub-project 2)**
- **Now playing for any player.** Uses `MediaSessionManager.getActiveSessions()`, which is allowed because LiquiWin already holds notification access.
- **Spotify:**
  - The Android SDK (App Remote) plus the Web API.
  - Since February 2026, new development-mode apps are limited to 5 users, and to 1 client ID per developer, raised to 25 in July 2026.
  - The recommendations endpoints have been closed to new apps since November 2024.
  - Wider ("extended") access is only granted to organisations.
  - Source: [Spotify Feb 2026 migration guide](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide).
- **Apple Music:**
  - MusicKit for Android (sign-in and playback) plus the Apple Music API.
  - The Android SDK is dated and doesn't support radio stations.
  - It needs the Apple Developer Program ($99 a year).
  - Sources: [MusicKit](https://developer.apple.com/musickit), [Apple forum thread](https://developer.apple.com/forums/thread/822428).
- **YouTube Music:**
  - There's no official API.
  - Search uses the YouTube Data API v3 (10,000 units a day, and a search costs 100), and playback is handed off to the YouTube Music app.
  - Avoid the unofficial `ytmusicapi`, which isn't endorsed by Google and conflicts with the plan to publish.
- **Recommendations.** A neutral source (for example Last.fm similar tracks or ListenBrainz), seeded by listening history recorded on the device from media sessions across all players. Each song is then played on whichever service the user has, as a nod to Windows Phone's "Smart DJ".
- **Privacy.** Music needs internet access, so sub-project 2 must keep notification data isolated from networked code and update the privacy statement.

**Mail (sub-project 3)**
- Gmail access (the Gmail API or IMAP with OAuth) uses restricted scopes. A public release needs Google's verification, including a recurring third-party security assessment. Personal use can run as an unverified app in testing mode with test users. Verify the details at the start of sub-project 3.
- Outlook through Microsoft Graph; other accounts through IMAP.

**Photos (sub-project 4)**
- Since 31 March 2025, the Google Photos Library API only returns media the app itself created. Photos will use on-device `MediaStore` and the Android photo picker, with Google Photos content reachable only through its picker.
