# LiquiWin handoff: stage 1 paused in Task 1

Written 2026-10-27 so the work can continue on another PC, in a Claude Code session that never saw the original conversation. The [spec](../specs/2026-10-27-liquiwin-launcher-start-screen-design.md) and the [plan](../plans/2026-10-27-liquiwin-stage-1-usable-home-screen.md) remain the authorities. This file adds where the work stopped, the decisions taken since the spec, and what the first PC ran into.

## Resume on the new PC

1. Install the tools listed under [Machine setup](#machine-setup).
2. Clone the repository and switch to the stage 1 branch:

   ```
   git clone https://github.com/B1TSagar/LiquiWin.git
   cd LiquiWin
   git switch worktree-stage-1-home-screen
   ```

3. Start Claude Code in that folder. Open it on the clone itself, not in a new worktree: the branch already keeps the work off `main`.
4. Paste this prompt:

```text
Read docs/superpowers/handoff/2026-10-27-liquiwin-stage-1-handoff.md first. Then
continue executing docs/superpowers/plans/2026-10-27-liquiwin-stage-1-usable-home-screen.md
inline with superpowers:executing-plans, in this checkout on branch
worktree-stage-1-home-screen. Recreate the ledger from the handoff's "Ledger" section,
resume Task 1 at step 1's check, and carry on through Task 11 without check-ins.
Then run the final whole-branch review and its fix pass, and stop at Task 12,
which needs me and the phone.
```

## Where the work stands

LiquiWin is a modern Windows Phone-style Android launcher. It's a personal daily driver now and a Play Store app later. The owner's request was split into five sub-projects (spec §1):

1. launcher shell, start screen and live tiles
2. Music
3. Mail
4. Photos
5. other Windows Phone-style apps

Sub-project 1 ships in six stages (spec §11). Stage 1, "usable home screen", is in progress.

| Item | Where | State |
|---|---|---|
| Windows Phone UI reference | `docs/reference/windows-phone-ui-reference.md` | Committed on `main` |
| Sub-project 1 spec | `docs/superpowers/specs/2026-10-27-liquiwin-launcher-start-screen-design.md` | Approved 2026-10-27 |
| Stage 1 plan, 12 tasks | `docs/superpowers/plans/2026-10-27-liquiwin-stage-1-usable-home-screen.md` | Approved; `main` at 895d06d |
| Stage 1 code | Branch `worktree-stage-1-home-screen`, cut from 895d06d | Task 1 in progress |

### Task 1, step by step

| Step | State |
|---|---|
| 1. Create the wrapper | Wrapper files are generated for 9.8.2-bin, with the distribution's SHA-256. The Expected line (`gradlew --version` prints `Gradle 9.8.2`) **hasn't been seen yet**, because the first PC never finished downloading Gradle. |
| 2. Version catalog | Written; matches the plan |
| 3. Settings, root build file, `gradle.properties` | Written |
| 4. Library build files | Written for all seven libraries |
| 5. `app/build.gradle.kts` | Written |
| 6. `HomeManifestTest` | Written, never run |
| 7. Watch it fail | Not started |
| 8. Manifest, `LiquiWinApp`, `HomeActivity`, theme, icon, `.gitignore` | Not started. This step also writes `src/test/resources/robolectric.properties` (`sdk=36`) in all eight modules (ruling 3). |
| 9. Test and `assembleDebug` | Not started |
| 10. Commit | Steps 1–6 are in commit 9c2b5cd, "WIP Task 1: wrapper, version catalog, build files and the failing manifest test". Step 10 commits the rest with the plan's message. |

Tasks 2–12 haven't started. After Task 11, three things remain:

1. The final whole-branch review. It's done by a fresh reviewer on the most capable model, given the plan's Review Focus verbatim and the ledger's `Ruling:` lines.
2. One fix pass.
3. superpowers:finishing-a-development-branch.

Task 12, the check on the Galaxy, needs the owner.

## Owner decisions

The brainstorming decisions are in spec §12. These came after it, during planning and execution.

- **Execution.** The owner said: "The plan looks right. Run it natively in this session." That means superpowers:executing-plans inline:
  - every task, without check-ins
  - TDD on every step
  - one ledger line per task
  - then a whole-branch review by a fresh reviewer and a single fix pass
- **Workspace.** Work happens on an isolated branch. The owner chose a worktree.
- **Approved downloads.**
  - For Task 2: `Selawik_Release.zip` (530,652 bytes) from the microsoft/Selawik 1.01 release, and five Fluent SVGs and two licence files from raw.githubusercontent.com.
  - The build's own dependencies: Gradle 9.8.2, the Maven Central and Google libraries, SDK platform 37, and Robolectric's jars.
  - Anything else needs the owner's OK first, with its name, source and size.
- **Standing rules.**
  - Never enter credentials or sign in for the owner. The owner sets LiquiWin as Home themself, in Task 12.
  - Use no unofficial APIs.
  - Don't change system or security settings, such as the hosts file.
  - Ask before pushing to `main` or merging. Commits on the branch are fine.

## Rulings so far

These are also in the ledger below, in its format.

1. **AF_UNIX sockets (first PC only).** The first PC's JVMs couldn't open AF_UNIX sockets under `%TEMP%`, so Gradle failed with "Unable to establish loopback connection". The fix sets `JAVA_TOOL_OPTIONS` in each command's environment, never in project files.
2. **Wrapper command.** The plan's wrapper command failed on the first PC. Its URL check timed out, because GitHub's CDN was unreachable. The wrapper was generated with `--no-validate-url` and the distribution's SHA-256 instead, so `gradle-wrapper.properties` has `validateDistributionUrl=false` and a `distributionSha256Sum`. Both stay: the checksum makes every machine verify the zip, and the flag only governs a reachability check.
3. **Robolectric properties.** Task 1's file list names `src/test/resources/robolectric.properties` (`sdk=36`) in all eight modules, but no step writes them. Step 8 writes them.
4. **Extra ignore entries.** On the first PC the worktree sat inside the main checkout, at `.claude/worktrees/`. Step 8's `.gitignore` also lists `.claude/worktrees/` and `.superpowers/`.
5. **Line endings.** Git for Windows checks text files out with CRLF by default. That breaks `./gradlew` under Git Bash, which is the shell Claude Code's Bash tool uses. A `.gitattributes`, the same one `gradle init` writes, keeps `gradlew` LF, `*.bat` CRLF and `*.jar` binary. It was added while pausing, in its own commit.

## Machine setup

What the first PC had:

- Oracle JDK 21.0.3 at `C:\Program Files\Java\jdk-21`, with `JAVA_HOME` set.
- The Android SDK at `C:\Android`, with `ANDROID_HOME` set. It had platforms 34–36, build-tools up to 37.0.0, platform-tools, and the licences accepted.

What the new PC needs:

- **JDK 21**, with `JAVA_HOME` pointing at it.
- **The Android SDK**, with platform-tools (adb) and `cmdline-tools;latest`. Version 1.0 of the cmdline-tools can't see Android 17.
  - Accept the licences with `sdkmanager --licenses`, so the first build can download `platforms;android-37` by itself.
  - Set `ANDROID_HOME`, or write `sdk.dir=…` in `local.properties`. That file is git-ignored once step 8 writes `.gitignore`.
- **Any install paths will do.** The plan's Global Constraints name the first PC's paths, but nothing in the build hard-codes them.
- **Android Studio is optional**, because every step runs from the command line. If you use it, it must be a release that supports AGP 9.4. Koala can't open AGP 9 projects.
- **Claude Code** with the superpowers plugin enabled. The plugin provides executing-plans, test-driven-development, requesting-code-review and finishing-a-development-branch.
- **For Task 12:** the Galaxy S24, with USB debugging on.

### Problems the first PC hit

If any of these show up on the new PC, here's what fixed them.

- **Gradle fails with "Unable to establish loopback connection"** (`UnixDomainSockets.connect0: Invalid argument`).
  - Before running Gradle, point the JVM's socket folder at a short path outside `%TEMP%`.
  - In PowerShell: `$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$env:USERPROFILE\.gradle"`. If that path has spaces, use its 8.3 short name.
- **The Gradle download times out.**
  - The cause: services.gradle.org redirects to GitHub's CDN, and one CDN address (185.199.109.133) didn't answer. Java doesn't try the next address. PowerShell's `Invoke-WebRequest` did connect, but only at about 60 KB/s.
  - The fix: download the zip yourself, with `curl.exe -L -o gradle-9.8.2-bin.zip https://services.gradle.org/distributions/gradle-9.8.2-bin.zip` or a browser.
  - Check the zip: it should be 151,620,138 bytes, with SHA-256 `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`.
  - Put it at `%USERPROFILE%\.gradle\wrapper\dists\gradle-9.8.2-bin\8wepis63pzuhrlfaiy04bagwu\gradle-9.8.2-bin.zip`. The folder name comes from the URL, so it's the same on every PC.
  - Task 2's downloads come from the same CDN.
- **Claude Code's Bash tool refused many commands in the worktree**, such as command substitution, quoted paths, and scripts outside the worktree. Two workarounds followed:
  - The executing-plans helper scripts (`sdd-workspace`, `task-start`, `task-done`, `review-package`) were replaced by the same steps done by hand.
  - Gradle ran through the PowerShell tool with the sandbox off, because the sandbox blocks network access.

  On the new PC, try the scripts first.

## Ledger

The ledger lives in a git-ignored folder, so it doesn't travel with the clone. To recreate it:

1. Write the block below to `.superpowers/sdd/2026-10-27-liquiwin-stage-1-usable-home-screen/progress.md`.
2. Add `.superpowers/sdd/.gitignore` containing `*`. The `sdd-workspace` script does this itself.
3. Append to the ledger from there on.

```markdown
# SDD ledger — plan: docs/superpowers/plans/2026-10-27-liquiwin-stage-1-usable-home-screen.md

Spec: docs/superpowers/specs/2026-10-27-liquiwin-launcher-start-screen-design.md (approved 2026-10-27)
Executor: executing-plans (inline). Worktree .claude/worktrees/stage-1-home-screen, branch worktree-stage-1-home-screen, cut from 895d06d.
Owner approvals (2026-10-27, in chat): work in a worktree; Task 2 downloads (Selawik_Release.zip 530,652 bytes from microsoft/Selawik 1.01; five Fluent SVGs and two licence files from raw.githubusercontent.com). Build dependencies were disclosed at hand-off and the plan was approved to run.

## Pre-flight

| Producer → consumer | Produces | Consumes | Found |
|---|---|---|---|
| T3 → T4 | AppKey, Tile, TileSize, TileKind, TileTemplate, SourceId.forApp, TilePlacement | entities and RoomTileStore map the same types | match |
| T3 → T6 | GridLayout flow/compact/append/remove/move/resize/reconcile, Cell, Placed | reconcile(sizes, saved, fallbackOrder, width), append, remove, move, resize | match |
| T4 → T6 | TileStore, LayoutEdit, FakeTileStore (seed, commitCount), MainDispatcherRule; placements in reading order | same names; tests expect placements in reading order | match |
| T5 → T6 | AppCatalog, LauncherApp, AppProfile, FakeAppCatalog (public MutableStateFlows, launched, unpauseRequests), testApp, appTile | same | match |
| T5 → T8, T9 | AppCatalog, LauncherApp, AppIcon, icon() | AppListViewModel(catalog…), rankApps(query, apps, lastLaunched), SearchOpener(catalog) | match |
| T2 → T7, T8, T9, T11 | LiquiWinTheme, LocalWpUnits, LocalStyleFacets, LocalLiquiWinColors, LocalLiquiWinType, Modifier.tilt, LiquiWinIcons, WpText, PageTitle, IconImage | same | match |
| T6 → T7 | StartViewModel, StartState, StartTile (with isWork, isPaused), EditState | same | match |
| T6 → T8 (wired in T10) | pinnedApps: StateFlow<Set<AppKey>>, pin(AppKey) | pinnedApps: Flow<Set<AppKey>>, onPinToStart: (AppKey) -> Unit | match |
| T7, T8, T9 → T10 | StartScreen(vm, icon, scrollState, modifier, banner); AppListScreen(vm, icon, onSearch, resetKey, modifier); SearchScreen(vm, icon, onOpened, modifier) | same | match |
| T10 → T11 | HomeActivity, LiquiWinApp, AppGraph | safe mode never touches AppGraph | match |
| T1 → T4, T5 | :core:testing has api(:core:tiles); :core:data tests do not use :core:testing | fakes live in :core:testing; IconCacheTest builds LauncherApp directly | match |

Pre-flight: no conflicts.

## Tasks

Task 1: Ruling: this PC's JVMs can't open AF_UNIX sockets under %TEMP%, so Gradle fails with "Unable to establish loopback connection" — set JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<short path to ~/.gradle> in each command's environment, never in project files, because it is a quirk of the machine — cost if wrong: none to the project; another PC may not need it.
Task 1: Ruling: services.gradle.org redirects to GitHub's CDN, one CDN address (185.199.109.133) times out from this PC and Java doesn't fall back, so the plan's wrapper command failed its URL check — generated the wrapper with --no-validate-url and --gradle-distribution-sha256-sum 21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607, and kept validateDistributionUrl=false and distributionSha256Sum, because the checksum makes every machine verify the zip and the flag only governs a reachability check — cost if wrong: one properties line.
Task 1: Ruling: the Files list names src/test/resources/robolectric.properties (sdk=36) in all eight modules, but no step writes them — write them in step 8 with the other files — cost if wrong: step 9's Robolectric run uses its default SDK.
Task 1: Ruling: the worktree sits inside the main checkout (.claude/worktrees/), and step 8's .gitignore list covers neither it nor the executor's .superpowers/ workspace — add .claude/worktrees/ and .superpowers/ to .gitignore in step 8 — cost if wrong: two extra ignore lines.
Task 1: Ruling: Git for Windows checks text files out with CRLF by default, which breaks ./gradlew under Git Bash (the shell of Claude Code's Bash tool), and the plan has no .gitattributes — added the one gradle init writes (gradlew LF, *.bat CRLF, *.jar binary) — cost if wrong: one small file.
Task 1: Paused 2026-10-27 after step 6: the owner is moving to another PC. Steps 1–6 are committed as WIP (9c2b5cd). Step 1's `gradlew --version` is unverified: the Gradle download (PowerShell, about 60 KB/s) was stopped at 61.5 of 151.6 MB and the partial file deleted. Handoff: docs/superpowers/handoff/2026-10-27-liquiwin-stage-1-handoff.md.
```

When stage 1 is finished, the finishing step either deletes this handoff file or keeps it as history.
