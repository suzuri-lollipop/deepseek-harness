# Agent Note: The browse dialog's left drives-and-folders tree

Status: implemented

English | [中文](2026-08-27-directory-picker-nav-tree.zh.md)

## Problem

The [browse dialog](../../../../packages/host/directory-picker-browse/README.md) reached folders only through the crumb trail, the path editor, or typed prefixes. On Windows the crumb trail stops at the drive root (`C:\`), so a second present drive was reachable only by typing its path; the dialog showed no drives at all. Multi-drive Windows deployments were the gap — POSIX deployments already had the single `/` root in their crumbs. The dialog needed a Windows-Explorer-style left pane: the platform roots plus folders, lazily expanded, with a row click navigating the Miller view.

## Decision

**Roots arrive through a probe, not an enumeration claim.** The browse seam of the [directory-picker capability seam](../architecture/2026-07-28-directory-picker-capability-seam.md) gains `listRoots(signal?)` → `DirectoryEntry[]`, served on the wire as `host.listRoots` (empty payload, `{ roots }` response). The backend computes platform candidates with `filesystemRoots(platform)` — the 26 drive roots `A:\`…`Z:\` in letter order on win32, the single `/` elsewhere — and probes each candidate with one `stat`, returning the present directories in probe order: a failed stat omits that candidate silently (an absent or inaccessible drive is not a root, not an error), and the caller's signal stops the remaining probes, rejecting with the caller's own reason rather than a missing-drive error. Roots name themselves by their full path (`/`, `C:\`), matching the root-crumbs convention. The candidate list is the static alphabet and the probe is a stdlib `stat` — no drive-letter dependency, per the seam note's survey.

**The tree is a flat DFS list with a per-open cache.** `DirectoryBrowser` renders a left pane beside the Miller columns: a named container holding one `div[role="tree"]` of sibling `span[role="treeitem"]` seats, indented by depth class. The roots are probed once per open (generation-guarded); every expansion reuses the same `listDirectory` the panes use, under a per-node `AbortController`. Node states and the child cache reset when the dialog closes; a collapse aborts the in-flight scan but a late result still fills the cache, so a re-expand renders from cache without a new scan, and the shared show-hidden filter applies to tree children at render time. A failed node scan marks only that seat (in-seat alert; re-expand retries); a failed root probe reports it in the pane heading and the tree renders no seats — the columns proceed on the home listing either way. A row click is a plain `navigate()`: the single-pane landing at root level, the two-pane selection-anchored landing deeper — no separate tree-navigation path. The active row carries `aria-current` and lights the current selection, or the listed level itself when nothing is selected; the comparison is case-folded, so `c:\` and `C:\` are the same row on Windows.

**Navigation does not expand the tree.** Crumb jumps, submitted paths, and draft-following walks never expand the tree's path to the target: expansion stays an explicit chevron gesture, the per-open cache is not a navigation precondition, and auto-expansion would re-scan every level of every draft keystroke.

**The pane is a tree, not a second navigation landmark.** The crumb trail already owns `role="navigation"`; a second `<nav>` around the tree would change the dialog's landmark reading and every existing assertion that queries the single crumb trail. The pane is therefore a plain aria-labeled container with one `role="tree"`; `aria-expanded` lives on the seat span (not the chevron), and the expand/collapse action is the chevron button with `browser.nav.expand/collapse:<name>` labels. The walk pushes each seat before visiting its children, so DOM order always reads parent-then-children (a JSX child expression would evaluate before the push and reverse the order); the copy sits in the dialog's own `directory-browser` locale namespace under `browser.nav.*`.

**Pins were removed from the design.** The original sketch carried pinned directories in the pane; the operator asked for drives and folders only, so there is no pin store, action, or copy — a pin feature is a separate note when it gets a consumer.

## Alternatives considered

- **Nested `<ul>`/`<li>` tree DOM.** Rejected: the dialog's row machinery (focus parking, selection, spec queries) is built around flat list rows; nesting would duplicate the row inside the tree and push seat state (aria-expanded, per-node status) behind a second DOM shape. The flat seat list keeps one row shape everywhere.
- **Auto-expanding the tree to follow navigations.** Rejected: one extra `listDirectory` per level per navigation, and the cache exists to make re-expansion free — refilling it on every draft keystroke would spend that freedom on the tree's own motion.
- **A `drivelist`-style native drive enumeration.** Rejected per the seam note's dependency survey; the static alphabet plus per-candidate `stat` is stdlib-only and the probe keeps the list honest on machines with fewer than 26 drives.
- **Pinned directories in the tree.** Removed by the operator during design review — the pane is drives and folders only. Reintroduction needs a store, actions, and copy, so it is a separate note, not a flag on this one.

## Testing

- The client nav spec pins the tree behavior end to end: the root probe on open (signal-carrying), lazy expansion with the hidden filter and cached re-expand, collapse aborting an in-flight scan whose late result still fills the cache, the per-seat failure and retry, the row-click navigation with the `aria-current` move, the listed-level highlight when nothing is selected, the close/reopen reset, and the root-probe failure rendering no seats with a pane-level alert.
- The host service spec pins `filesystemRoots` per platform (26 letter-ordered drive roots on win32, `/` on POSIX) and `listRoots` over a real temp tree (the temp drive's root present, absent drives omitted, abort surfacing the caller's reason); the apiproxy spec pins `host.listRoots`'s wire mapping (typed errors, cancelled on abort, `directory-picker-unavailable` under a native composition).
- The workspace-management web e2e pins the assembled dialog's aria golden: the nav pane sits between the path editor and the first column as the `Drives` heading text, the `role="tree"`, and the root seat (on POSIX platforms a single `treeitem` named `/`, with its chevron and row buttons).

## Consequences

- Multi-drive Windows deployments reach any present drive from the pane without typing; POSIX deployments see the single `/` root and an otherwise identical pane.
- One extra RPC per open (the root probe: one `stat` per candidate — 26 on Windows, 1 on POSIX) plus one per expanded node; both are abortable, and both ride the same wire method as the panes.
- The dialog body carries a third top-level region (the nav pane); the crumb trail remains the only `navigation` landmark, and the root status rows sit outside the `role="tree"` container so a probe failure never renders an unexplained empty tree.
- `host.listRoots` is browse-kind-only like `listDirectory`/`createDirectory`; native compositions answer `directory-picker-unavailable`, and the client flow passes the probe through `ctx.workspaces.listDirectoryRoots` with no kind branching.
