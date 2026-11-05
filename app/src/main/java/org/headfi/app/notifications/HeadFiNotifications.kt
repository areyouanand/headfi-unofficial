package org.headfi.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
    const val ID_WATCHED = 1003
    const val ID_SUMMARY = 1000

    /** All unread notifications share a group so Android can summarise them. */
    const val GROUP_UNREAD = "headfi_unread_group"

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
     * The runtime permission check lint looks for. `areNotificationsEnabled`
     * alone is not enough on Android 13+, where POST_NOTIFICATIONS is a runtime
     * grant even though the channel also exists.
     */
    private fun canPost(context: Context): Boolean {
        if (!areNotificationsEnabled(context)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Posts an "unread" notification that deep-links straight into the relevant
     * Head-Fi section when tapped.
     */
    @SuppressLint("MissingPermission") // guarded by canPost()
    fun notifyUnread(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        deepLinkUrl: String
    ) {
        ensureChannels(context)
        if (!canPost(context)) return

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
            .setGroup(GROUP_UNREAD)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    /**
     * Summary for a run that posted two or more unread notifications. Without
     * this, Android groups the children under a generic "Other" heading.
     */
    @SuppressLint("MissingPermission") // guarded by canPost()
    fun notifySummary(context: Context, count: Int) {
        ensureChannels(context)
        if (!canPost(context)) return

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.head-fi.org/")).apply {
            setClass(context, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ID_SUMMARY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_UNREAD)
            .setSmallIcon(R.drawable.ic_stat_headfi)
            .setContentTitle("New activity on Head-Fi")
            .setContentText("$count unread notifications")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroup(GROUP_UNREAD)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(ID_SUMMARY, notification)
        }
    }

    fun clear(context: Context, notificationId: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }
    }
}
