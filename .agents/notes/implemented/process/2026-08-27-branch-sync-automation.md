# Agent Note: branch-sync local commit rules automation

Status: implemented

English | [中文](2026-08-27-branch-sync-automation.zh.md)

## Problem

One working tree carries three surfaces: the Android client (`feature/android-remote-client`), the Web UI (`feature/webui`), and shared or backend changes. Every integration batch had to classify files by hand — staging each set on its branch, splitting `pnpm-lock.yaml` hunks across branches, resolving cross-branch Agent Note references against the translation-pairing gate, and merging everything into `develop`. The steps are mechanical, order-sensitive, and easy to get wrong.

## Decision

**`pnpm run branch:sync` classifies the uncommitted working tree and commits it per branch.** The rules live in `scripts/branch-rules.json`: each rule maps repository-relative path globs to one feature branch, and rules are evaluated in array order with first match winning. Paths matching no rule follow `unmatchedPolicy` (default `develop`: commit them straight to the merge target); paths matching a `skip` pattern (local scratch) are never committed and are reported instead.

**The pipeline stages the whole working tree on a temporary snapshot branch**, so every branch switch starts from a clean tree. For each rule it checks out the branch, folds in remote movement and whatever landed on `develop` since the branch forked (a merge, never a rebase), restores the bucket's files from the snapshot, and commits — skipping the commit when the branch already contains identical content. Each touched branch is then merged into `develop` with `--no-ff` and the conventional merge message, in rule order. The shared bucket is committed straight onto `develop`, and `develop` plus every branch ahead of its remote ref is pushed to the `fork` remote.

**Rule order encodes note-reference dependencies.** A branch whose Agent Notes may reference another branch's notes must be listed after it — currently `feature/android-remote-client` before `feature/webui` — because the translation-pairing gate requires referenced note files to exist on the committing branch, and `develop` already contains earlier-processed branches by the time a later branch commits.

**Failures preserve work.** Any failed gate or git error keeps the snapshot branch (during the snapshot stage the staged content is stashed instead, since it is the only copy) and prints the starting `develop` SHA, the snapshot SHA, and how to recover.

## Alternatives considered

**Git hooks (pre-commit or post-commit).** Rejected because a hook fires per commit and cannot see the whole working tree at once; the automation is a batch operation across several branches.

**A background file watcher.** Rejected because it would commit half-written edits. The user runs one command at a deliberate integration point.

**Hunk-level lockfile splitting as the default.** Rejected as bespoke machinery. Lockfiles follow `unmatchedPolicy` to `develop`; a feature branch whose `package.json` gained a dependency temporarily carries a lockfile missing that dependency until `develop` (which always has the full lockfile) is merged in, which the pipeline does before committing the branch.

**A GitHub-stacks workflow.** Rejected because this is local-only: the fork's remote branches are backup mirrors, not pull requests.

## Consequences

Integration batches are one command. Feature branch histories gain merge commits that pull `develop` forward; nothing is rewritten, so every push stays a fast-forward. The renamed `feature/webui` branch is re-established on its first push (the stale `fork/feature/composer-image-file-picker` mirror remains). Scratch files listed under `skip` stay untracked until a `skip` entry is added or removed; anything unlisted and unclassified lands on `develop` by default, so new feature areas need an explicit rule to keep their files off `develop`. The rule file is plain JSON: adding a rule or a skip pattern needs no code change.
