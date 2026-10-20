# Project Handoff: Head-Fi for Android (`org.headfi.app`)

## 1. Executive Summary
**Head-Fi for Android** is a native Kotlin + Jetpack Compose Android client for browsing [Head-Fi.org](https://www.head-fi.org/). It wraps Head-Fi's XenForo 2.3 platform inside a **Material 3 Expressive / Material You** adaptive shell with guaranteed login persistence across app restarts and Android device reboots, custom dark/AMOLED web styling, ad/tracker blocking, multi-photo uploads, and a **monochrome Head-Fi vector adaptive launcher icon**.

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
  - Supports **PullToRefreshBox**, **Predictive Back (`BackHandler`)**, a native **Search Dialog** (`SearchHeadFiDialog.kt`) with quick sub-forum chips, and a **Scroll-to-Top FAB**.
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
- **Cloudflare Turnstile Compatibility (`HeadFiWebView.kt`)**:
  - Strips `; wv` and `Version/X.X` tokens from the WebView `userAgentString` so Cloudflare Turnstile challenges and login flows treat the client as standard Chrome Mobile.
  - `AdBlockEngine.kt` explicitly allowlists `challenges.cloudflare.com`, `cloudflare.com`, and `cdn.head-fi.org` while blocking 30+ third-party ad/tracker domains.
- **File Uploads (`MainActivity.kt`)**:
  - Implements `WebChromeClient.onShowFileChooser` with `ActivityResultContracts.StartActivityForResult` supporting single and multiple photo selection (`EXTRA_ALLOW_MULTIPLE`) for forum attachments and Classifieds listings.
  - The picker intent is taken from `FileChooserParams.createIntent()` when available (it already carries the correct mode and MIME filters), with a hand-built `ACTION_GET_CONTENT` fallback.
  - **Camera capture**: when the field accepts images, `MediaStore.ACTION_IMAGE_CAPTURE` is offered as an `EXTRA_INITIAL_INTENTS` entry next to the document picker. The photo is written to `cacheDir/camera/headfi_<timestamp>.jpg` and shared through the existing `androidx.core.content.FileProvider` (`${applicationId}.fileprovider`). Because some camera apps report `RESULT_CANCELED` even after saving, the result handler falls back to that staged file whenever the returned intent carries no data. Staged files are pruned after 24 h and in `onDestroy()`.
- **Image Saving (`DownloadAndShareHelper.kt`)**:
  - Long-pressing any image in a thread opens a Material 3 dialog to save the image via `DownloadManager` (passing authenticated cookies) or share its URL.

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
        │   │   ├── HeadFiSection.kt
        │   │   └── UserSessionState.kt
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
- `.\gradlew.bat assembleDebug assembleRelease` **passes** as of this handoff: both APKs build, the release APK keeps `UnreadPollWorker` unobfuscated (verified in `mapping.txt` and in `classes.dex`), and the merged manifest contains `WorkManagerInitializer`, `POST_NOTIFICATIONS`, and the `FileProvider` authority.
- Note: `gradlew` (the Unix shell script) lost its executable bit, which broke a previous Linux CI run. Windows builds are unaffected.

### How to Build & Test in Android Studio
1. Open `F:\AI stuff\HeadFi-Org` in **Android Studio Ladybug (2024.2.1) or newer**.
2. Android Studio will pick up the SDK from the existing `local.properties` (`sdk.dir=F:/AI stuff/HeadFi-Org/.toolchain/android-sdk`) and the JDK from `JAVA_HOME`. Run **Sync Project with Gradle Files**.
3. Run the `app` configuration on an emulator or physical Android device (API 26–35). No device is currently attached (`adb devices` is empty).
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

### Potential Future Enhancements
- **Suppress notifications while the app is in the foreground** — e.g. via `ProcessLifecycleOwner` (`androidx.lifecycle:lifecycle-process`) so an alert that arrives while the user is actively reading doesn't also raise a notification.
- **Per-thread / watched-thread notifications** — the poller currently only looks at global alert and conversation counts; scraping `/watched/threads` would allow notifying on replies to specific threads.
- **Manual "Check now" action** — a `OneTimeWorkRequest` triggered from the account sheet so users can force an immediate refresh instead of waiting for the interval.
- **Notification grouping** — combine the alerts and conversations notifications into a single summary group when both fire in the same run.
