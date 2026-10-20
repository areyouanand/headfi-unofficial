package org.headfi.app.data

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.headfi.app.model.AppPreferences
import org.headfi.app.model.PollInterval
import org.headfi.app.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "headfi_prefs")

class SessionPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val INJECT_WEB_DARK = booleanPreferencesKey("inject_web_dark")
        val AD_BLOCKER = booleanPreferencesKey("ad_blocker")
        val HIDE_SITE_CHROME = booleanPreferencesKey("hide_site_chrome")
        val REMEMBER_LAST_PAGE = booleanPreferencesKey("remember_last_page")
        val LAST_VISITED_URL = stringPreferencesKey("last_visited_url")
        val SAVED_USERNAME = stringPreferencesKey("saved_username")
        val SAVED_AVATAR_URL = stringPreferencesKey("saved_avatar_url")
        val COOKIE_BACKUP_JSON = stringPreferencesKey("cookie_backup_json")
        val UNREAD_NOTIFICATIONS = booleanPreferencesKey("unread_notifications")
        val POLL_INTERVAL = stringPreferencesKey("poll_interval")
        val LAST_NOTIFIED_ALERTS = intPreferencesKey("last_notified_alerts")
        val LAST_NOTIFIED_CONVERSATIONS = intPreferencesKey("last_notified_conversations")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        AppPreferences(
            themeMode = themeMode,
            amoledBlack = prefs[Keys.AMOLED_BLACK] ?: false,
            injectWebDarkTheme = prefs[Keys.INJECT_WEB_DARK] ?: true,
            adBlockerEnabled = prefs[Keys.AD_BLOCKER] ?: true,
            hideSiteHeaderFooter = prefs[Keys.HIDE_SITE_CHROME] ?: false,
            rememberLastPage = prefs[Keys.REMEMBER_LAST_PAGE] ?: true,
            lastVisitedUrl = prefs[Keys.LAST_VISITED_URL] ?: "https://www.head-fi.org/",
            savedUsername = prefs[Keys.SAVED_USERNAME] ?: "",
            unreadNotificationsEnabled = prefs[Keys.UNREAD_NOTIFICATIONS] ?: false,
            pollInterval = PollInterval.fromName(prefs[Keys.POLL_INTERVAL])
        )
    }

    val savedUserFlow: Flow<Pair<String, String>> = context.dataStore.data.map { prefs ->
        Pair(
            prefs[Keys.SAVED_USERNAME] ?: "",
            prefs[Keys.SAVED_AVATAR_URL] ?: ""
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAmoledBlack(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AMOLED_BLACK] = enabled }
    }

    suspend fun setInjectWebDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.INJECT_WEB_DARK] = enabled }
    }

    suspend fun setAdBlockerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AD_BLOCKER] = enabled }
    }

    suspend fun setHideSiteHeaderFooter(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_SITE_CHROME] = enabled }
    }

    suspend fun setRememberLastPage(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_LAST_PAGE] = enabled }
    }

    suspend fun setUnreadNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UNREAD_NOTIFICATIONS] = enabled }
    }

    suspend fun setPollInterval(interval: PollInterval) {
        context.dataStore.edit { it[Keys.POLL_INTERVAL] = interval.name }
    }

    /**
     * Unread counts the user has already been told about (or already seen in the
     * app). Used by the background poller to only notify about *new* items.
     */
    suspend fun lastNotifiedCounts(): Pair<Int, Int> {
        val prefs = context.dataStore.data.first()
        return (prefs[Keys.LAST_NOTIFIED_ALERTS] ?: 0) to
            (prefs[Keys.LAST_NOTIFIED_CONVERSATIONS] ?: 0)
    }

    suspend fun updateLastNotified(alerts: Int?, conversations: Int?) {
        context.dataStore.edit { prefs ->
            alerts?.let { prefs[Keys.LAST_NOTIFIED_ALERTS] = it.coerceAtLeast(0) }
            conversations?.let {
                prefs[Keys.LAST_NOTIFIED_CONVERSATIONS] = it.coerceAtLeast(0)
            }
        }
    }

    suspend fun saveLastVisitedUrl(url: String) {
        if (url.startsWith("https://www.head-fi.org") || url.startsWith("https://head-fi.org")) {
            context.dataStore.edit { it[Keys.LAST_VISITED_URL] = url }
        }
    }

    suspend fun saveUserIdentity(username: String, avatarUrl: String) {
        context.dataStore.edit {
            it[Keys.SAVED_USERNAME] = username
            if (avatarUrl.isNotBlank()) {
                it[Keys.SAVED_AVATAR_URL] = avatarUrl
            }
        }
    }

    /**
     * Flushes Android WebView's CookieManager to SQLite disk and backs up critical
     * Head-Fi XenForo authentication cookies (`xf_user`, `xf_session`, `xf_csrf`, `xf_tfa_trust`)
     * to DataStore with persistent expiration so login survives app restarts and device reboots.
     */
    suspend fun syncAndBackupCookies() = withContext(Dispatchers.IO) {
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.flush()

            val urls = listOf("https://www.head-fi.org", "https://head-fi.org")
            val backupArray = JSONArray()
            var hasXfUser = false

            for (domainUrl in urls) {
                val rawCookieHeader = cookieManager.getCookie(domainUrl) ?: continue
                val pairs = rawCookieHeader.split(";")
                for (pair in pairs) {
                    val trimmed = pair.trim()
                    val eqIdx = trimmed.indexOf('=')
                    if (eqIdx <= 0) continue
                    val name = trimmed.substring(0, eqIdx).trim()
                    val value = trimmed.substring(eqIdx + 1).trim()

                    if (name.startsWith("xf_") || name.contains("cf_") || name.contains("clearance")) {
                        if (name == "xf_user" && value.isNotBlank()) {
                            hasXfUser = true
                        }
                        val obj = JSONObject().apply {
                            put("url", domainUrl)
                            put("name", name)
                            put("value", value)
                        }
                        backupArray.put(obj)
                    }
                }
            }

            if (hasXfUser && backupArray.length() > 0) {
                context.dataStore.edit { prefs ->
                    prefs[Keys.COOKIE_BACKUP_JSON] = backupArray.toString()
                }
            }
        }
    }

    /**
     * Restores backed-up Head-Fi XenForo authentication cookies into CookieManager on cold boot
     * if the WebView cookie store was cleared or lost session cookies across a reboot.
     */
    suspend fun restoreCookiesIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            val currentCookies = cookieManager.getCookie("https://www.head-fi.org").orEmpty()
            if (currentCookies.contains("xf_user=")) {
                cookieManager.flush()
                return@withContext true
            }

            val prefs = context.dataStore.data.first()
            val backupJson = prefs[Keys.COOKIE_BACKUP_JSON] ?: return@withContext false
            val array = JSONArray(backupJson)
            if (array.length() == 0) return@withContext false

            // 1 year max-age for persistent login across reboots
            val persistentAttributes = "; Domain=.head-fi.org; Path=/; Max-Age=31536000; Secure; SameSite=Lax"
            var restoredAny = false

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val url = item.optString("url", "https://www.head-fi.org")
                val name = item.optString("name")
                val value = item.optString("value")
                if (name.isNotBlank() && value.isNotBlank()) {
                    cookieManager.setCookie(url, "$name=$value$persistentAttributes")
                    restoredAny = true
                }
            }

            if (restoredAny) {
                cookieManager.flush()
            }
            restoredAny
        }.getOrDefault(false)
    }

    /**
     * Rebuilds a `Cookie:` header for authenticated requests made outside the
     * WebView (e.g. the background unread poller).
     *
     * Prefers the live WebView cookie store; falls back to the DataStore vault so
     * polling still works after the OS has trimmed the cookie database.
     */
    suspend fun cookieHeader(): String? = withContext(Dispatchers.IO) {
        runCatching {
            CookieManager.getInstance().getCookie("https://www.head-fi.org")
                ?.takeIf { it.contains("xf_user=") }
                ?.let { return@runCatching it }

            val prefs = context.dataStore.data.first()
            val backupJson = prefs[Keys.COOKIE_BACKUP_JSON] ?: return@runCatching null
            val array = JSONArray(backupJson)

            val names = LinkedHashSet<String>()
            val values = LinkedHashMap<String, String>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name = item.optString("name")
                val value = item.optString("value")
                if (name.isNotBlank() && value.isNotBlank() && names.add(name)) {
                    values[name] = value
                }
            }
            if (values.isEmpty() || !values.keys.any { it == "xf_user" }) {
                null
            } else {
                values.entries.joinToString("; ") { "${it.key}=${it.value}" }
            }
        }.getOrNull()
    }

    /** True when a persistent (`xf_user`) session has been backed up. */
    suspend fun hasStoredSession(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (CookieManager.getInstance()
                    .getCookie("https://www.head-fi.org")
                    ?.contains("xf_user=") == true
            ) {
                return@runCatching true
            }
            val prefs = context.dataStore.data.first()
            prefs[Keys.COOKIE_BACKUP_JSON]?.contains("\"xf_user\"") == true
        }.getOrDefault(false)
    }

    /**
     * Explicitly clears all cookies and saved session backup when the user chooses to log out.
     */
    suspend fun clearSessionData() = withContext(Dispatchers.IO) {
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            context.dataStore.edit { prefs ->
                prefs.remove(Keys.COOKIE_BACKUP_JSON)
                prefs.remove(Keys.SAVED_USERNAME)
                prefs.remove(Keys.SAVED_AVATAR_URL)
            }
        }
    }

    fun hasRememberMeCookie(): Boolean {
        return runCatching {
            val cookies = CookieManager.getInstance().getCookie("https://www.head-fi.org").orEmpty()
            cookies.contains("xf_user=")
        }.getOrDefault(false)
    }
}
