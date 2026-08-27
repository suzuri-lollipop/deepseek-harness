# DSH Android Client

[English](README.md) | 中文

原生（Kotlin / Jetpack Compose）Android 客户端，远程驱动 `dsh web` 宿主。
不使用 WebView：直接实现 `/api` 协议格式（wire format）（unary HTTP + 两条 WebSocket 下行流）。

## 功能

- 镜像 Web 空会话构图的新会话 hero 画面：品牌 rail（鱼标志、新建聊天、新建终端、会话、设置）、带预览 badge 的鱼标志标题、workspace / agent（智能体）preset chip 行，以及柔光上的 composer 卡片；固定使用 Web 深色主题 token
- agent preset：名簿经 `agentPreset.list` 取得；hero 的 preset chip 暂存一次选择，发送时经 `agentPreset.select` 应用到空白会话并在那里被消费，与 Web 的 seat-store 相同
- Server URL 设置，持久化
- 连接就绪（Ready）= 两条事件流均打开 + `host.describe` 成功
- 按 workspace 分组的会话树：`workspace.list` + `session.list` 按 Host 顺序分组，末尾未分组桶，subagent / 已归档会话隐藏，空白会话除最后打开者外隐藏，host 帧到达时自动刷新
- 每个 workspace 可添加 thread（已有该 workspace 的空白会话则复用，与 Web 客户端规则相同）；全局 New Session 按钮依次选择最后打开会话的 workspace、最近活跃的 workspace、host cwd
- 聊天：历史尾部经 `session.history` 读取，`session/event` mux 帧实时流式文本 / 推理（reasoning），工具调用卡片，轮次结束注记
- 工具权限卡片（`approval/requested` → 经 `/api/respond` 一次性允许 / 拒绝）
- 用户提问卡片（`question/requested` → 单选 / 多选 + 自由输入）
- 停止运行中的轮次（`session.cancel`）
- 带 backoff 的重连；每次 Ready 转换重新获取历史
- APK 自更新：连接界面下载 dsh web 宿主在 `/dsh-android.apk` 提供的 APK（10 分钟调用上限，校验 ZIP 魔数），经 FileProvider 交给系统安装器；缺少「安装未知应用」权限时打开对应权限页

## 构建

要件: Android SDK（compileSdk 36）、JDK 17（Android Studio JBR 即可）、Gradle 8.14.3。

```sh
cd apps/android
# local.properties must set sdk.dir
gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

根构建（`npm run build`）在 APK 存在时会将其复制到 `apps/web/dist/dsh-android.apk`，
于是 `dsh web` 宿主在 `/dsh-android.apk` 提供该文件，供应用内自更新与 Web 设置界面链接使用。

在已连接的设备上安装:

```sh
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 服务器端运行

客户端连接 `dsh web` 宿主。手机走 Tailscale 时，以 Tailscale 主机名作为 `--trusted-host` 启动宿主（见仓库的 `start-web-tailscale.bat`）：

```sh
tailscale serve --bg 3080
pnpm dsh web --trusted-host <tailscale-hostname>
```

然后把 App 指向 `https://<tailscale-hostname>`（端口 3080）。本地 `http://127.0.0.1:3080` 始终无需 `--trusted-host` 即可用。若服务器返回 403，说明信任围栏拒绝了 Host 头——用匹配的 `--trusted-host` 启动即可。

注意：真实模型轮次需要宿主环境中有 `DEEPSEEK_API_KEY`；缺少时协议本身仍可工作，但提示词会因提供方错误而失败，App 会在聊天中以红色注记显示。

## 目录结构

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

## 协议备注

- Upstream: `POST /api/<method>`，`client-request` 信封；方法在 URL 路径中。`POST /api/respond` 携带对挂起 server-request（权限、提问）的答复。
- Downlinks: `WebSocket /api/events.mux`（每会话帧）与 `/api/events.host`（宿主级帧）。帧为 `server-request` 信封；按 `payload.type` 分发。
- Workspace 分组：`workspace.list` 返回 `{items, archivedSessionIds}`（ISO 时间戳）；host 帧 `host/workspace-changed`（全量视图快照，upsert）、`host/workspace-removed`、`host/workspace-order-changed`（全量顺序）与 `host/archived-sessions-changed`（全量集合）保持树同步。`session.create` 接受 `workspaceId`（workspaceId/cwd 至多其一）；已附加会话的 `cwd` 成为 workspace 路径。
- agent preset: `agentPreset.list` 接收 `{}`，返回 `{presets: [{id, trust, isDefault, name?, description?, broken?}], authorable, hasDocument}`；`agentPreset.select` 接收 `{sessionId, agentPreset}`（preset id），拒绝非空白会话。hero 没有会话，因此模型 chip 显示 `host.describe` 的 `model` 字段。
- 客户端仅消费；不修改宿主或 Web 客户端。
