# Project Handoff: Head-Fi for Android (`org.headfi.app`)

## 1. Executive Summary
- **Head-Fi for Android** is a native Kotlin + Jetpack Compose Android client for browsing [Head-Fi.org](https://www.head-fi.org/). Forums, thread lists, thread pages, What's New, watched threads, search results and alerts are parsed from Head-Fi's XenForo 2.3 HTML and rendered as native Compose screens; the WebView remains as the sign-in/challenge/form fallback. Around that sit a **Material 3 Expressive / Material You** adaptive shell, guaranteed login persistence across app restarts and device reboots, custom dark/AMOLED web styling, ad/tracker blocking, background unread notifications, reading and layout controls (text size, Mobile/Desktop site layout, Data Saver), multi-photo uploads with camera capture, and a **monochrome Head-Fi vector adaptive launcher icon**.
- **No data collection, no analytics.** There is no telemetry, crash reporting, advertising, or Firebase SDK in the dependency graph, and no backend of the app's own. The README's Privacy section states this, and it is accurate: the only network destinations are Head-Fi's own pages, RSS feeds and image CDN.

---

## 2. Architecture & Tech Stack
- **Language & Runtime**: Kotlin `2.1.10`, JVM Target `17`
- **Android SDK**: `minSdk = 26` (Android 8.0), `targetSdk = 35` / `compileSdk = 35` (Android 15)
- **UI Framework**: Jetpack Compose (`2025.02.00` BOM), Material 3 (`1.3.1`), Material 3 Adaptive Navigation Suite (`NavigationSuiteScaffold`)
- **State & Persistence**:
  - `MainViewModel` (`StateFlow` reactive UI state)
  - Jetpack `DataStore` Preferences (`SessionPreferencesRepository`)
  - Android `CookieManager` SQLite persistence + JSON cookie vault backup/restore
- **Background Work**: WorkManager (`2.10.1`) `CoroutineWorker` + `PeriodicWorkRequest` for background unread polling; `lifecycle-process` (`ProcessLifecycleOwner`) for foreground detection
- **Pull to Refresh**: `androidx.swiperefreshlayout:swiperefreshlayout:1.1.0`
- **Web Engine**: AndroidX WebKit (`1.12.1`) + Chrome Custom Tabs (`androidx.browser:1.8.0`)
- **Native content layer**: OkHttp `4.12.0` + Jsoup `1.18.3` for fetching/parsing Head-Fi HTML and RSS, Coil `2.7.0` for authenticated images (all sharing the WebView's cookie jar and User-Agent)

---

## 3. Core Feature Implementation Details

### A. Login & Session Persistence Across Reboots
Head-Fi runs on **XenForo 2.3** behind **Cloudflare Turnstile**. A user's long-term login state depends on the `xf_user` cookie (issued only when *"Stay logged in"* is checked), alongside `xf_session`, `xf_csrf`, and Cloudflare clearance cookies.

1. **Auto-Checking "Stay Logged In" (`HeadFiScripts.kt`)**:
   - On every page load and DOM mutation (including XenForo modal login overlays), injected JavaScript locates `input[name="remember"]` and forces `checked = true` so Head-Fi always issues the long-lived `xf_user` cookie.
2. **Dual-Layer Cookie Persistence (`SessionPreferencesRepository.kt`)**:
   - **Layer 1 (Native SQLite Flush)**: `CookieManager.getInstance().flush()` is called after every `onPageFinished`, as well as in `MainActivity.onPause()` and `MainActivity.onStop()`.
   - **Layer 2 (DataStore Vault Backup & Cold-Boot Restore)**: Whenever `xf_user` is present, `syncAndBackupCookies()` serializes `xf_*` and `cf_*` cookies into a dedicated `headfi_session` DataStore. On cold boot (after a device reboot), `MainActivity` holds the splash screen until `restoreCookiesIfNeeded()` verifies `CookieManager` and re-injects any missing cookies with `Domain=.head-fi.org; Path=/; Max-Age=31536000; Secure; SameSite=Lax`. **The session store is excluded from cloud backup and device transfer** in `backup_rules.xml` / `data_extraction_rules.xml`; the vault is migrated out of the old `headfi_prefs` file on first use, so upgrading users also stop uploading their session. Preferences remain backed up.
3. **Live XenForo Session Bridge (`HeadFiJsBridge.kt`)**:
   - Exposes `window.HeadFiBridge.onXenForoStateDetected(...)` to read `data-logged-in`, username, avatar URL, unread alerts (`XF.config.visitorCounts.alerts_unviewed`), and unread conversations (`conversations_unread`) into the Material 3 TopAppBar and Account Sheet.

### B. Material 3 Expressive Shell & Navigation
- **`HeadFiAppScreen.kt`**:
  - Uses `NavigationSuiteScaffold` to automatically render a Material 3 **NavigationBar** on compact phones and a **NavigationRail** on tablets/foldables/landscape.
  - Top-level sections: **Home** (`/`), **Forums** (`/forums/`), **What's New** (`/whats-new/posts/`), **Head Gear** (`/showcase/`), and **Classifieds** (`/classifieds/`).
  - Tapping the currently active bottom navigation item smoothly scrolls the page to the top or reloads the section root.
  - **Smart auto-hiding toolbars**: the bottom navigation bar (or rail) slides out of view while scrolling down and slide back in on upward scroll, with hysteresis so small jitters don't toggle them (`SmartBarScrollTracker` in `HeadFiAppScreen.kt`). They are always visible near the top of a page and re-appear on page load.
  - Scroll input comes from **two independent sources** so direction detection can never silently die:
    1. `WebView.setOnScrollChangeListener` — note that `View.OnScrollChangeListener`'s SAM signature is `(v, scrollX, scrollY, oldScrollX, oldScrollY)`; binding the vertical positions to the wrong parameters makes `scrollY` always read `0` and the bars never hide. This was the original bug.
    2. A passive `scroll` listener injected by `HeadFiScripts.kt` that reports `window.scrollY` deltas through `HeadFiJsBridge.onPageScrolled(...)`.
  - A 250 ms "settle" window after each visibility flip absorbs the scroll blip caused by the bars themselves resizing the WebView (which clamps `scrollY` at the very bottom of a page and would otherwise ping-pong the bars forever).
- **No app bar and no floating button** (v1.5.0). The floating nav button and its overflow menu were removed, and so was the scroll-to-top FAB (the site has its own). The bottom bar (or rail on wide screens) is user-configurable: **Account & Settings → Bottom Bar** picks up to 4 items and their order (Profile is the fifth slot). Items are sections, back, forward, refresh, search, find in page, share, open in browser, and (signed-in only) watched threads, messages, bookmarks and alerts. Profile is always last and carries the unread badge. The default is Home, Forums, What's New and Classifieds. Stored in DataStore as `bottom_bar_items`, in user order. Models live in `model/BottomBarItem.kt`.
  - **Rendering (v1.5.0):** a custom bar (`HeadFiBottomBar` / `BottomBarSlot` in `HeadFiAppScreen.kt`), not Material's `NavigationBar`. Slots are equal width; the active section gets a short primary tab indicator above its icon, a primary-coloured icon and label, and a slightly wider slot (`animateFloatAsState` on the weight). Labels are `labelSmall`, single line, with ellipsis as a last resort. `BottomBarItem.shortLabel` gives a compact label where space is tight (*What's New* shows as *News*). Each tap sends a light haptic tick. The bar holds at most 4 selectable items plus Profile (`MAX_SELECTED = 4`). Signed-out users do not see the member items. The rail on wide screens still uses `NavigationRailItem`.
  - The site's own header is left alone, which is also why the "hide the site header" setting was removed (see D).
  - The **loading indicator** was moved off the app bar onto the top edge of the WebView (full width, 3 dp), since a floating bar has no full-width surface to hang it on.
- **Find in page**: bottom bar → *Find* (or the Find in Page option) opens an inline bar driven by `WebView.findAllAsync()` / `findNext()` with a live `n/m` match counter. Predictive back closes it (and calls `clearMatches()`) before it goes back in history.
- **Sign-in prompt**: a dismissible card appears at the top of the page whenever the user has neither a live session nor a `xf_user` cookie. Dismissal persists (`sign_in_prompt_dismissed`) and is reset on sign-out. The profile button at the end of the bottom bar is the other entry point — it shows a login glyph and a "Sign in" label until a session exists (`profileIconFor` / `profileLabelFor`).
- **Member shortcuts** (Watched Threads, Messages, Bookmarks, Alerts) are bottom-bar options that are hidden while signed out (`BottomBarItem.isVisibleTo`).
- Also supports **pull-to-refresh** (see D), **Predictive Back (`BackHandler`)** — which closes the find-in-page bar before it pops history — and a native **Search Dialog** (`SearchHeadFiDialog.kt`) with quick sub-forum chips, reached from the bottom bar's *Search* option.
- **`AccountAndSettingsSheet.kt`**:
  - Material 3 `ModalBottomSheet` displaying current session status, Sign In / Log Out actions, quick member links (**Alerts**, **Private Messages**, **Watched Threads**, **Bookmarks**, **Articles**), and live appearance toggles.
- **`Theme.kt`**:
  - Implements Material 3 Expressive shapes (`ExpressiveShapes`), typography, a fixed Head-Fi color scheme (Light/Dark/System modes plus an optional **Pure AMOLED Black (`#000000`)** surface override). Dynamic (Material You) color was removed for readability.

### C. Monochrome Head-Fi Adaptive Icon
- Located in:
  - `app/src/main/res/drawable/ic_launcher_monochrome.xml`
  - `app/src/main/res/drawable/ic_launcher_foreground.xml`
  - `app/src/main/res/drawable/ic_launcher_background.xml`
  - `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` & `ic_launcher_round.xml`
- Uses an `evenOdd` vector path reproducing the official Head-Fi split-contrast headphone mark (left half positive, right half negative inside a rounded box) above the rounded `"Head-Fi"` badge.
- Works both as an Android 13+ `<monochrome>` themed icon (tinted dynamically by the user's launcher) and as a permanent monochrome white-on-obsidian icon when themed icons are turned off.

### D. WebView Enhancements, File Uploads & Ad Blocker
- **Pull to refresh**: `HeadFiWebView` wraps the `WebView` in an Android `SwipeRefreshLayout` inside the `AndroidView` factory. This is deliberate — `SwipeRefreshLayout` is a ViewGroup that intercepts the drag in `onInterceptTouchEvent` *before* the WebView sees it and asks the child whether it can scroll up. Compose's `PullToRefreshBox` (which this replaced) relies on nested scroll, and a raw `AndroidView` child does not feed scroll back into the Compose chain, so the gesture frequently never fires for a WebView. The spinner is cleared from `onPageFinished`, `onProgressChanged` (>= 100) and `onReceivedError`, and is ignored while a load is already in flight.
- **`HeadFiWebView` must hold the `WebView` in Compose state, not a plain local `var`** (`var webViewInstance by remember { mutableStateOf<WebView?>(null) }`). A plain local `var` is re-initialised to `null` on *every* recomposition of `HeadFiWebView`, while the `AndroidView` factory runs only once — so after the first recomposition the settings-driven `LaunchedEffect`s (dark CSS, ad blocker, header hiding, text zoom, site layout) all silently ran against `null` and did nothing. This was live for several releases and is invisible except that a settings toggle appears to have no effect until the page is reloaded. Do not "simplify" this back to a local var.
- **Cloudflare Turnstile Compatibility (`HeadFiWebView.kt`)**:
  - Strips `; wv` and `Version/X.X` tokens from the WebView `userAgentString` so Cloudflare Turnstile challenges and login flows treat the client as standard Chrome Mobile.
  - `AdBlockEngine.kt` explicitly allowlists `challenges.cloudflare.com`, `cloudflare.com`, and `cdn.head-fi.org` while blocking 30+ third-party ad/tracker domains.
  - While the WebView is on `/bot/challenge`, `shouldInterceptRequest` bypasses the ad blocker entirely. The challenge widget's own error text blames privacy tools, and one wrongly-blocked subresource can strand a fresh install, so the blocker steps aside for the duration of the challenge (tracked by an `AtomicBoolean` set in `onPageStarted`).
   - `TurnstileChallengeWatcher` (`HeadFiWebView.kt`) polls for `cf_clearance` while the page is on `/bot/challenge` (up to 3 minutes, every 2 seconds) and reloads the home page once the cookie appears. It is capped at 2 reloads.
- **File Uploads (`MainActivity.kt`)**:
  - Implements `WebChromeClient.onShowFileChooser` with `ActivityResultContracts.StartActivityForResult` supporting single and multiple photo selection (`EXTRA_ALLOW_MULTIPLE`) for forum attachments and Classifieds listings.
  - The picker intent is taken from `FileChooserParams.createIntent()` when available (it already carries the correct mode and MIME filters), with a hand-built `ACTION_GET_CONTENT` fallback.
  - **Camera capture**: when the field accepts images, `MediaStore.ACTION_IMAGE_CAPTURE` is offered as an `EXTRA_INITIAL_INTENTS` entry next to the document picker. The photo is written to `cacheDir/camera/headfi_<timestamp>.jpg` and shared through the existing `androidx.core.content.FileProvider` (`${applicationId}.fileprovider`). Because some camera apps report `RESULT_CANCELED` even after saving, the result handler falls back to that staged file whenever the returned intent carries no data. Staged files are pruned after 24 h and in `onDestroy()`.
- **Image Saving (`DownloadAndShareHelper.kt`)**:
  - Long-pressing any image in a thread opens a Material 3 dialog to save the image via `DownloadManager` (passing authenticated cookies) or share its URL.
- **Reading & layout controls** (all in `AccountAndSettingsSheet.kt` → *Reading & Layout*):
  - **Text size** — `WebSettings.textZoom`, applied live without a reload. Four steps (85 % / 100 % / 115 % / 130 %).
  - **Site layout** — a segmented Mobile / Desktop select. Desktop mode swaps in a desktop Chrome User-Agent and reloads, because the server only sees the UA on the next request. The scroll offset is saved before the reload and restored after it (`pendingScrollRestore` in `HeadFiWebView.kt`).
  - **Data Saver** — drops image requests in `shouldInterceptRequest` by extension and `Accept` header, leaving stylesheets and scripts alone. Applied live.
    - This branch **must** be keyed off `loadImages`, never off `adBlockerEnabled`. It shipped in v1.3.0 keyed off `adBlockerEnabled`, which is on by default, so it returned an empty response for every image on every page. Head-Fi's CDN is allowlisted in `AdBlockEngine`, so the blocklist was never the cause — the symptom is "the whole site is text".
  - **"Hide Head-Fi's own header" was removed in v1.4.0** — the setting, its preference, its repository key, the ViewModel setter, the `HeadFiWebView` parameter and the CSS that hid `header.p-header` / `#header` / `.p-nav-smallLogo` are all gone. The site's header now stays exactly as Head-Fi renders it. Head-Fi renders **two** logos — one in `<header class="p-header">` and a second in `.p-nav-smallLogo` inside the sticky `.p-navSticky` nav — and the old CSS hid both; keep that in mind if the option is ever revisited, because hiding only the header leaves the nav logo looking like a header.
- **Offline handling**: a connection failure only takes over the whole screen while no page has loaded yet. Once something has rendered, it degrades to a top banner with a Retry button so the user keeps the page they were reading. A default-network callback (`HeadFiAppScreen.kt`) clears the error and reloads when connectivity returns.
- **Notification permission**: if the user denies `POST_NOTIFICATIONS`, a snackbar explains what broke and where to re-enable it. Previously the toggle flipped on and then silently did nothing.

### E. Background Unread Notifications (WorkManager)
- **`notifications/UnreadPollScheduler.kt`** — arms a single unique `PeriodicWorkRequest` (`headfi-unread-poll`) with `ExistingPeriodicWorkPolicy.UPDATE`, `NetworkType.CONNECTED`, and linear 10-minute backoff. Called from `HeadFiApplication.onCreate()` (re-arms after a process kill) and from `MainViewModel` whenever the user toggles the setting or changes the interval.
- **`notifications/UnreadPollWorker.kt`** — a `CoroutineWorker` that:
  1. Bails out unless the opt-in preference is on **and** a persistent `xf_user` session exists.
  2. Builds a `Cookie:` header from the live `CookieManager`, falling back to the DataStore cookie vault (`SessionPreferencesRepository.cookieHeader()`).
  3. `GET`s `https://www.head-fi.org/` (this both reads the counts and keeps the XenForo session warm while the app is closed — the equivalent of `/login/keep-alive`), reading at most the first 400 000 characters.
  4. Parses unread counts from `XF.config.visitorCounts` (`alerts_unviewed`, `conversations_unread`), falling back to the server-rendered `data-badge` attributes on `.p-navgroup-link--alerts` / `--conversations`. If **neither** signal is present the run is treated as "unknown" rather than "zero unread" — a Cloudflare challenge page must not clear notifications.
  5. Notifies only when a count has *grown* since the last run, clears the notification when it drops to 0, and records the new counts in DataStore.
- **`notifications/HeadFiNotifications.kt`** — creates the `headfi_unread` channel and posts notifications with a `PendingIntent` that deep-links straight into `/account/alerts` or `/conversations/`.
- **De-duplication against the app itself**: whenever the JS bridge reports session state, `MainViewModel` writes those counts back to DataStore, so the user is never notified for alerts they already saw in the top bar.
- **Permissions**: `POST_NOTIFICATIONS` is requested at runtime on Android 13+ when the user enables the feature; the worker also checks `areNotificationsEnabled()` before posting.
- **Opt-in UI**: `AccountAndSettingsSheet.kt` exposes the toggle plus frequency chips (15 min / 30 min / hourly / 3 hours — WorkManager enforces a 15 minute floor). A **Check now** button runs one poll immediately (`UnreadPollScheduler.checkNow`, unique one-time work with `manual=true` input).
- **Foreground suppression:** `UnreadPollWorker` checks `ProcessLifecycleOwner` and skips posting notifications while the app is on screen. `lastNotified` is left unchanged, so anything still unread is announced once the app is backgrounded. Manual checks always notify.
- **Watched-thread notifications:** each poll also reads `/watched/threads`, parses it with `ThreadListParser`, and notifies about threads that are unread *and* were not unread at the previous run. Unknown results (challenge/network/markup) are ignored rather than treated as "nothing unread". The set of unread URLs is stored in `LAST_NOTIFIED_WATCHED`; sign-out clears it. This is what makes the notification name the thread, which global alert counts cannot do.
- **Grouping:** unread notifications share `GROUP_UNREAD`. When a run posts two or more, `notifySummary()` adds a group summary (ID 1000) so Android shows one "New activity on Head-Fi" stack instead of unrelated notifications. The summary is cleared at the start of every run so it cannot outlive its children.
- **Session health (Settings → Account card):** `SessionHealthDialog` shows signed-in state, the cookie names in the vault, the exact User-Agent, and the result of a live `GET /forums/` probe. It exists because an expired `cf_clearance` otherwise looks like an empty forum; "Copy diagnostics" puts the same facts on the clipboard.

### F. Native Content Layer (forums, threads, alerts)
The app still ships a WebView, but reading no longer depends on it. Forums, thread
lists, thread pages, What's New, watched threads, search results and alerts are
fetched over HTTP and rendered as Compose screens.

- **Why the WebView cannot be removed.** Head-Fi sits behind Cloudflare Turnstile.
  A request without `cf_clearance` is answered with **`200 OK` and the
  `hfbot_challenge_widget` template** — a challenge page, not content, and not an
  error status. Only a real browser engine earns that cookie, so the WebView is
  kept as the sign-in/challenge mint. Everything else runs natively.
- **The full XenForo REST API is unavailable.** `https://www.head-fi.org/api/`
  answers `400 {"code":"no_api_key_in_request"}`; only a site admin can issue a
  key. Native content therefore comes from parsing server-rendered HTML with
  Jsoup, which is why every parser is defensive and falls back to the WebView.
- **Cookie bridge (`net/WebViewCookieJar.kt`)** — OkHttp reads and writes the
  same `CookieManager` the WebView uses. `xf_user`/`xf_session`/`cf_clearance`
  therefore apply to native requests with no second login. On cold boot, when the
  cookie database may have been trimmed, `HeadFiContentClient` falls back to the
  existing DataStore vault (`SessionPreferencesRepository.cookieHeader()`).
- **User-Agent must match (`net/HeadFiUserAgent.kt`)** — Cloudflare binds
  `cf_clearance` to the UA that solved the challenge. `HeadFiWebView`,
  `HeadFiContentClient`, `UnreadPollWorker` and Coil now all send the *same*
  captured string; `HeadFiApplication.onCreate` prefetches it on the main thread
  so a worker in a cold process does not fall back to a different UA.
- **RSS bypasses the challenge (verified live)** — `.../index.rss` feeds answer
  `200` with real XML even with no clearance cookie: `/forums/-/index.rss`
  (board-wide) and `/forums/<slug>.<id>/index.rss` (per forum) were both fetched
  successfully during this work. `RssThreadListParser` + `ContentRepository.rssFallback`
  therefore fill Home and forum views with their newest threads *before* the user
  has ever passed the challenge. Those results carry `ThreadListPage.isPartial`
  and the UI shows "Showing the latest threads · Verify", because a feed has no
  stickies and no pagination. (What's New's RSS variant is throttled by Head-Fi,
  so it has no fallback.)
- **Challenge detection is mandatory (`net/xenforo/XenForoMarkup.kt`)** — every
  fetch is classified before a parser sees it. `PagePayload.NeedsVerification`
  renders "Verify this session" with a button into the WebView, so an expired
  clearance cookie can never look like an empty forum.
- **Parsers (`net/xenforo/`)** — `ForumsIndexParser`, `ThreadListParser` (forum
  view / What's New / watched / search all share `.structItem--thread`),
  `ThreadPostsParser`, `PostBodyParser` (walks `.bbWrapper` into paragraphs,
  quotes, list items and images, with character-range spans for bold/italic/
  links/code) and `AlertsParser`. They throw `XenForoParseException` instead of
  returning empty data; `ContentRepository` turns that into an error state with
  an "Open in browser" action.
- **Navigation (`ui/native/NativeNavigation.kt`)** — `NativeDestination` covers
  Home, Forums, ThreadList, Thread and Alerts, with its own back/forward stack so
  the bottom bar behaves the same in both modes. `nativeDestinationFor(url)`
  decides native vs. WebView; reply/edit/form URLs are deliberately excluded.
- **Screens (`ui/native/`)** — native Home hub (unread counts, quick actions,
  latest activity), Forums, ThreadList with pager, Thread reader (avatars, post
  date/time, reaction count, quotes, inline images via Coil with the same cookies,
  "Reply in browser") and Alerts. Headers scroll with the content: the app still
  has no pinned app bar.
- **Pagination** — `PageNav` exposes first/previous/next/last URLs read from the
  rendered pager, and every list renders a five-slot pager (first, prev, page x of
  y, next, last). "First" and "prev" are disabled on page 1; "next" and "last" on
  the final page. Search-style query pagers fall back to their rendered links.
- **Post metadata** — the exact posting date/time comes from the `<time>` element
  (`data-time`, or `datetime` normalised for XenForo's `+0000` offset) and is
  formatted locally; the reaction count is derived from the reactions bar's
  summary text ("You", "You and Bob", "You and 3 others") by
  `ReactionSummary.countFrom`.
- **Watching threads (v1.6.0)** — the thread header has a bell that opens a native
  version of Head-Fi's watch dialog: **Alerts only**, **Alerts + email**, or
  **Stop watching**. `ThreadPostsParser` detects the current state from the site's
  switch-overlay control (an `<a href="…/watch">` whose text flips to "Unwatch"),
  and `ThreadPostsPage` carries `isWatching` plus the watch/unwatch URLs. Watching
  POSTs `_xfToken` + `email_subscribe=1|0` to `/threads/<slug>.<id>/watch`;
  stopping posts `_xfToken` + `stop=1` to the **same** route — there is no
  `/unwatch` endpoint, which is why the first attempt silently did nothing. The
  cache is invalidated after either action so the Watched list is not stale.
- **Escape hatch** — *Account & Settings → Reading & Layout → Native reader*.
  Turning it off keeps the user on the same URL but renders it in the WebView,
  and the setting persists. It exists because markup drift is the one real risk
  of a parser-based reader.
- **Post actions (v1.6.0-beta.2, verified in beta.3)** — each post renders Like /
  Quote / Reply / Share. `ThreadPostsParser` reads the reaction endpoint when the
  theme renders it, the per-post quote link (`/reply?quote=<postId>`) and the page
  CSRF token (`data-csrf`). Head-Fi's live theme omits the reaction anchor, so the
  Like route is constructed as `/posts/<postId>/react` from the parsed post id.
  Like POSTs `_xfToken` + `reaction_id` through `HeadFiContentClient.postForm`,
  which treats a challenge page, a login page or an `errors` payload as failure —
  then that post opens in the WebView instead of the app pretending it worked.
  The reaction is applied optimistically (repeat taps toggle it) rather than
  reloading the thread, because a reload would throw away the reader's scroll
  position. Reacted state is read back from `.reactionsBar .reactionsBar-link`
  containing "You". Quote/Reply open XenForo's editor with the quote prefilled;
  Share uses the Android share sheet with the post permalink.
- **Native reading polish** — lists and threads support **pull-to-refresh**
  (`NativePullToRefresh`; Material's `PullToRefreshBox` is fine for Compose
  `LazyColumn`s, unlike the WebView case in D). A refresh keeps the previous
  `Loadable.Ready` value on screen with `refreshing = true` instead of resetting
  to `Loading`, so the list does not blank or jump. `NativeContentHost` keeps one
  `LazyListState` per destination URL, so Back from a thread returns the list to
  where it was. Unread rows carry `unreadUrl` (parsed from an `/unread` link or
  synthesized as `/threads/<slug>.<id>/unread`), and tapping one opens the first
  unread post, like XenForo. The pager label opens a **jump-to-page** dialog, and
  `XenForoUrls.pageUrl()` handles both `/page-N` and query-style (`?page=N`)
  pagers. Native post images open an in-app `ImageViewerDialog` (pinch-zoom,
  save, share, open externally) rather than leaving for the browser. Error copy
  no longer leaks raw `HTTP n` / parser messages; the details go to logcat.
- **Offline fallback** — when a live GET fails with an `IOException`,
  `HeadFiContentClient` retries the same URL with
  `Cache-Control: only-if-cached, max-stale=604800`. Authenticated XenForo pages
  are `private, max-age=0`, so the regular cache would revalidate and fail
  offline; this serves the copy already fetched (up to a week old) if the app
  has read that page before. A cached challenge/login page is never returned —
  only parsed HTML reaches the screens.
- **Home front page (`HomeFeedParser`)** — Home renders Head-Fi's curated
  `hfh_homepage` grid natively: a hero card plus image/title/excerpt rows from
  `#hfh_grid_container .hfh_slot_container`. The image is a CSS
  `background-image` on `a.hfh_slot_background`, so it is read from the inline
  style rather than an `<img>`. Thread cards open natively (at their unread
  route); reviews, showcase and article cards hand off to the WebView. If the
  grid is missing or the session needs verification, the rest of Home still
  loads and a compact notice offers the browser instead. `ContentRepository.homeFeed()`
  exposes it; the first card is the hero, the rest are compact rows.
- **Debug probe** — debug builds `GET /forums/` once at startup and log a
  one-line classification + parse summary under the `HeadFiContent` logcat tag.
  Useful on a device where automated UI inspection is unavailable; never runs in
  release.
- **Verified on device (2026-11-04, OPPO CPH2841 / Android 17, v1.6.0):**
  the native layer works against the **live** site, not just fixtures. Home's RSS
  feed listed real threads; the session bridge reported `areyouanand` with
  "Session cookie active & backed up"; Forums rendered 22 forums in 8 categories;
  a forum listed 51 threads with unread dots and reply counts; a thread rendered
  real `bbWrapper` HTML (paragraphs, bold/italic, links, quotes) with working
  pagination ("Page 1 of 213"); a deep link opened the thread natively.
- **Post actions were verified live:** Like POSTed to `/posts/<id>/react`, the
  site's own UI then showed **"You 👍"** on that post, and after a force-stop and
  relaunch the native button still read the state (parsed from
  `.reactionsBar-link` = "You"). Quote opened XenForo's editor with the post
  prefilled. Reaction counts render ("Like · 11" on a post with 11 reactions),
  post dates render ("Nov 4, 2026 · 10:33 pm"), and post images load inline.
- **Watching was verified end-to-end:** "Alerts only" made the thread appear first
  in `/watched/threads` (2 → 3 threads), and "Stop watching" removed it again
  (3 → 2). Two bugs were found and fixed during this pass:
  1. The watch selector initially matched the site's nav link `/watched/threads`,
     so the POST went to the wrong URL and nothing was watched. It now matches
     only thread-scoped `/threads/<slug>.<id>/watch` anchors.
  2. "Stop watching" first posted to `/threads/<id>/unwatch`, which does not
     exist — XenForo posts `stop=1` to the same `/watch` route. Fixed, and the
     stale Watched list now invalidates its cache after each change.
- **Also fixed during this pass:** from the browser view with no WebView history,
  Back exited the app instead of returning to the native screen. `BackHandler` is
  now enabled whenever a web URL is showing.
- **Verified on device (2026-11-05, CPH2841 / Android 17, 1.7.0-dev):** installed
  over 1.6.0-beta.3 (code 30) with data intact. Native Home loaded the live RSS
  feed and the new curated front page (hero + image rows); a hero card opened the
  MOONDROP thread natively, a review card handed off to the Showcase page, and
  pull-to-refresh kept the content visible while refreshing. The saved username
  now renders ("Signed in as areyouanand") instead of "your account". From
  Forums → Portable Headphones, tapping an unread thread opened **page 9057 of
  9086** (first unread, not page 1); the pager label opened the **Jump to page**
  dialog, and an image in a thread opened the new in-app pinch-zoom viewer with
  save/share/browser/close. Logcat `HeadFiDump` confirmed the front page arrived
  as `hfh_homepage` (18 cards). The temporary homepage-dump scaffolding was
  removed after capturing the markup.
- **Alerts and Search were broken on live 2.3 markup and are now fixed and
  verified on device (2026-11-05):**
  - **Alerts** — the live page no longer has `.contentRow-title`; each alert is
    `li.alert` (carrying `is-unread`) with the sentence in `.contentRow-main`
    and the target in `a.fauxBlockLink-blockLink`. `AlertsParser` now accepts
    that shape (and the older one), reads the full sentence, resolves the post
    permalink, and only treats a zero-row page as empty when the template really
    is `account_alerts`. Live `/account/alerts` rendered 5 unread rows with
    avatars and timestamps.
  - **Search** — results are `.contentRow`, not `.structItem--thread`, so the
    shared `ThreadListParser` returned "No thread rows found". It now falls back
    to search rows, carries the matched `.contentRow-snippet` through
    `ThreadSummary.snippet`, and `nativeDestinationFor` maps `/posts/<id>` to the
    native thread reader. The in-app Search dialog and a direct
    `/search/search?keywords=moondrop` link both rendered **25 results**, with
    the subtitle now reading "25 results" rather than "25 threads".
- **Still unverified:** Showcase / Classifieds hand-off, reaction *removal*
  (tapping a reacted post re-sends the same reaction id; XenForo's toggle
  behaviour there is unconfirmed), the watched notification parser actually
  firing, notification grouping in the shade, Session health dialog, the offline
  cache fallback, and the ad-blocker challenge bypass. The jump dialog's IME Done
  action, the refresh-error banner, and the front-page failure notice are
  compile/lint/test-verified only.

---

## 4. File Map

```text
F:\AI stuff\HeadFi-Org\
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
├── HANDOFF.md
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    ├── src/main/
        ├── AndroidManifest.xml
        ├── java/org/headfi/app/
        │   ├── HeadFiApplication.kt
        │   ├── MainActivity.kt
        │   ├── data/
        │   │   ├── ContentRepository.kt   # fetch + parse + short cache for native screens
        │   │   └── SessionPreferencesRepository.kt
        │   ├── net/
        │   │   ├── ContentFetcher.kt      # interface ContentRepository depends on (test seam)
        │   │   ├── HeadFiContentClient.kt  # OkHttp fetch, classified into PagePayload
        │   │   ├── HeadFiHttpClient.kt     # shared OkHttp client + disk cache
        │   │   ├── HeadFiUserAgent.kt      # one UA for WebView, OkHttp, Coil, worker
        │   │   ├── PagePayload.kt          # Html / NeedsVerification / Unavailable
        │   │   ├── WebViewCookieJar.kt     # OkHttp <-> CookieManager bridge
        │   │   └── xenforo/
        │   │       ├── Alerts.kt / AlertsParser.kt
        │   │       ├── ForumsIndex.kt / ForumsIndexParser.kt
        │   │       ├── HomeFeed.kt / HomeFeedParser.kt  # curated front-page grid
        │   │       ├── PostBodyParser.kt   # bbWrapper -> paragraphs/quotes/images/spans
        │   │       ├── RssThreadListParser.kt  # public feeds: works pre-clearance
        │   │       ├── ThreadList.kt / ThreadListParser.kt  # forum lists + search rows
        │   │       ├── ThreadPosts.kt / ThreadPostsParser.kt
        │   │       ├── XenForoMarkup.kt    # challenge/login detection
        │   │       └── XenForoUrls.kt      # URL -> id helpers + pager
        │   ├── model/
        │   │   ├── BottomBarItem.kt        # bottom bar options, defaults, sign-in-only items, 4-item cap
│   │   ├── HeadFiSection.kt        # 5 nav destinations + QuickDestination (member URLs)
        │   │   └── UserSessionState.kt     # AppPreferences, ThemeMode, PollInterval, TextSize, SiteLayout
        │   ├── ui/
        │   │   ├── HeadFiAppScreen.kt
        │   │   ├── MainViewModel.kt
        │   │   ├── components/
        │   │   │   ├── AccountAndSettingsSheet.kt
        │   │   │   ├── HeadFiMonochromeLogoIcon.kt
        │   │   │   ├── HeadFiWebView.kt
        │   │   │   ├── SearchHeadFiDialog.kt
        │   │   │   └── SessionHealthDialog.kt  # cookie/UA/live-probe diagnostics
        │   │   ├── native/
        │   │   │   ├── AlertsScreen.kt
        │   │   │   ├── ForumsScreen.kt
        │   │   │   ├── HomeScreen.kt
        │   │   │   ├── ImageViewerDialog.kt  # pinch-zoom, save, share
        │   │   │   ├── NativeCommon.kt     # Loadable state, header, rows, avatar
        │   │   │   ├── NativeContentHost.kt
        │   │   │   ├── NativeNavigation.kt # destinations + back/forward stack
        │   │   │   ├── ThreadListScreen.kt
        │   │   │   └── ThreadScreen.kt
        │   │   └── theme/
        │   │       └── Theme.kt
        │   ├── util/
        │   │   └── DownloadAndShareHelper.kt
        │   ├── notifications/
        │   │   ├── HeadFiNotifications.kt
        │   │   ├── UnreadPollScheduler.kt
        │   │   └── UnreadPollWorker.kt
        │   └── webview/
        │       ├── AdBlockEngine.kt
        │       ├── HeadFiJsBridge.kt
        │       └── HeadFiScripts.kt
        └── res/
            ├── drawable/
            │   ├── ic_launcher_background.xml
            │   ├── ic_launcher_foreground.xml
            │   ├── ic_launcher_monochrome.xml
            │   └── ic_stat_headfi.xml
            ├── mipmap-anydpi-v26/
            │   ├── ic_launcher.xml
            │   └── ic_launcher_round.xml
            ├── values/
            │   ├── strings.xml
            │   └── themes.xml
            └── xml/
                ├── backup_rules.xml
                ├── data_extraction_rules.xml
                └── file_paths.xml
    └── src/test/                          # JVM-only; parsers take raw HTML
        ├── java/org/headfi/app/
        │   ├── data/ContentRepositoryTest.kt  # fake ContentFetcher; cache + error mapping
        │   └── net/xenforo/
        │       ├── AlertsParserTest.kt
        │       ├── ForumsIndexParserTest.kt
        │       ├── HomeFeedParserTest.kt
        │       ├── PostBodyParserTest.kt
        │       ├── RssThreadListParserTest.kt
        │       ├── ThreadListParserTest.kt
        │       ├── ThreadPostsParserTest.kt
        │       ├── XenForoMarkupTest.kt
        │       └── XenForoUrlsTest.kt
        └── resources/fixtures/
            ├── alerts.html
            ├── challenge.html
            ├── forums_index.html
            ├── home_page.html
            ├── rss_threads.xml
            ├── search_results.html
            ├── thread_list.html
            └── thread_posts.html
```

---

## 5. Build, Verification & Next Steps

### Environment Note
- **This workstation now has a working local toolchain.** It lives in `F:\AI stuff\HeadFi-Org\.toolchain\` (gitignored) and `local.properties` points at it:
  - JDK: `.toolchain/jdk-17.0.20.1+1` (Temurin 17)
  - Android SDK: `.toolchain/android-sdk` — `platforms;android-35`, `build-tools;35.0.0`, `platform-tools`, `cmdline-tools/latest`, licenses accepted
  - Gradle 8.11.1 wrapper distribution is cached in `%USERPROFILE%\.gradle\wrapper\dists`
- User-scope environment variables were written (`JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, plus `java`/`platform-tools`/`cmdline-tools` on `Path`), so new terminals resolve them automatically. They are **not** inherited by already-running processes.
- `.\gradlew.bat assembleDebug assembleRelease` **passes** as of v1.5.0: both APKs build, the release APK keeps `UnreadPollWorker` unobfuscated (verified in `mapping.txt` and in `classes.dex`), and the merged manifest contains `WorkManagerInitializer`, `POST_NOTIFICATIONS`, and the `FileProvider` authority.
- **The app is running on a real device, and that has already paid off twice.** Two regressions shipped in v1.3.0 and were reported from actual use, not from a test run: images not loading anywhere (Data Saver keyed off the ad-blocker flag instead of `loadImages`), and "hide the site header" leaving Head-Fi's second logo in the sticky nav visible. Both are fixed in v1.3.1 — see section D.
- **Device: OPPO CPH2841, Android 16 (SDK 36).** A physical phone was attached over adb for the v1.5.0 verification pass. It was **not attached** while the native content layer (section F) was built, so that work is compile- and test-verified only.
- **Images are now confirmed loading on-device.** Verified by inspecting the WebView HTTP cache of a fresh debug install of the fixed build (`adb shell run-as org.headfi.app.debug` — see the caveat below on why `run-as` is the useful trick here). The cache held four image responses from `cdn.head-fi.org` totalling ~500 KB: `HF_SponsorFlag_01a_Daylight.png` (308 KB), `Head-Fi_Featured_Image_01b_Daylight.jpg` (118 KB), `Head-Fi_Favicon_01b.png` (36 KB) and `HFXT_Head-Fi_Logo_800x170.png` (35 KB). Under the v1.3.0 bug every image request returned an empty response, so none of these could exist. **This is the technique to reach for when you cannot see the screen**: dump the cache keys with `head -c 400 <entry> | strings | grep -i http`, which prints the URL Chromium stored for each entry.
- **Automated UI inspection is not available on this device.** Do not plan on it:
  - `uiautomator dump` fails with `ERROR: null root node returned by UiTestAutomationBridge` on every attempt. OPPO blocks it.
  - `dumpsys activity top` returns the view hierarchy of an unrelated OPPO Settings window, not the app under test.
  - WebView remote debugging is dead: the `webview_devtools_remote_<pid>` abstract socket exists and accepts TCP connections (`adb forward tcp:9223 localabstract:webview_devtools_remote_<pid>`), but never responds to HTTP.
  - Compose **off-state** switches are effectively invisible to screenshot analysis — an outlined Material3 `Switch` track is mostly transparent, so the right-hand side of a settings row reads as flat background. You cannot locate toggles by pixel statistics.
  - What does work: `screencap` (pipe through `cmd /c ... > file.png`; PowerShell's `>` and `Set-Content -Encoding Byte` both corrupt binary), plus `run-as` on the debug build for app-private data.
- **Open risk — Cloudflare Turnstile does not auto-clear on a fresh profile.** A clean install lands on `/bot/challenge` and stays there; tapping the widget at several coordinates and waiting did not complete it. The user's own install carries a `cf_clearance` cookie so it passes, but if that cookie ever expires or app data is cleared, the app will sit on the challenge with no obvious way past it. This is untriaged and is the most likely next user-visible complaint.
- **Verified on device (v1.5.0 release, via screenshots):** the bottom bar layout, labels and tab indicator.
- **Still compile-verified only:** bottom bar customisation, ordering and persistence; the reconnect callback; the Turnstile challenge watcher; Check now; scroll restore on layout switch; foreground suppression; find-in-page; the offline banner; camera capture; and the live application of text zoom and Data Saver. The header hiding toggle is gone, so there is nothing left to verify there.
- **Native content layer (section F) — verified how?** `.\gradlew.bat testDebugUnitTest` runs 52 JVM tests against captured-shape XenForo fixtures (plus `ContentRepository` with a fake `ContentFetcher`, `PostBodyParser`, `HomeFeedParser`, `AlertsParser` and `XenForoUrls`), `.\gradlew.bat lintDebug` is clean, and `assembleDebug`/`assembleRelease` both pass with R8 (confirmed the parsers, `ContentRepository`, `HeadFiContentClient` and the native screens survive shrinking in `mapping.txt`). What remains unverified is the real thing: whether Head-Fi's live markup matches those selectors, and whether `cf_clearance` obtained by the WebView is accepted by OkHttp. Run the debug build on a device and watch `adb logcat -s HeadFiContent` — it logs whether `/forums/` came back as HTML (with category/forum counts) or as a challenge page. If it is a challenge page even after opening the site in the browser view, the UA/cookie bridge is the first suspect.
- Note: `gradlew` (the Unix shell script) must keep its executable bit — it was briefly committed as `100644`, which broke a Linux CI run, and was fixed in `ee3b4b0`. Windows builds are unaffected either way, so this is easy to regress by accident on a Windows checkout.

### Release & Versioning (v1.6.0)
- **v1.6.0 was published to GitHub Releases on 2026-11-04** as the repo's **Latest release** (tag `v1.6.0`, asset `HeadFi-v1.6.0.apk`, versionCode **41**, run [41881210572](https://github.com/areyouanand/headfi-unofficial/actions/runs/41881210572)). It is the first stable release with the native reader; the three pre-releases stay on the releases page but are superseded.
- A **`stable` branch** was created at the v1.6.0 commit. `main` continues to receive work; cherry-pick or fast-forward `stable` when releasing.
- **Version codes are now the GitHub Actions run number + 100.** Commit-count versioning was not monotonic: a hotfix tagged on `stable` after `main` had advanced could produce a lower code than an already-published APK, which Android refuses to install over it. `GITHUB_RUN_NUMBER` only ever grows, and the +100 offset keeps new builds above the local side-loaded builds (up to code 30) from the native-reader development cycle. It must only ever increase.
- v1.6.0 also adds first/last pagination, per-post date/time and reaction counts, inline post images, and native thread watching (alerts only / alerts + email / stop watching).

### Release & Versioning (v1.6.0-beta)
- **v1.6.0-beta was published to GitHub Releases on 2026-11-04 as a pre-release** (tag `v1.6.0-beta`, versionCode **17** from the commit count, asset `HeadFi-v1.6.0-beta.apk`). Run [41836169433](https://github.com/areyouanand/headfi-unofficial/actions/runs/41836169433) ran the new `testDebugUnitTest` step and then built/published. Because it is a pre-release it does **not** replace v1.5.0 as the repo's Latest release.
- The release workflow now treats any tag containing a hyphen (`v1.6.0-beta`, `-rc1`, …) as a pre-release: `prerelease: true`, `make_latest: false`, and a beta notice in the release body. A stable `vX.Y.Z` tag behaves exactly as before.
- **v1.6.0-beta.2** (2026-11-04, run [41869733335](https://github.com/areyouanand/headfi-unofficial/actions/runs/41869733335), versionCode **19**) adds per-post **Like / Quote / Reply / Share**. It supersedes `v1.6.0-beta`; both stay on the releases page as pre-releases, and v1.5.0 is still the repo's Latest release.
- versionCode 17 is above v1.5.0's 15, so the beta installs cleanly over an existing release without uninstalling.

### Release & Versioning (v1.5.0)
- Release APKs are versioned from the command line: `./gradlew assembleRelease "-PAPP_VERSION_NAME=1.5.0" "-PAPP_VERSION_CODE=14"`. Quote the arguments in PowerShell, or the dots split the argument and Gradle fails.
- **v1.5.0 was published to GitHub Releases on 2026-10-23.** The tag `v1.5.0` had never been pushed, so for three days GitHub Releases only carried up to v1.4.0 even though `main` was already at the 1.5.0 commit — **CI only publishes when a `v*` tag is pushed**, and nothing else creates releases. Pushing the annotated tag on `6a2379e` triggered the `Build & Publish APK` workflow (run [39955959029](https://github.com/areyouanand/headfi-unofficial/actions/runs/39955959029)), which released `HeadFi-v1.5.0.apk` as versionCode **15** (the commit count, per `release.yml`). The phone's hand-built install is versionCode 14, so the published APK upgrades over it cleanly (15 > 14, the check described below). The next release just needs a `v*` tag on the release commit — same as v1.0.0 through v1.5.0.
- The version code must increase on every install. The phone had 1.4.0 (code 13), so a build with the default code 1 was rejected with `INSTALL_FAILED_VERSION_DOWNGRADE`. `release.yml` now derives it from `GITHUB_RUN_NUMBER + 100`, which is monotonic across tags and branches; the first release after this change jumps well above v1.6.0's code 41 and installs over it cleanly.
- Installing over an existing release keeps its data. Uninstalling the debug package (`org.headfi.app.debug`) removes its data, including its login.
- Screenshots: `adb shell screencap -p /sdcard/x.png`, then `adb pull`. If the viewer cannot decode the file, crop it with `System.Drawing` (`powershell -File`, not inline, so PowerShell does not expand the `$` variables).

### How to Build & Test in Android Studio
1. Open `F:\AI stuff\HeadFi-Org` in **Android Studio Ladybug (2024.2.1) or newer**.
2. Android Studio will pick up the SDK from the existing `local.properties` (`sdk.dir=F:/AI stuff/HeadFi-Org/.toolchain/android-sdk`) and the JDK from `JAVA_HOME`. Run **Sync Project with Gradle Files**.
3. Run the `app` configuration on an emulator or physical Android device (API 26–35). A physical OPPO CPH2841 on Android 16 is attached over adb and is the practical option; no emulator is installed. Note that the debug APK installs as **`org.headfi.app.debug`** — a *separate package* from the release `org.headfi.app`, so both can be installed side by side without uninstalling. Expect a fresh install to stall on the Cloudflare Turnstile challenge (see section 5's open risk).
4. **Verify Login Persistence**:
   - Tap the top-right Sign In button (`AccountAndSettingsSheet` -> **Sign In to Head-Fi.org**).
   - Log in with your Head-Fi credentials.
   - Verify your username and the unread badge appear on the profile button at the end of the bottom navigation bar (there is no longer a top app bar).
   - Reboot the Android device or force-stop the app and relaunch — confirm your session remains logged in and resumes on your last visited thread.
5. **Verify Smart Auto-Hide Toolbars**:
   - Open any long thread and scroll down past ~220 px: the bottom navigation bar (or rail) should slide away.
   - Scroll up a little: both should slide straight back.
   - Scroll all the way to the very bottom of a page and keep scrolling: the bars must hide and *stay* hidden (no flickering/ping-pong).
   - Jump back to the top of the page: the bars should always be visible there.
6. **Verify Camera Capture**:
   - Start a new Classifieds listing or attach a photo to a post, then tap the file/attachment button.
   - The chooser sheet should offer **Camera** next to the document picker.
   - Take a photo, confirm it, and verify the attachment appears in the form.
   - Also test the "back out without shooting" path — the upload field should stay empty, not show a blank attachment.
7. **Verify Background Unread Notifications**:
   - Sign in, then open `AccountAndSettingsSheet` -> **Background Unread Notifications** and enable it (grant the notification permission on Android 13+).
   - Pick a 15-minute interval, force-stop the app, and trigger an unread alert/PM from another device or the desktop site.
   - Within ~15 minutes a notification should appear; tapping it should deep-link into `/account/alerts` or `/conversations/`.
   - Read the alert in the app and confirm the notification disappears.
8. **Verify Pull-to-Refresh** (highest regression risk — see the SwipeRefreshLayout note in D):
   - Open any thread, scroll to the very top, and drag down. The refresh spinner should appear and the page should reload.
   - Scroll down ~1 screen and drag down again — the gesture must **not** trigger, because the child can still scroll up.
   - Pull to refresh while a page is still loading — it should be ignored, not queue a second load.
   - Watch that the spinner actually disappears when the page settles (it is cleared from three separate callbacks for this reason).
9. **Verify Find in Page**:
   - Open a long thread, then bottom bar → **Find** (added via Account & Settings → Bottom Bar). The keyboard should come up automatically.
   - Type a common word (e.g. "the") — the `n/m` counter should count up and match the page.
   - Step forward and backward past the last match; it should wrap or stop, not crash.
   - Press system back — the find bar should close and the highlight should clear **before** the WebView goes back in history. Pressing back a second time should then navigate back.
   - Search for a string that does not exist — the counter should read `0/0` and the step buttons should be disabled.
10. **Verify Reading & Layout** (`AccountAndSettingsSheet` -> **Reading & Layout**):
    - **Text size**: step through Small → Extra large. Body text should rescale without a reload; confirm it also survives a page navigation and an app restart (it is persisted).
    - **Site layout**: switch to Desktop. The page should reload and serve the desktop site; confirm the desktop nav renders and that flipping back to Mobile restores it. Note this reloads the page, so scroll position is lost — verify that is tolerable in practice.
    - **Data Saver**: with it **off** (the default), confirm images load at all — this is the check that catches a mis-keyed block. Then enable it and reload: images should disappear but layout, CSS and scripts must keep working. (If the page goes unstyled, the `Accept`-header heuristic in `isImageRequest()` is too aggressive.)
    - **Hide Head-Fi's own header** no longer exists as a setting; the site's header is always shown now. Instead, confirm the opposite: the site header and **both** its logos (the `<header class="p-header">` one and the `.p-nav-smallLogo` one in the sticky nav) are visible, and that the app's bottom bar does not cover the content.
11. **Verify Offline Handling**:
    - With the app open on a loaded page, enable airplane mode and navigate to a new section. A **banner** with a Retry button should appear and the previous page should still be visible and readable behind it — it must not go full-screen.
    - Cold-start the app in airplane mode. The **full-screen** error should appear instead, since there is no page behind it.
    - Tap Retry after re-enabling the network and confirm it recovers.
12. **Verify the Sign-In Prompt & Permission Feedback**:
    - Sign out (or clear the app's data). A sign-in prompt card should appear at the top of the page, and the member options (Watched, Messages, Bookmarks, Alerts) should be hidden from the bottom bar.
    - Tap Sign in and confirm it lands on `/login/` with "Stay logged in" pre-checked (see A).
    - Dismiss the prompt and confirm it does not reappear until the process is restarted.
    - Enable Background Unread Notifications and **deny** the notification permission. A snackbar should explain that unread alerts will not be posted and where to re-enable them. Confirm the toggle's state is still coherent afterwards.
13. **Verify the Smart Bars Still Behave** (regression check for the `webViewInstance` state change in D):
    - With a page loaded, open the account sheet and toggle dark mode / ad blocker / header hiding *without* reloading the page. The change should take effect immediately. If nothing happens, the `LaunchedEffect` in `HeadFiWebView.kt` is running against a null `WebView` again.

### Potential Future Enhancements
- **Native conversations and bookmarks** — Messages and Bookmarks still hand off to the WebView from the native Home screen. They are the last obvious read-only screens outside the native layer.
- **Two-pane tablet/foldable layout** — the rail exists, but thread lists and the reader still replace each other. A list-detail split is the natural next adaptive step.
- **Offline reading** — authenticated XenForo pages are `private, max-age=0`, so the OkHttp cache does not retain them. A small explicit "saved threads" store (or Room) would let watched threads be read offline.
- **Full string extraction / localization** — almost every Compose label is still a hardcoded English literal (`strings.xml` is largely unused). This is a mechanical but broad pass, best done file by file.
- **Mark thread/forum read** — `first unread` is implemented; an explicit "Mark read" action would need XenForo's POST route verified on device first.

### Known Debt & Rough Edges
- **Bottom bar label width.** Four items plus Profile fit the labels at typical phone widths, but "Classifieds" and "Bookmarks" may still ellipsize on screens narrower than about 360 dp. The next step would be to shorten the labels or drop the cap to three.
- **Default bar changed to Home, Forums, What's New and Classifieds (Head Gear dropped).** Existing five-item selections keep their first four entries in saved order.
- **`WebView.FindListener` is deprecated as of API 33.** Find-in-page still uses it (with `@Suppress("DEPRECATION")`) to get the match count, and there is no clean replacement — the modern API is `findAllAsync`/`findNext` plus your own DOM query. It works today, but it will need reworking.
- **Site-layout switching reloads the page.** The scroll offset is saved before the reload and restored in `onPageFinished` (`HeadFiWebView.kt`), so the position survives, but it is unverified on device.
- **Denied notification permission only raises a snackbar.** There is no path to app settings, so the user has to find it themselves; an intent to `ACTION_APPLICATION_DETAILS_SETTINGS` would close that loop.
- **Text zoom and Data Saver are applied live** to an already-loaded page, which is untested. `textZoom` in particular may need a reflow before the change is fully visible.
- **Denied notification permission** now shows a snackbar with a *Settings* action that opens the app's notification settings.
- **The offline banner reconnects itself.** A default-network callback clears the error and reloads when the network returns (`HeadFiAppScreen.kt`). Retry still works.
- **Cloudflare Turnstile can strand a fresh install (highest-severity open risk).** Confirmed on device: a clean install lands on `/bot/challenge` and does not auto-clear. Tapping the widget at several coordinates and waiting roughly two minutes did not complete it. Existing installs survive on a `cf_clearance` cookie, so this stays invisible until that cookie expires or the user clears app data — at which point the app looks broken with no visible way forward. Worth investigating: the widget is `data-size="compact"` inside `.hfbot_challenge_widget`, and the page's own error text blames ad blockers and privacy extensions for exactly this failure mode. `AdBlockEngine` already allowlists `challenges.cloudflare.com`, so the likely culprit is something else — possibly the scrubbed User-Agent, or the challenge needing a second navigation once `cf_clearance` is set.
- **Mitigation added (unverified on device):** `TurnstileChallengeWatcher` in `HeadFiWebView.kt` polls for `cf_clearance` while the page is on `/bot/challenge` (for up to 3 minutes) and reloads the home page once it appears. It is capped at 2 reloads. If this does not fix the stall, the User-Agent and client-hint headers are the next suspects.
