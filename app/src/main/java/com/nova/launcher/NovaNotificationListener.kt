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

/**
 * Reads notifications only after the user explicitly enables notification-listener access in Android Settings.
 * It does not create an overlay or display anything over other applications.
 */
class NovaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        activeNotifications.orEmpty()
            .filterNot { it.packageName == packageName }
            .maxByOrNull { it.postTime }
            ?.let(::updateFromNotification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != packageName) updateFromNotification(sbn)
    }

    private fun updateFromNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() } ?: sbn.packageName
        val detail = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
            ?.takeIf { it.isNotBlank() } ?: "New notification"

        val actions = notification.actions.orEmpty().take(3).mapNotNull { action ->
            action.actionIntent?.let { pendingIntent ->
                IslandState.IslandAction(
                    action.title?.toString()?.takeIf { it.isNotBlank() } ?: "Action",
                    pendingIntent
                )
            }
        }
        IslandState.title = title.take(60)
        IslandState.detail = detail.take(140)
        IslandState.actions = actions
        sendBroadcast(Intent(IslandState.ACTION_UPDATE).setPackage(packageName))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Keep the last notification visible so the user has time to read it.
    }
}
