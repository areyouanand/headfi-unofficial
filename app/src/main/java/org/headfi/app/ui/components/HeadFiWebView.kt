package org.headfi.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.headfi.app.util.DownloadAndShareHelper
import org.headfi.app.webview.AdBlockEngine
import org.headfi.app.webview.HeadFiJsBridge
import org.headfi.app.webview.HeadFiScripts

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HeadFiWebView(
    initialUrl: String,
    enableDarkCss: Boolean,
    amoledBlack: Boolean,
    adBlockerEnabled: Boolean,
    hideSiteHeaderFooter: Boolean,
    onWebViewReady: (WebView) -> Unit,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onLoadingStateChanged: (Boolean) -> Unit,
    onNavigationStateChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onNetworkError: (String?) -> Unit,
    onSessionUpdate: (isLoggedIn: Boolean, username: String, avatarUrl: String, alerts: Int, convs: Int) -> Unit,
    onFileChooserRequested: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams?) -> Boolean,
    onImageLongPressed: (String) -> Unit,
    onWebViewScrolled: (scrollY: Int, oldScrollY: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance: WebView? = null

    // Re-apply CSS / JS enhancements whenever dark mode or adblocker settings toggle
    LaunchedEffect(enableDarkCss, amoledBlack, adBlockerEnabled, hideSiteHeaderFooter) {
        webViewInstance?.evaluateJavascript(
            HeadFiScripts.buildPageEnhancementScript(
                enableDarkCss = enableDarkCss,
                amoledBlack = amoledBlack,
                adBlockerEnabled = adBlockerEnabled,
                hideSiteHeaderFooter = hideSiteHeaderFooter
            ),
            null
        )
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadsImagesAutomatically = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    cacheMode = WebSettings.LOAD_DEFAULT
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportZoom(true)
                    setSupportMultipleWindows(true)
                    javaScriptCanOpenWindowsAutomatically = true
                    allowFileAccess = true
                    allowContentAccess = true
                    mediaPlaybackRequiresUserGesture = false

                    // Clean Chrome Mobile User-Agent so Cloudflare Turnstile and OAuth / passkeys work seamlessly
                    val defaultUa = userAgentString ?: ""
                    userAgentString = defaultUa
                        .replace("; wv", "")
                        .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
                }

                addJavascriptInterface(
                    HeadFiJsBridge(
                        onSessionUpdate = onSessionUpdate,
                        onScrollChanged = onWebViewScrolled
                    ),
                    "HeadFiBridge"
                )

                // Feed raw scroll positions to the smart auto-hide toolbar logic.
                // View.OnScrollChangeListener's SAM signature is
                // (v, scrollX, scrollY, oldScrollX, oldScrollY) — the vertical
                // positions are the 3rd and 5th arguments. Mixing those up makes
                // the direction detector see "scrollX" (always 0 for a vertically
                // scrolling page) and the bars never hide.
                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    onWebViewScrolled(scrollY, oldScrollY)
                }

                setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                    DownloadAndShareHelper.downloadFileOrImage(
                        context = context,
                        url = url,
                        userAgent = userAgent,
                        contentDisposition = contentDisposition,
                        mimeType = mimeType
                    )
                }

                setOnLongClickListener {
                    val result = hitTestResult
                    when (result.type) {
                        WebView.HitTestResult.IMAGE_TYPE,
                        WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                            val imageUrl = result.extra
                            if (!imageUrl.isNullOrBlank()) {
                                onImageLongPressed(imageUrl)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        if (AdBlockEngine.shouldBlockRequest(request, adBlockerEnabled)) {
                            return AdBlockEngine.emptyResponse()
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val uri = request.url ?: return false
                        val scheme = uri.scheme?.lowercase() ?: return false
                        val host = uri.host?.lowercase() ?: ""

                        if (scheme != "http" && scheme != "https") {
                            DownloadAndShareHelper.openInCustomTab(context, uri.toString())
                            return true
                        }

                        // Keep Head-Fi and Cloudflare challenges inside our persistent WebView
                        val isHeadFiInternal = host == "head-fi.org" ||
                            host.endsWith(".head-fi.org") ||
                            host.endsWith("cloudflare.com")

                        return if (isHeadFiInternal) {
                            false
                        } else {
                            DownloadAndShareHelper.openInCustomTab(context, uri.toString())
                            true
                        }
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onLoadingStateChanged(true)
                        onNetworkError(null)
                        if (!url.isNullOrBlank()) {
                            onUrlChanged(url)
                        }
                        onNavigationStateChanged(view.canGoBack(), view.canGoForward())
                    }

                    override fun onPageCommitVisible(view: WebView, url: String?) {
                        super.onPageCommitVisible(view, url)
                        view.evaluateJavascript(
                            HeadFiScripts.buildPageEnhancementScript(
                                enableDarkCss = enableDarkCss,
                                amoledBlack = amoledBlack,
                                adBlockerEnabled = adBlockerEnabled,
                                hideSiteHeaderFooter = hideSiteHeaderFooter
                            ),
                            null
                        )
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        onLoadingStateChanged(false)
                        if (!url.isNullOrBlank()) {
                            onUrlChanged(url)
                        }
                        onNavigationStateChanged(view.canGoBack(), view.canGoForward())

                        view.evaluateJavascript(
                            HeadFiScripts.buildPageEnhancementScript(
                                enableDarkCss = enableDarkCss,
                                amoledBlack = amoledBlack,
                                adBlockerEnabled = adBlockerEnabled,
                                hideSiteHeaderFooter = hideSiteHeaderFooter
                            ),
                            null
                        )

                        // Always flush cookies to disk after every page load
                        CookieManager.getInstance().flush()
                    }

                    override fun doUpdateVisitedHistory(
                        view: WebView,
                        url: String?,
                        isReload: Boolean
                    ) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        if (!url.isNullOrBlank()) {
                            onUrlChanged(url)
                        }
                        onNavigationStateChanged(view.canGoBack(), view.canGoForward())
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            onLoadingStateChanged(false)
                            onNetworkError(error.description?.toString() ?: "Unable to connect to Head-Fi.org")
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgressChanged(newProgress)
                        if (newProgress >= 100) {
                            onLoadingStateChanged(false)
                        }
                    }

                    override fun onReceivedTitle(view: WebView, title: String?) {
                        super.onReceivedTitle(view, title)
                        if (!title.isNullOrBlank()) {
                            onTitleChanged(title)
                        }
                    }

                    override fun onShowFileChooser(
                        webView: WebView,
                        filePathCallback: ValueCallback<Array<Uri>>,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        return onFileChooserRequested(filePathCallback, fileChooserParams)
                    }

                    override fun onCreateWindow(
                        view: WebView,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean {
                        val hitTest = view.hitTestResult
                        val data = hitTest.extra
                        if (!data.isNullOrBlank()) {
                            val host = runCatching { Uri.parse(data).host.orEmpty() }.getOrDefault("")
                            if (host.contains("head-fi.org")) {
                                view.loadUrl(data)
                            } else {
                                DownloadAndShareHelper.openInCustomTab(context, data)
                            }
                        }
                        return false
                    }
                }

                loadUrl(initialUrl)
                webViewInstance = this
                onWebViewReady(this)
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
        }
    }
}
