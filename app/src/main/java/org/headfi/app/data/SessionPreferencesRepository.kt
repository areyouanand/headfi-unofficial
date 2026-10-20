package org.headfi.app.data

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.headfi.app.model.AppPreferences
import org.headfi.app.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "headfi_prefs")

class SessionPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val INJECT_WEB_DARK = booleanPreferencesKey("inject_web_dark")
        val AD_BLOCKER = booleanPreferencesKey("ad_blocker")
        val HIDE_SITE_CHROME = booleanPreferencesKey("hide_site_chrome")
        val REMEMBER_LAST_PAGE = booleanPreferencesKey("remember_last_page")
        val LAST_VISITED_URL = stringPreferencesKey("last_visited_url")
        val SAVED_USERNAME = stringPreferencesKey("saved_username")
        val SAVED_AVATAR_URL = stringPreferencesKey("saved_avatar_url")
        val COOKIE_BACKUP_JSON = stringPreferencesKey("cookie_backup_json")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        AppPreferences(
            themeMode = themeMode,
            dynamicColors = prefs[Keys.DYNAMIC_COLORS] ?: true,
            amoledBlack = prefs[Keys.AMOLED_BLACK] ?: false,
            injectWebDarkTheme = prefs[Keys.INJECT_WEB_DARK] ?: true,
            adBlockerEnabled = prefs[Keys.AD_BLOCKER] ?: true,
            hideSiteHeaderFooter = prefs[Keys.HIDE_SITE_CHROME] ?: false,
            rememberLastPage = prefs[Keys.REMEMBER_LAST_PAGE] ?: true,
            lastVisitedUrl = prefs[Keys.LAST_VISITED_URL] ?: "https://www.head-fi.org/",
            savedUsername = prefs[Keys.SAVED_USERNAME] ?: ""
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

    suspend fun setDynamicColors(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLORS] = enabled }
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
