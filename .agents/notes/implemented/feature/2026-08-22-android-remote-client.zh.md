# Agent Note: Android 客户端通过 /api 线协议驱动 dsh web

Status: implemented

[English](2026-08-22-android-remote-client.md) | 中文

## Problem

`dsh web` GUI 只能在能访问主机的浏览器中操作，而需求是从手机上远程操作 harness。确认的方案是原生 Android 客户端——不是 `apps/web` 的 WebView 包装——因此应用必须自己会说宿主协议：一元 HTTP 请求、走 `/api/respond` 的挂起应答、以及两条 WebSocket 下行流。

## Decision

**`apps/android` 下独立的 Gradle 工程，不在 pnpm workspace 内。** 单 Activity 的 Kotlin/Jetpack Compose 应用（`com.deepseekai.dsh.client`），不用导航库。`core/DshClient.kt` 是传输层：一元请求是携带 `client-request` 信封的 `POST /api/<method>`，挂起应答以 `client-response` 走 `POST /api/respond`，两条下行流——`WebSocket /api/events.mux` 与 `/api/events.host`——只收不发，按 `payload.type` 分发。就绪态是两路 socket 全开加一次 `host.describe` 往返；断线后按 2s→15s 退避重连，`reconnectNonce` 自增让打开中的聊天重取历史，断档里不丢任何实时帧。一元 HTTP 带调用超时，挂死的响应不会卡死请求队列。

**聊天渲染是宽容折叠 + 消息分页。** `core/ChatFold.kt` 把 `SessionEvent` JSON 映射为显示行：按 `step` 归键的流式助手行、按 `callId` 归键的工具行、仅 `source.kind === 'user'` 的用户行，未知（插件合并的）事件类型忽略。`assistant/message` 替换其 step 的流式行；`tool/result` 通过 content 里的 `toolCallId` 匹配。历史与 web 客户端一样按消息分页：种子取最近 50 条消息（`maxMessages`），每次 load-older 用 `beforeSeq` = 已加载的最旧 seq 再取 50 条；历史条目以 `{ event: <session log event> }` 包装到达，入折前拆包。seq 去重让种子、load-older、实时帧保持同一顺序。`fold.rows` 是快照系统不跟踪的普通列表读取，所以用一个由 `fold.version` 驱动的屏幕自有版本计数器作键的零高度占位项把惰性组绑定到折叠变更上；（重）种子时整体替换折叠实例。用户气泡右对齐且宽度限制为行宽的 78%（水平边距 20.dp、垂直边距 8.dp），长提示在屏幕两侧保留可见余白。

**列表从最新消息打开，贴近底部时跟随尾部。** 种子把滚动状态跳到最后一行，每个版本 tick 仅当视口已在末尾三行以内时重新锚定。跟随目标是最后一个 *adapter* 项而不是最后一行：审批与提问卡片渲染在行之后，且阻塞在提问上的回合不再发出任何事件，所以按行号为目标会把刚渲染出的卡片永远留在屏外。

**交互面就是协议自身的，且服务端会重放挂起项。** 审批卡片以 `allowed-once` 或 `rejected` 应答 `approval/requested`；提问卡片一次性应答整个 `question/requested` 批次（单选/多选 + 自由文本）；Stop 是 `session.cancel`。服务端在 mux 连接建立时会向新连接重放挂起的 `question/requested`（以及审批）帧，所以重连会重新浮出未决提示；为避免幽灵卡片，应用还在 `question/resolved`（payload `questionRpcId`）与 `approval/resolved`（payload `approvalId`）时移除挂起条目，覆盖其他客户端或取消促成的应答。提问卡片是外层 LazyColumn 的自然高度项——嵌套在惰性项里的 `verticalScroll` 会在无限高度测量时崩溃——可展开区块封顶 320.dp；选项/自定义答案是 Compose 状态，以 map 值替换更新，因为原地改动记住的 `HashMap` 不会触发重组。v1 面：服务器地址（持久化，Tailscale 默认）、会话列表（空白会话隐藏）、带流式文本/思考与工具卡的聊天、审批、提问、停止、重连。应用自有 UI 以日语为主、英文并列。

## Alternatives considered

**`apps/web` 的 WebView 包装。** 用户否决：需求明确要原生客户端，WebView 会把整个 web 外壳——bundle、状态机、浏览器怪癖——搬到手机上，只为做 `/api` 协议已暴露的事情。

**TypeScript 客户端（APK 内 Node 桥或 JS 运行时）复用 SDK 客户端。** 否决：`/api` 就是 HTTP 与 WebSocket 上的纯 JSON，完整由 `packages/host/apiproxy/src/api/rpc.schema.ts` 规定，JVM OkHttp + org.json 实现只有几百行；APK 里塞 JS 运行时代价高于其省下的代码。

**每会话一条 WebSocket。** 协议每连接只有一条 mux 流；客户端跟随它并按 `sessionId` 过滤。

## Consequences

应用是纯消费者：不改宿主、不改 web 客户端、不加任何新的线面。其后的 [Android APK 自更新与可见的 OS 状态栏](2026-08-26-android-apk-self-update-and-status-bars.zh.md) 改动以宿主配信的一个只读资产（`/dsh-android.apk` 上的 debug APK）扩展了这一范围。就绪与恢复依赖 `host.describe` 和两条流；协议不推送的东西（队列位置、图片附件、手动 load-older 之外的历史）留在 v1 之外。模型回合需要宿主机上的 `DEEPSEEK_API_KEY`；没有它时其余流程全部正常，prompt 以 provider 错误失败并作为聊天里的注释显示。信任围栏不变：非 loopback 主机需要为手机的 Host 名传 `--trusted-host`（仓库的 `start-web-tailscale.bat` 已经传了），403 会连同该提示一起显示。构建方面，工程只 pin 离线构件缓存里已有的版本（Compose BOM 2026.06.01、AGP 8.13.2、JDK 17），Maven 构件缺失时用 build-tools 36.0.0 的 `aapt2`（`android.aapt2FromMavenOverride`），`~/.android` 不可写时用工程内 keystore 给 debug 构建签名。

按事件 `Log.d` 在这里是正确性隐患，不只是噪音：在 AOSP 模拟器上，per-event 日志风暴（每次种子数万行）引发的 logd 背压把 load-older 页的主线程 prepend 阻塞了十五分钟以上，而管线本身（抓取 + 拆包 + prepend 约 45k 事件）只要约 4.6 秒。发布面只保留稀有诊断（parser 拒绝的帧、load-older 失败）。

针对本应用的模拟器 UI 自动化需要定位点击（无 content-description 契约），所以可靠循环是 `uiautomator dump` → 解析 bounds → `input tap` 点 bounds 中心；在 1344×2992 的模拟器显示上聊天输入框约在 (594, 2818)、发送 FAB 约在 (1254, 2818)，但每次定位点击都必须来自一次新的 dump。`adb shell input text` 会弄乱 CJK 与引号，所以测试 prompt 走 `/api` RPC（与应用同一路径）而不是合成按键。

## Testing

`assembleDebug` 对着本地 SDK（build-tools 36.0.0）离线构建，用 Android Studio JBR；debug APK 经 adb 安装。已在 Tailscale serve 映射后运行的 `dsh web` 上实机验证：连接达到 Ready；会话列表反映宿主；种子聊天从最新消息打开，load-older 每次回退 50 条消息、`hasMore` 保持、边界无重复；RPC prompt 实时渲染用户行与流式助手行；Stop 点击取消回合并追加中断注释；审批允许/拒绝以回执往返；完整提问流程端到端驱动——重连时重放的挂起提问卡片无需手动滚动即渲染在末尾，点击选项渲染选中记号，提交应答批次、在 `question/resolved` 时移除卡片，模型在最终行原样回声所选选项。

## Related

- [Android hero 画面复刻 web 空会话构图](2026-08-24-android-hero-screen.zh.md) — 取代列表、成为已连接落地面的 hero 根。
- [API proxy wire protocol](../../../../packages/host/apiproxy/README.zh.md) — 本客户端消费的 `client-request` / `client-response` 信封与两条事件下行流。
