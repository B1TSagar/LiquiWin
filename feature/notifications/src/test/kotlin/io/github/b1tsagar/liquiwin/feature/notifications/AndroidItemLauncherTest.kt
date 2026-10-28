package io.github.b1tsagar.liquiwin.feature.notifications

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AndroidItemLauncherTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val launcher = AndroidItemLauncher(context)

    // Explicit, because Android 14+ refuses a mutable PendingIntent around an implicit intent.
    private fun action(input: RemoteInput) = Notification.Action.Builder(
        null, "Reply", PendingIntent.getBroadcast(context, 0, Intent("reply").setPackage(context.packageName), PendingIntent.FLAG_MUTABLE),
    ).addRemoteInput(input).build()

    @Test fun aReplyFillsTheRemoteInputAsTypedTextAndSendsTheAction() {
        assertTrue(launcher.reply(action(RemoteInput.Builder("reply").build()), "on my way"))
        val sent = shadowOf(context).broadcastIntents.single()
        assertEquals("on my way", RemoteInput.getResultsFromIntent(sent)?.getCharSequence("reply")?.toString())
        assertEquals(RemoteInput.SOURCE_FREE_FORM_INPUT, RemoteInput.getResultsSource(sent))
    }

    // Review Focus 2
    @Test fun anActionThatTakesNoTextOrWasCancelledFails() {
        val choicesOnly = RemoteInput.Builder("pick").setChoices(arrayOf("yes")).setAllowFreeFormInput(false).build()
        assertFalse(launcher.reply(action(choicesOnly), "hi"))
        val cancelled = action(RemoteInput.Builder("reply").build()).also { it.actionIntent.cancel() }
        assertFalse(launcher.reply(cancelled, "hi"))
        assertEquals(emptyList<Intent>(), shadowOf(context).broadcastIntents)
    }
}
