# Agent Note: The Android hero screen mirrors the web empty-session composition

Status: implemented

English | [中文](2026-08-24-android-hero-screen.zh.md)

## Problem

The v1 Android client landed on the session tree the moment it reached Ready, so the app's signature surface — the web empty-session hero: brand rail, fish headline with a Preview badge, the workspace / agent-preset chip row, and the composer card over the soft blue glow — was absent from the phone. The request was to create this screen composition in the Android app as well.

## Decision

**The hero is the connected root.** `MainActivity` keeps the client's single-activity plain-state navigation (no navigation library, per the [v1 decision](2026-08-22-android-remote-client.md)): in the Ready state `HeroScreen` is the root, the session tree and the settings screen (the existing `ConnectScreen` with a back gesture) are pushed from the hero rail, a session opens `ChatScreen` on top, and every back returns to the hero. When disconnected, the connect screen becomes the root again.

**Fixed web dark tokens, device theme ignored.** `ui/Theme.kt` maps the web dark palette (`body[data-ds-dark-theme]` in `design-platform.css`: base `#151517`, layer/label/border steps, the deepseek blues, glow `#6187D8`) onto a Material3 dark scheme that `DshTheme` renders regardless of the device setting, so the composition reads as the web composition.

**The composition is ported, not reconstructed.** `ui/HeroScreen.kt` places a 56dp rail (brand fish, new chat, new terminal, sessions, settings) left of a centered stack: fish mark + headline + Preview badge; a chip row (workspace chip and preset chip, each icon + label + chevron); and the 22dp-radius composer card (16sp input, +/shield/clip round tool buttons, model chip, circular up-arrow send) with a radial blue glow drawn behind the stack. The icons are vector drawables in `res/drawable/`; the fish path is ported verbatim from the web `FishLogo.tsx`.

**Behavior reuses the client's existing rules.** The rail new-chat and the hero send both resolve their workspace through `newSessionTarget` (a staged chip pick wins) and create or reuse the target workspace's blank session through `connectWorkspace`. The workspace chip opens a dialog over the `workspace.list` mirror plus a host-cwd entry; the staged pick survives screen pushes.

**Agent-preset staging mirrors the web seat-store.** The preset chip stages a pick from the `agentPreset.list` roster, refreshed on every Ready; at send the staged preset applies to the now-existing blank session via `agentPreset.select {sessionId, agentPreset}` and the stage is consumed. A select failure is surfaced as a note but neither blocks the prompt nor the session opening. The model chip shows `host.describe`'s `model` field — `session.models` requires a session id, and the hero has none. The chip and picker render `presetDisplayName`, mirroring the web `presetDisplayText`: a shipped (system) preset takes the localized built-in name (the web English copy), everything else falls back to the preset's own metadata, then its id.

## Alternatives considered

**Embedding the web hero in a WebView (or a static image).** Rejected: the client is native by the v1 decision precisely to avoid re-hosting the web shell, and a static image cannot show live workspace, preset, or model state or accept input.

**Keeping the session list as the root with the hero as a separate screen.** Rejected: on the web the hero is what appears with no session open, and making the list the root puts the phone's default state out of step with the web and buries the new-session composer behind a navigation hop.

**Deriving the preset/model chips from `session.models` or a phone-side preset store.** Rejected: the hero has no session, so `session.models` is uncallable, and a full preset store would duplicate web-side state the app does not need — a roster plus a one-shot select at send is the whole wire surface required.

## Consequences

The app now consumes `agentPreset.list` (fetched on every Ready; failure lands in the error note) and `agentPreset.select` (at send, blank sessions only). The +/shield/clip accessory buttons, the model chip, and the new-terminal entry are inert placeholders that show a not-implemented snackbar; the v1 negative guarantees (no queue position, attachments, or permission UI) carry over into the hero. Staging (workspace, preset, draft) lives in hero `rememberSaveable` state: it survives screen pushes but is not persisted like the server URL. The session tree gains a back arrow — it is a pushed screen now — and settings is the same `ConnectScreen` reused with a back gesture. System back follows the same unwind: chat, sessions, and settings all return to the hero root instead of leaving the app.

## Testing

`assembleDebug` builds offline against the local SDK with the Android Studio JBR and the debug APK installs over adb. Verified against a running `dsh web`: the hero renders the rail, the headline with the Preview badge, both chips, and the composer card over the glow on the dark token background; the workspace dialog lists the host's workspaces; a send creates or reuses the target workspace's blank session, prompts, and opens the chat; the rail new-chat opens a blank session; back returns to the hero with the draft intact.

## Related

- [Android client drives dsh web over the /api wire protocol](2026-08-22-android-remote-client.md) — the transport, fold, and v1 surface this builds on.
- [API proxy wire protocol](../../../../packages/host/apiproxy/README.md) — the consumed unary + WebSocket wire, including `agentPreset.list` / `agentPreset.select`.
