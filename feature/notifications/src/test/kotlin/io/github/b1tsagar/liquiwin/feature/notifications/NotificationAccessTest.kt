package io.github.b1tsagar.liquiwin.feature.notifications

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class NotificationAccessTest {
    @Test fun opensTheDetailPageForLiquiWin() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val access = NotificationAccess(context)
        access.openSettings(context)
        val opened = shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, opened.action)
        assertEquals(access.component.flattenToString(), opened.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME))
    }
}
