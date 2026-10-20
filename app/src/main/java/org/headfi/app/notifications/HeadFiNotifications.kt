package org.headfi.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.headfi.app.MainActivity
import org.headfi.app.R

/**
 * Native Android notifications for unread Head-Fi alerts and private messages
 * raised by the background poller.
 */
object HeadFiNotifications {

    const val CHANNEL_UNREAD = "headfi_unread"

    const val ID_ALERTS = 1001
    const val ID_CONVERSATIONS = 1002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_UNREAD) != null) return

        val channel = NotificationChannel(
            CHANNEL_UNREAD,
            "Unread Alerts & Messages",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "New Head-Fi alerts and private conversations while the app is closed"
            setShowBadge(true)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Posts an "unread" notification that deep-links straight into the relevant
     * Head-Fi section when tapped.
     */
    fun notifyUnread(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        deepLinkUrl: String
    ) {
        ensureChannels(context)
        if (!areNotificationsEnabled(context)) return

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUrl)).apply {
            setClass(context, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_UNREAD)
            .setSmallIcon(R.drawable.ic_stat_headfi)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun clear(context: Context, notificationId: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }
    }
}
