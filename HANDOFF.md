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
  - Supports **PullToRefreshBox**, **Predictive Back (`BackHandler`)**, a native **Search Dialog** (`SearchHeadFiDialog.kt`) with quick sub-forum chips, and a **Scroll-to-Top FAB**.
- **`AccountAndSettingsSheet.kt`**:
  - Material 3 `ModalBottomSheet` displaying current session status, Sign In / Log Out actions, quick member links (**Alerts**, **Private Messages**, **Watched Threads**, **Bookmarks**, **Articles**), and live appearance toggles.
- **`Theme.kt`**:
  - Implements Material 3 Expressive shapes (`ExpressiveShapes`), typography, Android 12+ **Material You Dynamic Color**, Light/Dark/System modes, and an optional **Pure AMOLED Black (`#000000`)** surface override.

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
- **Image Saving (`DownloadAndShareHelper.kt`)**:
  - Long-pressing any image in a thread opens a Material 3 dialog to save the image via `DownloadManager` (passing authenticated cookies) or share its URL.

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
        │   └── webview/
        │       ├── AdBlockEngine.kt
        │       ├── HeadFiJsBridge.kt
        │       └── HeadFiScripts.kt
        └── res/
            ├── drawable/
            │   ├── ic_launcher_background.xml
            │   ├── ic_launcher_foreground.xml
            │   └── ic_launcher_monochrome.xml
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
- This workstation did not have a local JDK (`java`) or Android SDK (`$LOCALAPPDATA\Android\Sdk`) installed during scaffolding, so the Gradle build was not executed locally.

### How to Build & Test in Android Studio
1. Open `F:\AI stuff\HeadFi-Org` in **Android Studio Ladybug (2024.2.1) or newer**.
2. Allow Android Studio to generate the local SDK path (`local.properties`) and run **Sync Project with Gradle Files**.
3. Run the `app` configuration on an emulator or physical Android device (API 26–35).
4. **Verify Login Persistence**:
   - Tap the top-right Sign In button (`AccountAndSettingsSheet` -> **Sign In to Head-Fi.org**).
   - Log in with your Head-Fi credentials.
   - Verify your username and unread badge counts appear in the native TopAppBar.
   - Reboot the Android device or force-stop the app and relaunch — confirm your session remains logged in and resumes on your last visited thread.

### Potential Future Enhancements
- **Background Unread Polling (`WorkManager`)**: Add a periodic background worker that checks `/login/keep-alive` or account alerts using the stored `xf_user` cookie to post native Android push notifications when new PMs or thread replies arrive.
- **Camera Capture in File Chooser**: Extend `MainActivity.launchFileChooser` with `MediaStore.ACTION_IMAGE_CAPTURE` alongside `Intent.ACTION_GET_CONTENT` so users can snap photos directly from the camera when creating a Classifieds listing.
