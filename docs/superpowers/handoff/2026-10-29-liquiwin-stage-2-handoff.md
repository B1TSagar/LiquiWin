# LiquiWin handoff: tile gestures, Galaxy re-check paused

Written 2026-10-29 on the first PC, so the work can continue on the other PC in a Claude Code session that never saw this conversation. The [spec](../specs/2026-10-27-liquiwin-launcher-start-screen-design.md) remains the authority. The [stage 2 handoff](2026-10-28-liquiwin-stage-2-handoff.md) still holds for machine setup, connecting the phone and the basic phone technique. This file adds where the work stopped, the owner's rules added since, and what the remaining phone checks need.

## Resume on the other PC

1. Get the branch:

   ```
   git fetch origin
   git switch stage-2-tiles-come-alive
   ```

   In a fresh clone, run `git clone https://github.com/B1TSagar/LiquiWin.git` first. The work stays on this branch, and `main` is untouched.
2. Restore the two plan ledgers. Git ignores `.superpowers/`, so they travel as copies in [2026-10-29-ledgers](2026-10-29-ledgers/). In PowerShell:

   ```powershell
   foreach ($p in '2026-10-28-liquiwin-stage-2-chat-window', '2026-10-29-liquiwin-stage-2-tile-gestures') { New-Item -ItemType Directory -Force ".superpowers/sdd/$p" | Out-Null; Copy-Item "docs/superpowers/handoff/2026-10-29-ledgers/$p.md" ".superpowers/sdd/$p/progress.md" }
   ```

   In Git Bash:

   ```bash
   for p in 2026-10-28-liquiwin-stage-2-chat-window 2026-10-29-liquiwin-stage-2-tile-gestures; do mkdir -p ".superpowers/sdd/$p" && cp "docs/superpowers/handoff/2026-10-29-ledgers/$p.md" ".superpowers/sdd/$p/progress.md"; done
   ```

3. Start Claude Code in that folder and turn on the owner's working mode: `/ponytail:ponytail [ultra]`.
4. Paste this prompt:

```text
Read docs/superpowers/handoff/2026-10-29-liquiwin-stage-2-handoff.md first. I'm on the
branch stage-2-tiles-come-alive and have restored the ledgers. Resume the tile-gestures
plan with superpowers:executing-plans, Native as I chose. Finish the paused Galaxy
re-check as the handoff says, starting with its Signing section: ask me how to install
before touching the phone. Then record the results, and go on with "After the re-check"
in order. Ask me before any push to main or merge.
```

## Where the work stands

- **The branch** `stage-2-tiles-come-alive` is on GitHub, 53 commits on top of `origin/main` (`8eb94e5`, "Add the stage 2 handoff"). Nothing is merged; the owner decides how it lands.
- **Tests:** 382 unit tests pass, and both APKs build, with `./gradlew testDebugUnitTest :app:assembleRelease :tools:notifier:assembleDebug`.
- **Three plans** ran on this branch, each Native (inline), each with one final review on the most capable model and one fix pass:

  | Plan | Left |
  |---|---|
  | [Part 1](../plans/2026-10-28-liquiwin-stage-2-tiles-come-alive.md), live app tiles | Task 12 Steps 6 and 8. Its ledger was deleted at its finish. |
  | [Chat window](../plans/2026-10-28-liquiwin-stage-2-chat-window.md) | Task 8 Step 5, then the finish |
  | [Tile gestures](../plans/2026-10-29-liquiwin-stage-2-tile-gestures.md) | The Galaxy re-check, Task 9 Step 5, then the finish |

- **The owner's requests after the tile gestures' Galaxy check**, each built test-first with its spec sections and the reference doc updated:

  | Commit | What it did |
  |---|---|
  | `c5be480` | A swipe left on a missed call showing its buttons presses the first, call back on most phones, as a tap does |
  | `a033634`, `dfa6ab4`, `88d4964` | WhatsApp posts a photo's message, then posts it again with the photo. A later post now fills in a stored message's missing photo. The notifier's `late` sample does the same. |
  | `51b50f5`, `c21099c` | GIFs and moving stickers up to 720 px and 5 MB are kept as they came. In the chat window one plays for 5 s, then holds, and a tap plays it again. Tiles show the first frame. The notifier's `gif` sample. |
  | `1af9ecc` | A new message shown in a large tile's list counts as the tile's move, so the tile rests 3 ticks. This was a rare test flake. |
  | `dd03569` | TalkBack can reach a moving photo, found by the re-check below |

- **The ledgers** hold every ruling, deferred minor and final-review finding. Each plan's finish lists them in full.

## The Galaxy re-check, paused

Started 2026-10-29 with the build of `1af9ecc`, which is still on the phone.

- **Passed:**
  1. A swipe left on the notifier's missed call logged `button: Call back`, opened no window, and marked the call read.
  2. `post late` showed its photo in the bubble after the second post.
  3. Emoji (🎉, 👍🏽, the family sequence 👨‍👩‍👧, ❤️) showed on the tile and in the window, with every code point in the accessibility text.
  4. The tile held the GIF's first frame over 10 uneven shots. In the window a tap played the GIF, and it then held.
- **Found:** TalkBack couldn't reach the moving photo at all. Compose hands an `AndroidView` to accessibility as the bare view, without the semantics set on it. A plain wrapper isn't enough either: Compose dropped the wrapper's description and split its role into a separate node. `dd03569` fixes it with a wrapper that uses `clearAndSetSemantics`, and its Robolectric test walks the window's accessibility tree the way TalkBack does.
- **Left, in order:**
  1. Install the build of `dd03569` or later (`adb install -r app/build/outputs/apk/release/app-release.apk`), after [Signing](#signing).
  2. `post gif`, wait for the notifier's tile to show the GIF, and swipe left on it to open the window. In `uiautomator dump`, expect one node with `content-desc="photo"`, `clickable="true"` and class `android.widget.Button`. The dump leaves out state descriptions, so "playing" stays with the unit test.
  3. Measure from opening: the photo alternates orange and white for about 5 s, then holds one colour, and a tap plays it again. The first pass didn't measure the play on opening.
  4. Back closes the window, and two Home intents put start back.
  5. Record the results:
     - a "Found during the re-check" bullet in the [reference doc](../../reference/windows-phone-ui-reference.md), under the tile gestures' values;
     - trim that section's "Still to check", but keep TalkBack's explore by touch on a moving photo, which needs TalkBack on, and only the owner turns that on;
     - a line in the tile-gestures ledger.
- **The phone when the work stopped:** the touch watcher had caught a real touch, and the phone then dozed and locked. The watcher is stopped, and this run's files are gone from `/data/local/tmp`. The owner unlocks the phone and leaves it alone before any run.

## Signing

LiquiWin's release build is signed with the debug key, and the phone's copy carries the first PC's key. Android won't update it from a build with another key. Uninstalling loses the Home role and notification access, which only the owner grants, and the standing rule is never to uninstall it. So before installing from the other PC, the owner chooses one:

- Copy the first PC's `%USERPROFILE%\.android\debug.keystore` to the same place on the other PC, by hand, never through git.
- Uninstall and grant both again.

The notifier on the phone is current, since nothing in it changed after `c21099c`.

## After the re-check

1. **Tile gestures, Task 9 Step 5,** the owner's own:
   - a WhatsApp photo and a moving sticker from a real chat;
   - a real missed call, whose swipe left now calls back for real;
   - whether a call's tile names the caller or reads "Missed call".

   The agent takes no shots and presses no real buttons here. Then run `task-done` for Task 9, with BASE `0b5701e` and the full test command above.
2. **Chat window, Task 8 Step 5,** the owner's real-app replies. WhatsApp's echo must land as the owner's own bubble; otherwise fix `isUser`. The final reviewer's extra checks: the keyboard leaves with the window (Back, scrim, Home), and a scrim tap works in the first 0.3 s.
3. **Each plan's finish** (superpowers:executing-plans, Finish):
   - "Rulings I made" and "Deferred minors" from its ledger, both complete;
   - delete its `.superpowers/sdd/` workspace;
   - then superpowers:finishing-a-development-branch.

   Ask before any push to `main` or merge.
4. **Part 1, Task 12:**
   - **Step 6:** the owner pins their real apps and uses the phone. Each wrong-looking tile gets a fixture and a fix, or a part 2 note. Also the work-profile check.
   - **Step 8:** a day later, run `dumpsys notification | grep -i liquiwin`. Ask about the "keep stopping" banner and record it in the reference doc. Commit "Record the day-long live tile check", then unpin the notifier's tile, keeping the app.
5. **The rest of part 2** (combined tiles, the editor, hub pages, other templates) gets its plan after these land.

## Owner rules added since the stage 2 handoff

The stage 2 handoff's rules all still hold.

- **Phone access:**
  - The owner taps Allow for notification permission; never `pm grant`.
  - Never toggle notification access, the Home role, TalkBack or the animator scale.
  - Never enter a PIN or pattern.
  - Never uninstall LiquiWin.
- **Messages and privacy:**
  - Never send real messages, and never press a real missed call's buttons from automation.
  - Never clear the owner's real notifications. Automated taps and swipes land only on the notifier's tile or the notifier's chat window.
  - Notification content stays on the device. Logs never include names, keys or texts.
  - From `dumpsys`, print structure and counts only. When listing nodes, print only those inside the notifier's tile or the window panel.
- **Shots:**
  - Crop to the notifier's tile or the window panel, and keep the keyboard's suggestion strip out.
  - Never send a shot of the owner's own notifications or apps.
  - Mockups use generic names.
- **Git:** commit messages have a short imperative subject with no prefix and end with the session's Co-Authored-By trailer. PR bodies end with the Claude Code line.

## Phone technique added since the stage 2 handoff

- **Touch watcher:** people pick the phone up mid-check, and a real touch voids a run. Start a watcher before a run, and check it before each step:

  ```
  adb shell "rm -f /data/local/tmp/lw-touched; pkill getevent; setsid sh -c 'getevent -q -c 1 /dev/input/event7 >/dev/null 2>&1; echo touched > /data/local/tmp/lw-touched' </dev/null >/dev/null 2>&1 &"
  adb shell "[ -e /data/local/tmp/lw-touched ] && echo TOUCHED || { pidof getevent >/dev/null && echo quiet || echo WATCHER-GONE; }"
  ```

  - `event7` is the S24's `sec_touchscreen`. Never watch every device, since `als_rear` is noisy.
  - Injected `input` never trips it, and it records no coordinates.
  - Run each step in the same `adb shell` as its guard: no marker, the watcher alive, and LiquiWin the resumed activity (`dumpsys activity activities | grep -m1 topResumedActivity`).
- **The notifier:**
  - Post with `am broadcast -a io.github.b1tsagar.liquiwin.tools.notifier.POST -n io.github.b1tsagar.liquiwin.tools.notifier/.PostReceiver --es chat <chat> --es text '<text>'`.
  - The chat is `alex`, `family`, `photo`, `snap`, `call`, `picture`, `late` or `gif`. The last six ignore the text.
  - Its logcat tag is `notifier`, and a pressed button logs `button: <label>`.
- **Text adb can't pass,** such as emoji: write it into a UTF-8 shell script, `adb push` it and run it with `sh`.
- **The notifier's tile** is wide, at 387,130–1025,436 px, while LiquiWin is the Home app. Otherwise LiquiWin's banner pushes the tiles down 153 px.
  - A swipe left on its message: `input swipe 706 283 387 283 300`.
  - A missed call's buttons aren't separate nodes, only custom actions on the tile's one node (`notifier`), so tell the tile's faces apart by pixels.
- **The chat window's panel,** for crops: left = 1080 − the right edge of `open in …`, top = its top − 10, right = min(1080, its right + 30), bottom = the bottom of `send` + 30.
- **The `gif` sample** is a 1 × 1 GIF scaled up that turns orange (240,150,9) and white every 0.5 s. A shot's mean colour over the photo's box tells which. On the tile, use 520,160–900,300.
- **`uiautomator dump`** writes characters outside the BMP as numeric entities, like `&#127881;`.
- **`am start`** with only new extras brings the old task forward, so add `--activity-clear-task`.
- **Without ffmpeg,** frames come from `screencap` shots, cut with System.Drawing.

## Test technique added

- **Robolectric's text sizes are fake:** every line is 35 px tall and about 1 px wide per character. A Compose test that needs several text blocks on screen may need `@Config(qualifiers = "h800dp")`; don't weaken its assertions.
- **Robolectric's native graphics** (`@GraphicsMode(NATIVE)`) decode image bytes but not files on Windows.
- **What TalkBack sees:** `AccessibilityNodeInfo.setQueryFromAppProcessEnabled` lets a Robolectric test walk the window's real accessibility tree, which Compose's semantics tree can't show (see `MovingPhotoTest`).
- **A user folder with a space** (like `C:\Users\Musical DNA`): Robolectric looks for `%20` paths, so Room tests fail, and Gradle may fail with "Unable to establish loopback connection". Set both fixes per command, never in project files: `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<short path>\.gradle -Duser.home=<8.3 short path>`.

## Carried forward

For part 2 or a follow-up, beyond the ledgers:
- the medium tile cuts a group's "Mum · Family" sender line to "Mum · F…";
- two Room observers per live source, with every source re-mapping on any post;
- `StartViewModel.live` shares `Eagerly`;
- a resume that loses the bind race counts as a stop;
- `KeepAwakeGuide` has a hard-coded white border;
- a notification with more than 999 messages is skipped;
- no test for the disconnect → health wiring;
- avatars are decoded again on every face;
- stage 1's search box is 39 dp, under the 48 dp target.
