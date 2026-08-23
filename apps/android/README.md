# DSH Android Client

ネイティブ（Kotlin / Jetpack Compose）で `dsh web` を遠隔操作する Android クライアント。
WebView を使わず、`/api` ワイヤプロトコル（unary HTTP + 2 本の WebSocket ダウンリンク）を直接実装する。

Native (Kotlin / Jetpack Compose) Android client that drives a `dsh web` host remotely.
No WebView: it speaks the `/api` wire protocol (unary HTTP + two WebSocket downlinks) directly.

## Features / 機能

- Server URL config, persisted (サーバー URL 設定、永続化)
- Readiness = both event streams open + `host.describe` OK
  （両ストリームオープン + `host.describe` 成功で接続済みと判定）
- Session list from `session.list` (blank sessions hidden; live refresh on host frames)
  （`session.list` によるセッション一覧。空セッションは非表示、host フレームで自動更新）
- Chat: history tail via `session.history`, live streaming text/reasoning from
  `session/event` mux frames, tool-call cards, turn-end notes
  （チャット：履歴読み込み、配信テキスト/思考のライブ表示、ツール呼び出しカード、ターン終了注記）
- Tool approval cards (`approval/requested` → allow-once / reject via `/api/respond`)
  （ツール承認カード：一度だけ許可 / 拒否）
- User-question cards (`question/requested` → single/multi-select + free text)
  （質問カード：単一/複数選択 + 自由入力）
- Stop a running turn (`session.cancel`)（実行中ターンの停止）
- Reconnect with backoff; history refetched on every Ready transition
  （バックオフ付き再接続。接続復帰時に履歴を再取得）

## Build / ビルド

Requirements: Android SDK (compileSdk 36), JDK 17 (Android Studio JBR works), Gradle 8.14.3.
要件: Android SDK（compileSdk 36）、JDK 17（Android Studio の JBR で可）、Gradle 8.14.3。

```sh
cd apps/android
# local.properties must set sdk.dir
gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected device:

```sh
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Running the server side / サーバー側の起動

The client connects to a `dsh web` host. For a phone on Tailscale, start the host with
`--trusted-host` for the Tailscale hostname (see the repo's `start-web-tailscale.bat`):

```sh
tailscale serve --bg 3080
pnpm dsh web --trusted-host <tailscale-hostname>
```

Then point the app at `https://<tailscale-hostname>` (port 3080). Local
`http://127.0.0.1:3080` always works without `--trusted-host`. If the server
answers 403, the trust fence is rejecting the Host header — start it with the
matching `--trusted-host`.

Note: real model turns need `DEEPSEEK_API_KEY` in the host environment; without
it the protocol still works but prompts fail with a provider error that the app
surfaces as a red note in the chat.

## Layout / 構成

```
app/src/main/java/com/deepseekai/dsh/client/
  core/
    DshClient.kt   transport: unary HTTP + 2 WebSockets, reconnect, pending state
    Protocol.kt    envelope encode/decode (client-request, server-request, client-response)
    ChatFold.kt    SessionEvent JSON → display rows (tolerant of unknown types)
    Models.kt      wire/data types
    Prefs.kt       SharedPreferences
  ui/
    ConnectScreen.kt   URL + connection status
    SessionListScreen.kt
    ChatScreen.kt      history seed → live fold, approvals, questions, stop
    Components.kt      row/cards renderers
    Bi.kt              Japanese-primary / English-secondary string pairs
  MainActivity.kt
```

## Protocol notes / プロトコル補足

- Upstream: `POST /api/<method>` with a `client-request` envelope; the method is in
  the URL path. `POST /api/respond` carries the answer to a pending
  server-request (approvals, questions).
- Downlinks: `WebSocket /api/events.mux` (per-session frames) and
  `/api/events.host` (host-level frames). Frames are `server-request` envelopes;
  dispatch on `payload.type`.
- The client is a consumer only; it does not modify the host or web client.
