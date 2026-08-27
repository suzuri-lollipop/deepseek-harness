# Agent Note: Android PWA 启动器图标

Status: implemented

[English](2026-08-27-android-pwa-launcher-icon.md) | 中文

## 问题

Android 会把已安装 PWA 的启动器图标合成在自己的背景之上，并按启动器形状进行遮罩。Web 安装 manifest 只声明了主题感知的 `/favicon.svg`——透明底色，标记在深色配色方案下渲染为白色。在深色模式下使用白色合成背景的 Android 启动器上，图标最终呈现为白底上的白色标记：在主屏幕上不可见。

## 决策

manifest 的图标列表现在是三个不透明 PNG，提交在 `apps/web/public/icons/` 下：`icon-192.png` 和 `icon-512.png` 的用途为 `any`，`icon-maskable-512.png` 的用途为 `maskable`。每个 PNG 都是品牌墨色 `#0F1115` 的满铺底色，鲸鱼标记以白色镂空呈现，因此启动器背景既不会透过来，也不会反转标记的颜色。可遮罩变体把标记缩放到居中的、占边长 80% 的安全区内，启动器的任何遮罩（圆形、圆角方形、泪滴形）都不会裁切到它。主题感知的 SVG 不再出现在 manifest 中；`index.html` 仍把它作为标签页 favicon 保留，透明底色在那里才是正确的。

`scripts/gen-web-icons.ts`（`pnpm run gen-web-icons`）从 `apps/web/public/favicon.svg` 中的标记重新生成三个 PNG：它提取标记的路径，用其坐标的控制点确定包围盒，用 `sharp` 将 512 像素的 SVG 文档光栅化，再将该光栅结果缩放得到 192 像素变体。any 图标占画布边长的 88%；可遮罩图标把标记的包围盒收进安全半径内。`scripts/gen-web-icons.spec.ts` 在任一已提交 PNG 与标记发生漂移时使测试套件失败，并固定路径包围盒与缩放几何。

## 验证

Web 构建产物测试解析输出的 manifest 并固定完整的元数据对象，包括三个图标条目；它同时固定 favicon 的深色模式行为。`scripts/gen-web-icons.spec.ts` 将图标重新生成到临时目录，并对每个已提交 PNG 逐像素比对（解码后的原始像素，对 PNG 元数据差异不敏感），同时固定路径包围盒、any 缩放与 maskable 缩放几何。

## 曾考虑的替代方案

**继续在 manifest 中列出主题感知的 SVG。** 不予采纳：它不是适合启动器合成的资产——透明底，且在深色模式下为白色；列出它等于保留出现全白图标的路径。标签页通过 HTML 图标链接继续使用 SVG，透明底色在那里才是正确的。

**改为给 favicon 一个不透明的深色底。** 不予采纳：favicon 服务于标签页，透明底色在浅色和深色浏览器界面中都能正确贴附；不透明底色会在标签页 UI 中渲染成一个深色方块。一个资产无法同时服务两种角色，因此启动器图标是单独提交的资产。

**只交付一个光栅图标，不交付可遮罩变体。** 不予采纳：Android 会对选定的图标进行遮罩；没有 maskable 条目时，启动器会退回对 any 图标做遮罩，而它的 88% 跨度可能被更紧的遮裁切。可遮罩变体存在的意义就是把标记保持在安全区内。

**在 Web 构建期间生成 PNG。** 不予采纳：仓库的生成资产约定——提交产物、生成脚本、漂移即失败的测试——已经拥有这种形态；已提交的 PNG 在历史中可 diff，构建也无需依赖光栅能力。

## 后果

Android 安装者能在任何启动器主题下获得不透明、可安全遮罩的启动器图标。标记派生自 favicon 路径：修改 `apps/web/public/favicon.svg` 后必须先运行 `pnpm run gen-web-icons` 再提交，漂移时测试会失败。manifest 携带三个固定的绝对路径资源 URL；[Web 安装 manifest 元数据](../feature/2026-08-06-web-install-manifest.zh.md) 已要求在路径前缀下部署时把这些 URL 与 manifest 链接、身份、启动和作用域 URL 一并重新审视。`sharp` 是根 devDependency，仅被生成器及其测试使用；没有任何发布的包依赖它。
