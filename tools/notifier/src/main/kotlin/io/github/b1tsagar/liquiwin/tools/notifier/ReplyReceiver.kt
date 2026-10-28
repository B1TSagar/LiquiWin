package io.github.b1tsagar.liquiwin.tools.notifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

/** Takes a reply typed into a chat's notification and adds it to the chat, with its echo. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Chats.REPLY) ?: return
        Chats.reply(context, intent.getStringExtra(Chats.CHAT) ?: return, text.toString())
    }
}
