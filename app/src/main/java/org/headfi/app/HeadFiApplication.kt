package org.headfi.app

import android.app.Application
import android.util.Log
import android.webkit.CookieManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.headfi.app.data.SessionPreferencesRepository
import org.headfi.app.net.HeadFiContentClient
import org.headfi.app.net.HeadFiHttpClient
import org.headfi.app.net.HeadFiUserAgent
import org.headfi.app.notifications.HeadFiNotifications
import org.headfi.app.notifications.UnreadPollScheduler

class HeadFiApplication : Application(), ImageLoaderFactory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Avatars and post images live on `cdn.head-fi.org` and sometimes on
     * `head-fi.org` itself, where the request needs the session cookies. Routing
     * Coil through the same client means images authenticate exactly like pages.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { HeadFiHttpClient.get(this@HeadFiApplication) }
        .crossfade(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        // Initialize persistent CookieManager early so XenForo session cookies
        // (xf_user, xf_session, xf_csrf) are immediately ready on cold boot after device reboot.
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
        }

        // Capture the WebView User-Agent on the main thread once, before any
        // background request needs it. Cloudflare binds `cf_clearance` to the UA
        // that solved the challenge, so OkHttp and the WebView must send the same
        // string; the background poller would otherwise fall back to a different
        // hard-coded UA and could be sent back to the challenge page.
        HeadFiUserAgent.prefetch(this)

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

        // Debug-only smoke test for the native content layer (net/). Logs one
        // classified fetch + parse to logcat so the path can be verified on a
        // device, which is the only verification channel available here.
        // Never runs in release builds.
        if (BuildConfig.DEBUG) {
            applicationScope.launch(Dispatchers.IO) {
                val client = HeadFiContentClient(
                    applicationContext,
                    SessionPreferencesRepository(applicationContext)
                )
                Log.i(LOG_TAG, client.probe())
            }
        }
    }

    private companion object {
        const val LOG_TAG = "HeadFiContent"
    }
}
