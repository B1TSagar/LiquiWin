package io.github.b1tsagar.liquiwin.tools.notifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Posts from the shell: `--es chat` is alex, family, photo, snap, call, picture, late or gif, and `--es text` the message, "hi" if left out. */
class PostReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Chats.post(context, intent.getStringExtra(Chats.CHAT) ?: return, intent.getStringExtra("text") ?: "hi")
    }
}
