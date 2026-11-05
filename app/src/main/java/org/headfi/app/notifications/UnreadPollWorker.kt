package org.headfi.app.notifications

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.headfi.app.data.SessionPreferencesRepository
import org.headfi.app.net.HeadFiContentClient
import org.headfi.app.net.HeadFiUserAgent
import org.headfi.app.net.PagePayload
import org.headfi.app.net.xenforo.ThreadListParser
import org.headfi.app.net.xenforo.ThreadSummary
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
        private const val WATCHED_URL = "https://www.head-fi.org/watched/threads"
        const val INPUT_MANUAL = "manual"

        /** Enough to cover `XF.config`, which lives in the document head. */
        private const val MAX_HTML_CHARS = 400_000
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

        // A stale summary from an earlier run would otherwise outlive the
        // children it was summarising.
        HeadFiNotifications.clear(applicationContext, HeadFiNotifications.ID_SUMMARY)
        var posted = 0

        counts.alerts?.let { alerts ->
            when {
                alerts > lastAlerts && alerts > 0 -> {
                    HeadFiNotifications.notifyUnread(
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
                    posted++
                }

                alerts == 0 -> HeadFiNotifications.clear(
                    applicationContext,
                    HeadFiNotifications.ID_ALERTS
                )
            }
        }

        counts.conversations?.let { conversations ->
            when {
                conversations > lastConversations && conversations > 0 -> {
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
                    posted++
                }

                conversations == 0 -> HeadFiNotifications.clear(
                    applicationContext,
                    HeadFiNotifications.ID_CONVERSATIONS
                )
            }
        }

        // Per-thread watched notifications: the global counts above cannot tell
        // the user *which* thread got a reply, so this reads the watched list.
        // A null result means "unknown" (challenge, network, markup) and must not
        // clear the existing notification.
        val watchedUnread = runCatching { fetchWatchedUnread(repository) }
            .onFailure { Log.w(TAG, "Watched-thread poll failed", it) }
            .getOrNull()

        watchedUnread?.let { threads ->
            val current = threads.map { it.url }.toSet()
            val previous = repository.lastNotifiedWatchedUrls()
            val fresh = threads.filter { it.url !in previous }

            when {
                fresh.isNotEmpty() -> {
                    val titles = fresh.take(3).joinToString("\n") { it.title }
                    val body = if (fresh.size > 3) {
                        "$titles\n…and ${fresh.size - 3} more"
                    } else {
                        titles
                    }
                    HeadFiNotifications.notifyUnread(
                        context = applicationContext,
                        notificationId = HeadFiNotifications.ID_WATCHED,
                        title = if (fresh.size == 1) {
                            "New reply in a watched thread"
                        } else {
                            "New replies in ${fresh.size} watched threads"
                        },
                        body = body,
                        deepLinkUrl = WATCHED_URL
                    )
                    posted++
                }

                current.isEmpty() -> HeadFiNotifications.clear(
                    applicationContext,
                    HeadFiNotifications.ID_WATCHED
                )
            }

            repository.updateLastNotifiedWatchedUrls(current)
        }

        if (posted >= 2) {
            HeadFiNotifications.notifySummary(applicationContext, posted)
        }

        repository.updateLastNotified(counts.alerts, counts.conversations)
        return Result.success()
    }

    /**
     * The unread threads in `/watched/threads`, or null when the page could not
     * be read (challenge, network, markup drift).
     */
    private suspend fun fetchWatchedUnread(
        repository: SessionPreferencesRepository
    ): List<ThreadSummary>? {
        val client = HeadFiContentClient(applicationContext, repository)
        val payload = client.get(WATCHED_URL)
        val html = (payload as? PagePayload.Html)?.html ?: return null
        return runCatching { ThreadListParser.parse(html, WATCHED_URL) }
            .getOrNull()
            ?.threads
            ?.filter { it.isUnread }
    }

    private fun fetchUnreadCounts(cookieHeader: String): UnreadCounts {
        val connection = (URL(POLL_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Cookie", cookieHeader)
            setRequestProperty("User-Agent", HeadFiUserAgent.mobile(applicationContext))
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
