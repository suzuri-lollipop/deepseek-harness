# Agent Note: The browse dialog's left drives-and-folders tree

Status: implemented

[English](2026-08-27-directory-picker-nav-tree.md) | 中文

## 问题

[浏览对话框](../../../../packages/host/directory-picker-browse/README.zh.md)此前只能靠面包屑、路径编辑器或键入前缀到达文件夹。Windows 上面包屑止于盘符根（`C:\`），因此第二块存在的盘符只能靠键入路径到达；对话框根本不展示盘符。多盘符的 Windows 部署就是缺口——POSIX 部署的面包屑里本就有唯一的 `/` 根。对话框需要 Windows 资源管理器风格的左栏：平台根目录加文件夹，惰性展开，行点击即导航 Miller 视图。

## 决策

**根目录来自探测，而非枚举断言。** [目录选择能力 seam](../architecture/2026-07-28-directory-picker-capability-seam.zh.md) 的 browse 能力新增 `listRoots(signal?)` → `DirectoryEntry[]`，经协议以 `host.listRoots` 提供（空载荷，`{ roots }` 应答）。后端用 `filesystemRoots(platform)` 计算平台候选——win32 上为按字母序的 26 个盘符根 `A:\`…`Z:\`，其余平台为单个 `/`——对每个候选做一次 `stat`，按探测顺序返回存在的目录：stat 失败即静默省略该候选（不存在或不可访问的盘符不是根、也不是错误），调用方的 signal 会停止其余探测，并以调用方自身的理由拒绝，而不是报「盘符缺失」。根以自身完整路径命名（`/`、`C:\`），与根 crumb 约定一致。候选表是静态字母表，探测是标准库 `stat`——按 seam note 的依赖调查，不引入盘符枚举依赖。

**树是扁平 DFS 列表，配每次打开的缓存。** `DirectoryBrowser` 在 Miller 各列之侧渲染左栏：一个带 aria-label 的容器，内含一个 `div[role="tree"]`，其子为兄弟 `span[role="treeitem"]` 座位，缩进由深度类给出。根在每次打开时探测一次（带 generation 防护）；每次展开复用与栏目相同的 `listDirectory`，在逐节点 `AbortController` 之下进行。节点状态与子缓存在对话框关闭时重置；折叠中止进行中的扫描，但迟到的结果仍入缓存，因此重新展开直接渲染缓存、不产生新扫描；共享的「显示隐藏」过滤在渲染时同样作用于树的子项。节点扫描失败只标记该座位（座位内 alert；重新展开时重试）；根探测失败在栏目标题处汇报，树不渲染任何座位——各栏照常在家目录列举上推进。行点击就是一次普通 `navigate()`：根层级落地单栏，更深落地为选中锚定的双栏——没有独立的树导航路径。激活行携带 `aria-current`，点亮当前选中项，未选中时点亮所列举的层级本身；比较不区分大小写，因此 Windows 上 `c:\` 与 `C:\` 是同一行。

**导航不会展开树。** crumb 跳转、提交的路径与草稿跟随行走都绝不把树展开到目标路径：展开保持为显式的 chevron 手势，每次打开的缓存不是导航的前置条件，自动展开会为草稿的每个按键重扫每一层。

**该栏是树，不是第二个导航地标。** 面包屑已持有 `role="navigation"`；再给树套一个 `<nav>` 会改变对话框的地标读法，也会击穿所有查询唯一面包屑轨迹的既有断言。因此该栏是普通的 aria-label 容器，内含一个 `role="tree"`；`aria-expanded` 挂在座位 span 上（不在 chevron 上），展开/收起动作是带 `browser.nav.expand/collapse:<name>` 标签的 chevron 按钮。遍历先推入座位再访问其子节点，DOM 顺序因此永远先父后子（JSX 子表达式会在 push 之前求值并颠倒顺序）；文案位于对话框自身的 `directory-browser` locale 命名空间，键前缀 `browser.nav.*`。

**图钉已从设计中移除。** 最初草图在栏内携带固定目录；操作者要求只留盘符与文件夹，因此没有图钉存储、动作或文案——图钉功能有消费方时另立 note。

## 备选方案

- **嵌套 `<ul>`/`<li>` 树 DOM。** 否决：对话框的行机制（焦点回停、选中、spec 查询）围绕扁平列表行构建；嵌套会把行复制进树内，并把座位状态（aria-expanded、逐节点状态）压进第二套 DOM 形态。扁平座位列表让所有地方只有一种行形态。
- **自动展开树以跟随导航。** 否决：每次导航每层多一次 `listDirectory`，而缓存的意义正是让重新展开免费——草稿每个按键都去回填它，等于把这份免费花在树自身的运动上。
- **`drivelist` 式的原生盘符枚举。** 按 seam note 的依赖调查否决；静态字母表加逐候选 `stat` 只用标准库，且探测让候选表在不足 26 盘的机器上保持诚实。
- **树内固定目录（图钉）。** 设计评审中由操作者移除——栏内只有盘符与文件夹。重新引入需要存储、动作与文案，因此另立 note，而不是在这条上加开关。

## 测试

- client 侧导航 spec 端到端钉住树的行为：打开时带 signal 的根探测、带隐藏过滤与缓存重展开的惰性展开、折叠中止进行中扫描且迟到结果仍入缓存、座位级失败与重试、行点击导航与 `aria-current` 的移动、无选中时点亮所列举层级、关闭/重开的重置、根探测失败时不渲染座位并给出栏级 alert。
- 宿主 service spec 按平台钉住 `filesystemRoots`（win32 为 26 个按字母序的盘符根，POSIX 为 `/`），并在真实临时目录树上钉住 `listRoots`（临时盘所在盘符根存在、不存在的盘符被省略、中止时表面调用方自身的理由）；apiproxy spec 钉住 `host.listRoots` 的协议映射（类型化错误、中止报 cancelled、native 组合下 `directory-picker-unavailable`）。
- workspace-management web e2e 钉住组装后对话框的 aria golden：导航栏位于路径编辑器与第一列之间，为 `Drives` 标题文本、`role="tree"` 与根座位（POSIX 平台上是单个以 `/` 命名的 `treeitem`，含其展开箭头与行按钮）。

## 后果

- 多盘符的 Windows 部署可不靠键入、直接从栏内到达任意存在的盘符；POSIX 部署看到单个 `/` 根，其余界面不变。
- 每次打开多一次 RPC（根探测：每候选一次 `stat`——Windows 26 次、POSIX 1 次），每个展开的节点再一次；两者都可中止，且都与栏目走同一条协议方法。
- 对话框主体多了一个顶层区域（导航栏）；面包屑仍是唯一的 `navigation` 地标，根状态行位于 `role="tree"` 容器之外，探测失败不会渲染出无法解释的空树。
- `host.listRoots` 与 `listDirectory`/`createDirectory` 一样仅 browse kind 可用；native 组合应答 `directory-picker-unavailable`，client 流程经 `ctx.workspaces.listDirectoryRoots` 透传探测，不做任何 kind 分支。
