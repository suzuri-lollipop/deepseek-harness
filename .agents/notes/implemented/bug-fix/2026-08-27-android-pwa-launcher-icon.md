# Agent Note: Android PWA launcher icon

Status: implemented

English | [中文](2026-08-27-android-pwa-launcher-icon.zh.md)

## Problem

Android composes an installed PWA's launcher icon over the launcher's own background and masks it into the launcher's shape. The Web install manifest declared only the theme-aware `/favicon.svg` — a transparent field with a mark that renders white under a dark color scheme. On Android launchers with a white composition background in dark mode, the icon resolved to a white mark on a white field: invisible on the home screen.

## Decision

The manifest's icon list is three opaque PNGs committed under `apps/web/public/icons/`: `icon-192.png` and `icon-512.png` with `purpose: "any"`, and `icon-maskable-512.png` with `purpose: "maskable"`. Every PNG is a full-bleed field of brand ink `#0F1115` with the whale mark knocked out in white, so no launcher background shows through or inverts the mark. The maskable variant scales the mark into the centered 80%-of-edge safe zone, so launcher masking (circle, squircle, teardrop) cannot clip it. The theme-aware SVG no longer appears in the manifest; `index.html` keeps it as the tab favicon, where a transparent field is correct.

`scripts/gen-web-icons.ts` (`pnpm run gen-web-icons`) regenerates the three PNGs from the mark in `apps/web/public/favicon.svg`: it extracts the mark's path, bounds it by the control points of its coordinates, and rasterizes a 512-pixel SVG document with `sharp`, downscaling that raster for the 192-pixel variant. The any icons span 88% of the canvas edge; the maskable icon fits the mark's bounding box inside the safe radius. `scripts/gen-web-icons.spec.ts` fails the suite when a committed PNG drifts from the mark and pins the path-box and scale geometry.

## Verification

The built-Web test parses the emitted manifest and pins the complete metadata object, including the three icon entries; it also pins the favicon's dark-mode behavior. `scripts/gen-web-icons.spec.ts` regenerates the icons into a temporary directory and compares every committed PNG pixel-for-pixel (decoded raw pixels, robust to PNG metadata differences), and pins the path-box, any-scale, and maskable-scale geometry.

## Alternatives considered

**Keep listing the theme-aware SVG in the manifest.** Rejected: it is the wrong asset for launcher composition — transparent, and white in dark mode — so listing it leaves a path to the all-white icon. The tab keeps the SVG through the HTML icon link, where a transparent field is the correct one.

**Give the favicon an opaque dark field instead.** Rejected: the favicon serves the tab, where a transparent field sits correctly on browser chrome in both light and dark schemes; an opaque field would render as a dark square in tab UI. One asset cannot serve both roles, so the launcher icons are separate committed assets.

**Ship a single raster icon without a maskable variant.** Rejected: Android masks the icon it chooses; without a maskable entry the launcher falls back to masking the any icon, whose 88% span can be clipped by tighter masks. The maskable variant exists to keep the mark inside the safe zone.

**Generate the PNGs during the Web build.** Rejected: the repo's generated-asset convention — committed output, a generator script, and a spec that fails on drift — already owns this shape, and committed PNGs stay diffable in history without a raster dependency in the build.

## Consequences

Android installers get an opaque, mask-safe launcher icon in every launcher theme. The mark is derived from the favicon path: a change to `apps/web/public/favicon.svg` requires `pnpm run gen-web-icons` before committing, and the spec fails on drift. The manifest carries three fixed absolute asset URLs, which the [web install manifest note](../feature/2026-08-06-web-install-manifest.md) already requires revisiting together when deploying below a path prefix. `sharp` is a root devDependency used only by the generator and its spec; no published package depends on it.
