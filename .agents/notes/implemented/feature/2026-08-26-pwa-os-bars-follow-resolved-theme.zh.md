# Agent Note: PWA 系统栏跟随解析后的主题颜色

Status: implemented

[English](2026-08-26-pwa-os-bars-follow-resolved-theme.md) | 中文

## Problem

已安装的 standalone PWA 在应用四周保留 OS 状态栏与导航栏。在 Android 上，状态栏实时跟随文档的 `theme-color` 元数据，但导航栏的颜色固定为页面初始提交时采样的值，之后不会从元数据的后续变化中重新读取。而 `theme-color` 节点只有在客户端插件树激活时（ui-layout 的 ThemePresenter）才创建，晚于首次绘制，因此即使应用解析到浅色调色板，Android PWA 的导航栏仍保持引擎的深色默认值：浅色界面上一条醒目的黑条。

## Decision

**`theme-color` 元数据在初始提交时即携带解析后的基础背景色。** `apps/web/index.html` 携带静态的 `<meta name="theme-color" content="#fff" />`。ui-theme 引导行——`<body>` 起始标签后紧随的同步内联脚本，内嵌 Host 持久化的偏好——按 OS 配色解析 `system`，并把解析后的调色板基础背景写入 meta：浅色为 `#fff`，深色为 `#151517`，与 boot 页面硬编码的调色板回退值相同（design-platform 的 neutral bluish 00 / 950 基础背景）。引导脚本就地处更新已存在的节点，节点缺失时新建。

**ThemePresenter 接管 boot 时创建的节点，而不是再建一个。** 其构造函数接管已存在的 `meta[name="theme-color"]`，仅在不存在时新建，文档因此只保留一个节点。`apply()` 仍在应用调色板与 token 后按计算出的 body 背景重写其内容，因此后续的主题切换——包括改变基础背景的 override 层主题——继续驱动状态栏，`dispose()` 随呈现器其他全局写入一并移除该节点。

## Alternatives considered

**manifest 中的静态 `theme_color` / `background_color`。** 否决：manifest 是静态的，无法承载用户解析后的明暗偏好，固定值必与两种解析调色板之一冲突——这正是 [web 安装 manifest 注记](2026-08-06-web-install-manifest.zh.md) 省略这两个字段的原因。boot 时 meta 在不触碰 manifest 的前提下把解析值放进初始提交，无颜色 manifest 的决策维持不变。

**让 ThemePresenter 继续自建节点，引导脚本不承担 meta 职责。** 否决：激活后将存在两个 `theme-color` 节点，树序在前者会遮蔽另一个，初始提交色与实时更新必有一方失效，取决于节点顺序。单个被接管的节点是唯一能同时保证初始提交色与实时更新的安排。

**从 CSS 自定义属性解析 boot 值。** 否决：设计 token 样式表只在 ui-theme 插件激活时才安装，首次绘制前基础背景无法从 CSS 解析。引导脚本硬编码这两个基础值，与 boot 页面既有的调色板回退保持一致。

## Consequences

Android standalone PWA 的导航栏从初始提交起跟随解析后的主题：浅色模式得浅色条，深色模式得深色条。该值仅限内置调色板基础背景：改变 `--dsw-alias-bg-base` 的第三方 override 层主题会经呈现器更新 meta（与状态栏），但导航栏保持 boot 时值，直到页面重载。静态 HTML 默认值为浅色：boot 前的采样看到 `#fff`，而禁用脚本的页面本就不渲染应用。PWA 启动屏保持引擎默认——本次变更未触碰，仍由无颜色 manifest 的决策管辖。

## Verification

`packages/client/ui-theme/tests/boot-theme.client.spec.ts` 固定引导脚本的 meta 写入：新建、就地更新、与 light/dark/system 解析一致。`packages/client/ui-layout/tests/theme-presenter.client.spec.ts` 固定 boot 时节点的接管、单节点更新与 dispose 移除。`apps/web/tests/pwa-manifest.e2e.ts` 固定构建产物 dist 中 index.html 的静态 meta。

## Related

- [Android APK 自更新与可见的 OS 状态栏](2026-08-26-android-apk-self-update-and-status-bars.zh.md)——让这些栏保持可见的 `display: "standalone"` 决策，以及 Android 应用自身的 edge-to-edge 处理。
- [Web 安装 manifest 元数据](2026-08-06-web-install-manifest.zh.md)——本注记保持原样的无颜色 manifest 决策。
