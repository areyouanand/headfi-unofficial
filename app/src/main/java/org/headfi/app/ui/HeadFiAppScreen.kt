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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.headfi.app.model.HeadFiSection
import org.headfi.app.model.QuickDestination
import org.headfi.app.model.SiteLayout
import org.headfi.app.model.ThemeMode
import org.headfi.app.model.UserSessionState
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
    val hasLoadedPage by viewModel.hasLoadedPage.collectAsState()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var longPressedImageUrl by remember { mutableStateOf<String?>(null) }
    var showSignInPrompt by remember { mutableStateOf(true) }

    // Find-in-page state
    var findInPageVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findMatchCount by remember { mutableStateOf(0) }
    var findActiveMatch by remember { mutableStateOf(-1) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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

    // Predictive Back Gesture handling for WebView history
    BackHandler(enabled = canGoBack || findInPageVisible) {
        if (findInPageVisible) {
            webViewRef?.clearMatches()
            findInPageVisible = false
            findQuery = ""
            findMatchCount = 0
            findActiveMatch = -1
        } else {
            webViewRef?.goBack()
        }
    }

    // Runtime notification permission (Android 13+) for the background poller
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Notifications are blocked, so unread alerts won't be posted. " +
                        "Enable them in Android Settings > Apps > Head-Fi > Notifications.",
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

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

    // Last known document scroll offset, used to hide the scroll-to-top FAB
    // while the user is already near the top of the page.
    var lastScrollY by remember { mutableStateOf(0) }

    // A freshly loaded page always starts with the bars visible
    LaunchedEffect(currentUrl, isLoading) {
        barScrollTracker.reset()
        barsVisible = true
    }

    // Runs a find-in-page query and keeps the match counters in sync.
    val runFind: (String) -> Unit = { query ->
        webViewRef?.let { view ->
            if (query.isBlank()) {
                view.clearMatches()
                findMatchCount = 0
                findActiveMatch = -1
            } else {
                view.findAllAsync(query)
            }
        }
    }

    val stepFind: (Boolean) -> Unit = { forward ->
        if (findMatchCount > 0) {
            webViewRef?.findNext(forward)
        }
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                // Only offer "scroll to top" once there is somewhere to scroll
                // back from.
                if (lastScrollY > FAB_VISIBILITY_THRESHOLD_PX) {
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
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                HeadFiWebView(
                    initialUrl = startUrl,
                    enableDarkCss = isDarkActive && preferences.injectWebDarkTheme,
                    amoledBlack = preferences.amoledBlack,
                    adBlockerEnabled = preferences.adBlockerEnabled,
                    textScale = preferences.textSize.scale,
                    loadImages = !preferences.blockImages,
                    desktopMode = preferences.siteLayout == SiteLayout.DESKTOP,
                    refreshAccentColor = MaterialTheme.colorScheme.primary.toArgb(),
                    refreshBackgroundColor = MaterialTheme.colorScheme.surface.toArgb(),
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
                        lastScrollY = scrollY
                        // Fed by both the WebView's OnScrollChangeListener and the
                        // page's own `scroll` listener; each passes its own delta.
                        barScrollTracker
                            .onScroll(scrollY, oldScrollY, System.currentTimeMillis())
                            ?.let { barsVisible = it }
                    },
                    onFindMatchCount = { activeOrdinal, total ->
                        findActiveMatch = if (total > 0) activeOrdinal else -1
                        findMatchCount = total
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Slim loading indicator pinned to the very top edge. It used to
                // live inside the app bar; with a floating bar there is no full-width
                // surface to hang it on, so it sits on the top edge of the WebView.
                AnimatedVisibility(
                    visible = isLoading && loadProgress in 1..99,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
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

                // Floating navigation button. This replaces the old full-width app
                // bar: it carries only page navigation and the overflow menu, and it
                // floats over the WebView instead of pushing it down. It hides and
                // returns with the same smart-scroll behaviour as the old bar.
                // Everything else moved - profile to the end of the bottom bar,
                // search into the overflow menu - and the site's own header is left
                // alone so it renders the way Head-Fi intends.
                AnimatedVisibility(
                    visible = barsVisible,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            if (canGoBack) {
                                IconButton(onClick = { webViewRef?.goBack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { webViewRef?.loadUrl(HeadFiSection.HOME.url) }
                                ) {
                                    HeadFiMonochromeLogoIcon(
                                        size = 24.dp,
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            if (canGoForward) {
                                IconButton(onClick = { webViewRef?.goForward() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                        contentDescription = "Forward"
                                    )
                                }
                            }
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
                                            text = { Text("Search Head-Fi") },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.Search, contentDescription = null)
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                showSearchDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Find in Page") },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.Search, contentDescription = null)
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                findInPageVisible = true
                                            }
                                        )
                                        if (sessionState.isLoggedIn || sessionState.hasRememberMeCookie) {
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text(QuickDestination.WATCHED_THREADS.label) },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Outlined.Visibility,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    webViewRef?.loadUrl(QuickDestination.WATCHED_THREADS.url)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(QuickDestination.CONVERSATIONS.label) },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Outlined.MailOutline,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    webViewRef?.loadUrl(QuickDestination.CONVERSATIONS.url)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(QuickDestination.BOOKMARKS.label) },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Outlined.BookmarkBorder,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    webViewRef?.loadUrl(QuickDestination.BOOKMARKS.url)
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
                    }
                }

                // Sign-in prompt: the app's whole value proposition (persistent
                // login, unread badges, background notifications) is invisible
                // until the user signs in, and the button used to be buried in
                // the account sheet.
                if (showSignInPrompt &&
                    !sessionState.isLoggedIn &&
                    !sessionState.hasRememberMeCookie
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 8.dp,
                                top = 12.dp,
                                bottom = 12.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Login,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sign in to Head-Fi",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Keeps you logged in across reboots and " +
                                        "enables unread notifications",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            TextButton(
                                onClick = {
                                    webViewRef?.loadUrl("https://www.head-fi.org/login/")
                                }
                            ) {
                                Text("Sign in")
                            }
                            IconButton(onClick = { showSignInPrompt = false }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Connection trouble. Full-screen only when there is nothing
                // behind it; otherwise a banner so the user can keep reading the
                // page they were on.
                if (networkError != null) {
                    if (hasLoadedPage) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 8.dp,
                                    top = 6.dp,
                                    bottom = 6.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "No connection",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        viewModel.onNetworkError(null)
                                        webViewRef?.reload()
                                    }
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    } else {
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
                                    text = networkError
                                        ?: "Unable to reach Head-Fi.org. Check your internet connection.",
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

                // Find-in-page bar, anchored above the bottom navigation.
                if (findInPageVisible) {
                    FindInPageBar(
                        query = findQuery,
                        matchCount = findMatchCount,
                        activeMatch = findActiveMatch,
                        onQueryChange = { newQuery ->
                            findQuery = newQuery
                            runFind(newQuery)
                        },
                        onPrevious = { stepFind(false) },
                        onNext = { stepFind(true) },
                        onClose = {
                            webViewRef?.clearMatches()
                            findInPageVisible = false
                            findQuery = ""
                            findMatchCount = 0
                            findActiveMatch = -1
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
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
                            // Profile moved here from the old app bar. It sits at the
                            // end of the bar and carries the unread badge, so the
                            // alert count is still visible without a top-bar button.
                            NavigationRailItem(
                                selected = false,
                                onClick = { showAccountSheet = true },
                                icon = {
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
                                            imageVector = profileIconFor(sessionState),
                                            contentDescription = "Account & Settings"
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = profileLabelFor(sessionState),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            )
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
                            // Profile moved here from the old app bar. It sits at the
                            // end of the bar and carries the unread badge, so the
                            // alert count is still visible without a top-bar button.
                            NavigationBarItem(
                                selected = false,
                                onClick = { showAccountSheet = true },
                                icon = {
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
                                            imageVector = profileIconFor(sessionState),
                                            contentDescription = "Account & Settings"
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = profileLabelFor(sessionState),
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
            onPollIntervalChange = viewModel::setPollInterval,
            onTextSizeChange = viewModel::setTextSize,
            onBlockImagesChange = viewModel::setBlockImages,
            onSiteLayoutChange = viewModel::setSiteLayout
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
 * Inline find-in-page bar. Uses the WebView's own `findAllAsync` / `findNext`
 * so it matches exactly what the page renders, including text inside threads.
 */
@Composable
private fun FindInPageBar(
    query: String,
    matchCount: Int,
    activeMatch: Int,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Close find in page"
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Find in page") },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
            if (matchCount > 0) {
                Text(
                    text = "${activeMatch + 1}/$matchCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            } else if (query.isNotBlank()) {
                Text(
                    text = "0/0",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            IconButton(onClick = onPrevious, enabled = matchCount > 0) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Previous match"
                )
            }
            IconButton(onClick = onNext, enabled = matchCount > 0) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Next match"
                )
            }
        }
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

/** Below this scroll offset the "scroll to top" FAB has nothing to do. */
private const val FAB_VISIBILITY_THRESHOLD_PX = 300

/**
 * Icon for the profile entry that now lives at the end of the bottom bar.
 * Shows a login glyph until a session exists, so a signed-out user can still
 * find the way in.
 */
private fun profileIconFor(session: UserSessionState): ImageVector =
    if (session.isLoggedIn || session.hasRememberMeCookie) {
        Icons.Outlined.Person
    } else {
        Icons.AutoMirrored.Outlined.Login
    }

/** Label for the bottom-bar profile entry. */
private fun profileLabelFor(session: UserSessionState): String =
    if (session.isLoggedIn || session.hasRememberMeCookie) "Account" else "Sign in"
