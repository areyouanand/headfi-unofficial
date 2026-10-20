package org.headfi.app

import android.app.Application
import android.webkit.CookieManager

class HeadFiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize persistent CookieManager early so XenForo session cookies
        // (xf_user, xf_session, xf_csrf) are immediately ready on cold boot after device reboot.
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
        }
    }
}
