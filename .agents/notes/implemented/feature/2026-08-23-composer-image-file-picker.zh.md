# Agent Note：composer 图片文件选择器

状态：implemented

[English](2026-08-23-composer-image-file-picker.md) | 中文

## 问题

图片加入只有两个入口：向 composer 粘贴，以及在页面任意位置拖放。两者对新用户都不可发现——粘贴要求剪贴板上已经有图片，拖放目标只在拖拽过程中出现——而且在剪贴板和拖放不可用的场景两者都不可达：远程或移动端客户端、读屏流程，以及"从磁盘附加一个文件"这一常见路径。加入预检、待发送图片栏、拒收文案、宿主端准入链路都已完成，缺的只是入口控件。

## 决定

composer 工具行新增一个回形针附加按钮（与命令菜单触发器相同的 28px 圆形 chrome），点击打开一个隐藏的多文件 input，并把选择结果送入该栏已有的 `intakeImages` 包装——与粘贴、拖放共用同一套预检、拒收横幅和 `addImages` 路径。三个入口汇合到同一个加入实现；选择器本身不引入新的校验、状态或载荷路径。

选择器的 `accept` 镜像 `imageLimits` 投影的媒体类型，使系统对话框过滤到该部署接受的格式；未组合 attachment 服务时，选择器保持渲染且不加过滤，由宿主的权威准入决定。按钮在与拖放覆盖层相同的 `canAcceptDrop` 闸门上禁用，因此锁定、忙、无机器状态下的 composer 拒绝选择器与拒绝拖放完全一致；其 mousedown 保留 textarea 焦点，对话框关闭后输入可以延续。

按钮是 `ui-conversation` `InputBar` 的常驻 composer chrome，不属于 `conversation.input.attachments` slot：加入行为是栏自身的输入行为（预检和包装都在 `InputBar`），slot 保持为草稿的可选呈现（图片栏、拖放覆盖层、灯箱），attachment 呈现插件缺席时选择器依然可用。slot 既有的"位于常驻 chrome（access mode、plan、attach）之后"的排序即为其座位。

## 备选方案

**把选择器放进 attachments slot（`ComposerAttachments`）。** slot 已经接收 `onAddImages` 和 `canAcceptDrop`，是改动最小的路线。落选原因：它让一个核心输入手势依赖一个可选呈现插件——`ui-attachment` 缺席时粘贴仍然可用（机器持有草稿），选择器却会消失；slot owner 契约还会承担一个它并未为此设计的 DOM 级选择器职责。

**新增命名输入座位（`conversation.input.attach`）并配 owner props。** plan/model 座位之所以命名，是因为其 owner 除 `locked` 外不携带会话数据；选择器需要加入回调、拖放闸门和投影的媒体类型，只有栏自身的接线拥有这些。命名座位必须扩宽其 owner share 来携带它们。

**左 slot 条目。** `conversation.input.left` list slot 共享通用 `InputZone {session, input}`——没有加入回调，也没有拖放闸门。其 JSDoc 把 attach 记为条目之后的常驻 chrome，即本次落地的形态。

## 后果

图片加入拥有三个可发现的入口，共享同一套加入实现：粘贴用于剪贴板图片，拖放用于页面任意文件，选择器用于从磁盘取文件的路径，包括剪贴板和拖放不可达的远程与移动端场景。工具行多出一个 28px 圆形 chrome；新增 DOM 仅有一个 `display: none` 的 file input，不在 tab 序列中，只能经由按钮触达。

[整页图片拖放 note](2026-08-12-web-image-intake-and-limits-alignment.zh.md) 拥有本选择器复用的加入预检与拒收语义。
