# Agent Note: PWA OS bars follow the resolved theme color

Status: implemented

English | [中文](2026-08-26-pwa-os-bars-follow-resolved-theme.zh.md)

## Problem

The installed standalone PWA keeps the OS status and navigation bars visible around the app. On Android, the status bar follows the document's `theme-color` metadata live, but the navigation bar is fixed at the color sampled in the page's initial commit and is not re-read from later metadata changes. The `theme-color` node is minted only when the client plugin tree activates (ui-layout's ThemePresenter), after first paint, so an Android PWA's navigation bar keeps its dark engine default even when the app resolves to the light palette: a conspicuous black bar over the light UI.

## Decision

**The `theme-color` metadata carries the resolved base background at the initial commit.** `apps/web/index.html` ships a static `<meta name="theme-color" content="#fff" />`. The ui-theme bootstrap row — a synchronous inline script immediately after the `<body>` open tag, embedding the Host's durable preference — resolves `system` from the OS scheme and writes the resolved palette base background to the meta: `#fff` for light and `#151517` for dark, the same two values the boot page hardcodes as its palette fallbacks (the design-platform neutral bluish 00 / 950 base backgrounds). The bootstrap updates an existing node in place and mints one when absent.

**The ThemePresenter adopts the boot-time node instead of minting a second.** Its constructor adopts the existing `meta[name="theme-color"]` and mints a node only when none exists, so the document keeps one node. `apply()` still rewrites its content from the computed body background after palette and token application, so later theme switches — including override-layer themes that change the base background — keep driving the status bar, and `dispose()` removes the node with the presenter's other global writes.

## Alternatives considered

**Static `theme_color` / `background_color` in the manifest.** Rejected: the manifest is static and cannot carry the user's resolved light/dark preference, so a fixed value would fight one of the two resolved palettes — the same reason the [web install manifest note](2026-08-06-web-install-manifest.md) omits both fields. The boot-time meta puts the resolved value into the initial commit without touching the manifest, so the colorless-manifest decision stands.

**Let the ThemePresenter keep minting its own node and give the bootstrap no meta role.** Rejected: two `theme-color` nodes would coexist after activation, and the tree-order-first node shadows the other, so either the initial-commit color or the live updates stop working depending on order. One adopted node is the only arrangement in which both the initial-commit color and the live updates hold.

**Resolve the boot value from CSS custom properties.** Rejected: the design token sheets install only when the ui-theme plugin activates, so the base background is not resolvable from CSS before first paint. The bootstrap hardcodes the two base values, matching the boot page's existing palette fallbacks.

## Consequences

The navigation bar follows the resolved theme on Android standalone PWAs from the initial commit: light mode gets a light bar, dark mode a dark one. The value is the built-in palette base background only: a third-party override-layer theme that changes `--dsw-alias-bg-base` updates the meta (and the status bar) through the presenter, but the navigation bar keeps its boot-time value until the page reloads. The static HTML default is light: a pre-bootstrap sample sees `#fff`, and a script-disabled page renders no app. The PWA splash screen keeps the engine default — untouched by this change and still governed by the colorless-manifest decision.

## Verification

`packages/client/ui-theme/tests/boot-theme.client.spec.ts` pins the bootstrap's meta writes: minted, updated in place, and matching the light/dark/system resolution. `packages/client/ui-layout/tests/theme-presenter.client.spec.ts` pins adoption of a boot-time node, single-node updates, and disposal. `apps/web/tests/pwa-manifest.e2e.ts` pins the static meta in the built dist's index.html.

## Related

- [Android APK self-update and visible OS status bars](2026-08-26-android-apk-self-update-and-status-bars.md) — the `display: "standalone"` decision that keeps these bars visible, and the Android app's own edge-to-edge handling.
- [Web install manifest metadata](2026-08-06-web-install-manifest.md) — the colorless-manifest decision this note keeps intact.
