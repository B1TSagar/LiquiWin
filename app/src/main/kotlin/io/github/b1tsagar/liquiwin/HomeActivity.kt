package io.github.b1tsagar.liquiwin

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.home.HomeDependencies
import io.github.b1tsagar.liquiwin.home.HomePress
import io.github.b1tsagar.liquiwin.home.HomeRoot
import io.github.b1tsagar.liquiwin.safemode.SafeModeApp
import io.github.b1tsagar.liquiwin.safemode.SafeModeScreen
import io.github.b1tsagar.liquiwin.safemode.launchSafeModeApp
import io.github.b1tsagar.liquiwin.safemode.loadSafeModeApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext

class HomeActivity : ComponentActivity() {
    private val liquiWin get() = application as LiquiWinApp
    private val graph get() = liquiWin.graph
    private val homePresses = MutableSharedFlow<HomePress>(extraBufferCapacity = 1)

    // The banner re-checks the role on resume, so the result itself isn't needed.
    private val setHome = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        crashIfAsked(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (liquiWin.crashGuard.shouldStartInSafeMode()) {
            // Never touches AppGraph: whatever crashed may live there.
            setContent {
                LiquiWinTheme {
                    val apps by produceState(emptyList<SafeModeApp>()) { value = withContext(Dispatchers.IO) { loadSafeModeApps(applicationContext) } }
                    SafeModeScreen(
                        apps = apps,
                        onOpen = { launchSafeModeApp(this@HomeActivity, it) },
                        onExit = {
                            liquiWin.crashGuard.exitSafeMode()
                            recreate()
                        },
                    )
                }
            }
            return
        }
        val deps = HomeDependencies(
            tileStore = graph.tileStore,
            catalog = graph.catalog,
            isDefaultHome = graph.homeRole::isHeld,
            onSetHome = { setHome.launch(graph.homeRole.requestIntent() ?: graph.homeRole.settingsIntent()) },
            onOpenHomeSettings = { startActivity(graph.homeRole.settingsIntent()) },
        )
        setContent { LiquiWinTheme { HomeRoot(deps, homePresses) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        crashIfAsked(intent)
        if (intent.action != Intent.ACTION_MAIN || !intent.hasCategory(Intent.CATEGORY_HOME)) return
        val whileOnHome = hasWindowFocus() && intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0
        homePresses.tryEmit(if (whileOnHome) HomePress.WHILE_ON_HOME else HomePress.FROM_APP)
    }

    // Debug builds only: lets a check on the phone trigger the crash loop that safe mode answers.
    private fun crashIfAsked(intent: Intent?) {
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("debug_crash", false) == true) throw IllegalStateException("debug crash")
    }
}
