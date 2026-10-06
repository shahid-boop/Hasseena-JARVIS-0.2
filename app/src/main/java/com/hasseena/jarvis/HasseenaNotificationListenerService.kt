package com.hasseena.jarvis

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

class HasseenaNotificationListenerService : NotificationListenerService() {
    companion object {
        private val recent = CopyOnWriteArrayList<String>()
        fun latest(limit: Int = 8): String = recent.takeLast(limit).reversed().joinToString("\n").ifBlank { "کوئی notification محفوظ نہیں ہے۔" }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title").orEmpty()
        val body = extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return
        recent.add("$title: $body".trim(':', ' ', '\n'))
        while (recent.size > 40) recent.removeAt(0)
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}
