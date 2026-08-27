# Agent Note: Android APK 自更新与可见的 OS 状态栏

Status: implemented

[English](2026-08-26-android-apk-self-update-and-status-bars.md) | 中文

## Problem

来自同一用户请求的三个界面问题。其一，已安装的 PWA 请求 `display: "fullscreen"`，这是沉浸式模式，浏览器会把 OS 状态栏完全隐藏——用户希望已安装应用周围能看到 OS 状态栏。其二，Android 应用 target 到 SDK 36，该版本强制 edge-to-edge（边到边绘制），但内容没有任何 inset，直接画在状态栏下方，每个屏幕顶部的行都被状态栏遮住。其三，更新 Android 应用需要在已连接设备上 `adb install`；用户希望 APK 能从 web UI 和应用自身内部下载。

## Decision

**PWA 在 `apps/web/public/manifest.webmanifest` 中请求 `display: "standalone"`**，已安装应用因此保留 OS 状态栏与导航栏。此前的安全区处理——`apps/web/index.html` 的 `viewport-fit=cover` 加上 shell base CSS 中 `#root` 的安全区 padding——作为防御性 inset 保留。浏览器在安装时锁定 PWA 的 display 模式，因此本改动之前的安装会保持 fullscreen，直到用户移除应用并重新添加。

**Android 应用以 edge-to-edge 绘制并给内容加 inset。** `MainActivity` 调用 `enableEdgeToEdge()`，通过 `WindowInsetsControllerCompat` 强制状态栏与导航栏图标为浅色（UI 永远是深色 web 调色板），并在通铺的 `Surface` 内用 `Modifier.systemBarsPadding()` 为屏幕树加 padding——背景延伸到栏后，而每一行都从状态栏下方开始。

**web 宿主携带 debug APK，两个客户端都取用。** `scripts/build.ts` 在 web 构建之后把 `apps/android/app/build/outputs/apk/debug/app-debug.apk` 复制到 `apps/web/dist/dsh-android.apk`（APK 缺失只记一条跳过日志，不是错误），`dsh-host-frontend-static` 把 `.apk` 映射为 `application/vnd.android.package-archive`。web 通用设置页注册一行 shell 自有行——`settings.general.item` slot，id `android`，order 30——带 `download` 属性链接到 `/dsh-android.apk`。Android 连接屏经 `core/ApkDownload.kt` 下载同一路径：专用 OkHttp 客户端带十分钟调用上限，把文件流式写入 `cacheDir/apks/` 并报告进度，拒绝不以 ZIP 本地文件头魔数开头的载荷，然后经 FileProvider（authority `${applicationId}.apk`，缓存路径 `apks/`）以类型 `application/vnd.android.package-archive` 的 `ACTION_VIEW` 安装；`canRequestPackageInstalls()` 为 false 时改为打开 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`。`DshClient.normalizeUrl` 为公开方法，连接与下载共用同一 URL 规范化。

## Alternatives considered

**保留 fullscreen 并围绕 OS chrome 自建标题栏。** 被否决：用户要的就是 OS 状态栏，且 [web 安装 manifest Note](2026-08-06-web-install-manifest.zh.md) 已裁定 DSH 不拥有自定标题栏或原生窗口控件的布局。

**通过命名 web 路由或下载端点提供 APK。** 被否决：`dsh-host-frontend-static` 已经提供 dist 根下任意文件；一个静态文件只需一条 MIME 条目，命名路由只会为同一能力增加 webserver 面。

**为 Android 设置行创建专门客户端包。** 被否决：`dsh-client-ui-settings-general` 本就承载不属于任何单一 feature 的行，而这行没有任何 feature；为一条静态链接走全新包检查单得不偿失。

**经 Play 风格通道或 App Bundle 分发更新。** 对只在宿主机分发的自托管 debug 客户端超纲；应用内路径替代的是 `adb install`，不是商店。

## Consequences

已安装的 PWA 保持 fullscreen，直到用户移除并重新添加；新安装从 OS 栏可见开始。从未运行过 `gradlew assembleDebug` 的机器上，web 构建不带 `dsh-android.apk`：设置链接返回 404，应用内下载报告宿主不配信 APK，web dist 其余部分不受影响。覆盖安装同一包名的运行中应用会在系统安装期间被强制结束——属预期行为，连接屏已注明。所携带 APK 是 debug 签名构建，因此自更新通道只能替换此前以 debug 方式安装的应用；debug 与正式签名之间切换仍需手动安装。新设置行使 settings-chrome 对话框 golden 恰好多出这一行；golden 源自 Linux，重录应发生在 Linux CI 侧，而非本地 Windows 环境。

## Verification

`apps/web/tests/pwa-manifest.e2e.ts` 在构建产物中钉住 `display: "standalone"` 与 viewport 链接；`frontend-static.spec.ts` 钉住 `.apk` 媒体类型；`dsh-client-ui-settings-general` 的 spec 钉住该行的注册选项与渲染出的链接；`gradlew assembleDebug` 编译通过；构建产物以 Android 包媒体类型提供 `/dsh-android.apk`。

## Related

- [Web 安装 manifest 元数据](2026-08-06-web-install-manifest.zh.md) —— 最初的 `display: "fullscreen"` 决策；本 Note 仅取代 display 模式选择，manifest 的身份、启动、图标与无 service worker 决策继续有效。
- [Android 客户端通过 /api 线协议驱动 dsh web](2026-08-22-android-remote-client.zh.md) —— v1 客户端；本改动以宿主配信的一个只读资产扩展其纯消费者范围。
- [Android hero 画面复刻 web 空会话构图](2026-08-24-android-hero-screen.zh.md) —— edge-to-edge padding 所包裹的单 Activity 屏幕树。
