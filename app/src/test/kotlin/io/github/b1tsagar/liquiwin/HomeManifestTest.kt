package io.github.b1tsagar.liquiwin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

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
