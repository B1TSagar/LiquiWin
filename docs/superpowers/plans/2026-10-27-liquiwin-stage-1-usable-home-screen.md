# LiquiWin Stage 1: Usable Home Screen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An installable LiquiWin Home app on the owner's Galaxy: a WP8.1-style start screen of app tiles that can be pinned, unpinned, moved and resized, an A–Z app list with app search, the Home role, and a crash safe mode.

**Architecture:** A multi-module Gradle project laid out as in spec §3.1. Pure Kotlin logic (grid layout, search ranking, crash counting, tilt maths) is unit-tested on the JVM. Room, the `LauncherApps` wrapper and the Compose screens are tested under Robolectric. `:app` wires the modules together and routes between screens, and no feature module depends on another.

**Tech Stack:** Kotlin 2.4.21, Android Gradle Plugin 9.4.1 (built-in Kotlin), Gradle 9.8.2, Jetpack Compose (BOM 2026.09.00, foundation and ui only, no Material), Room 2.8.5 with KSP 2.3.12, kotlinx.coroutines 1.11.0, JUnit 4, Robolectric 4.17, Turbine 1.2.1.

**Spec:** [docs/superpowers/specs/2026-10-27-liquiwin-launcher-start-screen-design.md](../specs/2026-10-27-liquiwin-launcher-start-screen-design.md), delivery stage 1 (§11). Windows Phone values come from [docs/reference/windows-phone-ui-reference.md](../../reference/windows-phone-ui-reference.md).

Stages 2–6 each get their own plan, written after the previous stage lands so it can build on the real code.

## Global Constraints

- Android only: `minSdk 29` (Android 10), `targetSdk 36` (Android 16), `compileSdk 37`. The test phone is a Galaxy S24 (SM-S921E, adb serial `RZGYB0137YB`) on Android 17 and One UI 9.
- Toolchain: Gradle 9.8.2, AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, JDK 21 at `C:\Program Files\Java\jdk-21`, Android SDK at `C:\Android`. AGP 9 compiles Kotlin itself, so no module applies `org.jetbrains.kotlin.android`.
- Every version lives in `gradle/libs.versions.toml`. Build files use the Kotlin DSL.
- Package root and applicationId: `io.github.b1tsagar.liquiwin`. Module namespaces append the module path, for example `io.github.b1tsagar.liquiwin.core.design`.
- Apps are listed through `LauncherApps` plus a `<queries>` MAIN/LAUNCHER intent filter. No `QUERY_ALL_PACKAGES`.
- Never requested: `INTERNET`, `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, any accessibility service, storage, location, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- No Microsoft names, logos or trademarks in the product name or icon, and no Segoe fonts. Selawik ships unmodified with its licence. Fluent icon paths keep their MIT notice.
- No feature module depends on another feature module. Cross-screen actions are callbacks that `:app` wires.
- Single process. The main thread only renders: database, `LauncherApps` and image work runs on `Dispatchers.IO` or `Dispatchers.Default`. StrictMode logs violations in debug builds.
- "WP px" are Windows Phone pixels on a 480-px-wide screen: dp = WP px × (screen width in dp ÷ 480). Grid unit = (screen width − 2 × side margin − (units − 1) × gap) ÷ units.
- Page headers and menu items are lowercase, as on Windows Phone.
- Tests use JUnit 4. Robolectric runs at SDK 36, set by `sdk=36` in each module's `src/test/resources/robolectric.properties`.

## Review Focus

1. **Apps changing under their tiles.** An app is uninstalled while LiquiWin isn't running, an app switches its launcher activity (apps that offer alternative icons do this through activity aliases), work apps are paused, or the work profile is removed. The tile follows its app to the new activity, stays greyed while work apps are paused, and goes only when the app or profile is really gone. A stale cached app list, or an empty one, never removes tiles. Tests in Task 6.
2. **A crash or process death in the middle of an edit.** A tile was inserted but never placed, or placement rows remain for a deleted tile. The next start shows every tile exactly once, with nothing overlapping. Tests in Tasks 3 and 6.
3. **App names beyond plain A–Z.** Accents, digits, symbols, other scripts, and two apps with the same name (personal and work). They land in sensible groups, "cafe" finds "Café", and both same-named apps show in a stable order. Tests in Tasks 8 and 9.
4. **Tiles at the grid's edges.** Wide or large tiles dragged or resized near the right edge, drops below the last tile, a resize that pushes other tiles down. Tiles stay inside the grid, never overlap, and holes close. Tests in Task 3.
5. **Home pressed from anywhere.** From the app list, search, edit mode, a context menu or another app, Home brings back the start screen with everything closed. Pressing Home while already there scrolls to the top. Tests in Task 10.

---

## File structure

| Path | Module | Holds |
|---|---|---|
| `app/` | `:app` | `HomeActivity`, `LiquiWinApp`, `AppGraph`, `HomeRoot` (pager, search overlay, Home presses, Home-app banner), `HomeRole`, crash guard, safe mode, backup rules |
| `core/design/` | `:core:design` | Theme, `WpUnits`, Selawik type ramp, `StyleFacets` (one preset), tilt, icons, `WpText`, `PageTitle`, `IconImage` |
| `core/data/` | `:core:data` | `AppKey`, `LauncherApp`, `AppCatalog`, `AppSource` over `LauncherApps`, `AppRepository`, `IconCache`, `layout.db`, `cache.db` |
| `core/tiles/` | `:core:tiles` | Tile model, `TileStore`, `RoomTileStore` |
| `core/testing/` | `:core:testing` | Test-only helpers: `FakeTileStore`, `FakeAppCatalog`, `testApp()`, `appTile()`, `MainDispatcherRule` |
| `feature/start/` | `:feature:start` | `GridLayout`, `GridMetrics`, `planAppTileRepairs`, `StartViewModel`, `StartScreen` |
| `feature/applist/` | `:feature:applist` | `AppGrouper`, `AppListViewModel`, `AppListScreen` |
| `feature/search/` | `:feature:search` | `SearchProvider`, `rankApps`, `AppSearchProvider`, `SearchOpener`, `SearchViewModel`, `SearchScreen` |

Kotlin sources go under `src/main/kotlin/io/github/b1tsagar/liquiwin/<module path>/`, and tests under `src/test/kotlin/` in the same package. Below, `…/` stands for that package directory.

Module dependencies follow spec §3.1. `:core:tiles` exposes `:core:design` and `:core:data` as `api`. `:feature:start` uses `:core:tiles`, while `:feature:applist` and `:feature:search` use `:core:design` and `:core:data`. `:app` uses everything, and `:core:testing` appears only in `testImplementation`.

**Not in this stage:**
- Stage 2: notifications, live tiles, combined tiles, the tile editor, the hub and quick reply.
- Stage 3: the other presets and facets, backgrounds, the turnstile and the rest of the motion set, the WP7 and W10M app lists, and status-bar hiding.
- Stage 4: Glass.
- Stage 5: folders, widgets, pinned shortcuts, the other search providers, the search tile and the Action Center.
- Stage 6: first-time setup, settings pages, hiding apps, lock layout, export and the Baseline Profile.

---

### Task 1: Gradle project, modules and the Home activity

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`, and the Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`)
- Create: `build.gradle.kts` and `src/test/resources/robolectric.properties` (`sdk=36`) in each of `app/`, `core/design/`, `core/data/`, `core/tiles/`, `core/testing/`, `feature/start/`, `feature/applist/` and `feature/search/`
- Create: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, `app/src/main/res/drawable/ic_launcher_background.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`, `…/LiquiWinApp.kt`, `…/HomeActivity.kt`
- Test: `app/src/test/kotlin/…/HomeManifestTest.kt`

**Interfaces:**
- Produces: the eight modules; `HomeActivity`, which answers the Home intent; `LiquiWinApp : Application`.

- [ ] **Step 1: Create the wrapper.** Write `settings.gradle.kts` containing only `rootProject.name = "LiquiWin"`. Then use the Gradle 8.13 already in the local cache to write a 9.8.2 wrapper:

```bash
"$(ls -d ~/.gradle/wrapper/dists/gradle-8.13-all/*/gradle-8.13 | head -1)/bin/gradle" wrapper --gradle-version 9.8.2 --distribution-type bin
./gradlew --version
```

Expected: `Gradle 9.8.2`.

- [ ] **Step 2: Write `gradle/libs.versions.toml`.**

```toml
[versions]
compileSdk = "37"
targetSdk = "36"
minSdk = "29"
agp = "9.4.1"
kotlin = "2.4.21"
ksp = "2.3.12"
composeBom = "2026.09.00"
coreKtx = "1.19.1"
activity = "1.13.0"
lifecycle = "2.11.0"
room = "2.8.5"
coroutines = "1.11.0"
junit = "4.13.2"
androidxTestJunit = "1.3.0"
robolectric = "4.17"
turbine = "1.2.1"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-test-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestJunit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 3: Write `settings.gradle.kts`, the root `build.gradle.kts` and `gradle.properties`.**
  - **Settings:** `pluginManagement` repositories `google()`, `mavenCentral()`, `gradlePluginPortal()`. `dependencyResolutionManagement` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS` with `google()` and `mavenCentral()`. Include `:app`, `:core:design`, `:core:data`, `:core:tiles`, `:core:testing`, `:feature:start`, `:feature:applist` and `:feature:search`.
  - **Root build file:** every plugin alias with `apply false`. `kotlin-android` is there only to pin the Kotlin Gradle plugin version, which AGP's built-in Kotlin and the Compose compiler then use.
  - **`gradle.properties`:**

    ```properties
    org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
    org.gradle.parallel=true
    org.gradle.caching=true
    org.gradle.configuration-cache=true
    ```

- [ ] **Step 4: Write the library build files.** `core/design/build.gradle.kts` is the template:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.b1tsagar.liquiwin.core.design"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

The other libraries differ as follows. Modules without Compose drop the `kotlin.compose` plugin, `buildFeatures` and the Compose dependencies.

| Module | Plugins besides `android.library` | Main dependencies | Tests also use |
|---|---|---|---|
| `core/design` | `kotlin.compose` | Compose BOM, `compose.foundation`, `compose.ui` | — |
| `core/data` | `ksp`, `room` | `api(room.runtime)`, `room.ktx`, `ksp(room.compiler)`, `api(kotlinx.coroutines.android)`, `androidx.core.ktx`. Add `room { schemaDirectory("$projectDir/schemas") }` | — |
| `core/tiles` | — | `api(project(":core:design"))`, `api(project(":core:data"))` | — |
| `core/testing` | — | `api(project(":core:tiles"))`, `api(kotlinx.coroutines.test)`, `api(junit)` | — |
| `feature/start` | `kotlin.compose` | `project(":core:tiles")`, Compose, `androidx.activity.compose`, `androidx.lifecycle.viewmodel.compose`, `androidx.lifecycle.runtime.compose` | `project(":core:testing")` |
| `feature/applist`, `feature/search` | `kotlin.compose` | `project(":core:design")`, `project(":core:data")`, then the same as `feature/start` | `project(":core:testing")` |

- [ ] **Step 5: Write `app/build.gradle.kts`.**
  - **Plugins:** `android.application` and `kotlin.compose`.
  - **`defaultConfig`:** `applicationId = "io.github.b1tsagar.liquiwin"`, `targetSdk` from the catalog, `versionCode = 1`, `versionName = "0.1.0"`.
  - **`buildFeatures`:** `compose = true` and `buildConfig = true`.
  - **Release build type:** `isMinifyEnabled = true`, `isShrinkResources = true`, `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))`. It's signed with the debug key (`signingConfig = signingConfigs.getByName("debug")`), which is fine for personal builds. A store release needs its own key.
  - **Dependencies:** every module, `androidx.activity.compose`, `androidx.core.ktx`, both lifecycle libraries and Compose. Tests use the template's test libraries and `testOptions`, plus `project(":core:testing")`.

- [ ] **Step 6: Write the failing test** `app/src/test/kotlin/…/HomeManifestTest.kt`:

```kotlin
@RunWith(AndroidJUnit4::class)
class HomeManifestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pm = context.packageManager

    @Test fun homeActivityAnswersTheHomeIntent() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName)
        assertEquals(HomeActivity::class.java.name, pm.queryIntentActivities(home, 0).single().activityInfo.name)
    }

    @Test fun forbiddenPermissionsAreNeverRequested() {
        val requested = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        val forbidden = setOf(
            Manifest.permission.INTERNET, Manifest.permission.QUERY_ALL_PACKAGES,
            Manifest.permission.SYSTEM_ALERT_WINDOW, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        assertEquals(emptySet<String>(), requested intersect forbidden)
    }

    @Test fun noAccessibilityService() {
        val services = pm.getPackageInfo(context.packageName, PackageManager.GET_SERVICES).services.orEmpty()
        assertTrue(services.none { it.permission == Manifest.permission.BIND_ACCESSIBILITY_SERVICE })
    }
}
```

- [ ] **Step 7: Run it and watch it fail.**

Run: `./gradlew :app:testDebugUnitTest --tests "*HomeManifestTest"`
Expected: FAIL, because `HomeActivity` doesn't exist yet.

- [ ] **Step 8: Write the manifest, `LiquiWinApp`, a placeholder `HomeActivity`, the theme and the icon.**
  - **Manifest:**
    - `<uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />`, for uninstalling from the app list in Task 8
    - `<queries>` holding a MAIN/LAUNCHER `<intent>`
    - `<application>` with `android:name=".LiquiWinApp"`, `@style/Theme.LiquiWin`, `@mipmap/ic_launcher`, `@string/app_name` ("LiquiWin") and `android:supportsRtl="true"`
    - `HomeActivity`: `exported="true"`, `launchMode="singleTask"`, `clearTaskOnLaunch="true"`, `stateNotNeeded="true"`, `excludeFromRecents="true"`, `resumeWhilePausing="true"`, `screenOrientation="portrait"`, `windowSoftInputMode="adjustResize"`
    - Two intent filters on it: MAIN + HOME + DEFAULT, and MAIN + LAUNCHER
  - **`Theme.LiquiWin`:** parent `android:Theme.Material.NoActionBar` with a black `android:windowBackground`.
  - **Icon:** an original adaptive icon. The background is `#1BA1E2`. The white foreground is one medium and two small squares arranged in an L inside the 72 dp safe zone. Never use four squares in a 2×2 grid, which echoes the Windows logo. The same foreground doubles as the `<monochrome>` layer.
  - **`HomeActivity`:** a `ComponentActivity` that calls `enableEdgeToEdge()` and shows a placeholder `BasicText("liquiwin")`. Task 10 replaces the placeholder.
  - **`LiquiWinApp.onCreate`:** when `BuildConfig.DEBUG`, set a StrictMode thread policy and VM policy, both `detectAll().penaltyLog()`.
  - **`.gitignore`:** `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `.kotlin/`, `captures/`, `.cxx/`.

- [ ] **Step 9: Run the test and build.**

Run: `./gradlew :app:testDebugUnitTest --tests "*HomeManifestTest" assembleDebug`
Expected: BUILD SUCCESSFUL.

The first build downloads `platforms;android-37` by itself, because the licences in `C:\Android\licenses` are already accepted. If it can't, install it by hand. The SDK manager in `C:\Android\cmdline-tools\1.0` is too old to see Android 17, so install `cmdline-tools;latest` with it first, then run `sdkmanager "platforms;android-37"` from the new copy.

- [ ] **Step 10: Commit.**

```bash
git add -A
git commit -m "Set up the Gradle project, modules and Home activity"
```

---

### Task 2: Design basics: theme, units, Selawik, tilt and icons

**Files:**
- Create: `core/design/src/main/res/font/` (the five Selawik `.ttf` files), `core/design/src/main/assets/licenses/selawik-OFL.txt`, `core/design/src/main/assets/licenses/fluent-system-icons-MIT.txt`
- Create: `…/theme/WpUnits.kt`, `…/theme/LiquiWinColors.kt`, `…/theme/LiquiWinType.kt`, `…/theme/LiquiWinTheme.kt`, `…/style/StyleFacets.kt`, `…/motion/Tilt.kt`, `…/icons/LiquiWinIcons.kt`, `…/components/WpText.kt`, `…/components/PageTitle.kt`, `…/components/IconImage.kt`
- Test: `core/design/src/test/kotlin/…/theme/WpUnitsTest.kt`, `core/design/src/test/kotlin/…/motion/TiltTest.kt`

**Interfaces:**
- Produces:
  - `class WpUnits(val screenWidthDp: Float) { fun dp(wpPx: Float): Dp; fun sp(wpPx: Float): TextUnit }` and `LocalWpUnits`
  - `data class LiquiWinColors(val background: Color, val foreground: Color, val subtle: Color, val chrome: Color, val accent: Color)` and `LocalLiquiWinColors`
  - `data class LiquiWinTypography(val pageTitle: TextStyle, val header: TextStyle, val listItem: TextStyle, val body: TextStyle, val small: TextStyle, val tileLabel: TextStyle)` and `LocalLiquiWinType`
  - `data class StyleFacets(val gridColumns: Int, val accent: Color, val sideMarginWpPx: Float, val gapWpPx: Float)` with `val gridUnits: Int get() = gridColumns * 2`, the constant `StyleFacets.Wp81Basic`, and `LocalStyleFacets`
  - `@Composable fun LiquiWinTheme(style: StyleFacets = StyleFacets.Wp81Basic, content: @Composable () -> Unit)`
  - `data class Tilt(val rotationXDeg: Float, val rotationYDeg: Float, val depression: Float)`, `fun tiltFor(touch: Offset, size: Size): Tilt` and `fun Modifier.tilt(enabled: Boolean = true): Modifier`
  - `object LiquiWinIcons { val Search: ImageVector; val PinOff: ImageVector; val Resize: ImageVector; val Check: ImageVector; val Briefcase: ImageVector }`
  - `@Composable fun WpText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = LocalLiquiWinColors.current.foreground, maxLines: Int = Int.MAX_VALUE)`
  - `@Composable fun PageTitle(text: String, modifier: Modifier = Modifier)`
  - `@Composable fun IconImage(bitmap: Bitmap?, size: Dp, modifier: Modifier = Modifier)`

- [ ] **Step 1: Write the failing tests.**

```kotlin
class WpUnitsTest {
    @Test fun wpPixelsScaleWithScreenWidth() {
        val units = WpUnits(screenWidthDp = 411.43f) // the Galaxy S24 at its 420 dpi setting
        assertEquals(411.43f, units.dp(480f).value, 0.01f)
        assertEquals(20.57f, units.dp(24f).value, 0.01f)
        assertEquals(61.71f, units.sp(72f).value, 0.01f)
    }
}

class TiltTest {
    private val size = Size(200f, 100f)

    @Test fun centreTouchDepressesWithoutRotating() {
        val tilt = tiltFor(Offset(100f, 50f), size)
        assertEquals(0f, tilt.rotationXDeg, 0.01f)
        assertEquals(0f, tilt.rotationYDeg, 0.01f)
        assertEquals(1f, tilt.depression, 0.001f)
    }

    @Test fun edgeTouchRotatesByTheMaximumAngle() {
        val right = tiltFor(Offset(200f, 50f), size)
        val left = tiltFor(Offset(0f, 50f), size)
        assertEquals(17.19f, abs(right.rotationYDeg), 0.01f) // 0.3 rad
        assertEquals(-right.rotationYDeg, left.rotationYDeg, 0.01f)
        assertEquals(0f, right.rotationXDeg, 0.01f)
        assertEquals(0f, right.depression, 0.001f)
    }

    @Test fun touchesOutsideTheTileAreClamped() {
        val tilt = tiltFor(Offset(500f, -40f), size)
        assertEquals(17.19f, abs(tilt.rotationYDeg), 0.01f)
        assertEquals(17.19f, abs(tilt.rotationXDeg), 0.01f)
        assertEquals(0f, tilt.depression, 0.001f)
    }
}
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :core:design:testDebugUnitTest`
Expected: FAIL, because `WpUnits` and `tiltFor` don't exist.

- [ ] **Step 3: Implement `WpUnits` and `tiltFor`.**
  - **`WpUnits`:** `dp(px)` is `px × screenWidthDp ÷ 480` in dp. `sp` returns the same number in sp, so the phone's font size setting still applies on top.
  - **`tiltFor`:** normalise the touch to `nx`, `ny` in −1…1, measured from the centre and clamped. The rotations are `nx` and `ny` times 17.1887° (0.3 rad), and depression is `1 − min(1, hypot(nx, ny))`. Pick the signs so that the edge under the finger moves away from the viewer. Task 12 checks this by eye.

- [ ] **Step 4: Run the tests again.**

Run: `./gradlew :core:design:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Add the font and icon files.** This is the only step that downloads anything besides the build's own dependencies (Gradle, Maven artifacts, SDK packages and Robolectric's Android jars). If the owner hasn't already approved this plan's downloads, ask before running it.
  - **Selawik:**
    - Download `Selawik_Release.zip` (530,652 bytes) from `https://github.com/microsoft/Selawik/releases/download/1.01/Selawik_Release.zip` into a new, empty folder outside the repo, and unzip it there.
    - Copy its five `.ttf` files byte for byte into `core/design/src/main/res/font/`.
    - Keep each file name if it's already only lowercase letters, digits and underscores, which is Android's rule for resource names. Otherwise rename the file only. Renaming a file doesn't modify the font.
    - Save `https://raw.githubusercontent.com/microsoft/Selawik/master/LICENSE.txt` as `assets/licenses/selawik-OFL.txt`.
  - **Fluent icons:** fetch five SVGs from `https://raw.githubusercontent.com/microsoft/fluentui-system-icons/main/assets/<folder>/SVG/<file>`, writing spaces in folder names as `%20`:

    | Icon | Folder | File |
    |---|---|---|
    | `Search` | `Search` | `ic_fluent_search_24_regular.svg` |
    | `PinOff` | `Pin Off` | `ic_fluent_pin_off_24_regular.svg` |
    | `Resize` | `Arrow Maximize` | `ic_fluent_arrow_maximize_24_regular.svg` |
    | `Check` | `Checkmark` | `ic_fluent_checkmark_24_regular.svg` |
    | `Briefcase` | `Briefcase` | `ic_fluent_briefcase_24_regular.svg` |

    Each file holds one `<path d="…">` on a 24×24 viewBox. Save the repository's `LICENSE` as `assets/licenses/fluent-system-icons-MIT.txt`.

- [ ] **Step 6: Implement the theme, type ramp, icons and text components.**
  - **Colours** (dark only in this stage): background `#000000`, foreground `#FFFFFF`, subtle `#99FFFFFF`, chrome `#1F1F1F`, and the accent from `StyleFacets`.
  - **`StyleFacets.Wp81Basic`:** `StyleFacets(gridColumns = 3, accent = Color(0xFF1BA1E2), sideMarginWpPx = 24f, gapWpPx = 12f)`. The accent is WP8 cyan. Margin and gap come from the WP8 start screen at 480 px: 4 × 99 + 3 × 12 = 432 px of tiles, which leaves 24 px each side (reference §1).
  - **Selawik `FontFamily`:** Light 300, Semilight 350, Regular 400, Semibold 600, Bold 700.
  - **Type ramp in WP px** (reference §4): `pageTitle` 72 Semilight, `header` 32 Regular, `listItem` 25.333 Regular, `body` 20 Regular, `small` 18.667 Regular, `tileLabel` 18.667 Regular. The reference doesn't give the list and tile label sizes, so these are starting values. Task 12 records what's chosen.
  - **`LiquiWinTheme`:** provides `LocalWpUnits` (from `LocalConfiguration.current.screenWidthDp`), the colours, the type ramp and the style.
  - **`LiquiWinIcons`:** each one is `ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(addPathNodes(d), fill = SolidColor(Color.White)).build()`, with `d` copied from its SVG.
  - **`Modifier.tilt()`:**
    - It watches pointer events without consuming them, so taps and long-presses still reach the modifiers after it.
    - It updates the tilt while the finger moves.
    - After release it waits 200 ms, then returns to rest over 100 ms, linearly (reference §5).
    - It draws through `graphicsLayer`: `rotationX`, `rotationY`, `scaleX = scaleY = 1 − 0.025 × depression`, and `cameraDistance = 12 × density`.
  - **`PageTitle`:** lowercases its text with `Locale.getDefault()` and uses `pageTitle`. Its padding is start 21, top 10 and bottom 28 WP px, which is the TitlePanel margin 12,17,0,28 plus the page-title margin 9,−7,0,0 (reference §4).
  - **`WpText`:** `BasicText` with ellipsis.
  - **`IconImage`:** draws `bitmap.asImageBitmap()` at `size`, and nothing when the bitmap is null.

- [ ] **Step 7: Build and test.**

Run: `./gradlew :core:design:testDebugUnitTest :core:design:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit.**

```bash
git add core/design
git commit -m "Add the design basics: theme, Selawik type ramp, tilt and icons"
```

---

### Task 3: Tile model and the grid layout engine

**Files:**
- Create: `core/data/src/main/kotlin/…/core/data/apps/AppKey.kt`
- Create: `core/tiles/src/main/kotlin/…/core/tiles/model/TileSize.kt`, `TileKind.kt`, `TileTemplate.kt`, `SourceId.kt`, `Tile.kt`, `TilePlacement.kt`
- Create: `feature/start/src/main/kotlin/…/feature/start/layout/GridLayout.kt`
- Test: `core/data/src/test/kotlin/…/apps/AppKeyTest.kt`, `core/tiles/src/test/kotlin/…/model/TileSizeTest.kt`, `feature/start/src/test/kotlin/…/layout/GridLayoutTest.kt`

**Interfaces:**
- Produces:
  - `data class AppKey(val packageName: String, val className: String, val userSerial: Long)` with `fun flatten(): String`, giving `"<package>/<class>@<userSerial>"`, and `companion object { fun parse(value: String): AppKey? }`
  - `enum class TileSize(val widthUnits: Int, val heightUnits: Int) { SMALL(1, 1), MEDIUM(2, 2), WIDE(4, 2), LARGE(4, 4) }` with `fun next(): TileSize`, cycling small → medium → wide → large → small
  - `enum class TileKind { APP, COMBINED, FOLDER, WIDGET, SHORTCUT, SEARCH, ACTION_CENTER }`
  - `enum class TileTemplate { FLIP, PEEK, CYCLE, ICONIC, STATIC }`
  - `@JvmInline value class SourceId(val value: String)` with `companion object { fun forApp(packageName: String, userSerial: Long): SourceId }`, giving `"app:<package>@<userSerial>"`
  - `data class Tile(val id: Long, val kind: TileKind, val title: String?, val size: TileSize, val template: TileTemplate, val colorOverride: Int?, val appKey: AppKey?, val sources: List<SourceId>)`
  - `data class TilePlacement(val tileId: Long, val gridWidth: Int, val x: Int, val y: Int)`
  - `data class Cell(val x: Int, val y: Int)` and `data class Placed(val id: Long, val size: TileSize, val cell: Cell)`
  - `object GridLayout` with:
    - `fun flow(tiles: List<Pair<Long, TileSize>>, width: Int): List<Placed>`
    - `fun compact(tiles: List<Placed>, width: Int): List<Placed>`
    - `fun append(tiles: List<Placed>, id: Long, size: TileSize, width: Int): List<Placed>`
    - `fun remove(tiles: List<Placed>, id: Long, width: Int): List<Placed>`
    - `fun move(tiles: List<Placed>, id: Long, target: Cell, width: Int): List<Placed>`
    - `fun resize(tiles: List<Placed>, id: Long, newSize: TileSize, width: Int): List<Placed>`
    - `fun reconcile(sizes: Map<Long, TileSize>, saved: List<TilePlacement>?, fallbackOrder: List<Long>, width: Int): List<Placed>`

Every `GridLayout` function takes a `width` of 4, 6 or 8 units. It returns a valid arrangement: in bounds, no overlaps, compacted, and sorted in reading order (by `y`, then `x`).

- [ ] **Step 1: Write the failing tests.** `AppKeyTest` checks that `parse(flatten())` round-trips, and that `parse("nonsense")` and `parse("a/b@x")` return null. `TileSizeTest` checks that `next()` cycles SMALL → MEDIUM → WIDE → LARGE → SMALL. `GridLayoutTest`:

```kotlin
class GridLayoutTest {
    private fun p(id: Long, size: TileSize, x: Int, y: Int) = Placed(id, size, Cell(x, y))
    private fun cells(tiles: List<Placed>) = tiles.associate { it.id to it.cell }

    @Test fun flowFillsRowsFromTheTopLeft() {
        val out = GridLayout.flow(listOf(1L to MEDIUM, 2L to WIDE, 3L to SMALL, 4L to SMALL, 5L to MEDIUM), width = 6)
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(2, 0), 3L to Cell(0, 2), 4L to Cell(1, 2), 5L to Cell(2, 2)), cells(out))
    }

    @Test fun flowFillsEarlierHolesWithLaterSmallTiles() {
        val out = GridLayout.flow(listOf(1L to MEDIUM, 2L to WIDE, 3L to SMALL), width = 4)
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(0, 2), 3L to Cell(2, 0)), cells(out))
    }

    @Test fun compactionFloatsTilesUpWithinTheirColumns() {
        val out = GridLayout.compact(listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 2, 4), p(3, SMALL, 0, 5)), width = 4)
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(2, 0), 3L to Cell(0, 2)), cells(out))
    }

    @Test fun appendAddsAfterTheLastRow() {
        val start = listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 2, 0), p(3, MEDIUM, 4, 0), p(4, MEDIUM, 0, 2))
        assertEquals(Cell(2, 2), cells(GridLayout.append(start, 5, MEDIUM, width = 6))[5])
        assertEquals(Cell(0, 0), cells(GridLayout.append(emptyList(), 1, WIDE, width = 6))[1])
    }

    @Test fun removeClosesTheGap() {
        val out = GridLayout.remove(listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 0, 2), p(3, MEDIUM, 2, 2)), id = 1, width = 4)
        assertEquals(mapOf(2L to Cell(0, 0), 3L to Cell(2, 0)), cells(out))
    }

    @Test fun moveOntoATileReflowsFromTheFirstCollision() {
        val out = GridLayout.move(listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 2, 0), p(3, MEDIUM, 0, 2)), id = 3, target = Cell(2, 0), width = 4)
        assertEquals(mapOf(1L to Cell(0, 0), 3L to Cell(2, 0), 2L to Cell(0, 2)), cells(out))
    }

    @Test fun moveKeepsWideTilesInsideTheGridAndBelowNothing() {
        assertEquals(Cell(0, 0), cells(GridLayout.move(listOf(p(1, WIDE, 0, 0)), id = 1, target = Cell(3, 5), width = 4))[1])
    }

    @Test fun resizePushesCollidingTilesDown() {
        val out = GridLayout.resize(listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 2, 0), p(3, SMALL, 0, 2)), id = 1, newSize = WIDE, width = 4)
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(0, 2), 3L to Cell(2, 2)), cells(out))
        assertEquals(WIDE, out.single { it.id == 1L }.size)
    }

    @Test fun resizeLeavesTilesThatStillFitInPlace() {
        val out = GridLayout.resize(listOf(p(1, MEDIUM, 0, 0), p(2, MEDIUM, 4, 0)), id = 1, newSize = WIDE, width = 6)
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(4, 0)), cells(out))
    }

    @Test fun resizeAtTheRightEdgeShiftsTheTileLeft() {
        assertEquals(Cell(2, 0), cells(GridLayout.resize(listOf(p(1, SMALL, 3, 0)), id = 1, newSize = MEDIUM, width = 4))[1])
    }

    @Test fun reconcileFlowsAWidthWithNoSavedArrangement() {
        val out = GridLayout.reconcile(mapOf(1L to MEDIUM, 2L to MEDIUM, 3L to SMALL), saved = null, fallbackOrder = listOf(2, 1, 3), width = 4)
        assertEquals(mapOf(2L to Cell(0, 0), 1L to Cell(2, 0), 3L to Cell(0, 2)), cells(out))
    }

    @Test fun reconcileRepairsABrokenSavedArrangement() { // Review Focus 2
        val saved = listOf(TilePlacement(1, 4, 0, 0), TilePlacement(9, 4, 2, 0), TilePlacement(2, 4, 0, 0))
        val out = GridLayout.reconcile(mapOf(1L to MEDIUM, 2L to MEDIUM, 3L to SMALL), saved, fallbackOrder = emptyList(), width = 4)
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(2, 0), 3L to Cell(0, 2)), cells(out))
    }

    @Test fun randomEditsAlwaysLeaveAValidArrangement() { // Review Focus 4
        val random = Random(7)
        for (width in listOf(4, 6, 8)) {
            var tiles = emptyList<Placed>()
            var nextId = 1L
            repeat(300) {
                tiles = when (if (tiles.isEmpty()) 0 else random.nextInt(4)) {
                    0 -> GridLayout.append(tiles, nextId++, TileSize.entries.random(random), width)
                    1 -> GridLayout.remove(tiles, tiles.random(random).id, width)
                    2 -> GridLayout.move(tiles, tiles.random(random).id, Cell(random.nextInt(width + 2), random.nextInt(20)), width)
                    else -> GridLayout.resize(tiles, tiles.random(random).id, TileSize.entries.random(random), width)
                }
                assertValid(tiles, width)
            }
        }
    }

    // Ids unique, reading order, in bounds, no two tiles share a cell, and no tile could move up a row.
    private fun assertValid(tiles: List<Placed>, width: Int) {
        assertEquals(tiles.size, tiles.map { it.id }.toSet().size)
        assertEquals(tiles.sortedWith(compareBy({ it.cell.y }, { it.cell.x })), tiles)
        val taken = mutableSetOf<Cell>()
        for (t in tiles) {
            assertTrue(t.cell.x >= 0 && t.cell.y >= 0 && t.cell.x + t.size.widthUnits <= width)
            for (dx in 0 until t.size.widthUnits) for (dy in 0 until t.size.heightUnits)
                assertTrue(taken.add(Cell(t.cell.x + dx, t.cell.y + dy)))
        }
        for (t in tiles.filter { it.cell.y > 0 })
            assertTrue((0 until t.size.widthUnits).any { Cell(t.cell.x + it, t.cell.y - 1) in taken })
    }
}
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :core:data:testDebugUnitTest :core:tiles:testDebugUnitTest :feature:start:testDebugUnitTest`
Expected: FAIL, because the types don't exist yet.

- [ ] **Step 3: Implement the model types and `GridLayout`.** The tests pin the outcomes. These notes pin the method:
  - **First fit:** scan rows from the top, or from a given row, and within each row scan `x` from 0. Take the first position where the tile is in bounds and overlaps nothing.
  - **`flow`:** first fit for each tile, in the given order, from the top.
  - **`compact`:** repeat until nothing moves: in reading order, move each tile up one row while the row above is free in all its columns.
  - **`append`:** first fit starting at the largest `y` any tile has, then compact.
  - **`remove`:** drop the tile, then compact.
  - **`move`:**
    - Clamp the target to `0 ≤ x ≤ width − w` and `y ≥ 0`, and fix the moved tile there.
    - Walk the other tiles in reading order. Tiles before the first one that overlaps the moved tile stay where they are.
    - That tile and every tile after it are re-flowed in reading order. Each takes the first fit starting at the first colliding tile's row, around the tiles already placed, so no tile jumps up into a hole above the collision.
    - Then compact.
  - **`resize`:** keep the tile's cell, clamping `x` to `width − w`, and treat it as the fixed tile. Re-flow from the first collision as in `move`, then compact. Applied to another width, the same call gives spec §4.2's "leaves the other widths' positions where they still fit".
  - **`reconcile`:**
    - With no saved arrangement, flow the tiles in `fallbackOrder`, then any remaining tiles by ascending id.
    - With one, keep its placements in reading order, but drop any that are out of bounds, overlap an earlier one, or belong to a tile that no longer exists.
    - Append tiles that have no placement, by ascending id, then compact.

- [ ] **Step 4: Run the tests again.**

Run: `./gradlew :core:data:testDebugUnitTest :core:tiles:testDebugUnitTest :feature:start:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add core feature
git commit -m "Add the tile model and grid layout engine"
```

---

### Task 4: Layout database and tile store

**Files:**
- Create: `core/data/src/main/kotlin/…/core/data/db/layout/LayoutDatabase.kt`, `TileEntity.kt`, `TileSourceEntity.kt`, `TilePlacementEntity.kt`, `TileDao.kt`
- Create: `core/tiles/src/main/kotlin/…/core/tiles/TileStore.kt`, `RoomTileStore.kt`
- Create: `core/testing/src/main/kotlin/…/core/testing/FakeTileStore.kt`, `MainDispatcherRule.kt`
- Create: `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/AndroidManifest.xml` (backup attributes)
- Test: `core/tiles/src/test/kotlin/…/RoomTileStoreTest.kt`
- Generated: `core/data/schemas/` (Room's schema export; commit it)

**Interfaces:**
- Consumes: `AppKey`, `Tile`, `TileSize`, `TileKind`, `TileTemplate`, `SourceId` and `TilePlacement` from Task 3.
- Produces:
  - `LayoutDatabase` (Room, file `layout.db`, version 1) with `fun tileDao(): TileDao`, and `companion object { fun open(context: Context): LayoutDatabase }`
  - Tables `tile`, `tile_source` and `tile_placement` (spec §3.3)
  - `interface TileStore`:

    ```kotlin
    interface TileStore {
        val tiles: Flow<List<Tile>>                      // ordered by id
        fun placements(gridWidth: Int): Flow<List<TilePlacement>>   // in reading order: by y, then x
        suspend fun savedWidths(): Set<Int>
        suspend fun insertAppTile(app: AppKey, size: TileSize): Long
        suspend fun commit(edit: LayoutEdit)
    }

    data class LayoutEdit(
        val deleted: Set<Long> = emptySet(),
        val sizes: Map<Long, TileSize> = emptyMap(),
        val appKeys: Map<Long, AppKey> = emptyMap(),
        val placements: Map<Int, List<TilePlacement>> = emptyMap(), // replaces each listed width entirely
    )
    ```

  - `class RoomTileStore(dao: TileDao) : TileStore`
  - `class FakeTileStore : TileStore`: in memory, with `var commitCount: Int` and `fun seed(tiles: List<Tile>, placements: Map<Int, List<TilePlacement>>)`. `insertAppTile` hands out ids 1, 2, 3 and so on.
  - `class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher`, which sets `Dispatchers.Main` and resets it afterwards

- [ ] **Step 1: Write the failing test.** Use a Room in-memory database under Robolectric:

```kotlin
@RunWith(AndroidJUnit4::class)
class RoomTileStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LayoutDatabase::class.java).build()
    private val store = RoomTileStore(db.tileDao())
    private val whatsapp = AppKey("com.whatsapp", "com.whatsapp.Main", 0)

    @After fun tearDown() = db.close()

    @Test fun insertAppTileStoresTheTileAndItsAppSource() = runTest {
        val id = store.insertAppTile(whatsapp, TileSize.MEDIUM)
        val tile = store.tiles.first().single()
        assertEquals(id, tile.id)
        assertEquals(TileKind.APP, tile.kind)
        assertEquals(TileTemplate.FLIP, tile.template)
        assertEquals(whatsapp, tile.appKey)
        assertEquals(listOf(SourceId("app:com.whatsapp@0")), tile.sources)
    }

    @Test fun commitReplacesOnlyTheWidthsItNames() = runTest {
        val id = store.insertAppTile(whatsapp, TileSize.MEDIUM)
        store.commit(LayoutEdit(placements = mapOf(4 to listOf(TilePlacement(id, 4, 0, 0)), 6 to listOf(TilePlacement(id, 6, 0, 0)))))
        store.commit(LayoutEdit(placements = mapOf(6 to listOf(TilePlacement(id, 6, 2, 0)))))
        assertEquals(listOf(TilePlacement(id, 4, 0, 0)), store.placements(4).first())
        assertEquals(listOf(TilePlacement(id, 6, 2, 0)), store.placements(6).first())
        assertEquals(setOf(4, 6), store.savedWidths())
    }

    @Test fun commitChangesSizesAndAppKeys() = runTest {
        val id = store.insertAppTile(whatsapp, TileSize.MEDIUM)
        val alias = whatsapp.copy(className = "com.whatsapp.AltIcon")
        store.commit(LayoutEdit(sizes = mapOf(id to TileSize.WIDE), appKeys = mapOf(id to alias)))
        val tile = store.tiles.first().single()
        assertEquals(TileSize.WIDE, tile.size)
        assertEquals(alias, tile.appKey)
    }

    @Test fun deletingATileRemovesItsSourcesAndPlacements() = runTest {
        val id = store.insertAppTile(whatsapp, TileSize.MEDIUM)
        store.commit(LayoutEdit(placements = mapOf(6 to listOf(TilePlacement(id, 6, 0, 0)))))
        store.commit(LayoutEdit(deleted = setOf(id)))
        assertEquals(emptyList<Tile>(), store.tiles.first())
        assertEquals(emptyList<TilePlacement>(), store.placements(6).first())
        assertEquals(0, db.tileDao().countSources())
    }
}
```

- [ ] **Step 2: Run it and watch it fail.**

Run: `./gradlew :core:tiles:testDebugUnitTest --tests "*RoomTileStoreTest"`
Expected: FAIL, because the database and store don't exist.

- [ ] **Step 3: Implement the database, DAO and stores.**
  - **`TileEntity`** (table `tile`):
    - `id`, auto-generated
    - `kind`, `size` and `template`, stored as enum names
    - `title`, `color_override`, `surface_override`
    - `show_previews` (default true) and `keep_read_items_24h` (default false)
    - `parent_folder_id`, `app_widget_id`, `shortcut_ref`
    - `app_key`: the flattened `AppKey`, which is the spec's "package reference"
  - **`TileSourceEntity`** (table `tile_source`): primary key (`tile_id`, `source_id`), plus `sort_order`. The spec calls it `order`, but `order` is an SQL keyword. Index `source_id`.
  - **`TilePlacementEntity`** (table `tile_placement`): primary key (`tile_id`, `grid_width`), plus `x` and `y`. Both child tables have a foreign key to `tile.id` with `onDelete = CASCADE`.
  - **`TileDao`:** an abstract class with:
    - an observed `@Transaction` query returning tiles with their sources
    - observed placements for one width
    - `savedWidths()`
    - `countSources()`, for tests
    - `@Transaction open suspend fun insertTileWithSources(...)`
    - `@Transaction open suspend fun commit(...)`, which deletes, then updates sizes, then app keys, then replaces each listed width's rows, skipping deleted ids
  - **Store mapping:** `RoomTileStore` maps entities to the `:core:tiles` model. `insertAppTile` writes `kind = APP`, `template = FLIP` and one source, `SourceId.forApp(app.packageName, app.userSerial)`.
  - **Backup rules:** spec §3.3 has Auto Backup include the tile tables and exclude notification data, so this plan keeps them in separate files: `layout.db` is backed up, and `cache.db` (Task 5, and the notification store in stage 2) isn't.
    - In both XML files, include `database/layout.db`, `database/layout.db-wal`, `database/layout.db-shm` and `file/datastore/`.
    - Point the manifest at them with `android:dataExtractionRules` (API 31+) and `android:fullBackupContent` (API 29–30).
  - **Migrations:** version 1 needs none. The first schema change, in stage 2, brings the migration test and spec §9's fallback (copy the old file aside and start with the default layout).

- [ ] **Step 4: Run the test again.**

Run: `./gradlew :core:tiles:testDebugUnitTest --tests "*RoomTileStoreTest"`
Expected: PASS, and `core/data/schemas/…LayoutDatabase/1.json` exists.

- [ ] **Step 5: Commit.**

```bash
git add core app
git commit -m "Add the layout database and tile store"
```

---

### Task 5: App catalog: LauncherApps, the cached app list and the icon cache

**Files:**
- Create: `core/data/src/main/kotlin/…/core/data/apps/LauncherApp.kt`, `AppCatalog.kt`, `AppSource.kt`, `LauncherAppsSource.kt`, `AppRepository.kt`, `IconCache.kt`
- Create: `core/data/src/main/kotlin/…/core/data/db/cache/CacheDatabase.kt`, `AppEntryEntity.kt`, `AppLaunchEntity.kt`, `AppDao.kt`
- Create: `core/testing/src/main/kotlin/…/core/testing/FakeAppCatalog.kt`, `TestApps.kt`
- Test: `core/data/src/test/kotlin/…/apps/AppRepositoryTest.kt`, `IconCacheTest.kt`, `FakeAppSource.kt`

**Interfaces:**
- Consumes: `AppKey` from Task 3.
- Produces:

```kotlin
data class AppProfile(val userSerial: Long, val isWork: Boolean, val isPaused: Boolean)
data class LauncherApp(val key: AppKey, val label: String, val firstInstallTime: Long,
                       val isWorkProfile: Boolean, val isPaused: Boolean, val versionToken: String)
data class AppIcon(val bitmap: Bitmap, val isMonochrome: Boolean)

interface AppCatalog {
    val apps: StateFlow<List<LauncherApp>>        // sorted by label (locale Collator), then key.flatten()
    val profiles: StateFlow<List<AppProfile>>
    val isFresh: StateFlow<Boolean>               // true once LauncherApps has been read in this process
    val lastLaunched: StateFlow<Map<AppKey, Long>>
    fun launch(key: AppKey, sourceBounds: Rect? = null, options: Bundle? = null)
    fun openAppInfo(key: AppKey)
    fun uninstall(key: AppKey)
    fun requestUnpause(userSerial: Long)
    suspend fun icon(app: LauncherApp): AppIcon?
}

data class AppActivity(val key: AppKey, val label: String, val firstInstallTime: Long, val versionToken: String)

interface AppSource {
    suspend fun profiles(): List<AppProfile>
    suspend fun activities(profile: AppProfile): List<AppActivity>
    fun changes(): Flow<Unit>                     // any LauncherApps callback or profile broadcast
    fun loadIcon(key: AppKey, densityDpi: Int): Drawable?
    fun launch(key: AppKey, sourceBounds: Rect?, options: Bundle?)
    fun openAppInfo(key: AppKey)
    fun uninstall(key: AppKey)
    fun requestUnpause(userSerial: Long)
}
```

- `class AppRepository(source: AppSource, db: CacheDatabase, icons: IconCache, scope: CoroutineScope, io: CoroutineDispatcher = Dispatchers.IO, nowMillis: () -> Long = System::currentTimeMillis) : AppCatalog`
- `class IconCache(source: AppSource, dir: File, densityDpi: Int, sizePx: Int = 256, io: CoroutineDispatcher = Dispatchers.IO)` with `suspend fun get(app: LauncherApp): AppIcon?`
- `CacheDatabase` (Room, file `cache.db`, version 1) with tables `app_entry` (`app_key` primary key, `label`, `first_install_time`, `is_work`, `version_token`) and `app_launch` (`app_key` primary key, `last_launched_at`)
- In `:core:testing`:
  - `class FakeAppCatalog(apps: List<LauncherApp> = emptyList(), profiles: List<AppProfile> = listOf(AppProfile(0, isWork = false, isPaused = false)), fresh: Boolean = true) : AppCatalog`. Its flows are public `MutableStateFlow`s, and it records calls in `launched`, `appInfoOpened`, `uninstalled` and `unpauseRequests`. `icon()` returns null.
  - `fun testApp(label: String, packageName: String = "com.example." + label.lowercase().filter(Char::isLetterOrDigit), className: String = "$packageName.Main", userSerial: Long = 0, firstInstallTime: Long = 0, isPaused: Boolean = false, versionToken: String = "v1"): LauncherApp`, where `isWorkProfile = userSerial != 0L`
  - `fun appTile(id: Long, key: AppKey, size: TileSize = TileSize.MEDIUM): Tile`, which builds an APP tile with `SourceId.forApp(...)` and `FLIP`

- [ ] **Step 1: Write the failing tests.** `FakeAppSource` lives in the test folder. It serves `activities` for each profile (filtered by `userSerial`), can hold `activities()` on an optional `gate: CompletableDeferred<Unit>`, exposes `changes` as a `MutableSharedFlow<Unit>`, returns a configurable `icon: Drawable?`, and counts `iconLoads` and `launched`. The tests use an in-memory `CacheDatabase` and `UnconfinedTestDispatcher(testScheduler)` as `io`.

```kotlin
@RunWith(AndroidJUnit4::class)
class AppRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
    private val camera = AppActivity(AppKey("com.cam", "com.cam.Main", 0), "Camera", 1L, "v1")
    private val maps = AppActivity(AppKey("com.maps", "com.maps.Main", 0), "maps", 2L, "v1")
    private val calculator = AppActivity(AppKey("com.calc", "com.calc.Main", 0), "Calculator", 3L, "v1")

    @After fun tearDown() = db.close()

    @Test fun showsTheCachedListUntilTheFirstRefreshFinishes() = runTest {
        db.appDao().replaceEntries(listOf(AppEntryEntity(camera.key.flatten(), "Camera", 1L, isWork = false, versionToken = "v1")))
        val source = FakeAppSource(activities = listOf(camera, maps), gate = CompletableDeferred())
        val repo = repository(source)
        assertEquals(listOf("Camera"), repo.apps.first { it.isNotEmpty() }.map { it.label })
        assertFalse(repo.isFresh.value)
        source.gate!!.complete(Unit)
        repo.isFresh.first { it }
        assertEquals(listOf("Camera", "maps"), repo.apps.value.map { it.label })
    }

    @Test fun refreshesWhenLauncherAppsReportsAChange() = runTest {
        val source = FakeAppSource(activities = listOf(camera))
        val repo = repository(source).also { it.isFresh.first { fresh -> fresh } }
        source.activities = listOf(camera, maps)
        source.changes.emit(Unit)
        repo.apps.first { it.size == 2 }
    }

    @Test fun sortsLabelsWithTheLocaleCollator() = runTest {
        val repo = repository(FakeAppSource(activities = listOf(maps, camera, calculator))).also { it.isFresh.first { f -> f } }
        assertEquals(listOf("Calculator", "Camera", "maps"), repo.apps.value.map { it.label })
    }

    @Test fun launchingRecordsTheTime() = runTest {
        val source = FakeAppSource(activities = listOf(camera))
        val repo = repository(source, now = { 5_000L }).also { it.isFresh.first { f -> f } }
        repo.launch(camera.key)
        assertEquals(listOf(camera.key), source.launched)
        assertEquals(5_000L, repo.lastLaunched.first { it.isNotEmpty() }[camera.key])
    }

    @Test fun appsInAPausedProfileAreMarkedPaused() = runTest {
        val slack = AppActivity(AppKey("com.slack", "com.slack.Main", 10), "Slack", 1L, "v1")
        val profiles = listOf(AppProfile(0, isWork = false, isPaused = false), AppProfile(10, isWork = true, isPaused = true))
        val repo = repository(FakeAppSource(profiles = profiles, activities = listOf(camera, slack))).also { it.isFresh.first { f -> f } }
        val app = repo.apps.value.single { it.label == "Slack" }
        assertTrue(app.isWorkProfile && app.isPaused)
    }

    private fun TestScope.repository(source: FakeAppSource, now: () -> Long = { 0L }) = AppRepository(
        source, db, IconCache(source, File(context.cacheDir, "icons"), densityDpi = 420),
        backgroundScope, io = UnconfinedTestDispatcher(testScheduler), nowMillis = now,
    )
}
```

`IconCacheTest` builds its `LauncherApp`s directly, because `:core:testing` depends on `:core:data`. It has three tests:
- **`rendersEachIconOncePerVersion`:** with a `ColorDrawable` source, `get(app)` twice gives `iconLoads == 1`, and `isMonochrome` is false. `get(app.copy(versionToken = "v2"))` makes it 2.
- **`usesTheMonochromeLayerWhenThereIsOne`:** an `AdaptiveIconDrawable(background, foreground, monochrome)` source gives `isMonochrome == true`.
- **`aMissingIconGivesNull`:** a source that returns null gives null.

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: FAIL, because the classes don't exist.

- [ ] **Step 3: Implement `LauncherAppsSource`.**
  - **Profiles:** `UserManager.userProfiles`, with `getSerialNumberForUser`. A profile is work when it isn't `Process.myUserHandle()`, and paused when `isQuietModeEnabled(user)`.
  - **Activities:** `launcherApps.getActivityList(null, user)`. Take the label from `info.label`, the first install time from `info.firstInstallTime`, and use `info.applicationInfo.sourceDir` as the `versionToken`, because that path changes whenever the app is updated.
  - **Icons:** `loadIcon` uses `info.getIcon(densityDpi)` from the last activity list it read.
  - **Changes:** `callbackFlow` over `LauncherApps.Callback` (every callback sends `Unit`). Also register a receiver for `ACTION_MANAGED_PROFILE_AVAILABLE`, `…_UNAVAILABLE`, `…_ADDED` and `…_REMOVED` with `ContextCompat.registerReceiver(…, RECEIVER_NOT_EXPORTED)`. Unregister both in `awaitClose`.
  - **Launch:** `launcherApps.startMainActivity(component, user, sourceBounds, options)`.
  - **App info:** `startAppDetailsActivity`.
  - **Uninstall:** `Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))` with `Intent.EXTRA_USER` and `FLAG_ACTIVITY_NEW_TASK`.
  - **Unpause:** `userManager.requestQuietModeEnabled(false, user)`. Catch `SecurityException`, which happens when LiquiWin isn't the default Home app, and log it.

- [ ] **Step 4: Implement `CacheDatabase`, `IconCache` and `AppRepository`.**
  - **`IconCache`:**
    - The memory cache is an `LruCache` capped at 32 MB of bitmap bytes. Behind it is the disk folder `<dir>/<sha1 of key.flatten() + versionToken>_<m or c>.png`, and behind that rendering.
    - Render on `io`. On API 33+, when the drawable is an `AdaptiveIconDrawable` with a `monochrome` layer, draw that layer tinted white with bounds −0.25 × size … 1.25 × size. That maps the layer's visible 72/108 area onto the bitmap. Otherwise draw the whole drawable at 0…size.
    - Write the PNG without making the caller wait.
  - **`AppRepository`:**
    - **On creation:** publish the `app_entry` rows (as unpaused apps) and the launch times, then refresh.
    - **On refresh:** read all profiles and activities on `io` and publish the sorted list, the profiles and `isFresh = true`. Then replace the `app_entry` rows.
    - Refreshes are driven by `changes().onStart { emit(Unit) }.conflate()`. The first refresh starts at once, a burst of callbacks causes one more refresh, and a change during a refresh is never missed.
    - **`launch`:** delegates to the source, then upserts `app_launch` with `nowMillis()`.
    - **`icon`:** delegates to `IconCache`.

- [ ] **Step 5: Run the tests again.**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Write `FakeAppCatalog`, `testApp` and `appTile` in `:core:testing`, then build them.**

Run: `./gradlew :core:testing:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit.**

```bash
git add core
git commit -m "Add the app catalog over LauncherApps with cached apps and icons"
```

---

### Task 6: Start screen logic: pin, unpin, resize, move and repairs

**Files:**
- Create: `feature/start/src/main/kotlin/…/feature/start/AppTileRepairs.kt`, `StartViewModel.kt`
- Test: `feature/start/src/test/kotlin/…/AppTileRepairsTest.kt`, `StartViewModelTest.kt`

**Interfaces:**
- Consumes: `TileStore`, `LayoutEdit` (Task 4); `AppCatalog`, `LauncherApp`, `AppProfile` (Task 5); `GridLayout`, `Cell`, `Placed` (Task 3); `FakeTileStore`, `FakeAppCatalog`, `testApp`, `appTile`, `MainDispatcherRule` (Tasks 4 and 5).
- Produces:

```kotlin
data class AppTileRepairs(val repoint: Map<Long, AppKey> = emptyMap(), val remove: Set<Long> = emptySet())
fun planAppTileRepairs(tiles: List<Tile>, apps: List<LauncherApp>, profiles: List<AppProfile>): AppTileRepairs

class StartViewModel(store: TileStore, catalog: AppCatalog, gridWidth: Int) : ViewModel() {
    val state: StateFlow<StartState>
    val pinnedApps: StateFlow<Set<AppKey>>
    fun pin(app: AppKey)
    fun unpin(tileId: Long)
    fun cycleSize(tileId: Long)
    fun enterEdit(tileId: Long)
    fun select(tileId: Long)
    fun previewMove(tileId: Long, cell: Cell)
    fun endDrag(tileId: Long, cell: Cell)
    fun cancelDrag()
    fun exitEdit()
    fun open(tileId: Long, sourceBounds: Rect?, options: Bundle?)
}
data class StartState(val gridWidth: Int, val tiles: List<StartTile>, val edit: EditState?, val loaded: Boolean)
data class StartTile(val id: Long, val size: TileSize, val cell: Cell, val app: LauncherApp?, val label: String,
                     val isWork: Boolean, val isPaused: Boolean)
data class EditState(val selectedId: Long?, val preview: Map<Long, Cell>?)
```

The rules:
- **Repairs.** For each APP tile, the first rule that matches decides:
  1. Its key is listed: keep it.
  2. Another activity of the same package and user is listed: repoint the tile to the first one.
  3. Its profile isn't listed: remove it.
  4. Its profile is paused, or lists no apps at all: keep it. A profile that lists nothing is starting up or broken, not empty.
  5. Otherwise: remove it.

  When `profiles` has no main (non-work) profile, change nothing.
- **The view model:**
  - It applies repairs only while `catalog.isFresh` is true, in one `commit` that also compacts every saved width.
  - Every action, every repair and every commit of a repaired arrangement runs under a single `Mutex` and reads the latest store state.
  - The arrangement shown is `GridLayout.reconcile(...)` of the tiles and the current width's saved placements. When a saved arrangement needed repair, the repaired one is committed once.
  - `fallbackOrder` is empty, because stage 1 has only one width. Stage 3 passes the reading order of the most recently used width (spec §4.2).
  - **Tiles shown:** `app` is the listed app with the tile's key, or null. `label` is the app's label, or the package name when the app isn't listed. `isWork` is true when the tile's user isn't the main profile's, and `isPaused` when its profile is listed as paused.
- **Edits:** `pin`, `unpin`, `cycleSize` and `endDrag` are edits. Each one saves the current width's arrangement, and `pin`, `unpin` and `cycleSize` apply the same `GridLayout` call to every other saved width.
  - New tiles are MEDIUM, the "default size" facet in spec §4.1.
  - Pinning an app that already has an APP tile does nothing.
- **Previews:** `previewMove` only sets `edit.preview` from `GridLayout.move` and never writes. `cancelDrag` clears the preview and stays in edit mode.
- **Opening:** `open` selects the tile while in edit mode. For a tile with `isPaused` it calls `catalog.requestUnpause(userSerial)`. Otherwise it calls `catalog.launch(key, sourceBounds, options)`.

- [ ] **Step 1: Write the failing tests.**

```kotlin
class AppTileRepairsTest { // Review Focus 1
    private val main = AppProfile(0, isWork = false, isPaused = false)
    private val camera = testApp("Camera")
    private val notes = testApp("Notes")

    @Test fun keepsTilesWhoseAppIsListed() =
        assertEquals(AppTileRepairs(), planAppTileRepairs(listOf(appTile(1, camera.key)), listOf(camera), listOf(main)))

    @Test fun followsTheAppToItsNewLauncherActivity() {
        val alias = camera.copy(key = camera.key.copy(className = "com.example.camera.AltIcon"))
        assertEquals(AppTileRepairs(repoint = mapOf(1L to alias.key)), planAppTileRepairs(listOf(appTile(1, camera.key)), listOf(alias), listOf(main)))
    }

    @Test fun keepsTilesOfAPausedProfile() {
        val slack = testApp("Slack", userSerial = 10)
        val profiles = listOf(main, AppProfile(10, isWork = true, isPaused = true))
        assertEquals(AppTileRepairs(), planAppTileRepairs(listOf(appTile(1, slack.key)), listOf(camera), profiles))
    }

    @Test fun keepsTilesOfAProfileThatListsNothing() {
        val slack = testApp("Slack", userSerial = 10)
        val profiles = listOf(main, AppProfile(10, isWork = true, isPaused = false))
        assertEquals(AppTileRepairs(), planAppTileRepairs(listOf(appTile(1, slack.key)), listOf(camera), profiles))
    }

    @Test fun removesTilesWhoseAppIsGone() =
        assertEquals(AppTileRepairs(remove = setOf(1L)), planAppTileRepairs(listOf(appTile(1, notes.key)), listOf(camera), listOf(main)))

    @Test fun removesTilesOfARemovedProfile() {
        val slack = testApp("Slack", userSerial = 10)
        assertEquals(AppTileRepairs(remove = setOf(1L)), planAppTileRepairs(listOf(appTile(1, slack.key)), listOf(camera), listOf(main)))
    }

    @Test fun changesNothingWhenTheMainProfileListIsEmpty() =
        assertEquals(AppTileRepairs(), planAppTileRepairs(listOf(appTile(1, camera.key)), emptyList(), listOf(main)))
}

class StartViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val camera = testApp("Camera"); private val maps = testApp("Maps"); private val mail = testApp("Mail")
    private val store = FakeTileStore()
    private val catalog = FakeAppCatalog(apps = listOf(camera, maps, mail))
    private fun viewModel() = StartViewModel(store, catalog, gridWidth = 6)

    @Test fun pinnedAppsAppendAsMediumTiles() = runTest {
        val vm = viewModel(); vm.pin(camera.key); vm.pin(maps.key)
        val tiles = vm.state.first { it.tiles.size == 2 }.tiles
        assertEquals(listOf(Cell(0, 0), Cell(2, 0)), tiles.map { it.cell })
        assertTrue(tiles.all { it.size == TileSize.MEDIUM })
        assertEquals(setOf(6), store.savedWidths())
    }

    @Test fun pinningAPinnedAppDoesNothing() = runTest {
        val vm = viewModel(); vm.pin(camera.key); vm.pin(camera.key)
        assertEquals(1, vm.state.first { it.loaded && it.tiles.isNotEmpty() }.tiles.size)
    }

    @Test fun unpinCompactsEverySavedWidth() = runTest {
        store.seed(
            tiles = listOf(appTile(1, camera.key), appTile(2, maps.key)),
            placements = mapOf(4 to listOf(TilePlacement(1, 4, 0, 0), TilePlacement(2, 4, 0, 2)),
                               6 to listOf(TilePlacement(1, 6, 0, 0), TilePlacement(2, 6, 0, 2))),
        )
        viewModel().unpin(1)
        assertEquals(listOf(TilePlacement(2, 4, 0, 0)), store.placements(4).first())
        assertEquals(listOf(TilePlacement(2, 6, 0, 0)), store.placements(6).first())
    }

    @Test fun cycleSizeGoesThroughEverySize() = runTest {
        val vm = viewModel(); vm.pin(camera.key)
        val sizes = (1..4).map { vm.cycleSize(1); store.tiles.first().single().size }
        assertEquals(listOf(TileSize.WIDE, TileSize.LARGE, TileSize.SMALL, TileSize.MEDIUM), sizes)
    }

    @Test fun previewMoveDoesNotWriteButEndDragDoes() = runTest {
        val vm = viewModel(); vm.pin(camera.key); vm.pin(maps.key); vm.enterEdit(1)
        val before = store.commitCount
        vm.previewMove(1, Cell(2, 0))
        assertEquals(before, store.commitCount)
        assertEquals(Cell(0, 0), vm.state.value.edit!!.preview!![2L])
        vm.endDrag(1, Cell(2, 0))
        assertEquals(before + 1, store.commitCount)
        assertEquals(listOf(TilePlacement(2, 6, 0, 0), TilePlacement(1, 6, 2, 0)), store.placements(6).first())
    }

    @Test fun aTileThatWasNeverPlacedIsAppendedOnLoad() = runTest { // Review Focus 2
        store.seed(tiles = listOf(appTile(1, camera.key), appTile(2, maps.key)), placements = mapOf(6 to listOf(TilePlacement(1, 6, 0, 0))))
        val tiles = viewModel().state.first { it.loaded }.tiles
        assertEquals(mapOf(1L to Cell(0, 0), 2L to Cell(2, 0)), tiles.associate { it.id to it.cell })
        assertEquals(2, store.placements(6).first().size)
    }

    @Test fun repairsWaitForAFreshAppList() = runTest { // Review Focus 1
        catalog.isFresh.value = false
        catalog.apps.value = listOf(maps)
        store.seed(tiles = listOf(appTile(1, camera.key)), placements = emptyMap())
        val vm = viewModel()
        assertEquals(1, vm.state.first { it.loaded }.tiles.size)
        catalog.isFresh.value = true
        vm.state.first { it.tiles.isEmpty() }
    }

    @Test fun openingAPausedWorkAppRequestsUnpause() = runTest {
        val slack = testApp("Slack", userSerial = 10, isPaused = true)
        catalog.apps.value = listOf(camera, slack)
        catalog.profiles.value = listOf(AppProfile(0, false, false), AppProfile(10, true, true))
        val vm = viewModel(); vm.pin(slack.key)
        vm.open(1, null, null)
        assertEquals(listOf(10L), catalog.unpauseRequests)
        assertEquals(emptyList<AppKey>(), catalog.launched)
    }

    @Test fun openingInEditModeSelectsInsteadOfLaunching() = runTest {
        val vm = viewModel(); vm.pin(camera.key); vm.pin(maps.key); vm.enterEdit(1)
        vm.open(2, null, null)
        assertEquals(2L, vm.state.value.edit!!.selectedId)
        assertEquals(emptyList<AppKey>(), catalog.launched)
    }
}
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:start:testDebugUnitTest`
Expected: FAIL, because `StartViewModel` and `planAppTileRepairs` don't exist.

- [ ] **Step 3: Implement `planAppTileRepairs` and `StartViewModel`** to the rules above.

- [ ] **Step 4: Run the tests again.**

Run: `./gradlew :feature:start:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add feature/start
git commit -m "Add start screen logic for pinning, editing and repairing tiles"
```

---

### Task 7: Start screen UI: the grid, tiles and edit mode

**Files:**
- Create: `feature/start/src/main/kotlin/…/feature/start/GridMetrics.kt`, `StartScreen.kt`, `StartTileView.kt`, `EditControls.kt`
- Test: `feature/start/src/test/kotlin/…/GridMetricsTest.kt`, `StartScreenTest.kt`

**Interfaces:**
- Consumes: `StartViewModel` and its state (Task 6); `LiquiWinTheme`, `LocalWpUnits`, `LocalStyleFacets`, `Modifier.tilt`, `LiquiWinIcons`, `WpText` and `IconImage` (Task 2); `AppIcon` (Task 5).
- Produces:
  - `data class GridMetrics(val screenWidthDp: Float, val units: Int, val sideMarginWpPx: Float, val gapWpPx: Float)` with `val sideMarginDp: Float`, `val gapDp: Float`, `val unitDp: Float`, `val stepDp: Float` (unit + gap) and `fun sizeDp(size: TileSize): DpSize`
  - `@Composable fun StartScreen(viewModel: StartViewModel, icon: suspend (LauncherApp) -> AppIcon?, scrollState: ScrollState, modifier: Modifier = Modifier, banner: @Composable () -> Unit = {})`

The look and behaviour, from spec §4.2, §4.3, §4.6 and §4.7:
- **Grid:**
  - A `verticalScroll(scrollState)` around a custom `Layout`. Each tile sits at `cell × stepDp` inside the side margins.
  - Top padding is the status-bar inset plus one gap. Bottom padding is the navigation-bar inset plus 96 WP px.
  - `banner` sits above the first row.
- **Tile:**
  - Filled with the style's accent colour, with square corners.
  - The icon is centred at 50% of the tile's shorter side.
  - On medium and larger tiles, the label is `tileLabel` in white, on one line with ellipsis, 12 WP px from the left and 8 WP px from the bottom.
  - Tiles with `isPaused` are drawn at 40% opacity. Tiles with `isWork` get the Briefcase glyph, a quarter of a unit wide, in the top-right corner.
  - The content description is the tile's `label`, and tiles tilt when pressed outside edit mode.
  - Each tile has the test tag `"tile-<id>"`. Tests find tiles by it, because an app's label also appears in the app list.
- **Normal mode:**
  - A tap calls `open(id, bounds, options)`. `bounds` are the tile's window bounds, and `options` is `ActivityOptions.makeScaleUpAnimation(LocalView.current, x, y, w, h).toBundle()`. This is the "zoom from tile" launch animation, standing in for the turnstile until stage 3.
  - A long-press calls `enterEdit(id)` with `HapticFeedbackType.LongPress`. Dragging without lifting the finger moves the tile.
- **Edit mode:**
  - The selected tile scales to 1.04 and draws on top. The others scale to 0.92 at 40% opacity.
  - Two 32 dp white circles with black glyphs are centred on the selected tile's corners, each with a 48 dp touch target: top right **unpin** (`PinOff`, which calls `unpin`) and bottom right **resize** (`Resize`, which calls `cycleSize`).
  - The bottom bar uses the chrome colour and is 72 WP px tall, plus the navigation-bar inset. It holds one centred **done** button: the `Check` glyph in a 2 dp white ring with "done" under it, calling `exitEdit`. The spec's other bar buttons (widget, new tile, style) arrive with their features in later stages.
  - Tapping a tile selects it, and pressing and dragging any tile selects and moves it. A tap on empty space or Back (`BackHandler`) calls `exitEdit`.
  - Content descriptions are "unpin", "resize" and "done".
- **Drag:**
  - The tile follows the finger.
  - The hover cell is the dragged tile's top-left ÷ `stepDp`, rounded and clamped to `0…width − w` and `y ≥ 0`. This follows spec §4.3's "centre moves into a different cell" in whole-cell steps, and stays stable for medium and larger tiles, whose centre sits on a cell line.
  - A hover cell held for 150 ms calls `previewMove`. Release calls `endDrag(id, hoverCell)`, and pointer cancel calls `cancelDrag`.
  - Within 64 dp of the viewport's top or bottom edge, the grid auto-scrolls by up to 12 dp per frame, in proportion to how deep the finger is.
  - Other tiles animate to their preview cells with `spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)`.
- **Empty start screen:** centred `body` text in the subtle colour: "swipe left for your apps. long-press one and choose pin to start."

- [ ] **Step 1: Write the failing tests.**

```kotlin
class GridMetricsTest {
    @Test fun galaxyS24WithThreeColumns() {
        val m = GridMetrics(screenWidthDp = 411.43f, units = 6, sideMarginWpPx = 24f, gapWpPx = 12f)
        assertEquals(53.14f, m.unitDp, 0.01f)
        assertEquals(63.43f, m.stepDp, 0.01f)
        assertEquals(116.57f, m.sizeDp(TileSize.MEDIUM).width.value, 0.01f)
    }
}

@RunWith(AndroidJUnit4::class)
class StartScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>() // Back: compose.activity.onBackPressedDispatcher
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val camera = testApp("Camera"); private val maps = testApp("Maps"); private val mail = testApp("Mail")
    private val store = FakeTileStore()
    private val catalog = FakeAppCatalog(apps = listOf(camera, maps, mail))
    // Each test pins camera, maps and mail (medium tiles at (0,0), (2,0) and (4,0)) unless it says otherwise,
    // then shows LiquiWinTheme { StartScreen(viewModel, { null }, rememberScrollState()) }.

    @Test fun tappingATileOpensItsApp()            // click "Camera" → catalog.launched == [camera.key]
    @Test fun longPressShowsTheEditControls()      // longClick on "Camera" → "unpin", "resize" and "done" exist
    @Test fun resizeMakesTheTileWide()             // long-press "Camera", click "resize" → the stored size of tile 1 is WIDE
    @Test fun unpinRemovesTheTile()                // long-press "Camera", click "unpin" → "Camera" doesn't exist
    @Test fun draggingSwapsTilesAfterTheDwell()    // long-press "Camera", move right by 2 × stepDp, wait 200 ms, release
                                                   //   → placements(6): Maps (0,0), Camera (2,0), Mail (4,0)
    @Test fun backAndEmptySpaceLeaveEditMode()     // long-press, press Back → no "done"; long-press, tap below the tiles → no "done"
    @Test fun anEmptyStartScreenShowsTheHint()     // pin nothing → the hint text exists
}
```

Write each body as described in its comment. Get `stepDp` from `GridMetrics` with the test window's width, so the drag distance is exact.

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:start:testDebugUnitTest --tests "*GridMetricsTest" --tests "*StartScreenTest"`
Expected: FAIL, because `GridMetrics` and `StartScreen` don't exist.

- [ ] **Step 3: Implement `GridMetrics`, `StartScreen`, `StartTileView` and `EditControls`** to the rules above. Load icons with `produceState(null, app.key, app.versionToken) { value = icon(app) }`.

- [ ] **Step 4: Run the tests again.**

Run: `./gradlew :feature:start:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add feature/start
git commit -m "Add the start screen grid, tiles and edit mode"
```

---

### Task 8: App list (WP8.1 style)

**Files:**
- Create: `feature/applist/src/main/kotlin/…/feature/applist/AppGrouper.kt`, `AppListViewModel.kt`, `AppListScreen.kt`, `AppContextMenu.kt`, `LetterGrid.kt`
- Test: `feature/applist/src/test/kotlin/…/AppGrouperTest.kt`, `AppListViewModelTest.kt`, `AppListScreenTest.kt`

**Interfaces:**
- Consumes: `AppCatalog` and `LauncherApp` (Task 5); design components (Task 2).
- Produces:

```kotlin
class AppGrouper(locale: Locale) { fun group(apps: List<LauncherApp>): AppIndex }
data class AppIndex(val sections: List<AppSection>, val letters: List<LetterCell>)
data class AppSection(val label: String, val apps: List<LauncherApp>)   // label is lowercase
data class LetterCell(val label: String, val hasApps: Boolean)

class AppListViewModel(
    catalog: AppCatalog,
    pinnedApps: Flow<Set<AppKey>>,
    onPinToStart: (AppKey) -> Unit,
    nowMillis: () -> Long = System::currentTimeMillis,
    locale: Locale = Locale.getDefault(),
) : ViewModel() {
    val state: StateFlow<AppListState>
    fun open(app: LauncherApp, sourceBounds: Rect?, options: Bundle?)   // paused work app → requestUnpause
    fun pin(app: LauncherApp)
    fun appInfo(app: LauncherApp)
    fun uninstall(app: LauncherApp)
}
data class AppListState(val index: AppIndex, val pinned: Set<AppKey>, val newApps: Set<AppKey>)

@Composable fun AppListScreen(viewModel: AppListViewModel, icon: suspend (LauncherApp) -> AppIcon?,
                              onSearch: () -> Unit, resetKey: Int, modifier: Modifier = Modifier)
```

The rules (spec §6.1):
- **Grouping:**
  - The index is `android.icu.text.AlphabeticIndex(locale).addLabels(Locale.ENGLISH).buildImmutableIndex()`.
  - A label whose first code point isn't a letter goes under "#".
  - A label that lands in a NORMAL bucket takes that bucket's label.
  - Anything else (underflow, inflow or overflow) gets its own group, named after its first code point.
  - Sections run "#" first, then the index's letters in order, then the other-script groups in `Collator` order. Section labels are lowercased with the locale.
  - Within a section, apps sort by label with the locale `Collator`, then by `key.flatten()`.
  - `letters` lists "#", every NORMAL bucket label and the other-script groups that have apps, lowercased like the sections, each with `hasApps`.
- **New:** an app is new when `firstInstallTime ≥ now − 3 days` (259,200,000 ms). Grouping runs on `Dispatchers.Default`.
- **Screen:**
  - **Search box:** at the top, below the status bar. It's a chrome-coloured box with the `Search` glyph and "search apps" in the subtle colour, and tapping it calls `onSearch`.
  - **List:** a `LazyColumn`. Rows are a 56 WP px accent square holding the icon at 50%, with the label in `listItem` next to it. Rows tilt when pressed (spec §4.7).
  - **"new" label:** new apps show "new" under their label, in the accent colour and `small` style.
  - **Work apps:** the Briefcase glyph on the icon square. Paused work apps are drawn at 40% opacity.
  - **Section headers:** a 56 WP px square with a 2 dp accent outline, holding the lowercase label at the bottom left in `header` style. This is meant as WP8.1's look; Task 12 compares it with screenshots.
- **Letter grid:** tapping a header opens it.
  - It's a full-screen `#E6000000` overlay with four columns of 96 WP px squares, 12 WP px apart. Letters are lowercase, in `pageTitle` weight at 42.667 WP px.
  - Letters with apps are accent-filled and jump the list to their section, then close the grid. Empty letters use the chrome colour, have subtle text and can't be tapped.
  - Back closes it.
- **Context menu:**
  - A long-press on a row opens a chrome-coloured panel under the row, or above it when there's no room below, and dims the rest of the list.
  - Its lowercase `listItem` entries are "pin to start" (hidden when the app is already pinned), "app info" and "uninstall".
  - A tap outside it, or Back, closes it.
- **Resetting:** changing `resetKey` closes the menu and the letter grid.

- [ ] **Step 1: Write the failing tests.**

```kotlin
@RunWith(AndroidJUnit4::class)
class AppGrouperTest { // Review Focus 3
    private val grouper = AppGrouper(Locale.ENGLISH)
    private fun labels(index: AppIndex) = index.sections.associate { s -> s.label to s.apps.map { it.label } }

    @Test fun groupsByFirstLetterInCollatorOrder() =
        assertEquals(mapOf("c" to listOf("calculator", "Camera"), "z" to listOf("Zoom")),
            labels(grouper.group(listOf(testApp("Zoom"), testApp("Camera"), testApp("calculator")))))

    @Test fun digitsAndSymbolsGoUnderHash() = // the Collator puts punctuation before digits
        assertEquals(listOf("@home", "7-Zip"), labels(grouper.group(listOf(testApp("7-Zip"), testApp("@home"))))["#"])

    @Test fun accentedLettersJoinTheirBaseLetter() =
        assertEquals(listOf("Échecs"), labels(grouper.group(listOf(testApp("Échecs"))))["e"])

    @Test fun otherScriptsGetTheirOwnGroupsAfterTheLatinLetters() {
        val index = grouper.group(listOf(testApp("Zoom"), testApp("हिन्दी"), testApp("Яндекс")))
        assertEquals(listOf("z", "я", "ह"), index.sections.map { it.label })
    }

    @Test fun theLetterGridListsEveryLetterAndMarksEmptyOnes() {
        val letters = grouper.group(listOf(testApp("Camera"))).letters
        assertEquals("#", letters.first().label)
        assertTrue(letters.single { it.label == "c" }.hasApps)
        assertFalse(letters.single { it.label == "a" }.hasApps)
        assertEquals(27, letters.size)
    }

    @Test fun sameNamedPersonalAndWorkAppsBothAppearPersonalFirst() {
        val personal = testApp("Camera"); val work = testApp("Camera", userSerial = 10)
        assertEquals(listOf(personal.key, work.key), grouper.group(listOf(work, personal)).sections.single().apps.map { it.key })
    }
}
```

`AppListViewModelTest` runs under Robolectric because `AppGrouper` uses ICU, with `MainDispatcherRule` and a `FakeAppCatalog`. It has three tests:
- **`appsInstalledInTheLastThreeDaysAreNew`:** with now = 10 days, an app installed at now − 2 days is in `newApps`, and one installed at now − 4 days isn't.
- **`pinCallsThePinCallback`:** the recorded keys equal `[camera.key]`.
- **`openingAPausedWorkAppRequestsUnpause`.**

`AppListScreenTest` uses Robolectric Compose with the real view model. It has three tests:
- **`longPressThenPinToStartCallsPin`.**
- **`pinToStartIsHiddenForPinnedApps`:** with `pinnedApps = flowOf(setOf(camera.key))`, there's no "pin to start" node.
- **`tappingAHeaderOpensTheGridAndJumps`:** using 40 apps across a–z, tap the "c" header, then tap "w" in the grid. The first "w" app is then displayed.

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:applist:testDebugUnitTest`
Expected: FAIL, because the classes don't exist.

- [ ] **Step 3: Implement `AppGrouper`, `AppListViewModel` and `AppListScreen`, with its menu and letter grid,** to the rules above.

- [ ] **Step 4: Run the tests again.**

Run: `./gradlew :feature:applist:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add feature/applist
git commit -m "Add the WP8.1-style app list with letter grid and context menu"
```

---

### Task 9: App search

**Files:**
- Create: `feature/search/src/main/kotlin/…/feature/search/SearchProvider.kt`, `SearchResult.kt`, `AppRanking.kt`, `AppSearchProvider.kt`, `SearchOpener.kt`, `SearchViewModel.kt`, `SearchScreen.kt`
- Test: `feature/search/src/test/kotlin/…/AppRankingTest.kt`, `SearchViewModelTest.kt`, `SearchScreenTest.kt`

**Interfaces:**
- Consumes: `AppCatalog` and `LauncherApp` (Task 5); design components (Task 2).
- Produces:

```kotlin
interface SearchProvider {                      // exactly as spec §3.2
    val id: String
    val title: String
    fun search(query: String): Flow<List<SearchResult>>
}
sealed interface SearchResult {
    val key: String
    data class App(val app: LauncherApp) : SearchResult { override val key get() = "app:" + app.key.flatten() }
}
fun rankApps(query: String, apps: List<LauncherApp>, lastLaunched: Map<AppKey, Long>,
             collator: Collator = Collator.getInstance()): List<LauncherApp>
class AppSearchProvider(catalog: AppCatalog) : SearchProvider     // id = "apps", title = "apps"
class SearchOpener(catalog: AppCatalog) { fun open(result: SearchResult) }  // paused work app → requestUnpause
class SearchViewModel(providers: List<SearchProvider>, opener: SearchOpener) : ViewModel() {
    val state: StateFlow<SearchState>
    fun onQueryChange(query: String)
    fun open(result: SearchResult)
    fun openFirst(): Boolean            // false when there are no results
}
data class SearchState(val query: String = "", val groups: List<SearchGroup> = emptyList())
data class SearchGroup(val providerId: String, val title: String, val results: List<SearchResult>)

@Composable fun SearchScreen(viewModel: SearchViewModel, icon: suspend (LauncherApp) -> AppIcon?,
                             onOpened: () -> Unit, modifier: Modifier = Modifier)
```

The rules (spec §6.2):
- **Normalising:** NFD, strip combining marks (`\p{Mn}`), then lowercase with `Locale.ROOT`. Trim the query; a blank query gives nothing.
- **Words** split on anything that isn't a letter or digit.
- **Initials** are the first letter of each word, plus every uppercase letter that follows a lowercase one. They're taken from the original label, then normalised: "WhatsApp" gives "wa".
- **Tiers:** 0 means the label starts with the query, 1 that a later word starts with it, 2 that the initials start with it, and 3 that the label contains it. Labels in no tier are left out.
- **Order:** tier first, then the most recently launched (never launched last), then the `Collator` label order, then `key.flatten()`.
- **The view model:**
  - `state` is shared with `SharingStarted.Eagerly`, so tests see results without collecting. `state.query` updates at once, but `groups` follow the query through `debounce(120)` and `flatMapLatest`.
  - Within that, all providers run together through `combine`, each starting from `emptyList()`.
  - Groups keep provider order, and groups with no results are left out.
- **The screen:**
  - A `BasicTextField` that takes focus and opens the keyboard as soon as it appears, with `ImeAction.Go`. Go calls `openFirst()` and then `onOpened()`.
  - Each group shows its lowercase title in `header` style, then rows that look and tilt like the app list's. Tapping a row calls `open` and then `onOpened()`.

- [ ] **Step 1: Write the failing tests.**

```kotlin
class AppRankingTest { // Review Focus 3
    private fun rank(q: String, vararg labels: String, launched: Map<String, Long> = emptyMap()): List<String> {
        val apps = labels.map { testApp(it) }
        return rankApps(q, apps, launched.mapKeys { (label, _) -> apps.single { it.label == label }.key }).map { it.label }
    }

    @Test fun tiersArePrefixThenWordStartThenInitialsThenContains() =
        assertEquals(listOf("Mail", "Maps", "Google Maps", "Gmail"), rank("ma", "Gmail", "Maps", "Google Maps", "Mail", "Clock"))

    @Test fun initialsFindCamelCaseNames() =
        assertEquals(listOf("WhatsApp", "Hardware"), rank("wa", "Hardware", "WhatsApp", "Twitter"))

    @Test fun tiesGoToTheMostRecentlyLaunched() =
        assertEquals(listOf("Camera", "Calendar", "Calculator"),
            rank("ca", "Calculator", "Calendar", "Camera", launched = mapOf("Camera" to 300L, "Calendar" to 200L)))

    @Test fun matchingIgnoresCaseAndAccents() =
        assertEquals(listOf("Café Bustelo"), rank("CAFE", "Café Bustelo", "Chrome"))

    @Test fun aBlankQueryFindsNothing() = assertEquals(emptyList<String>(), rank("  ", "Camera"))
}
```

`SearchViewModelTest` uses `MainDispatcherRule(StandardTestDispatcher())` and fake providers that count calls. It has three tests:
- **`waitsFor120msBeforeSearching`:** after `onQueryChange("ca")` and `advanceTimeBy(119)`, there are 0 calls. After `advanceTimeBy(2)`, there's 1.
- **`groupsFollowProviderOrderAndSkipEmptyOnes`:** with providers A (results arrive late), B (empty) and C (results), `groups.map { it.providerId }` is `["a", "c"]`.
- **`aNewQueryReplacesTheOldOne`:** type "c", wait 50 ms, type "ca", and let it settle. Only "ca" reached the providers.

`SearchScreenTest` uses Robolectric Compose and has one test, **`goOpensTheFirstResult`:** type "cam", then `performImeAction()`. After that, `catalog.launched == [camera.key]` and `onOpened` has been called.

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :feature:search:testDebugUnitTest`
Expected: FAIL, because the classes don't exist.

- [ ] **Step 3: Implement the search classes and screen** to the rules above.

- [ ] **Step 4: Run the tests again.**

Run: `./gradlew :feature:search:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add feature/search
git commit -m "Add app search with ranked, debounced results"
```

---

### Task 10: Home shell: pager, search overlay, Home presses and the Home role

**Files:**
- Create: `…/AppGraph.kt`, `…/home/HomeRoot.kt`, `…/home/HomeRole.kt`, `…/home/HomeAppBanner.kt` (all in `app/src/main/kotlin/…`)
- Modify: `…/LiquiWinApp.kt` (holds the `AppGraph`), `…/HomeActivity.kt` (shows `HomeRoot` and turns Home intents into Home presses)
- Test: `app/src/test/kotlin/…/home/HomeRootTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 2–9.
- Produces:

```kotlin
class AppGraph(context: Context) {   // lazy singletons; scope = SupervisorJob() + Dispatchers.Default
    val tileStore: TileStore
    val catalog: AppCatalog
    val homeRole: HomeRole
}
class HomeRole(context: Context) {
    fun isHeld(): Boolean                    // RoleManager.isRoleHeld(ROLE_HOME)
    fun requestIntent(): Intent?             // createRequestRoleIntent(ROLE_HOME), or null if the role isn't available
    fun settingsIntent(): Intent             // Settings.ACTION_HOME_SETTINGS
}
class HomeDependencies(val tileStore: TileStore, val catalog: AppCatalog, val isDefaultHome: () -> Boolean,
                       val onSetHome: () -> Unit, val onOpenHomeSettings: () -> Unit)
enum class HomePress { FROM_APP, WHILE_ON_HOME }
@Composable fun HomeRoot(deps: HomeDependencies, homePresses: Flow<HomePress>, modifier: Modifier = Modifier)
```

The rules:
- **Layout:**
  - `HomeRoot` builds `StartViewModel(gridWidth = StyleFacets.Wp81Basic.gridUnits)`, `AppListViewModel` and `SearchViewModel` with `viewModel { … }` initializers.
  - The app list gets `pinnedApps = startViewModel.pinnedApps` and `onPinToStart = startViewModel::pin`. Search gets `listOf(AppSearchProvider(catalog))`.
  - A `HorizontalPager` with two pages (0 is the start screen, 1 the app list) and `beyondViewportPageCount = 1`.
  - The app list's search box opens `SearchScreen` over everything.
- **Back:** with search open, it closes search. On page 1, it animates to page 0. On the start screen, edit mode handles Back itself, and otherwise Back does nothing.
- **Home presses:**
  - `HomeActivity.onNewIntent` turns MAIN + HOME intents into presses. It's `WHILE_ON_HOME` when `hasWindowFocus()` and the intent lacks `FLAG_ACTIVITY_BROUGHT_TO_FRONT`, and `FROM_APP` otherwise.
  - Both close search, exit edit mode, bump the app list's `resetKey` and jump to page 0 without animation.
  - `WHILE_ON_HOME` also animates the start screen's scroll to the top.
- **Home-app banner:**
  - When `isDefaultHome()` is false (checked on every `ON_RESUME`), a slim chrome-coloured banner above the first row reads "liquiwin isn't your home app yet". It has two buttons: **set**, which calls `onSetHome`, and **settings**, which calls `onOpenHomeSettings`.
  - `HomeActivity` provides `onSetHome`, which launches `homeRole.requestIntent()` through `registerForActivityResult(StartActivityForResult())`, or the settings intent when the role isn't available.
- **System bars:** `HomeActivity` calls `enableEdgeToEdge` with `SystemBarStyle.dark(Color.TRANSPARENT)` for both bars, and wraps everything in `LiquiWinTheme`.

- [ ] **Step 1: Write the failing tests.** Use Robolectric Compose with `createAndroidComposeRule<ComponentActivity>()`, pressing Back through `compose.activity.onBackPressedDispatcher`. Show `HomeRoot` over `FakeTileStore`, `FakeAppCatalog` (Camera, Maps and Mail), `isDefaultHome = { true }` and a `MutableSharedFlow<HomePress>`.

```kotlin
@RunWith(AndroidJUnit4::class)
class HomeRootTest { // Review Focus 5
    @Test fun homeWhileOnHomeReturnsToTheTopOfStart()      // pin 30 apps, scroll the start screen down, swipe left to the app list,
                                                           //   emit WHILE_ON_HOME → the first tile ("Camera") is displayed
    @Test fun homeFromAnAppClosesSearchEditModeAndMenus()  // open search from the app list, emit FROM_APP → no search field,
                                                           //   page 0 shown; long-press a tile, emit FROM_APP → no "done";
                                                           //   open an app list context menu, emit FROM_APP, swipe left → no "pin to start"
    @Test fun backFromTheAppListReturnsToStart()           // swipe left, press Back → the start screen's hint is displayed
    @Test fun pinFromTheAppListAppearsOnStart()            // swipe left, long-press "Maps", click "pin to start", swipe right → tile "Maps" exists
    @Test fun theBannerShowsOnlyWhenNotTheHomeApp()        // isDefaultHome = { false } → banner text exists, and "set" calls onSetHome
}
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :app:testDebugUnitTest --tests "*HomeRootTest"`
Expected: FAIL, because `HomeRoot` doesn't exist.

- [ ] **Step 3: Implement `AppGraph`, `HomeRole`, `HomeRoot` and the banner, and update `LiquiWinApp` and `HomeActivity`,** to the rules above.

- [ ] **Step 4: Run all the tests, then build.**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**

```bash
git add app
git commit -m "Wire the home shell: pager, search, Home presses and the Home role"
```

---

### Task 11: Crash safe mode

**Files:**
- Create: `app/src/main/kotlin/…/crash/CrashGuard.kt`, `…/crash/PrefsCrashStore.kt`, `…/safemode/SafeModeScreen.kt`
- Modify: `…/LiquiWinApp.kt` (installs the guard first), `…/HomeActivity.kt` (chooses between safe mode and home, and adds the debug crash trigger)
- Test: `app/src/test/kotlin/…/crash/CrashGuardTest.kt`, `app/src/test/kotlin/…/safemode/SafeModeTest.kt`

**Interfaces:**
- Consumes: `LiquiWinTheme`, `PageTitle` and `WpText` (Task 2); `HomeActivity` (Task 10).
- Produces:

```kotlin
interface CrashStore { var earlyCrashes: Int }
class PrefsCrashStore(context: Context) : CrashStore   // SharedPreferences "crash_guard", read on every access, written with commit()
class CrashGuard(private val store: CrashStore, private val uptimeMillis: () -> Long) {
    fun shouldStartInSafeMode(): Boolean   // earlyCrashes >= 3
    fun onUncaughtException()              // counts the crash only when uptime < 60_000 ms
    fun onSurvivedStartWindow()            // resets the count
    fun exitSafeMode()                     // resets the count
}
data class SafeModeApp(val label: String, val component: ComponentName, val user: UserHandle)
@Composable fun SafeModeScreen(apps: List<SafeModeApp>, onOpen: (SafeModeApp) -> Unit, onExit: () -> Unit)
```

The rules (spec §9):
- **Counting:** "3 crashes within 60 s of start" means three processes in a row that each crashed within 60 s of starting. A process that survives 60 s resets the count.
- **Installing:** `LiquiWinApp.onCreate` creates the guard before anything else, using `SystemClock.elapsedRealtime() − Process.getStartElapsedRealtime()` as the uptime. It wraps the default uncaught-exception handler: count the crash, then call the previous handler. It also posts `onSurvivedStartWindow` for the 60 s mark.
- **Safe mode:** when `shouldStartInSafeMode()` is true, `HomeActivity` never touches `AppGraph`. It lists apps straight from `LauncherApps` on a background thread, with no database and no icon cache, and shows `SafeModeScreen`:
  - `PageTitle("safe mode")`
  - the text "liquiwin closed unexpectedly 3 times in a row, so it's showing a plain app list. your tiles are safe."
  - an **exit safe mode** button, which calls `exitSafeMode()` and then `recreate()`
  - the hint "to use a different home app: settings › apps › choose default apps › home app"
  - a plain list of app labels, where a tap launches the app
- **Debug crash trigger:** in debug builds only, `HomeActivity` throws `IllegalStateException("debug crash")` when an intent reaching `onCreate` or `onNewIntent` has the boolean extra `debug_crash`.

- [ ] **Step 1: Write the failing tests.**

```kotlin
class CrashGuardTest {
    private val store = object : CrashStore { override var earlyCrashes = 0 }
    private var uptime = 5_000L
    private val guard = CrashGuard(store) { uptime }

    @Test fun threeEarlyCrashesStartSafeMode() { repeat(3) { guard.onUncaughtException() }; assertTrue(guard.shouldStartInSafeMode()) }
    @Test fun twoEarlyCrashesDoNot() { repeat(2) { guard.onUncaughtException() }; assertFalse(guard.shouldStartInSafeMode()) }
    @Test fun crashesAfterTheFirstMinuteDoNotCount() { uptime = 61_000; repeat(3) { guard.onUncaughtException() }; assertFalse(guard.shouldStartInSafeMode()) }
    @Test fun survivingTheFirstMinuteResetsTheCount() {
        repeat(2) { guard.onUncaughtException() }; guard.onSurvivedStartWindow(); guard.onUncaughtException()
        assertFalse(guard.shouldStartInSafeMode())
    }
    @Test fun exitingSafeModeResetsTheCount() { repeat(3) { guard.onUncaughtException() }; guard.exitSafeMode(); assertFalse(guard.shouldStartInSafeMode()) }
}
```

`SafeModeTest` uses Robolectric with `createEmptyComposeRule()`. It sets `earlyCrashes = 3` in the `crash_guard` preferences, then launches `HomeActivity` with `ActivityScenario`. "exit safe mode" should exist. After clicking it, the start screen's hint should exist and `earlyCrashes` should be 0.

- [ ] **Step 2: Run them and watch them fail.**

Run: `./gradlew :app:testDebugUnitTest --tests "*CrashGuardTest" --tests "*SafeModeTest"`
Expected: FAIL, because `CrashGuard` doesn't exist.

- [ ] **Step 3: Implement `CrashGuard`, `PrefsCrashStore` and `SafeModeScreen`, and update `LiquiWinApp` and `HomeActivity`,** to the rules above. StrictMode will log the one small preferences read at start-up. Leave it, or wrap that read in `StrictMode.allowThreadDiskReads()`.

- [ ] **Step 4: Run all the tests.**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add app
git commit -m "Add crash safe mode"
```

---

### Task 12: Stage 1 check on the Galaxy

**Files:**
- Modify: `docs/reference/windows-phone-ui-reference.md` (record the values chosen)

- [ ] **Step 1: Run the full test suite and a release build.**

Run: `./gradlew testDebugUnitTest :app:assembleRelease`
Expected: BUILD SUCCESSFUL, with the APK at `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 2: Install it on the Galaxy.**

Run: `/c/Android/platform-tools/adb -s RZGYB0137YB install -r app/build/outputs/apk/release/app-release.apk`
Expected: `Success`.

- [ ] **Step 3: The owner sets LiquiWin as Home** with the banner's **set** button, then confirms in Android's dialog. This step is the owner's to do, not the agent's.

- [ ] **Step 4: Go through the checklist with the owner.** This is the stage 1 part of spec §10's Galaxy checklist. Note anything that fails as a follow-up fix before closing the stage.
  - Pin six apps from the app list. Move one, resize one through all four sizes, then unpin one.
  - Tilt: the edge under the finger moves away. This confirms the signs chosen in Task 2.
  - App list: the letter grid works, "new" shows on a recent install, uninstall opens Android's dialog, and app info opens.
  - Search: "wa" finds WhatsApp, and Go opens the first result.
  - Home from an app returns to start. Home on start scrolls to the top. Back from the app list goes to start.
  - Work profile, if there is one: the badge shows, and pausing work apps greys their tiles out.
  - Gesture navigation works. Expect the longer hold for recent apps (spec §1.5).
  - Restart the phone: the tiles are still there.
  - Safe mode: install a debug build (`./gradlew :app:installDebug`), then run `adb -s RZGYB0137YB shell am force-stop io.github.b1tsagar.liquiwin` so the crashes that follow each come within a minute of a fresh start. Run `adb -s RZGYB0137YB shell am start -n io.github.b1tsagar.liquiwin/.HomeActivity --ez debug_crash true` three times, pressing Home after each crash. Safe mode should appear, and **exit safe mode** should bring the start screen back. Reinstall the release build afterwards.
  - Compare the app list's letter headers and the edit-mode buttons with WP8.1 screenshots.

- [ ] **Step 5: Record the chosen values.** Add a "Values chosen in stage 1" list to the reference doc covering:
  - tile label size and insets
  - list row and icon sizes, and the header look
  - chrome colour `#1F1F1F`
  - margin 24 and gap 12 at six units
  - tilt depression scale 0.025
  - anything changed during Step 4

- [ ] **Step 6: Commit.**

```bash
git add docs/reference/windows-phone-ui-reference.md
git commit -m "Record the values chosen in stage 1"
```
