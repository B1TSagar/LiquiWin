# LiquiWin handoff: stage 2 not started

Written 2026-10-28 so the work can continue on another PC, in a Claude Code session that never saw the original conversation. The [spec](../specs/2026-10-27-liquiwin-launcher-start-screen-design.md) remains the authority. This file adds where the work stopped, what changed after stage 1, how the owner wants phone checks done, and what this PC ran into.

## Resume on the other PC

1. Install the tools listed under [Machine setup](#machine-setup).
2. Clone the repository, or update an existing clone:

   ```
   git clone https://github.com/B1TSagar/LiquiWin.git
   cd LiquiWin
   ```

   In an existing clone: `git switch main`, then `git pull`.
3. Start Claude Code in that folder and turn on the owner's working mode: `/ponytail:ponytail [ultra]`.
4. Paste this prompt:

```text
Read docs/superpowers/handoff/2026-10-28-liquiwin-stage-2-handoff.md first. Then write
the stage 2 ("Tiles come alive") implementation plan with superpowers:writing-plans,
following the handoff's "Next: stage 2" section, and save it to
docs/superpowers/plans/2026-10-28-liquiwin-stage-2-tiles-come-alive.md. Review it against
the spec, then stop and ask me to review it and choose how it runs.
```

## Where the work stands

- **`main` holds everything**, on GitHub too. Nothing is in progress, and there are no other branches.
- **Stage 1 is done**: all 12 tasks, the final review and its fixes, and the Galaxy check. Its workspace and ledger were deleted as the process says; git history is the record.
- **After stage 1, the owner asked for motion work**, all merged and pushed:

  | Commit | What it did |
  |---|---|
  | `e9d200a` | The app list feathers in as it opens (turnstile in plus WP's slide-in), and a right-edge letter scrubber |
  | `7891b73` | Opening an app turnstiles the screen away first: start by column with the tapped tile last, the list top to bottom with the tapped row last |
  | `7a909e6` | Start and the app list change in place, with no sliding: the leaving screen turnstiles away, then the other turns in, and going back plays both backwards. Coming back from an app rewinds the exit, tapped item first, once Android's enter animation ends. Swipes are ignored while rearranging, and TalkBack gets "app list" and "start" actions. |
  | `427f9d2` | Every letter the scrubber reaches replays the feather-in from that letter's header |
  | `3ddc8a2` | After Home from another app, start's tiles wait out One UI's close-to-home animation before rewinding |

- **Tests:** 122 unit tests in 28 suites pass with `./gradlew testDebugUnitTest`, and the release build (R8) works.
- **Docs follow the code:** the spec's §4.2, §4.7 and §6.1 describe the changes above, and the [reference](../../reference/windows-phone-ui-reference.md) has "Values chosen in stage 1" and "Values added after stage 1".
- **The owner's phone** runs the current release build, with LiquiWin set as Home.

## Next: stage 2

The spec's §11 row: "2. Tiles come alive | Notification listener, normaliser, store, live action cache; templates; combined tiles and the tile editor; tap regions; hub page; quick reply; `:tools:notifier`; paused banner and Samsung handling".

No plan exists yet. A first drafting attempt was stopped before it wrote anything. The brief for the plan:

- **Format:** exactly like the [stage 1 plan](../plans/2026-10-27-liquiwin-stage-1-usable-home-screen.md). That means the header (with the "For agentic workers" line, Goal, Architecture, Tech Stack and Spec), Global Constraints with the spec's values verbatim, and Review Focus: the five failure modes no task's tests exercise, each pinned by a test in its owning task. Each task has Files, Interfaces (Consumes and Produces, with exact names and types) and TDD steps. Commit messages follow the repo's style: a short imperative subject, no prefix.
- **Read** the whole spec, especially §3, §4.4, §4.5, §5, §8, §9, §10, §12 and Appendix B, plus the reference doc.
- **Reuse, don't re-plan:**
  - `core/design/.../motion/Turnstile.kt`: the `Turnstile` motions IN, OUT and BACK_IN, `TurnstileClock` with play, rewind and hideFor, and `Modifier.turnstile`. Also `Tilt.kt`.
  - HomeRoot's in-place navigation and its `onScreen` gating.
  - StartScreen's flip-then-launch.
  - The app list's feather, flip, rewind and `LetterScrubber`.
  - The tile model's multiple sources per tile (`TileSourceEntity`, `SourceId`), plus `TileTemplate` and `TileKind`. Check what exists before planning new tables.
- **Data:** the notification tables go in `cache.db`, which isn't backed up, as the spec says.
- **Privacy:** quick reply sends only on the owner's explicit send action, and notification content stays on the device (spec §8).
- **Dependencies:** prefer none. Any needed download goes in a "Downloads to approve" note with its name, coordinates and size.
- **Last task:** an automated Galaxy check (see [Phone testing](#phone-testing)), then recording the chosen values in the reference doc.
- **Size:** stage 1 was 12 tasks, and stage 2 is bigger. Weigh splitting it into two plans, and tell the owner either way.
- **After the plan:** the owner reviews it and chooses subagent-driven or native (inline) execution.

## Owner decisions and standing rules

- **Never enter credentials or sign in for the owner.** The owner grants special access themself, such as the Home role and notification access.
- **Use no unofficial APIs.**
- **Don't change system or security settings**, such as the hosts file, "Stay awake", the screen timeout or the default apps.
- **Ask before pushing to `main` or merging.** Commits on a branch are fine.
- **Downloads** beyond the build's own dependencies need the owner's OK, with the name, source and size.
- **Working mode:** `/ponytail:ponytail [ultra]`. Make the smallest complete change, question parts the need doesn't justify, and never cut accessibility, security or validation. End each reply with what was skipped or not checked, and any risk.
- **Phone checks:** automate them as below, and ask the owner only for what can't be automated.

## Machine setup

- **JDK 21 and the Android SDK** (platform 37, platform-tools). If `JAVA_HOME` and `ANDROID_HOME` aren't set globally, set them in each Gradle command. Nothing in the build hard-codes paths.
  - The PC this was written on: `JAVA_HOME=C:\Program Files\Android\openjdk\jdk-21.0.8`, `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`, with adb at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
  - The first PC used `C:\Android` paths.
- **Claude Code** with the superpowers plugin (writing-plans, executing-plans, subagent-driven-development, test-driven-development) and the ponytail plugin.
- **ffmpeg**, for pulling frames out of phone recordings.
- **Git Bash on Windows:** set `MSYS_NO_PATHCONV=1` before passing device paths like `/data/local/tmp` to adb. Otherwise Git Bash rewrites them into Windows paths.

## Phone testing

The phone is a Galaxy S24 (SM-S921E, 1080 × 2340 px, 411 dp wide) on One UI, with gesture navigation.

**Connecting**
- Wireless debugging is the way in; the USB cable at hand is charge-only.
- Each PC needs pairing once. The owner opens Developer options › Wireless debugging › Pair device with pairing code, and types the code into `adb pair <ip:port>` themself.
- `CERTIFICATE_UNKNOWN` in `%TEMP%\adb.log` means the phone has forgotten the pairing.
- At first the home router kept Wi-Fi devices apart, so the phone was reached over the PC's Windows Mobile hotspot, which the owner switched on. Later it also connected over home Wi-Fi. If it can't connect, use the hotspot.

**How the owner wants checks done**
- Drive the phone over adb and send the owner screenshots and recordings.
- The owner only unlocks, grants access, picks the Home app and restarts.
- Ask the owner to keep their hands off during a run; their own swipes once mixed into a scripted run.

**Script technique**
- `uiautomator dump` gives element bounds, because Compose exposes text and content descriptions:
  - tiles by description (the app's label);
  - list rows by text;
  - edit buttons as "unpin", "resize" and "done".
- **Resetting:** two explicit Home intents put LiquiWin at the top of start: `am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n io.github.b1tsagar.liquiwin/.HomeActivity`. A plain `am start -n io.github.b1tsagar.liquiwin/.HomeActivity` brings LiquiWin back as it was left.
- **Guarding:** before every step, check which app is in front (`dumpsys activity activities`, `topResumedActivity`), and stop if it isn't the expected one.
- **Swipes:** start them well inside the edges, since a touch near the right edge can start Back. Don't fake a held drag with separate `input motionevent` calls; they don't form one gesture.
- **The scrubber** spreads the letters over the whole screen height. Scrub to y ≈ 5 to land on the first letter.
- **Recording:** use `screenrecord --bit-rate 4000000`. Log `/proc/uptime` right after starting it; a moment's time in the video is about its uptime minus that mark.
- **Frames:** tile them with ffmpeg's `-vf "fps=N,scale=180:-1,tile=6x2"`. A frame's mean colour from `showinfo` tells tiles apart from black.
- **ACKO** needs Back pressed twice, so bring LiquiWin back with the plain `am start -n` instead.
- **If LiquiWin crashes while it's Home**, Android drops it as the preferred Home, and the owner picks it again in the picker.

The helper used, in Git Bash:

```bash
export MSYS_NO_PATHCONV=1 # stop Git Bash rewriting device paths like /data/local/tmp
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" # adjust per PC
S=$("$ADB" devices | awk '$2 == "device" { print $1; exit }')
a() { "$ADB" -s "$S" "$@"; }
# Every node with a text or content description: text|description|left top right bottom
nodes() {
  a shell uiautomator dump /data/local/tmp/lw.xml > /dev/null || return 1
  a exec-out cat /data/local/tmp/lw.xml | grep -oE '<node [^>]*>' |
    sed -nE 's/.* text="([^"]*)".* content-desc="([^"]*)".* bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1|\2|\3 \4 \5 \6/p' |
    grep -v '^||'
}
# The centre of the on-screen node whose text or description is $1
spot() { nodes | awk -F'|' -v want="$1" '($1 == want || $2 == want) { split($3, b, " "); if (b[1] >= 0 && b[1] < 1080) { print int((b[1] + b[3]) / 2), int((b[2] + b[4]) / 2); exit } }'; }
front() { a shell dumpsys activity activities | grep -m1 topResumedActivity | grep -oE "u0 [a-zA-Z0-9_.]+/"; }
```

## Carried forward

**Stage 1's deferred minors**, still the owner's to decide:
1. Reopening search shows the last query.
2. A failed cache write (disk full) crashes the app.
3. Tapping a paused work tile does nothing visible while LiquiWin isn't the Home app.
4. The crash counter is written on the main thread at the one-minute mark even when unchanged.
5. Each tile tap reads the database before launching.
6. The search row duplicates the app list's row.
7. Context-menu entries are under the 48 dp touch target on phones narrower than the S24.
8. Untested: telling Home presses apart, and the icon cache's disk read.
9. One in-between frame is possible after a resize.
10. The app list's search box is about 39 dp tall, under the 48 dp touch target.

A stage 1 minor about edit mode surviving a swipe to the list was fixed by `7a909e6`.

**Noticed on the phone, not yet decided:**
- After a crash, Android shows its Home picker while LiquiWin still holds the Home role, so LiquiWin's banner stays hidden.
- App list rows scroll under the status bar.
- A shrink can put a medium tile on an odd column.

**Checked only on the phone, with no unit test:**
- the scrubber's replay;
- the waits for Android's enter animation and One UI's close-to-home animation before a rewind.

**One UI ignores the fade** LiquiWin asks for when opening an app.
