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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
    textScale: Float,
    loadImages: Boolean,
    desktopMode: Boolean,
    refreshAccentColor: Int,
    refreshBackgroundColor: Int,
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
    onFindMatchCount: (activeMatchOrdinal: Int, numberOfMatches: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Held in Compose state rather than a plain local `var`: a plain var is reset
    // to null on every recomposition, which silently broke the settings-driven
    // CSS re-injection (the AndroidView factory only runs once).
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val context = LocalContext.current
    val mobileUserAgent = remember {
        WebSettings.getDefaultUserAgent(context)
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
    }

    // Re-apply CSS / JS enhancements whenever appearance settings toggle
    LaunchedEffect(enableDarkCss, amoledBlack, adBlockerEnabled) {
        webViewInstance?.evaluateJavascript(
            HeadFiScripts.buildPageEnhancementScript(
                enableDarkCss = enableDarkCss,
                amoledBlack = amoledBlack,
                adBlockerEnabled = adBlockerEnabled
            ),
            null
        )
    }

    // Text zoom and image loading can be applied without reloading the page.
    LaunchedEffect(textScale, loadImages) {
        webViewInstance?.settings?.apply {
            textZoom = textScale.toZoomPercent()
            loadsImagesAutomatically = loadImages
        }
    }

    // Switching the site layout changes the User-Agent, which the server only
    // sees on the next request, so the page must be reloaded. The first pass is
    // skipped because the factory already applies the right UA for the initial
    // load.
    var appliedDesktopMode by remember { mutableStateOf(desktopMode) }
    LaunchedEffect(desktopMode) {
        if (desktopMode != appliedDesktopMode) {
            appliedDesktopMode = desktopMode
            webViewInstance?.let { view ->
                view.settings.userAgentString =
                    if (desktopMode) DESKTOP_USER_AGENT else mobileUserAgent
                view.reload()
            }
        }
    }

    // Mirrors the WebView's loading state so the refresh gesture can be ignored
    // while a page is already in flight.
    var pageIsLoading by remember { mutableStateOf(true) }
    val reportLoadingState: (Boolean) -> Unit = { loading ->
        pageIsLoading = loading
        onLoadingStateChanged(loading)
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            // SwipeRefreshLayout is a ViewGroup that intercepts the drag before
            // the WebView ever sees it, then asks the child whether it can scroll
            // up. That is what makes pull-to-refresh reliable inside a WebView;
            // Compose's PullToRefreshBox relies on nested scroll, which a raw
            // AndroidView child does not feed back into the Compose chain.
            val swipeRefreshLayout = SwipeRefreshLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setColorSchemeColors(refreshAccentColor)
                setProgressBackgroundColorSchemeColor(refreshBackgroundColor)
            }

            val webView = WebView(ctx).apply {
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
                    loadsImagesAutomatically = loadImages
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
                    textZoom = textScale.toZoomPercent()
                    userAgentString = if (desktopMode) {
                        DESKTOP_USER_AGENT
                    } else {
                        mobileUserAgent
                    }
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

                // Pull-to-refresh. Ignored while a load is already running so a
                // stray drag can't queue up reloads.
                swipeRefreshLayout.setOnRefreshListener {
                    if (pageIsLoading) {
                        swipeRefreshLayout.isRefreshing = false
                    } else {
                        reload()
                    }
                }

                setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                    DownloadAndShareHelper.downloadFileOrImage(
                        context = ctx,
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

                @Suppress("DEPRECATION")
                setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                    onFindMatchCount(activeMatchOrdinal, numberOfMatches)
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        // Data Saver: drop image requests only when the user asked
                        // for it. Note this must key off `loadImages`, NOT
                        // `adBlockerEnabled` — the ad blocker is on by default, and
                        // keying off it here blocked every image on every page.
                        if (!loadImages && isImageRequest(request)) {
                            return AdBlockEngine.emptyResponse()
                        }
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
                            DownloadAndShareHelper.openInCustomTab(ctx, uri.toString())
                            return true
                        }

                        // Keep Head-Fi and Cloudflare challenges inside our persistent WebView
                        val isHeadFiInternal = host == "head-fi.org" ||
                            host.endsWith(".head-fi.org") ||
                            host.endsWith("cloudflare.com")

                        return if (isHeadFiInternal) {
                            false
                        } else {
                            DownloadAndShareHelper.openInCustomTab(ctx, uri.toString())
                            true
                        }
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        reportLoadingState(true)
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
                                adBlockerEnabled = adBlockerEnabled
                            ),
                            null
                        )
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        reportLoadingState(false)
                        swipeRefreshLayout.isRefreshing = false
                        if (!url.isNullOrBlank()) {
                            onUrlChanged(url)
                        }
                        onNavigationStateChanged(view.canGoBack(), view.canGoForward())

                        view.evaluateJavascript(
                            HeadFiScripts.buildPageEnhancementScript(
                                enableDarkCss = enableDarkCss,
                                amoledBlack = amoledBlack,
                                adBlockerEnabled = adBlockerEnabled
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
                            reportLoadingState(false)
                            swipeRefreshLayout.isRefreshing = false
                            onNetworkError(
                                error.description?.toString()
                                    ?: "Unable to connect to Head-Fi.org"
                            )
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgressChanged(newProgress)
                        if (newProgress >= 100) {
                            reportLoadingState(false)
                            swipeRefreshLayout.isRefreshing = false
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
                            val host = runCatching {
                                Uri.parse(data).host.orEmpty()
                            }.getOrDefault("")
                            if (host.contains("head-fi.org")) {
                                view.loadUrl(data)
                            } else {
                                DownloadAndShareHelper.openInCustomTab(ctx, data)
                            }
                        }
                        return false
                    }
                }

                loadUrl(initialUrl)
                onWebViewReady(this)
            }

            swipeRefreshLayout.addView(
                webView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            webViewInstance = webView
            swipeRefreshLayout
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
        }
    }
}

private fun Float.toZoomPercent(): Int = (this * 100).toInt().coerceIn(50, 300)

/**
 * True when the request looks like an image. Used by Data Saver to drop images
 * without breaking stylesheets, scripts or fonts.
 */
private fun isImageRequest(request: WebResourceRequest): Boolean {
    val path = request.url?.path?.lowercase() ?: return false
    if (IMAGE_EXTENSIONS.any { path.endsWith(it) }) return true
    val accept = request.requestHeaders?.get("Accept")?.lowercase() ?: return false
    return accept.startsWith("image/")
}

private val IMAGE_EXTENSIONS = listOf(
    ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg", ".ico", ".avif"
)

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/131.0.0.0 Safari/537.36"
