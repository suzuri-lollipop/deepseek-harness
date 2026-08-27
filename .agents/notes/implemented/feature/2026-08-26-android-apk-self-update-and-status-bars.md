# Agent Note: Android APK self-update and visible OS status bars

Status: implemented

English | [中文](2026-08-26-android-apk-self-update-and-status-bars.zh.md)

## Problem

Three surfaces from one user request. First, the installed PWA requested `display: "fullscreen"`, an immersive mode in which browsers hide the OS status bar entirely — the user wanted the OS status bar visible around the installed app. Second, the Android app targets SDK 36, where edge-to-edge is enforced, but its content drew under the status bar with no inset, so the top rows of every screen sat behind it. Third, updating the Android app required `adb install` on a connected device; the user wanted the APK downloadable from the web UI and from inside the app itself.

## Decision

**The PWA requests `display: "standalone"`** in `apps/web/public/manifest.webmanifest`, so an installed app keeps the OS status and navigation bars. The earlier safe-area work — `viewport-fit=cover` in `apps/web/index.html` plus the safe-area padding on `#root` in the shell base CSS — stays as a defensive inset. Browsers lock a PWA's display mode at install time, so an installation from before this change keeps fullscreen until the user removes and re-adds the app.

**The Android app draws edge-to-edge with inset content.** `MainActivity` calls `enableEdgeToEdge()`, forces light status- and navigation-bar icons through `WindowInsetsControllerCompat` (the UI is always the dark web palette), and pads the screen tree with `Modifier.systemBarsPadding()` inside the full-bleed `Surface`, so the background extends under the bars while every row starts below the status bar.

**The web host ships the debug APK and both clients fetch it.** `scripts/build.ts` copies `apps/android/app/build/outputs/apk/debug/app-debug.apk` to `apps/web/dist/dsh-android.apk` after the web build (a missing APK logs a skip and is not an error), and `dsh-host-frontend-static` maps `.apk` to `application/vnd.android.package-archive`. The web general settings page registers one shell-owned row — `settings.general.item` slot, id `android`, order 30 — with a `download` link to `/dsh-android.apk`. The Android connect screen downloads the same path through `core/ApkDownload.kt`: a dedicated OkHttp client with a ten-minute call ceiling streams the file into `cacheDir/apks/` with progress, rejects payloads that do not start with the ZIP local-file-header magic, and installs through FileProvider (authority `${applicationId}.apk`, cache path `apks/`) with `ACTION_VIEW` typed `application/vnd.android.package-archive`; when `canRequestPackageInstalls()` is false it opens `ACTION_MANAGE_UNKNOWN_APP_SOURCES` instead. `DshClient.normalizeUrl` is public so connect and download share one URL normalization.

## Alternatives considered

**Keep fullscreen and build a custom title bar around the OS chrome.** Rejected because the user wants the OS status bar, and DSH owns no custom title bar or layout for native window controls, as the [web install manifest note](2026-08-06-web-install-manifest.md) already ruled.

**Serve the APK through a named web route or download endpoint.** Rejected because `dsh-host-frontend-static` already serves any file under the dist root; one static file needed only a MIME entry, and a named route would add webserver surface for no capability.

**Create a dedicated client package for the Android settings row.** Rejected because `dsh-client-ui-settings-general` owns rows that belong to no single feature, and this row has none; a new package would trigger the full package checklist for one static link.

**Route updates through a Play-style channel or App Bundle.** Out of scope for a self-hosted debug client whose only distribution is the host machine; the in-app path replaces `adb install`, not a store.

## Consequences

An installed PWA keeps fullscreen until the user removes and re-adds it; new installs start with the OS bars visible. A web build on a machine that never ran `gradlew assembleDebug` ships no `dsh-android.apk`: the settings link then 404s and the in-app download reports the host does not serve an APK, while everything else in the web dist is unaffected. Installing over the running same-package app force-closes it for the duration of the system install — expected, and stated in the connect screen. The shipped APK is the debug-signed build, so the self-update channel replaces only a previously debug-installed app; moving between debug and an official signature still needs a manual install. The new settings row changes the settings-chrome dialog goldens by exactly that row; the goldens are Linux-derived, so re-recording belongs to the Linux CI side, not a local Windows pass.

## Verification

`apps/web/tests/pwa-manifest.e2e.ts` pins `display: "standalone"` and the viewport link in the built dist; `frontend-static.spec.ts` pins the `.apk` media type; the `dsh-client-ui-settings-general` specs pin the row's registration options and rendered link; `gradlew assembleDebug` compiles the app; and the built dist serves `/dsh-android.apk` with the Android package media type.

## Related

- [Web install manifest metadata](2026-08-06-web-install-manifest.md) — the original `display: "fullscreen"` decision; this note supersedes the display-mode choice only, and the manifest's identity, launch, icon, and no-service-worker decisions stand.
- [Android client drives dsh web over the /api wire protocol](2026-08-22-android-remote-client.md) — the v1 client whose consumer-only scope this change extends with one read-only asset the host serves.
- [The Android hero screen mirrors the web empty-session composition](2026-08-24-android-hero-screen.md) — the single-activity screen tree the edge-to-edge padding wraps.
