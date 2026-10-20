# Project Handoff: Head-Fi for Android (`org.headfi.app`)

## 1. Executive Summary
- **Head-Fi for Android** is a native Kotlin + Jetpack Compose Android client for browsing [Head-Fi.org](https://www.head-fi.org/). It wraps Head-Fi's XenForo 2.3 platform inside a **Material 3 Expressive / Material You** adaptive shell with guaranteed login persistence across app restarts and Android device reboots, custom dark/AMOLED web styling, ad/tracker blocking, background unread notifications, reading and layout controls (text size, Mobile/Desktop site layout, Data Saver), multi-photo uploads with camera capture, and a **monochrome Head-Fi vector adaptive launcher icon**.

---

## 2. Architecture & Tech Stack
- **Language & Runtime**: Kotlin `2.1.10`, JVM Target `17`
- **Android SDK**: `minSdk = 26` (Android 8.0), `targetSdk = 35` / `compileSdk = 35` (Android 15)
- **UI Framework**: Jetpack Compose (`2025.02.00` BOM), Material 3 (`1.3.1`), Material 3 Adaptive Navigation Suite (`NavigationSuiteScaffold`)
- **State & Persistence**:
  - `MainViewModel` (`StateFlow` reactive UI state)
  - Jetpack `DataStore` Preferences (`SessionPreferencesRepository`)
  - Android `CookieManager` SQLite persistence + JSON cookie vault backup/restore
- **Background Work**: WorkManager (`2.10.1`) `CoroutineWorker` + `PeriodicWorkRequest` for background unread polling
- **Pull to Refresh**: `androidx.swiperefreshlayout:swiperefreshlayout:1.1.0`
- **Web Engine**: AndroidX WebKit (`1.12.1`) + Chrome Custom Tabs (`androidx.browser:1.8.0`)

---

## 3. Core Feature Implementation Details

### A. Login & Session Persistence Across Reboots
Head-Fi runs on **XenForo 2.3** behind **Cloudflare Turnstile**. A user's long-term login state depends on the `xf_user` cookie (issued only when *"Stay logged in"* is checked), alongside `xf_session`, `xf_csrf`, and Cloudflare clearance cookies.

1. **Auto-Checking "Stay Logged In" (`HeadFiScripts.kt`)**:
   - On every page load and DOM mutation (including XenForo modal login overlays), injected JavaScript locates `input[name="remember"]` and forces `checked = true` so Head-Fi always issues the long-lived `xf_user` cookie.
2. **Dual-Layer Cookie Persistence (`SessionPreferencesRepository.kt`)**:
   - **Layer 1 (Native SQLite Flush)**: `CookieManager.getInstance().flush()` is called after every `onPageFinished`, as well as in `MainActivity.onPause()` and `MainActivity.onStop()`.
   - **Layer 2 (DataStore Vault Backup & Cold-Boot Restore)**: Whenever `xf_user` is present, `syncAndBackupCookies()` serializes `xf_*` and `cf_*` cookies into `DataStore` (`headfi_prefs`). On cold boot (after a device reboot), `MainActivity` holds the splash screen until `restoreCookiesIfNeeded()` verifies `CookieManager` and re-injects any missing cookies with `Domain=.head-fi.org; Path=/; Max-Age=31536000; Secure; SameSite=Lax`.
3. **Live XenForo Session Bridge (`HeadFiJsBridge.kt`)**:
   - Exposes `window.HeadFiBridge.onXenForoStateDetected(...)` to read `data-logged-in`, username, avatar URL, unread alerts (`XF.config.visitorCounts.alerts_unviewed`), and unread conversations (`conversations_unread`) into the Material 3 TopAppBar and Account Sheet.

### B. Material 3 Expressive Shell & Navigation
- **`HeadFiAppScreen.kt`**:
  - Uses `NavigationSuiteScaffold` to automatically render a Material 3 **NavigationBar** on compact phones and a **NavigationRail** on tablets/foldables/landscape.
  - Top-level sections: **Home** (`/`), **Forums** (`/forums/`), **What's New** (`/whats-new/posts/`), **Head Gear** (`/showcase/`), and **Classifieds** (`/classifieds/`).
  - Tapping the currently active bottom navigation item smoothly scrolls the page to the top or reloads the section root.
  - **Smart auto-hiding toolbars**: the top app bar and bottom navigation bar (or rail) slide out of view while scrolling down and slide back in on upward scroll, with hysteresis so small jitters don't toggle them (`SmartBarScrollTracker` in `HeadFiAppScreen.kt`). Bars are always visible near the top of a page and re-appear on page load.
  - Scroll input comes from **two independent sources** so direction detection can never silently die:
    1. `WebView.setOnScrollChangeListener` — note that `View.OnScrollChangeListener`'s SAM signature is `(v, scrollX, scrollY, oldScrollX, oldScrollY)`; binding the vertical positions to the wrong parameters makes `scrollY` always read `0` and the bars never hide. This was the original bug.
    2. A passive `scroll` listener injected by `HeadFiScripts.kt` that reports `window.scrollY` deltas through `HeadFiJsBridge.onPageScrolled(...)`.
  - A 250 ms "settle" window after each visibility flip absorbs the scroll blip caused by the bars themselves resizing the WebView (which clamps `scrollY` at the very bottom of a page and would otherwise ping-pong the bars forever).
- **Find in page**: overflow menu → *Find in Page* opens an inline bar driven by `WebView.findAllAsync()` / `findNext()` with a live `n/m` match counter. Predictive back closes it (and calls `clearMatches()`) before it goes back in history.
- **Sign-in prompt**: a dismissible card appears at the top of the page whenever the user has neither a live session nor a `xf_user` cookie. Previously the only sign-in entry point was an icon inside the account sheet, behind an auto-hiding app bar.
- **Scroll-to-top FAB** only appears once the user is more than 300 px down the page.
- **Member shortcuts in the overflow menu**: Watched Threads, Conversations and Bookmarks (shown only when signed in), so they are reachable without opening the account sheet.
- Also supports **pull-to-refresh** (see D), **Predictive Back (`BackHandler`)** — which closes the find-in-page bar before it pops history — a native **Search Dialog** (`SearchHeadFiDialog.kt`) with quick sub-forum chips, and a **Scroll-to-Top FAB** that only appears past 300 px.
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
- **File Uploads (`MainActivity.kt`)**:
  - Implements `WebChromeClient.onShowFileChooser` with `ActivityResultContracts.StartActivityForResult` supporting single and multiple photo selection (`EXTRA_ALLOW_MULTIPLE`) for forum attachments and Classifieds listings.
  - The picker intent is taken from `FileChooserParams.createIntent()` when available (it already carries the correct mode and MIME filters), with a hand-built `ACTION_GET_CONTENT` fallback.
  - **Camera capture**: when the field accepts images, `MediaStore.ACTION_IMAGE_CAPTURE` is offered as an `EXTRA_INITIAL_INTENTS` entry next to the document picker. The photo is written to `cacheDir/camera/headfi_<timestamp>.jpg` and shared through the existing `androidx.core.content.FileProvider` (`${applicationId}.fileprovider`). Because some camera apps report `RESULT_CANCELED` even after saving, the result handler falls back to that staged file whenever the returned intent carries no data. Staged files are pruned after 24 h and in `onDestroy()`.
- **Image Saving (`DownloadAndShareHelper.kt`)**:
  - Long-pressing any image in a thread opens a Material 3 dialog to save the image via `DownloadManager` (passing authenticated cookies) or share its URL.
- **Reading & layout controls** (all in `AccountAndSettingsSheet.kt` → *Reading & Layout*):
  - **Text size** — `WebSettings.textZoom`, applied live without a reload. Four steps (85 % / 100 % / 115 % / 130 %).
  - **Site layout** — a segmented Mobile / Desktop select. Desktop mode swaps in a desktop Chrome User-Agent and reloads, because the server only sees the UA on the next request.
  - **Data Saver** — drops image requests in `shouldInterceptRequest` by extension and `Accept` header, leaving stylesheets and scripts alone. Applied live.
    - This branch **must** be keyed off `loadImages`, never off `adBlockerEnabled`. It shipped in v1.3.0 keyed off `adBlockerEnabled`, which is on by default, so it returned an empty response for every image on every page. Head-Fi's CDN is allowlisted in `AdBlockEngine`, so the blocklist was never the cause — the symptom is "the whole site is text".
  - **Hide Head-Fi's own header** — hides the site's duplicate chrome. Head-Fi renders **two** logos: one in `<header class="p-header">` and a second in `.p-nav-smallLogo` inside the sticky `.p-navSticky` nav. Both must be targeted or the nav logo survives and looks like a header. `.p-nav` itself is deliberately left visible, since it carries the site's real navigation (Forums, Articles, Sponsors, search, account).
- **Offline handling**: a connection failure only takes over the whole screen while no page has loaded yet. Once something has rendered, it degrades to a top banner with a Retry button so the user keeps the page they were reading.
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
- **Opt-in UI**: `AccountAndSettingsSheet.kt` exposes the toggle plus frequency chips (15 min / 30 min / hourly / 3 hours — WorkManager enforces a 15 minute floor).

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
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/org/headfi/app/
        │   ├── HeadFiApplication.kt
        │   ├── MainActivity.kt
        │   ├── data/
        │   │   └── SessionPreferencesRepository.kt
        │   ├── model/
        │   │   ├── HeadFiSection.kt        # 5 nav destinations + QuickDestination (member URLs)
        │   │   └── UserSessionState.kt     # AppPreferences, ThemeMode, PollInterval, TextSize, SiteLayout
        │   ├── ui/
        │   │   ├── HeadFiAppScreen.kt
        │   │   ├── MainViewModel.kt
        │   │   ├── components/
        │   │   │   ├── AccountAndSettingsSheet.kt
        │   │   │   ├── HeadFiMonochromeLogoIcon.kt
        │   │   │   ├── HeadFiWebView.kt
        │   │   │   └── SearchHeadFiDialog.kt
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
```

---

## 5. Build, Verification & Next Steps

### Environment Note
- **This workstation now has a working local toolchain.** It lives in `F:\AI stuff\HeadFi-Org\.toolchain\` (gitignored) and `local.properties` points at it:
  - JDK: `.toolchain/jdk-17.0.20.1+1` (Temurin 17)
  - Android SDK: `.toolchain/android-sdk` — `platforms;android-35`, `build-tools;35.0.0`, `platform-tools`, `cmdline-tools/latest`, licenses accepted
  - Gradle 8.11.1 wrapper distribution is cached in `%USERPROFILE%\.gradle\wrapper\dists`
- User-scope environment variables were written (`JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, plus `java`/`platform-tools`/`cmdline-tools` on `Path`), so new terminals resolve them automatically. They are **not** inherited by already-running processes.
- `.\gradlew.bat assembleDebug assembleRelease` **passes** as of this handoff (v1.3.1): both APKs build, the release APK keeps `UnreadPollWorker` unobfuscated (verified in `mapping.txt` and in `classes.dex`), and the merged manifest contains `WorkManagerInitializer`, `POST_NOTIFICATIONS`, and the `FileProvider` authority.
- **The app is running on a real device, and that has already paid off twice.** Two regressions shipped in v1.3.0 and were reported from actual use, not from a test run: images not loading anywhere (Data Saver keyed off the ad-blocker flag instead of `loadImages`), and "hide the site header" leaving Head-Fi's second logo in the sticky nav visible. Both are fixed in v1.3.1 — see section D.
- **Device: OPPO CPH2841, Android 16 (SDK 36).** A physical phone is attached over adb; no emulator is installed.
- **Images are now confirmed loading on-device.** Verified by inspecting the WebView HTTP cache of a fresh debug install of the fixed build (`adb shell run-as org.headfi.app.debug` — see the caveat below on why `run-as` is the useful trick here). The cache held four image responses from `cdn.head-fi.org` totalling ~500 KB: `HF_SponsorFlag_01a_Daylight.png` (308 KB), `Head-Fi_Featured_Image_01b_Daylight.jpg` (118 KB), `Head-Fi_Favicon_01b.png` (36 KB) and `HFXT_Head-Fi_Logo_800x170.png` (35 KB). Under the v1.3.0 bug every image request returned an empty response, so none of these could exist. **This is the technique to reach for when you cannot see the screen**: dump the cache keys with `head -c 400 <entry> | strings | grep -i http`, which prints the URL Chromium stored for each entry.
- **Automated UI inspection is not available on this device.** Do not plan on it:
  - `uiautomator dump` fails with `ERROR: null root node returned by UiTestAutomationBridge` on every attempt. OPPO blocks it.
  - `dumpsys activity top` returns the view hierarchy of an unrelated OPPO Settings window, not the app under test.
  - WebView remote debugging is dead: the `webview_devtools_remote_<pid>` abstract socket exists and accepts TCP connections (`adb forward tcp:9223 localabstract:webview_devtools_remote_<pid>`), but never responds to HTTP.
  - Compose **off-state** switches are effectively invisible to screenshot analysis — an outlined Material3 `Switch` track is mostly transparent, so the right-hand side of a settings row reads as flat background. You cannot locate toggles by pixel statistics.
  - What does work: `screencap` (pipe through `cmd /c ... > file.png`; PowerShell's `>` and `Set-Content -Encoding Byte` both corrupt binary), plus `run-as` on the debug build for app-private data.
- **Open risk — Cloudflare Turnstile does not auto-clear on a fresh profile.** A clean install lands on `/bot/challenge` and stays there; tapping the widget at several coordinates and waiting did not complete it. The user's own install carries a `cf_clearance` cookie so it passes, but if that cookie ever expires or app data is cleared, the app will sit on the challenge with no obvious way past it. This is untriaged and is the most likely next user-visible complaint.
- **Still compile-verified only:** the header-hiding toggle on the live site, find-in-page, the offline banner, camera capture, and the live application of text zoom and Data Saver. The header fix itself is confirmed present in the shipped dex (`classes7.dex` contains `p-nav-smallLogo`, `p-header-logo`, `header.p-header`) and the selectors match the live Head-Fi markup — it just has not been seen working.
- Note: `gradlew` (the Unix shell script) must keep its executable bit — it was briefly committed as `100644`, which broke a Linux CI run, and was fixed in `ee3b4b0`. Windows builds are unaffected either way, so this is easy to regress by accident on a Windows checkout.

### How to Build & Test in Android Studio
1. Open `F:\AI stuff\HeadFi-Org` in **Android Studio Ladybug (2024.2.1) or newer**.
2. Android Studio will pick up the SDK from the existing `local.properties` (`sdk.dir=F:/AI stuff/HeadFi-Org/.toolchain/android-sdk`) and the JDK from `JAVA_HOME`. Run **Sync Project with Gradle Files**.
3. Run the `app` configuration on an emulator or physical Android device (API 26–35). A physical OPPO CPH2841 on Android 16 is attached over adb and is the practical option; no emulator is installed. Note that the debug APK installs as **`org.headfi.app.debug`** — a *separate package* from the release `org.headfi.app`, so both can be installed side by side without uninstalling. Expect a fresh install to stall on the Cloudflare Turnstile challenge (see section 5's open risk).
4. **Verify Login Persistence**:
   - Tap the top-right Sign In button (`AccountAndSettingsSheet` -> **Sign In to Head-Fi.org**).
   - Log in with your Head-Fi credentials.
   - Verify your username and unread badge counts appear in the native TopAppBar.
   - Reboot the Android device or force-stop the app and relaunch — confirm your session remains logged in and resumes on your last visited thread.
5. **Verify Smart Auto-Hide Toolbars**:
   - Open any long thread and scroll down past ~220 px: the top app bar and the bottom navigation bar (or rail) should slide away together.
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
   - Open a long thread, then overflow menu → **Find in Page**. The keyboard should come up automatically.
   - Type a common word (e.g. "the") — the `n/m` counter should count up and match the page.
   - Step forward and backward past the last match; it should wrap or stop, not crash.
   - Press system back — the find bar should close and the highlight should clear **before** the WebView goes back in history. Pressing back a second time should then navigate back.
   - Search for a string that does not exist — the counter should read `0/0` and the step buttons should be disabled.
10. **Verify Reading & Layout** (`AccountAndSettingsSheet` -> **Reading & Layout**):
    - **Text size**: step through Small → Extra large. Body text should rescale without a reload; confirm it also survives a page navigation and an app restart (it is persisted).
    - **Site layout**: switch to Desktop. The page should reload and serve the desktop site; confirm the desktop nav renders and that flipping back to Mobile restores it. Note this reloads the page, so scroll position is lost — verify that is tolerable in practice.
    - **Data Saver**: with it **off** (the default), confirm images load at all — this is the check that catches a mis-keyed block. Then enable it and reload: images should disappear but layout, CSS and scripts must keep working. (If the page goes unstyled, the `Accept`-header heuristic in `isImageRequest()` is too aggressive.)
    - **Hide Head-Fi's own header**: enable it and confirm **both** the big `<header class="p-header">` and the smaller logo inside the sticky nav (`.p-nav-smallLogo`) are gone, with no Head-Fi wordmark left at the top. The site's `.p-nav` navigation should remain. Disable it again to confirm it is reversible.
11. **Verify Offline Handling**:
    - With the app open on a loaded page, enable airplane mode and navigate to a new section. A **banner** with a Retry button should appear and the previous page should still be visible and readable behind it — it must not go full-screen.
    - Cold-start the app in airplane mode. The **full-screen** error should appear instead, since there is no page behind it.
    - Tap Retry after re-enabling the network and confirm it recovers.
12. **Verify the Sign-In Prompt & Permission Feedback**:
    - Sign out (or clear the app's data). A sign-in prompt card should appear at the top of the page and the overflow menu's member shortcuts should be hidden.
    - Tap Sign in and confirm it lands on `/login/` with "Stay logged in" pre-checked (see A).
    - Dismiss the prompt and confirm it does not reappear until the process is restarted.
    - Enable Background Unread Notifications and **deny** the notification permission. A snackbar should explain that unread alerts will not be posted and where to re-enable them. Confirm the toggle's state is still coherent afterwards.
13. **Verify the Smart Bars Still Behave** (regression check for the `webViewInstance` state change in D):
    - With a page loaded, open the account sheet and toggle dark mode / ad blocker / header hiding *without* reloading the page. The change should take effect immediately. If nothing happens, the `LaunchedEffect` in `HeadFiWebView.kt` is running against a null `WebView` again.

### Potential Future Enhancements
- **Suppress notifications while the app is in the foreground** — e.g. via `ProcessLifecycleOwner` (`androidx.lifecycle:lifecycle-process`) so an alert that arrives while the user is actively reading doesn't also raise a notification.
- **Per-thread / watched-thread notifications** — the poller currently only looks at global alert and conversation counts; scraping `/watched/threads` would allow notifying on replies to specific threads.
- **Manual "Check now" action** — a `OneTimeWorkRequest` triggered from the account sheet so users can force an immediate refresh instead of waiting for the interval.
- **Notification grouping** — combine the alerts and conversations notifications into a single summary group when both fire in the same run.

### Known Debt & Rough Edges
- **`WebView.FindListener` is deprecated as of API 33.** Find-in-page still uses it (with `@Suppress("DEPRECATION")`) to get the match count, and there is no clean replacement — the modern API is `findAllAsync`/`findNext` plus your own DOM query. It works today, but it will need reworking.
- **Site-layout switching reloads the page**, so flipping to Desktop mid-thread loses scroll position. Unavoidable given how User-Agents work; could be softened by preserving the scroll offset across the reload.
- **The sign-in prompt is session-scoped only.** It is a `remember { mutableStateOf(true) }`, so it reappears on every cold start until the user signs in. Persisting the dismissal would be friendlier.
- **Denied notification permission only raises a snackbar.** There is no path to app settings, so the user has to find it themselves; an intent to `ACTION_APPLICATION_DETAILS_SETTINGS` would close that loop.
- **Text zoom and Data Saver are applied live** to an already-loaded page, which is untested. `textZoom` in particular may need a reflow before the change is fully visible.
- **`hideSiteHeaderFooter` is applied via injected CSS on every page load**, so it costs a JS round-trip per navigation. Fine at this size, worth revisiting if more CSS-driven settings accumulate.
- **Only five navigation destinations exist.** Member shortcuts were added to the overflow menu instead of a sixth nav item; if the account area grows, a dedicated destination would surface them better than a menu.
- **The offline banner has no connectivity listener**, so "Retry" is the only recovery path — it will not clear itself when the network comes back.
- **Cloudflare Turnstile can strand a fresh install (highest-severity open risk).** Confirmed on device: a clean install lands on `/bot/challenge` and does not auto-clear. Tapping the widget at several coordinates and waiting roughly two minutes did not complete it. Existing installs survive on a `cf_clearance` cookie, so this stays invisible until that cookie expires or the user clears app data — at which point the app looks broken with no visible way forward. Worth investigating: the widget is `data-size="compact"` inside `.hfbot_challenge_widget`, and the page's own error text blames ad blockers and privacy extensions for exactly this failure mode. `AdBlockEngine` already allowlists `challenges.cloudflare.com`, so the likely culprit is something else — possibly the scrubbed User-Agent, or the challenge needing a second navigation once `cf_clearance` is set.
