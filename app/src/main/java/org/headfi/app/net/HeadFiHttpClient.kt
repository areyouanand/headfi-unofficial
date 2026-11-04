package org.headfi.app.net

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The process-wide OkHttp client for native Head-Fi requests.
 *
 * Shared so the connection pool, HTTP cache and cookie jar are reused across
 * screens; a per-screen client would re-open TLS connections and re-read the
 * WebView cookie store on every navigation.
 */
object HeadFiHttpClient {

    private const val CACHE_BYTES = 24L * 1024 * 1024

    @Volatile
    private var instance: OkHttpClient? = null

    fun get(context: Context): OkHttpClient = instance ?: synchronized(this) {
        instance ?: build(context.applicationContext).also { instance = it }
    }

    private fun build(context: Context): OkHttpClient = OkHttpClient.Builder()
        // Responses carry the user's session, but the cache lives in app-private
        // storage and XenForo sends `private, max-age=0` for signed-in pages, so
        // little authenticated HTML is actually stored.
        .cache(Cache(File(context.cacheDir, "headfi-http"), CACHE_BYTES))
        .cookieJar(WebViewCookieJar())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
}
