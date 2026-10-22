package org.headfi.app.notifications

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.headfi.app.data.SessionPreferencesRepository
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Periodically re-checks Head-Fi for unread alerts and private conversations
 * using the persisted `xf_user` session cookie, and raises a native Android
 * notification when the count has grown since the last run.
 *
 * Fetching the front page also keeps the XenForo session warm (the WebView
 * equivalent of hitting `/login/keep-alive`), so the "stay logged in across
 * reboots" guarantee keeps holding even when the app is closed for days.
 */
class UnreadPollWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "HeadFiUnreadPoll"
        private const val POLL_URL = "https://www.head-fi.org/"
        const val INPUT_MANUAL = "manual"

        /** Enough to cover `XF.config`, which lives in the document head. */
        private const val MAX_HTML_CHARS = 400_000

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    /**
     * A `null` value means "no signal on the page" — e.g. we were served a
     * Cloudflare challenge or the login page. Those runs are ignored rather than
     * being treated as "zero unread", which would wrongly clear notifications.
     */
    private data class UnreadCounts(val alerts: Int?, val conversations: Int?)

    override suspend fun doWork(): Result {
        val repository = SessionPreferencesRepository(applicationContext)

        val preferences = repository.preferencesFlow.first()
        if (!preferences.unreadNotificationsEnabled) {
            return Result.success()
        }
        if (!repository.hasStoredSession()) {
            return Result.success()
        }

        val cookieHeader = repository.cookieHeader() ?: return Result.success()

        val counts = runCatching { fetchUnreadCounts(cookieHeader) }
            .onFailure { Log.w(TAG, "Unread poll failed", it) }
            .getOrNull() ?: return Result.retry()

        // The app is already on screen and shows the same badges, so skip the
        // system notification. lastNotified is left alone so anything still unread
        // is announced once the app goes to the background. A manual check always
        // reports, because it is started from the settings sheet.
        val isManual = inputData.getBoolean(INPUT_MANUAL, false)
        val appInForeground = ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
        if (appInForeground && !isManual) {
            return Result.success()
        }

        val (lastAlerts, lastConversations) = repository.lastNotifiedCounts()

        counts.alerts?.let { alerts ->
            when {
                alerts > lastAlerts && alerts > 0 -> HeadFiNotifications.notifyUnread(
                    context = applicationContext,
                    notificationId = HeadFiNotifications.ID_ALERTS,
                    title = if (alerts == 1) "New Head-Fi alert" else "New Head-Fi alerts",
                    body = if (alerts == 1) {
                        "You have 1 unread alert on Head-Fi."
                    } else {
                        "You have $alerts unread alerts on Head-Fi."
                    },
                    deepLinkUrl = "https://www.head-fi.org/account/alerts"
                )

                alerts == 0 -> HeadFiNotifications.clear(
                    applicationContext,
                    HeadFiNotifications.ID_ALERTS
                )
            }
        }

        counts.conversations?.let { conversations ->
            when {
                conversations > lastConversations && conversations > 0 ->
                    HeadFiNotifications.notifyUnread(
                        context = applicationContext,
                        notificationId = HeadFiNotifications.ID_CONVERSATIONS,
                        title = if (conversations == 1) {
                            "New Head-Fi message"
                        } else {
                            "New Head-Fi messages"
                        },
                        body = if (conversations == 1) {
                            "You have 1 unread conversation on Head-Fi."
                        } else {
                            "You have $conversations unread conversations on Head-Fi."
                        },
                        deepLinkUrl = "https://www.head-fi.org/conversations/"
                    )

                conversations == 0 -> HeadFiNotifications.clear(
                    applicationContext,
                    HeadFiNotifications.ID_CONVERSATIONS
                )
            }
        }

        repository.updateLastNotified(counts.alerts, counts.conversations)
        return Result.success()
    }

    private fun fetchUnreadCounts(cookieHeader: String): UnreadCounts {
        val connection = (URL(POLL_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Cookie", cookieHeader)
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }

        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Unexpected response ${connection.responseCode}")
            }

            val html = StringBuilder()
            connection.inputStream.bufferedReader().use { reader: BufferedReader ->
                val buffer = CharArray(8_192)
                var read = reader.read(buffer)
                while (read > 0 && html.length < MAX_HTML_CHARS) {
                    html.append(buffer, 0, read)
                    read = reader.read(buffer)
                }
            }

            val page = html.toString()
            return UnreadCounts(
                alerts = parseCount(page, "alerts_unviewed", "p-navgroup-link--alerts"),
                conversations = parseCount(
                    page,
                    "conversations_unread",
                    "p-navgroup-link--conversations"
                )
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Prefers XenForo's `XF.config.visitorCounts` numbers; falls back to the
     * server-rendered `data-badge` attributes on the navigation links. Returns
     * `null` when neither signal is present.
     */
    private fun parseCount(html: String, configKey: String, navLinkClass: String): Int? {
        Regex("\"$configKey\"\\s*:\\s*(\\d+)").find(html)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?.let { return it }

        return Regex("$navLinkClass[^>]*?data-badge=\"(\\d+)\"").find(html)
            ?.groupValues?.get(1)?.toIntOrNull()
    }
}
