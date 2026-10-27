package io.github.b1tsagar.liquiwin.home

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Whether LiquiWin is the default Home app, and the ways to make it one. */
class HomeRole(context: Context) {
    private val roles = context.getSystemService(RoleManager::class.java)

    fun isHeld(): Boolean = roles.isRoleHeld(RoleManager.ROLE_HOME)

    /** The system's "set as Home app" request, or null when the role isn't available on this device. */
    fun requestIntent(): Intent? =
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME)) roles.createRequestRoleIntent(RoleManager.ROLE_HOME) else null

    fun settingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)
}
