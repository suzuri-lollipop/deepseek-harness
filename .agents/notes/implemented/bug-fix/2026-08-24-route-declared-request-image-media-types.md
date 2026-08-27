# Agent Note: Route-declared request image media types

Status: implemented

English | [中文](2026-08-24-route-declared-request-image-media-types.zh.md)

## Problem

llama.cpp's `llama-server` exposes an OpenAI-compatible route whose image decoder accepts PNG and JPEG but rejects WebP. The harness stores alpha sources as WebP: an alpha screenshot normalizes to WebP, and a pi-ai OpenAI-completions route sends it inline as base64. Every such request fails deterministically with HTTP 400 `Failed to load image or audio file`, and because session history durably carries the normalized reference, every later turn in that session re-sends the same stored WebP and fails the same way. The stored attachment is provider-independent by design. Normalization cannot know what each route's backend decodes, so a stored type a backend cannot decode breaks every request that carries it.

## Decision

`ImageRequestPolicy` gains an optional `mediaTypes` field, and the pi-ai provider profile exposes it as `requestImageMediaTypes`. The profile resolves absence or an empty list to no declaration, while a policy constructed with an empty list fails validation with `INVALID_ATTACHMENT_REF`. With no declaration the pipeline keeps every stored media type: an in-budget stored attachment passes through byte-identically, the request-variant descriptor omits the list, and existing request-image caches stay valid.

A declared list restricts pass-through to stored attachments whose media type the list names. Every other stored attachment re-encodes at request time into an allowed type, using the existing color-branch candidate list filtered to the allowed media types. When the filtered candidate list is empty — alpha against a list without a transparent type, the one class the fixed encoder set cannot encode — the read fails with `UNSUPPORTED_IMAGE_TYPE` instead of degrading the image. The declared list is part of the request-variant descriptor, so declaring or lifting a list derives a new variant for the same stored bytes, and a cached entry written under a different list never satisfies the read.

The built-in DeepSeek route resolves an unrestricted policy because its Files API accepts WebP. A route whose backend cannot decode a stored type declares the types it decodes; `[image/png, image/jpeg]` for a llama.cpp OpenAI-compatible server makes a stored WebP screenshot request as PNG.

## Alternatives considered

**Convert at normalization (save) time.** Normalization stays provider-independent per the owning pipeline decision. One content-addressed object serves every route that reads it, so choosing the most restrictive stored type for the least capable backend degrades every route, and published stored bytes cannot change without a new object.

**Tolerate the provider 400 and retry.** The failure is a deterministic decode rejection, not a transient error. Retrying the same bytes can never succeed, burns turns, and requires the adapter to special-case a provider error whose response body is not part of any contract.

**Store per-route variants.** Duplicates every image object per backend, forces session history to name a route-specific variant for one stored image, and still leaves WebP already stored in existing sessions. Request-time re-encoding recovers that history without migration.

## Consequences

- An alpha screenshot against an `[image/png, image/jpeg]` route requests as alpha PNG and decodes on the stb-based backend; a stuck session history recovers without editing the log.
- Unrestricted routes (the default) preserve byte-identical pass-through and the existing cache identity; the descriptor differs only when a route declares a list.
- The restricted candidate list keeps every image class encodable except alpha against a JPEG-only list, which fails with `UNSUPPORTED_IMAGE_TYPE`.
- A deployment that needs alpha on a restricted route must include a transparent type in its list.
- `request-image.spec.ts` pins restricted pass-through, restricted re-encoding (alpha WebP to PNG, opaque JPEG to PNG, low-color PNG to JPEG), the alpha refusal, empty-list validation, and variant stability. The `llm-pi-ai` config and adapter specs pin the profile key's schema, absence and empty-list resolution, and policy forwarding.

## Related

- [Unified normalized attachments, request versions, and provider files](../feature/2026-08-20-unified-image-request-pipeline.md) owns the pipeline this extends.
