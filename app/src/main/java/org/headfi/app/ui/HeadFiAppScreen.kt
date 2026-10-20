package org.headfi.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.headfi.app.model.HeadFiSection
import org.headfi.app.model.ThemeMode
import org.headfi.app.ui.components.AccountAndSettingsSheet
import org.headfi.app.ui.components.HeadFiMonochromeLogoIcon
import org.headfi.app.ui.components.HeadFiWebView
import org.headfi.app.ui.components.SearchHeadFiDialog
import org.headfi.app.util.DownloadAndShareHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadFiAppScreen(
    viewModel: MainViewModel,
    deepLinkUrl: String?,
    onConsumeDeepLink: () -> Unit,
    onFileChooserRequested: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams?) -> Boolean
) {
    val context = LocalContext.current
    val preferences by viewModel.preferences.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val currentUrl by viewModel.currentUrl.collectAsState()
    val pageTitle by viewModel.pageTitle.collectAsState()
    val selectedSection by viewModel.selectedSection.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadProgress by viewModel.loadProgress.collectAsState()
    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val networkError by viewModel.networkError.collectAsState()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var longPressedImageUrl by remember { mutableStateOf<String?>(null) }
    var isPullRefreshing by remember { mutableStateOf(false) }

    val systemDark = isSystemInDarkTheme()
    val isDarkActive = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Handle incoming deep link intents
    LaunchedEffect(deepLinkUrl, webViewRef) {
        val target = deepLinkUrl
        val wv = webViewRef
        if (!target.isNullOrBlank() && wv != null) {
            wv.loadUrl(target)
            onConsumeDeepLink()
        }
    }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            isPullRefreshing = false
        }
    }

    // Predictive Back Gesture handling for WebView history
    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    // Runtime notification permission (Android 13+) for the background poller
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Nothing to do: the worker checks areNotificationsEnabled() itself */ }

    val onUnreadNotificationsToggled: (Boolean) -> Unit = { enabled ->
        viewModel.setUnreadNotificationsEnabled(enabled)
        if (enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Smart toolbar state: top & bottom bars slide away while reading and
    // return as soon as the user scrolls up again
    var barsVisible by remember { mutableStateOf(true) }
    val barScrollTracker = remember { SmartBarScrollTracker() }

    // A freshly loaded page always starts with the bars visible
    LaunchedEffect(currentUrl, isLoading) {
        barScrollTracker.reset()
        barsVisible = true
    }

    val startUrl = remember {
        if (!deepLinkUrl.isNullOrBlank()) {
            deepLinkUrl
        } else if (preferences.rememberLastPage && preferences.lastVisitedUrl.isNotBlank()) {
            preferences.lastVisitedUrl
        } else {
            HeadFiSection.HOME.url
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        val onSectionClick: (HeadFiSection) -> Unit = { section ->
            if (selectedSection == section) {
                // Tapping the active tab scrolls smoothly to top or reloads section root
                webViewRef?.evaluateJavascript(
                    "if (window.scrollY > 120) { window.scrollTo({top: 0, behavior: 'smooth'}); } else { window.location.href = '${section.url}'; }",
                    null
                )
            } else {
                webViewRef?.loadUrl(section.url)
            }
        }

        val appBody: @Composable () -> Unit = {
        Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = barsVisible,
                    enter = slideInVertically(initialOffsetY = { -it }) +
                        expandVertically(expandFrom = Alignment.Top),
                    exit = slideOutVertically(targetOffsetY = { -it }) +
                        shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        navigationIcon = {
                            if (canGoBack) {
                                IconButton(onClick = { webViewRef?.goBack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .padding(start = 12.dp, end = 4.dp)
                                        .size(36.dp),
                                    onClick = { webViewRef?.loadUrl(HeadFiSection.HOME.url) }
                                ) {
                                    HeadFiMonochromeLogoIcon(
                                        size = 26.dp,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(5.dp)
                                    )
                                }
                            }
                        },
                        title = {
                            Column(
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = pageTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (sessionState.isLoggedIn || sessionState.hasRememberMeCookie) {
                                        val user = sessionState.username.ifBlank {
                                            preferences.savedUsername.ifBlank { "Signed in" }
                                        }
                                        "head-fi.org • $user"
                                    } else {
                                        "head-fi.org"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            // Search button
                            IconButton(onClick = { showSearchDialog = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search Head-Fi"
                                )
                            }

                            // Alerts shortcut when logged in
                            if (sessionState.isLoggedIn || sessionState.hasRememberMeCookie) {
                                IconButton(
                                    onClick = {
                                        webViewRef?.loadUrl("https://www.head-fi.org/account/alerts")
                                    }
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (sessionState.totalUnread > 0) {
                                                Badge {
                                                    Text(sessionState.totalUnread.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Notifications,
                                            contentDescription = "Alerts & Messages"
                                        )
                                    }
                                }
                            }

                            // Expressive Account / Login pill button
                            FilledTonalIconButton(
                                onClick = { showAccountSheet = true },
                                modifier = Modifier.padding(end = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (sessionState.isLoggedIn || sessionState.hasRememberMeCookie) {
                                        Icons.Outlined.Person
                                    } else {
                                        Icons.AutoMirrored.Outlined.Login
                                    },
                                    contentDescription = "Account & Settings"
                                )
                            }

                            // Overflow menu
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = "More options"
                                    )
                                }

                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Refresh Page") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            webViewRef?.reload()
                                        }
                                    )
                                    if (canGoForward) {
                                        DropdownMenuItem(
                                            text = { Text("Go Forward") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.AutoMirrored.Outlined.ArrowForward,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                webViewRef?.goForward()
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Share Thread / Page") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Share, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            DownloadAndShareHelper.sharePage(
                                                context = context,
                                                title = pageTitle,
                                                url = currentUrl
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open in External Browser") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.OpenInBrowser, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            DownloadAndShareHelper.openInCustomTab(context, currentUrl)
                                        }
                                    )
                                }
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = isLoading && loadProgress in 1..99,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        LinearProgressIndicator(
                            progress = { loadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }
                }
            },
            floatingActionButton = {
                SmallFloatingActionButton(
                    onClick = {
                        webViewRef?.evaluateJavascript(
                            "window.scrollTo({top: 0, behavior: 'smooth'});",
                            null
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VerticalAlignTop,
                        contentDescription = "Scroll to top"
                    )
                }
            }
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = isPullRefreshing,
                onRefresh = {
                    isPullRefreshing = true
                    webViewRef?.reload()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                HeadFiWebView(
                    initialUrl = startUrl,
                    enableDarkCss = isDarkActive && preferences.injectWebDarkTheme,
                    amoledBlack = preferences.amoledBlack,
                    adBlockerEnabled = preferences.adBlockerEnabled,
                    hideSiteHeaderFooter = preferences.hideSiteHeaderFooter,
                    onWebViewReady = { webViewRef = it },
                    onUrlChanged = viewModel::onUrlChanged,
                    onTitleChanged = viewModel::onTitleChanged,
                    onProgressChanged = viewModel::onProgressChanged,
                    onLoadingStateChanged = viewModel::onLoadingStateChanged,
                    onNavigationStateChanged = viewModel::onNavigationStateChanged,
                    onNetworkError = viewModel::onNetworkError,
                    onSessionUpdate = viewModel::onXenForoSessionDetected,
                    onFileChooserRequested = onFileChooserRequested,
                    onImageLongPressed = { imageUrl ->
                        longPressedImageUrl = imageUrl
                    },
                    onWebViewScrolled = { scrollY, oldScrollY ->
                        // Fed by both the WebView's OnScrollChangeListener and the
                        // page's own `scroll` listener; each passes its own delta.
                        barScrollTracker
                            .onScroll(scrollY, oldScrollY, System.currentTimeMillis())
                            ?.let { barsVisible = it }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Offline / Network Error Overlay
                if (networkError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Connection Interrupted",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = networkError ?: "Unable to reach Head-Fi.org. Check your internet connection.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    viewModel.onNetworkError(null)
                                    webViewRef?.reload()
                                }
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
        }

        if (useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = barsVisible,
                    enter = slideInHorizontally(initialOffsetX = { -it }) +
                        expandHorizontally(expandFrom = Alignment.Start),
                    exit = slideOutHorizontally(targetOffsetX = { -it }) +
                        shrinkHorizontally(shrinkTowards = Alignment.Start)
                ) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        HeadFiSection.entries.forEach { section ->
                            NavigationRailItem(
                                selected = selectedSection == section,
                                onClick = { onSectionClick(section) },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedSection == section) {
                                            section.selectedIcon
                                        } else {
                                            section.unselectedIcon
                                        },
                                        contentDescription = section.title
                                    )
                                },
                                label = {
                                    Text(
                                        text = section.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    appBody()
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    appBody()
                }
                AnimatedVisibility(
                    visible = barsVisible,
                    enter = slideInVertically(initialOffsetY = { it }) +
                        expandVertically(expandFrom = Alignment.Bottom),
                    exit = slideOutVertically(targetOffsetY = { it }) +
                        shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        HeadFiSection.entries.forEach { section ->
                            NavigationBarItem(
                                selected = selectedSection == section,
                                onClick = { onSectionClick(section) },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedSection == section) {
                                            section.selectedIcon
                                        } else {
                                            section.unselectedIcon
                                        },
                                        contentDescription = section.title
                                    )
                                },
                                label = {
                                    Text(
                                        text = section.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Account, Quick Destinations & App Settings Sheet
    if (showAccountSheet) {
        AccountAndSettingsSheet(
            sessionState = sessionState,
            preferences = preferences,
            onDismiss = { showAccountSheet = false },
            onNavigateUrl = { url -> webViewRef?.loadUrl(url) },
            onLoginClick = {
                webViewRef?.loadUrl("https://www.head-fi.org/login/")
            },
            onLogoutClick = {
                viewModel.logoutAndClearSession {
                    webViewRef?.loadUrl("https://www.head-fi.org/")
                }
            },
            onThemeModeChange = viewModel::setThemeMode,
            onAmoledBlackChange = viewModel::setAmoledBlack,
            onInjectWebDarkChange = viewModel::setInjectWebDarkTheme,
            onAdBlockerChange = viewModel::setAdBlockerEnabled,
            onRememberLastPageChange = viewModel::setRememberLastPage,
            onUnreadNotificationsChange = onUnreadNotificationsToggled,
            onPollIntervalChange = viewModel::setPollInterval
        )
    }

    // Native Search Dialog
    if (showSearchDialog) {
        SearchHeadFiDialog(
            onDismiss = { showSearchDialog = false },
            onExecuteSearchUrl = { searchUrl ->
                webViewRef?.loadUrl(searchUrl)
            }
        )
    }

    // Long-press image save / share dialog
    longPressedImageUrl?.let { imgUrl ->
        AlertDialog(
            onDismissRequest = { longPressedImageUrl = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Image Options") },
            text = {
                Text(
                    text = imgUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        DownloadAndShareHelper.downloadFileOrImage(context, imgUrl)
                        longPressedImageUrl = null
                    }
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Image")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            DownloadAndShareHelper.sharePage(context, "Head-Fi Image", imgUrl)
                            longPressedImageUrl = null
                        }
                    ) {
                        Text("Share")
                    }
                    TextButton(onClick = { longPressedImageUrl = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

/**
 * Direction detection with hysteresis for the smart auto-hide toolbars.
 * Accumulates scroll deltas so tiny jitters don't toggle the bars, and
 * always keeps the bars visible near the top of the page.
 *
 * Returns `true` to show the bars, `false` to hide them, or `null` to keep
 * the current state.
 *
 * The tracker is fed from two independent sources — the WebView's own
 * `OnScrollChangeListener` and a `scroll` listener injected into the page —
 * so every caller must pass its *own* `oldScrollY`. Because both sources
 * observe the same gesture, their deltas always agree in sign.
 */
private class SmartBarScrollTracker {

    private var accumulated = 0f

    /** Timestamp of the last visibility flip, used to ignore layout feedback. */
    private var lastToggleAtMs = 0L

    fun reset() {
        accumulated = 0f
        lastToggleAtMs = 0L
    }

    fun onScroll(scrollY: Int, oldScrollY: Int, nowMs: Long): Boolean? {
        val delta = scrollY - oldScrollY
        if (delta == 0) return null

        // Near the top of the page, always keep the bars on screen
        if (scrollY <= TOP_AREA_PX) {
            accumulated = 0f
            return true
        }

        // Hiding the bars makes the WebView taller, which can clamp the scroll
        // offset at the very bottom of a page and fire a scroll event of its
        // own. Without this guard that feedback loop flips the bars forever.
        if (nowMs - lastToggleAtMs < SETTLE_MS) {
            accumulated = 0f
            return null
        }

        accumulated += delta
        val result = when {
            accumulated > HIDE_THRESHOLD_PX -> false
            accumulated < -SHOW_THRESHOLD_PX -> true
            else -> null
        }
        if (result != null) {
            accumulated = 0f
            lastToggleAtMs = nowMs
        }
        return result
    }

    companion object {
        private const val TOP_AREA_PX = 220
        private const val HIDE_THRESHOLD_PX = 32f
        private const val SHOW_THRESHOLD_PX = 18f

        /** How long to ignore scroll blips produced by the bar animation itself. */
        private const val SETTLE_MS = 250L
    }
}
