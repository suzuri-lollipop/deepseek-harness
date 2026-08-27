# DSH Android Client

English | [中文](README.zh.md)

ネイティブ（Kotlin / Jetpack Compose）で `dsh web` を遠隔操作する Android クライアント。
WebView を使わず、`/api` ワイヤプロトコル（unary HTTP + 2 本の WebSocket ダウンリンク）を直接実装する。

Native (Kotlin / Jetpack Compose) Android client that drives a `dsh web` host remotely.
No WebView: it speaks the `/api` wire protocol (unary HTTP + two WebSocket downlinks) directly.

## Features / 機能

- New-session hero mirroring the web empty-session composition: brand rail
  (fish mark, new chat, new terminal, sessions, settings), fish headline with
  preview badge, workspace / agent-preset chip row, and the composer card over
  the soft blue glow; fixed web dark tokens
  （Web の空セッション画面を模したヒーロー画面：ブランドレール、魚ロゴ見出しと
  プレビューバッジ、ワークスペース/エージェントプリセットのチップ行、
  淡い青い光の背景にコンポーザーカード。Web ダークテーマのトークンを固定使用）
- Agent presets: roster via `agentPreset.list`; the hero's preset chip stages a
  pick that applies (`agentPreset.select`) to the blank session at send time and
  is consumed there, mirroring the web seat-store
  （エージェントプリセット：`agentPreset.list` で名簿取得。チップで選んだプリセットは
  送信時に空白セッションへ `agentPreset.select` で適用され、そこで消費される）
- Server URL config, persisted (サーバー URL 設定、永続化)
- Readiness = both event streams open + `host.describe` OK
  （両ストリームオープン + `host.describe` 成功で接続済みと判定）
- Workspace-grouped session tree: `workspace.list` + `session.list` grouped in
  Host order, trailing Ungrouped bucket, subagent/archived sessions hidden,
  blank sessions hidden except the last-opened one, live refresh on host frames
  （ワークスペースごとのセッションツリー：`workspace.list` + `session.list` を
  Host 順でグループ化、末尾に未グループ、サブエージェント/アーカイブは非表示、
  空セッションは最後に開いたもののみ表示、host フレームで自動更新）
- Per-workspace thread add (reuses the workspace's blank session when one
  exists, mirroring the web client); the global New Session button targets the
  last-opened session's workspace, then the most recently active workspace,
  then the host cwd
  （ワークスペースごとのスレッド追加（既存の空セッションがあれば再利用、
  Web クライアントと同一のルール）。全体の新規セッションボタンは
  最後に開いたセッションのワークスペース、次に最近アクティブなワークスペース、
  最後に Host の cwd を選ぶ）
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
- APK self-update: the connect screen downloads the APK the dsh web host serves
  at `/dsh-android.apk` (10-minute call ceiling, ZIP-magic verified) and hands it
  to the system installer via FileProvider, prompting for the unknown-apps
  grant when missing
  （APK 自己更新：接続画面から dsh web ホストが `/dsh-android.apk` で配信する
  APK をダウンロード（10 分の呼び出し上限、ZIP マジック検証）し、FileProvider
  経由でシステムインストーラに渡す。権限がなければ「不明なアプリのインストール」
  権限画面を開く）

## Build / ビルド

Requirements: Android SDK (compileSdk 36), JDK 17 (Android Studio JBR works), Gradle 8.14.3.
要件: Android SDK（compileSdk 36）、JDK 17（Android Studio の JBR で可）、Gradle 8.14.3。

```sh
cd apps/android
# local.properties must set sdk.dir
gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

The root build (`npm run build`) copies that APK to
`apps/web/dist/dsh-android.apk` when present, so a `dsh web` host serves it at
`/dsh-android.apk` for the in-app self-update and the web settings link.
ホスト側のルートビルド（`npm run build`）は、APK が存在すれば
`apps/web/dist/dsh-android.apk` にコピーする。`dsh web` ホストが
`/dsh-android.apk` で配信し、アプリ内自己更新と Web 設定画面のリンクが使う。

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
    ApkDownload.kt APK self-update: cache-dir download + installer intent
  ui/
    ConnectScreen.kt   URL + connection status (also the settings screen)
    SessionListScreen.kt
    HeroScreen.kt      web empty-session composition: rail, hero, chips, composer
    ChatScreen.kt      history seed → live fold, approvals, questions, stop
    Components.kt      row/cards renderers
    Theme.kt           web dark-theme tokens + fixed Material3 scheme
    Bi.kt              Japanese-primary / English-secondary string pairs
  res/drawable/        hero vector icons (fish, chat+, terminal+, search, …)
  MainActivity.kt      hero root; chat / session tree / settings pushed on top
```

## Protocol notes / プロトコル補足

- Upstream: `POST /api/<method>` with a `client-request` envelope; the method is in
  the URL path. `POST /api/respond` carries the answer to a pending
  server-request (approvals, questions).
- Downlinks: `WebSocket /api/events.mux` (per-session frames) and
  `/api/events.host` (host-level frames). Frames are `server-request` envelopes;
  dispatch on `payload.type`.
- Workspace grouping: `workspace.list` returns `{items, archivedSessionIds}`
  (ISO timestamps); the host frames `host/workspace-changed` (full view
  snapshot, upserted), `host/workspace-removed`, `host/workspace-order-changed`
  (full order), and `host/archived-sessions-changed` (full set) keep the tree
  in sync. `session.create` accepts `workspaceId` (at most one of
  workspaceId/cwd); an attached session's `cwd` becomes the workspace path.
- Agent presets: `agentPreset.list` takes `{}` and returns `{presets: [{id,
  trust, isDefault, name?, description?, broken?}], authorable, hasDocument}`;
  `agentPreset.select` takes `{sessionId, agentPreset}` (a preset id) and
  refuses non-blank sessions. The hero has no session, so the model chip shows
  `host.describe`'s `model` field.
- The client is a consumer only; it does not modify the host or web client.
