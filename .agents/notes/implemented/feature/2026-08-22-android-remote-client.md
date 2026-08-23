# Agent Note: Android client drives dsh web over the /api wire protocol

Status: implemented

English | [中文](2026-08-22-android-remote-client.zh.md)

## Problem

The `dsh web` GUI is only operable from a browser that can reach the host, and the request was to operate the harness remotely from a phone. The confirmed approach was a native Android client — not a WebView wrapper around `apps/web` — so the app must speak the host protocol itself: unary HTTP requests, the `/api/respond` path for pending answers, and the two WebSocket downlinks.

## Decision

**A standalone Gradle project at `apps/android`, outside the pnpm workspace.** Single-activity Kotlin/Jetpack Compose app (`com.deepseekai.dsh.client`), no navigation library. `core/DshClient.kt` is the transport: unary requests are `POST /api/<method>` carrying the `client-request` envelope, pending answers go through `POST /api/respond` as `client-response`, and both downlinks — `WebSocket /api/events.mux` and `/api/events.host` — are consume-only, dispatching on `payload.type`. Ready state is both sockets open plus a `host.describe` round-trip; drops reconnect with 2s→15s backoff, and a `reconnectNonce` bump makes an open chat refetch history so no live frame is lost in the gap. Unary HTTP carries a call timeout so a hung response cannot pin the request queue.

**Chat rendering is a tolerant fold with message pagination.** `core/ChatFold.kt` maps `SessionEvent` JSON to display rows: a streaming assistant row keyed by `step`, a tool row keyed by `callId`, user rows only for `source.kind === 'user'`, and unknown (plugin-merged) event types ignored. `assistant/message` replaces the streaming row for its step; `tool/result` matches through the `toolCallId` in its content. History is paged like the web client: the seed fetches the last 50 messages (`maxMessages`), and load-older fetches 50 more per tap with `beforeSeq` = the oldest loaded seq; history entries arrive wrapped as `{ event: <session log event> }` and are unwrapped before the fold. Seq deduping keeps seed, load-older, and live frames in one order. `fold.rows` is a plain list read the snapshot system does not track, so a zero-height spacer item keyed on a screen-owned version counter (fed by `fold.version`) binds the lazy group to fold mutations, and the fold instance is replaced whole on (re)seed. User bubbles are right-aligned and capped at 78% of the row width (20.dp horizontal, 8.dp vertical margins) so a long prompt keeps visible breathing room at both screen edges.

**The list opens at the newest message and follows the tail while near it.** The seed jumps the scroll state to the last row, and each version tick re-anchors only when the viewport is already within three items of the end. The follow target is the last *adapter* item, not the last row: approval and question cards render after the rows, and a turn blocked on a question emits no further events, so a rows-based target would leave a freshly rendered card off-screen forever.

**Interaction surfaces are the protocol's own, and the server replays pending ones.** Approval cards answer `approval/requested` with `allowed-once` or `rejected`; question cards answer a whole `question/requested` batch in one respond (single/multi-select plus free text); Stop is `session.cancel`. The server replays pending `question/requested` (and approval) frames to a new mux connection on stream open, so a reconnect re-surfaces outstanding prompts; to avoid ghost cards the app also removes pending entries on `question/resolved` (payload `questionRpcId`) and `approval/resolved` (payload `approvalId`), which covers answers settled by another client or by cancel. A question card is a natural-height item of the outer LazyColumn — a nested `verticalScroll` inside a lazy item crashes on infinite-height measurement — with expandable sections capped at 320.dp, and selections/custom answers are Compose state updated by map value replacement, because mutating a remembered `HashMap` in place does not recompose. The v1 surface is: server URL (persisted, Tailscale default), session list (blank sessions hidden), chat with streaming text/reasoning and tool cards, approvals, questions, stop, reconnect. App-owned UI is Japanese-primary with English alongside.

## Alternatives considered

**A WebView wrapper around `apps/web`.** Rejected by the user: the request was explicit for a native client, and a WebView would re-host the whole web shell — bundle, state machine, and browser quirks — on a phone to do nothing the `/api` protocol does not already expose.

**A TypeScript-based client (Node bridge or JS runtime in the APK) reusing the SDK client.** Rejected: `/api` is plain JSON over HTTP and WebSocket, fully specified by `packages/host/apiproxy/src/api/rpc.schema.ts`, and a JVM OkHttp + org.json implementation is a few hundred lines; a JS runtime in the APK would cost more than it deletes.

**A per-session WebSocket.** The protocol has one mux stream per connection; the client follows it and filters by `sessionId` instead.

## Consequences

The app is consumer-only: no host or web-client changes and no new wire surface. Readiness and recovery ride `host.describe` and the two streams; anything the protocol does not push (queue position, image attachments, history beyond manual load-older) stays out of v1. A model turn needs `DEEPSEEK_API_KEY` on the host; without it every other flow works and the prompt fails with a provider error surfaced as a chat note. The trust fence is unchanged: a non-loopback host needs `--trusted-host` for the phone's hostname (the repo's `start-web-tailscale.bat` already passes it), and a 403 is surfaced with that hint. Build-wise the project pins only versions present in the offline artifact cache (Compose BOM 2026.06.01, AGP 8.13.2, JDK 17), uses the build-tools 36.0.0 `aapt2` via `android.aapt2FromMavenOverride` when the Maven artifact is absent, and signs debug builds with a project-local keystore when `~/.android` is not writable.

Per-event `Log.d` logging is a correctness hazard here, not just noise: on the AOSP emulator the logd backpressure from a per-event log storm (tens of thousands of lines per seed) blocked the main-thread prepend of a load-older page for over fifteen minutes, while the pipeline itself (fetch + unwrap + prepend of ~45k events) takes about 4.6s. The release surface keeps only rare diagnostics (parser-rejected frames, load-older failures).

Emulator UI automation against this app needs positional taps (no content-description contracts), so the reliable loop is `uiautomator dump` → parse bounds → `input tap` at the bounds center; on the 1344×2992 emulator display the chat input sits at ~(594, 2818) and the send FAB at ~(1254, 2818), but every positional tap must come from a fresh dump. `adb shell input text` garbles CJK and quotes, so test prompts go through the `/api` RPC (the same path the app uses) instead of synthetic keystrokes.

## Testing

`assembleDebug` builds offline against the local SDK (build-tools 36.0.0) with the Android Studio JBR; the debug APK installs over adb. Verified live against a running `dsh web` behind the Tailscale serve mapping: connect reaches Ready; the session list reflects the host; a seeded chat opens at the newest message and load-older pages 50 messages back at a time with `hasMore` maintained and no boundary duplicates; an RPC prompt renders the user row and streamed assistant rows live; the Stop tap cancels the turn and appends the interrupted note; approval allow/reject round-trips as receipts; and the full question flow was driven end-to-end — the replayed pending question card renders at the tail on reconnect without manual scrolling, tapping an option renders the selection marker, and submit answers the batch, removes the card on `question/resolved`, and the model echoes the chosen option in its final row.

## Related

- [API proxy wire protocol](../../../../../packages/host/apiproxy/README.md) — the `client-request` / `client-response` envelope and the two event downlinks this client consumes.
