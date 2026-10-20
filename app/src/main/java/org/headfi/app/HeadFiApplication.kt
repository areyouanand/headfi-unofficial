package org.headfi.app

import android.app.Application
import android.webkit.CookieManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.headfi.app.data.SessionPreferencesRepository
import org.headfi.app.notifications.HeadFiNotifications
import org.headfi.app.notifications.UnreadPollScheduler

class HeadFiApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // Initialize persistent CookieManager early so XenForo session cookies
        // (xf_user, xf_session, xf_csrf) are immediately ready on cold boot after device reboot.
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
        }

        HeadFiNotifications.ensureChannels(this)

        // Re-arm the background unread poller if the user opted in. Periodic work
        // survives reboots, but this also covers the first launch right after the
        // toggle was flipped and the process was killed before MainActivity ran.
        applicationScope.launch {
            val repository = SessionPreferencesRepository(applicationContext)
            val preferences = repository.preferencesFlow.first()
            UnreadPollScheduler.sync(
                context = applicationContext,
                enabled = preferences.unreadNotificationsEnabled,
                interval = preferences.pollInterval
            )
        }
    }
}
