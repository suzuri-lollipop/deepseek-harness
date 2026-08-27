# Agent Note: Android hero 画面复刻 web 空会话构图

Status: implemented

[English](2026-08-24-android-hero-screen.md) | 中文

## Problem

v1 Android 客户端达到 Ready 后就落在会话树上，于是应用的招牌界面——web 的空会话 hero：品牌栏、带 Preview 徽章的鱼形标题、workspace／agent preset chip 行、淡蓝辉光上的输入卡片——在手机上缺席。需求是把这套画面构成同样做到 Android 应用里。

## Decision

**hero 是已连接态的根。** `MainActivity` 沿用客户端的单 Activity 纯状态导航（不用导航库，见[v1 决策](2026-08-22-android-remote-client.zh.md)）：Ready 状态下 `HeroScreen` 是根，会话树与设置屏（带返回手势的既有 `ConnectScreen`）从 hero 侧栏压入，打开会话时 `ChatScreen` 叠在上方，一切返回都回到 hero。断开连接时，连接屏重新成为根。

**固定 web 深色令牌，忽略设备主题。** `ui/Theme.kt` 把 web 深色配色（`design-platform.css` 的 `body[data-ds-dark-theme]`：基底 `#151517`、层级／标签／边框梯度、deepseek 蓝、辉光 `#6187D8`）映射到 Material3 深色方案，由 `DshTheme` 无视设备设置恒定渲染，使这套构图读起来就是 web 的构图。

**构图是移植，不是重建。** `ui/HeroScreen.kt` 在居中的堆叠左侧放一条 56dp 侧栏（品牌鱼、新聊天、新终端、会话、设置）：鱼形标志 + 标题 + Preview 徽章；chip 行（workspace chip 与 preset chip，各为图标 + 标签 + 箭头）；以及 22dp 圆角的输入卡片（16sp 输入框、+／盾牌／回形针圆形工具按钮、模型 chip、圆形上箭头发送），堆叠背后绘制径向蓝色辉光。图标是 `res/drawable/` 里的矢量 drawable；鱼的路径原样移植自 web 的 `FishLogo.tsx`。

**行为复用客户端既有规则。** 侧栏新聊天与 hero 发送都经 `newSessionTarget`（已暂存的 chip 选择优先）解析目标 workspace，再经 `connectWorkspace` 创建或复用目标 workspace 的空会话。workspace chip 打开一个对话框，列出 `workspace.list` 镜像外加一项 host cwd；暂存选择可跨越屏面切换保留。

**agent preset 暂存镜像 web 的 seat-store。** preset chip 从 `agentPreset.list` 名册（每次 Ready 刷新）中暂存一次选择；发送时已存在的空会话经 `agentPreset.select {sessionId, agentPreset}` 应用暂存 preset，随后暂存即被消费。select 失败会显示为注释，但既不阻塞 prompt 也不阻塞会话打开。模型 chip 显示 `host.describe` 的 `model` 字段——`session.models` 需要 session id，而 hero 没有会话。chip 与选择器渲染 `presetDisplayName`，镜像 web 的 `presetDisplayText`：内置（system）preset 采用本地化的内置名称（web 英文文案），其余回退到 preset 自身元数据，再回退到其 id。

## Alternatives considered

**在 WebView 中嵌入 web hero（或用静态图）。** 否决：按 v1 决策，客户端是原生的，正是为了避免重新托管 web shell；静态图也无法显示实时的 workspace、preset 或模型状态，更不能接收输入。

**会话树仍为根，hero 作为独立屏面。** 否决：web 上无会话时呈现的就是 hero；让列表做根会让手机的默认状态与 web 脱节，并把新会话输入器埋进一次导航跳转之后。

**从 `session.models` 或手机端 preset store 推导 preset／模型 chip。** 否决：hero 没有会话，`session.models` 无法调用；完整的 preset store 会复制应用并不需要的 web 侧状态——一份名册加发送时的一次性 select 就是所需的全部线面。

## Consequences

应用现在消费 `agentPreset.list`（每次 Ready 抓取；失败落入错误注释）与 `agentPreset.select`（发送时，仅限空会话）。+／盾牌／回形针附件按钮、模型 chip、新终端入口都是显示"未实装"toast 的惰性占位；v1 的负面保证（无队列位置、附件、权限 UI）随之带入 hero。暂存（workspace、preset、草稿）保存在 hero 的 `rememberSaveable` 状态里：可跨越屏面切换保留，但不会像服务器 URL 那样持久化。会话树获得返回箭头——它现在是压入的屏面——设置则是带返回手势复用的同一 `ConnectScreen`。系统返回键沿同一栈回退：聊天、会话树、设置都回到 hero 根，而不是离开应用。

## Testing

`assembleDebug` 用 Android Studio JBR 对着本地 SDK 离线构建，debug APK 经 adb 安装。已对照运行中的 `dsh web` 验证：hero 渲染出侧栏、带 Preview 徽章的标题、两个 chip，以及深色令牌背景上辉光之上的输入卡片；workspace 对话框列出宿主的 workspace；发送时创建或复用目标 workspace 的空会话、发出 prompt 并打开聊天；侧栏新聊天打开空会话；返回时回到 hero 且草稿完好。

## Related

- [Android 客户端通过 /api 线协议驱动 dsh web](2026-08-22-android-remote-client.zh.md) —— 本改动所基于的传输、折叠与 v1 面。
- [API 代理线协议](../../../../packages/host/apiproxy/README.zh.md) —— 所消费的一元 + WebSocket 协议格式（wire format），含 `agentPreset.list` / `agentPreset.select`。
