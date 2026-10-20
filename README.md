# Head-Fi for Android (Unofficial)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/areyouanand/headfi-unofficial?label=APK%20release)](https://github.com/areyouanand/headfi-unofficial/releases/latest)

> **⚠️ Unofficial app — built for personal convenience.**
> This is a free, unofficial Android client for browsing [Head-Fi.org](https://www.head-fi.org/).
> It is **not** affiliated with, endorsed by, sponsored by, or connected to Head-Fi.org,
> its staff, or its community team in any way. "Head-Fi" and the Head-Fi logo are the
> property of their respective owners. This project simply wraps the site in a native
> shell for a nicer mobile experience. Use at your own risk.

---

A native **Kotlin + Jetpack Compose** client that wraps Head-Fi's XenForo 2.3 platform in a **Material 3 Expressive** adaptive shell, with guaranteed login persistence across app restarts and device reboots, custom dark/AMOLED styling, ad/tracker blocking, background unread notifications, reading and layout controls, multi-photo uploads with camera capture, and a monochrome Head-Fi adaptive icon.

## 📥 Download

Grab the latest APK from the [**Releases**](https://github.com/areyouanand/headfi-unofficial/releases) page: `HeadFi-vX.Y.Z.apk`.

- **Requires:** Android 8.0+ — best experience on **Android 12+** (themed icon, gesture navigation).
- **Install:** open the downloaded APK and allow "install unknown apps" for your browser when prompted.
- **Updates:** every release is signed with the same committed project key, so new versions install straight over old ones — no uninstall needed.
- **Builds are automated:** pushing a tag like `v1.2.0` makes GitHub Actions build the APK and attach `HeadFi-v1.2.0.apk` to a release automatically (see [.github/workflows/release.yml](.github/workflows/release.yml)).

## ✨ Features

- **Login that survives reboots** — dual-layer cookie persistence (native `CookieManager` SQLite flush + a DataStore vault that re-injects `xf_*`/`cf_*` cookies on cold boot). "Stay logged in" is auto-checked so the long-lived `xf_user` cookie is always issued.
- **XenForo session bridge** — username, avatar, unread alerts and conversations show up natively in the top bar and account sheet.
- **Material 3 Expressive shell** — bottom navigation bar on phones, navigation rail on tablets/foldables; Home, Forums, What's New, Head Gear, and Classifieds sections; pull-to-refresh, predictive back, find-in-page, native search dialog, scroll-to-top FAB.
- **Smart auto-hiding toolbars** — the top app bar and bottom navigation slide away while you scroll down to read and slide back the moment you scroll up (always visible near the top of a page).
- **Reading & layout controls** — four text-size steps, a Mobile/Desktop site-layout switch, a Data Saver that skips images on a metered connection, and an option to hide Head-Fi's own header to reclaim vertical space.
- **Find in page** — inline search bar with a live match counter and previous/next stepping; system back closes it before it pops history.
- **Sign-in prompt** — a dismissible prompt appears until you're signed in, since persistent login, unread badges and background notifications are all invisible without a session.
- **Background unread notifications** — an opt-in WorkManager poller re-checks Head-Fi with your saved `xf_user` cookie (keeping the session warm while the app is closed) and raises a native notification when new alerts or private messages arrive. Choose between 15-minute and 3-hour intervals.
- **Camera capture & multi-photo uploads** — the attachment picker offers *Camera* alongside the file picker for forum posts and Classifieds listings, and supports selecting several photos at once.
- **Theming** — fixed Head-Fi color scheme that mirrors the site's own palette for consistent readability (light/dark/system plus a pure AMOLED black override).
- **Cloudflare Turnstile friendly** — WebView user agent is scrubbed so challenges treat it as standard Chrome Mobile; Cloudflare/CDN domains are allowlisted while 30+ ad/tracker domains are blocked.
- **Image save & share** — long-press any image to save it via `DownloadManager` (with authenticated cookies) or share its URL.
- **Offline resilience** — losing your connection mid-session shows a Retry banner over the page you were reading rather than blanking it; the full-screen error is reserved for a failed first load.
- **Monochrome adaptive icon** — vector Head-Fi headphone mark, works as an Android 13+ themed icon.

## 🔧 Building it yourself

Open the project in Android Studio (Ladybug or newer), or from the command line:

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (signed with keystore/headfi-release.keystore)
```

### Versioning scheme

- **Version name** comes from the git tag (`v1.2.3` → `1.2.3`), or defaults to `1.0.0`.
- **Version code** is the total commit count, so it always increments.
- Override either manually: `./gradlew assembleRelease -PAPP_VERSION_NAME=1.2.3 -PAPP_VERSION_CODE=42`.

### About the committed signing key

`keystore/headfi-release.keystore` is committed on purpose. This is an unofficial, personal-convenience project with no store presence — the key exists purely so releases keep a stable signature and can update in place. It is not a secret worth protecting, and it must never be used to sign anything for distribution on Google Play.

## ⚖️ License

The code in this repository is released under the **[MIT License](LICENSE)** — free to use, fork, modify, and redistribute, including commercially.

Two things sit outside that license:

- **Head-Fi trademarks** — the "Head-Fi" name, logo, and related marks belong to Head-Fi.org and are not covered by the MIT grant (see [NOTICE.md](NOTICE.md)). Forks must carry their own unofficial disclaimer.
- **The Head-Fi.org site itself** — this client only displays publicly available pages; the community and its content belong to Head-Fi.org.

## 🙏 Acknowledgements

- [Head-Fi.org](https://www.head-fi.org/) — the best headphones community on the internet, and the sole subject of this client.
- The Head-Fi name, site, and logo belong to Head-Fi.org. This unofficial client claims no rights over them.
