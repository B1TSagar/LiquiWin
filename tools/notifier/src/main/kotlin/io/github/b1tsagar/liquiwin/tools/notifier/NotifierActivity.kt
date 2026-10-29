package io.github.b1tsagar.liquiwin.tools.notifier

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** The notifier's one screen: a button that posts a sample of each chat, under "chat <name>" when a notification opened it. */
class NotifierActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        intent.getStringExtra(Chats.CHAT)?.let { chat ->
            Chats.cancel(this, chat)
            screen.addView(TextView(this).apply { text = "chat $chat"; textSize = 24f })
        }
        for (chat in listOf("alex", "family", "photo", "snap", "call", "picture")) {
            screen.addView(Button(this).apply { text = chat; setOnClickListener { Chats.post(context, chat, "hi") } })
        }
        setContentView(screen)
    }
}
