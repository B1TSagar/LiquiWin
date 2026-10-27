package io.github.b1tsagar.liquiwin.core.data.apps

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** [AppSource] over `LauncherApps`: lists every profile's launcher activities without QUERY_ALL_PACKAGES. */
class LauncherAppsSource(private val context: Context) : AppSource {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    // From the last activity lists read, for icons.
    private val infos = ConcurrentHashMap<AppKey, LauncherActivityInfo>()

    override suspend fun profiles(): List<AppProfile> = userManager.userProfiles.map { user ->
        AppProfile(
            userSerial = userManager.getSerialNumberForUser(user),
            isWork = user != Process.myUserHandle(),
            isPaused = userManager.isQuietModeEnabled(user),
        )
    }

    override suspend fun activities(profile: AppProfile): List<AppActivity> {
        val user = userManager.getUserForSerialNumber(profile.userSerial) ?: return emptyList()
        val list = try {
            launcherApps.getActivityList(null, user)
        } catch (e: SecurityException) { // the profile went away after profiles() listed it
            Log.w(TAG, "Can't list the apps of profile ${profile.userSerial}", e)
            return emptyList()
        }
        val read = list.associateBy { AppKey(it.componentName.packageName, it.componentName.className, profile.userSerial) }
        infos.keys.removeAll { it.userSerial == profile.userSerial && it !in read }
        infos.putAll(read)
        return read.map { (key, info) -> AppActivity(key, info.label.toString(), info.firstInstallTime, info.applicationInfo.sourceDir) }
    }

    override fun changes(): Flow<Unit> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageRemoved(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageChanged(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) { trySend(Unit) }
            override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) { trySend(Unit) }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { trySend(Unit) }
        }
        val profileActions = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        ContextCompat.registerReceiver(context, receiver, profileActions, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose {
            launcherApps.unregisterCallback(callback)
            context.unregisterReceiver(receiver)
        }
    }

    override fun loadIcon(key: AppKey, densityDpi: Int): Drawable? = (infos[key] ?: resolve(key))?.getIcon(densityDpi)

    override fun launch(key: AppKey, sourceBounds: Rect?, options: Bundle?) {
        val user = user(key.userSerial) ?: return
        try {
            launcherApps.startMainActivity(key.component(), user, sourceBounds, options)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Can't launch ${key.flatten()}", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Can't launch ${key.flatten()}", e)
        }
    }

    override fun openAppInfo(key: AppKey) {
        val user = user(key.userSerial) ?: return
        launcherApps.startAppDetailsActivity(key.component(), user, null, null)
    }

    override fun uninstall(key: AppKey) {
        val user = user(key.userSerial) ?: return
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.packageName, null))
                .putExtra(Intent.EXTRA_USER, user)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun requestUnpause(userSerial: Long) {
        val user = user(userSerial) ?: return
        try {
            userManager.requestQuietModeEnabled(false, user)
        } catch (e: SecurityException) { // LiquiWin isn't the default Home app
            Log.w(TAG, "Can't unpause profile $userSerial", e)
        }
    }

    // An app the cached list shows before the first activity list is read.
    private fun resolve(key: AppKey): LauncherActivityInfo? {
        val user = user(key.userSerial) ?: return null
        return launcherApps.resolveActivity(Intent(Intent.ACTION_MAIN).setComponent(key.component()), user)
    }

    private fun user(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    private fun AppKey.component() = ComponentName(packageName, className)

    private companion object {
        const val TAG = "LauncherAppsSource"
    }
}
