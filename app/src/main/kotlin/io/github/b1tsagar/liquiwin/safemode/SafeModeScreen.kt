package io.github.b1tsagar.liquiwin.safemode

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.b1tsagar.liquiwin.core.design.components.PageTitle
import io.github.b1tsagar.liquiwin.core.design.components.WpText
import io.github.b1tsagar.liquiwin.core.design.style.LocalStyleFacets
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinColors
import io.github.b1tsagar.liquiwin.core.design.theme.LocalLiquiWinType
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits
import java.text.Collator

data class SafeModeApp(val label: String, val component: ComponentName, val user: UserHandle)

/** After a crash loop: a plain list of apps that launch on a tap, and the way back to the normal home screen. */
@Composable
fun SafeModeScreen(apps: List<SafeModeApp>, onOpen: (SafeModeApp) -> Unit, onExit: () -> Unit) {
    val units = LocalWpUnits.current
    val colors = LocalLiquiWinColors.current
    val type = LocalLiquiWinType.current
    val side = units.dp(LocalStyleFacets.current.sideMarginWpPx)
    val bars = WindowInsets.systemBars.asPaddingValues()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = side, end = side, top = bars.calculateTopPadding(), bottom = bars.calculateBottomPadding() + side),
        verticalArrangement = Arrangement.spacedBy(units.dp(12f)),
    ) {
        item { PageTitle("safe mode") }
        item { WpText("liquiwin closed unexpectedly 3 times in a row, so it's showing a plain app list. your tiles are safe.", type.body) }
        item {
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .border(2.dp, Color.White)
                    .clickable(role = Role.Button, onClick = onExit)
                    .padding(horizontal = units.dp(12f), vertical = units.dp(8f)),
                contentAlignment = Alignment.Center,
            ) {
                WpText("exit safe mode", type.body)
            }
        }
        item { WpText("to use a different home app: settings › apps › choose default apps › home app", type.small, color = colors.subtle) }
        items(apps, key = { "${it.component.flattenToString()}@${it.user.hashCode()}" }) { app ->
            WpText(app.label, type.listItem, Modifier.fillMaxWidth().clickable { onOpen(app) }.padding(vertical = units.dp(8f)), maxLines = 1)
        }
    }
}

/** Every profile's launcher activities, straight from LauncherApps: no database and no icon cache. Call off the main thread. */
fun loadSafeModeApps(context: Context): List<SafeModeApp> {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val users = context.getSystemService(UserManager::class.java).userProfiles
    val collator = Collator.getInstance()
    return users.flatMap { user -> launcherApps.getActivityList(null, user).map { SafeModeApp(it.label.toString(), it.componentName, user) } }
        .sortedWith(compareBy(collator) { it.label })
}

/** Launches [app], logging instead of crashing when it can't: safe mode must not crash. */
fun launchSafeModeApp(context: Context, app: SafeModeApp) {
    try {
        context.getSystemService(LauncherApps::class.java).startMainActivity(app.component, app.user, null, null)
    } catch (e: RuntimeException) {
        Log.w("SafeMode", "Can't launch ${app.component.flattenToShortString()}", e)
    }
}
