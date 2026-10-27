package io.github.b1tsagar.liquiwin

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.home.HomeDependencies
import io.github.b1tsagar.liquiwin.home.HomePress
import io.github.b1tsagar.liquiwin.home.HomeRoot
import kotlinx.coroutines.flow.MutableSharedFlow

class HomeActivity : ComponentActivity() {
    private val graph get() = (application as LiquiWinApp).graph
    private val homePresses = MutableSharedFlow<HomePress>(extraBufferCapacity = 1)

    // The banner re-checks the role on resume, so the result itself isn't needed.
    private val setHome = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
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
        if (intent.action != Intent.ACTION_MAIN || !intent.hasCategory(Intent.CATEGORY_HOME)) return
        val whileOnHome = hasWindowFocus() && intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0
        homePresses.tryEmit(if (whileOnHome) HomePress.WHILE_ON_HOME else HomePress.FROM_APP)
    }
}
