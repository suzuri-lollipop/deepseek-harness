# Agent Note: Composer image file picker

Status: implemented

English | [中文](2026-08-23-composer-image-file-picker.zh.md)

## Problem

Image intake had exactly two entries: paste into the composer and drop anywhere on the page. Both are undiscoverable to a new user — paste requires an image already on the clipboard, and the drop target appears only mid-drag — and both are unreachable where clipboard and drag are unavailable: remote or mobile clients, screen-reader flows, and the common "attach a file from disk" path. The intake pre-check, the pending-image rail, the refusal copy, and the host-side admission chain were complete; only the entry affordance was missing.

## Decision

The composer's tool row carries a paperclip attach button (the same 28px circular chrome as the command-menu trigger) that opens a hidden multiple file input and routes the selection through the bar's existing `intakeImages` wrapper — the same pre-check, refusal banner, and `addImages` path that paste and drop already share. All three entries converge on one intake implementation; the picker adds no validation, state, or payload path of its own.

The picker's `accept` mirrors the `imageLimits` projection's media types, so the OS dialog filters to the formats the deployment admits; with no attachment service composed, the picker stays rendered unfiltered and the host's authoritative admission decides. The button disables on the same `canAcceptDrop` gate as the drop overlay, so locked, busy, and machine-less composers refuse the picker exactly as they refuse a drop, and its mousedown keeps the textarea's focus so typing continues after the dialog closes.

The button is resident composer chrome in `ui-conversation`'s `InputBar`, not part of the `conversation.input.attachments` slot: intake is the bar's own input behavior (the pre-check and the wrapper live in `InputBar`), the slot stays the optional presentation of the draft (rail, drop overlay, lightbox), and the picker remains available when the attachment presentation plugin is absent. The slot's existing "after the resident chrome (access mode, plan, attach)" ordering names the seat.

## Alternatives considered

**Render the picker inside the attachments slot (`ComposerAttachments`).** The slot already receives `onAddImages` and `canAcceptDrop`, making this the minimal-diff route. It loses because it makes a core input gesture depend on an optional presentation plugin: with `ui-attachment` absent, paste still works (the machine owns the draft) while the picker would vanish, and the slot owner contract would carry a DOM-level picker concern it was not designed for.

**A new named input seat (`conversation.input.attach`) with owner props.** The plan/model seats are named because their owners carry no session data beyond `locked`; the picker needs the intake callback, the drop gate, and the projection's media types, which only the bar's own wiring has. A named seat would widen its owner share to carry them.

**A left-slot entry.** The `conversation.input.left` list slot shares the generic `InputZone {session, input}` — no intake callback, no drop gate. Its JSDoc names attach as resident chrome the entries follow, which is what shipped.

## Consequences

Image intake has three discoverable entries sharing one intake implementation: paste for clipboard images, drop for any file on the page, and the picker for the file-from-disk flow, including remote and mobile contexts where clipboard and drag are unavailable. The tool row gains one 28px circle of chrome; the only new DOM is a `display: none` file input, out of the tab order and reachable only through the button.

The [whole-page image drop note](2026-08-12-web-image-intake-and-limits-alignment.md) owns the intake pre-check and refusal semantics this picker reuses.
