package com.nova.launcher

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Shared, in-process state displayed by the launcher's embedded Dynamic Island. */
object IslandState {
    data class IslandAction(val label: String, val intent: PendingIntent)

    @Volatile var title: String = "NOVA ISLAND"
    @Volatile var detail: String = "Tap to expand • Notification access optional"
    @Volatile var actions: List<IslandAction> = emptyList()
    const val ACTION_UPDATE = "com.nova.launcher.ISLAND_UPDATE"
}

/** Reads notifications only after the user explicitly enables notification access in Android Settings. */
class NovaNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() } ?: sbn.packageName
        val detail = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
            ?.takeIf { it.isNotBlank() } ?: "New notification"

        val actions = notification.actions.orEmpty().take(3).mapNotNull { action ->
            action.actionIntent?.let { pendingIntent ->
                IslandState.IslandAction(action.title?.toString()?.takeIf(String::isNotBlank) ?: "Open", pendingIntent)
            }
        }
        IslandState.title = title.take(60)
        IslandState.detail = detail.take(140)
        IslandState.actions = actions
        sendBroadcast(Intent(IslandState.ACTION_UPDATE).setPackage(packageName))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Leave the latest notification visible; Android may remove it before the user sees the island.
    }
}
