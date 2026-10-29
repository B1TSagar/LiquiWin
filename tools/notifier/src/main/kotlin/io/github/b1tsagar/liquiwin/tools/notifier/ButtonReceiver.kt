package io.github.b1tsagar.liquiwin.tools.notifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** A missed call's button. It only logs which was pressed, `button: <label>` under the tag "notifier". */
class ButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("notifier", "button: ${intent.getStringExtra(Chats.BUTTON)}")
    }
}
