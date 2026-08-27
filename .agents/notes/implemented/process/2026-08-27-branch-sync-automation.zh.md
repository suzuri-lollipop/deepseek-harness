# Agent Note: branch-sync 本地提交规则自动化

Status: implemented

[English](2026-08-27-branch-sync-automation.md) | 中文

## 问题

同一个工作树承载三个面:Android 客户端(`feature/android-remote-client`)、Web UI(`feature/webui`)、以及共享或后端改动。每次集成批次都要手工分类文件——把每组文件分别暂存到对应分支、把 `pnpm-lock.yaml` 的 hunk 拆分到不同分支、在翻译配对门禁下解决跨分支 Agent Note 引用、再把所有内容合并进 `develop`。这些步骤是机械的、对顺序敏感、且容易出错。

## 决策

**`pnpm run branch:sync` 对未提交的工作树做分类，并按分支提交。** 规则放在 `scripts/branch-rules.json`:每条规则把仓库相对路径 glob 映射到一个 feature 分支，规则按数组顺序求值、先匹配者生效。不匹配任何规则的路径遵循 `unmatchedPolicy`(默认 `develop`:直接提交到合并目标分支);匹配 `skip` 模式(本地临时文件)的路径永不提交，只报告。

**流水线先把整个工作树暂存到一个临时快照分支**，这样每次分支切换都从干净树开始。对每条规则，它检出目标分支，并入远程的变动以及该分支分叉后落到 `develop` 上的内容(用合并，绝不用 rebase)，从快照恢复该组文件并提交——若分支已包含相同内容则跳过提交。随后每个被触及的分支按规则顺序以 `--no-ff` 和惯用合并信息合并进 `develop`。共享组直接提交到 `develop`，然后把 `develop` 以及所有领先于其远程引用的分支推送到 `fork` 远程。

**规则顺序编码了 Note 引用依赖。** 若某分支的 Agent Notes 可能引用另一分支的 Note，则它必须列在后者之后——目前是 `feature/android-remote-client` 排在 `feature/webui` 之前——因为翻译配对门禁要求被引用的 Note 文件存在于提交所在的分支上，而靠后处理的分支提交时，`develop` 已包含先前处理过的分支。

**失败时保留工作成果。** 任何门禁或 git 错误都会保留快照分支(快照阶段则改为把已暂存内容放入 stash，因为那是唯一副本)，并打印起始 `develop` SHA、快照 SHA 以及恢复方法。

## 曾考虑的替代方案

**Git 钩子(pre-commit 或 post-commit)。** 拒绝，因为钩子按次提交触发，无法一次性看到整个工作树；本自动化是跨多个分支的批处理操作。

**后台文件监视器。** 拒绝，因为它会提交写到一半的编辑。用户在有意的集成点手动运行一条命令。

**把 hunk 级 lockfile 拆分作为默认机制。** 拒绝，那是定制化的机械装置。Lockfile 遵循 `unmatchedPolicy` 落到 `develop`；某个 `package.json` 新增了依赖的 feature 分支会暂时携带缺失该依赖的 lockfile，直到合并进 `develop`(它始终有完整 lockfile)——流水线在提交该分支之前就会执行这次合并。

**GitHub-stacks 工作流。** 拒绝，因为这是纯本地流程:fork 的远程分支只是备份镜像，不是 pull request。

## 影响

集成批次变成一条命令。Feature 分支历史会新增把 `develop` 向前拉的合并提交；没有任何改写，因此每次推送都是 fast-forward。重命名后的 `feature/webui` 分支在首次推送时于远程重建(陈旧的 `fork/feature/composer-image-file-picker` 镜像保留)。列在 `skip` 下的临时文件保持未跟踪状态，直到新增或删除 `skip` 条目；任何未列出且未分类的内容默认落到 `develop`，所以新的功能区域需要一条显式规则才能让文件不落到 `develop`。规则文件是纯 JSON:新增规则或 skip 模式无需改代码。
