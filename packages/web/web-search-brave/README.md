# @deepseek-ai/dsh-web-search-brave

English | [中文](README.zh.md)

A [Brave Search](https://search.brave.com)-backed `WebSearchProvider` for the harness [web capability seam](../web/README.md) (`ctx.web`). It calls Brave's `GET /res/v1/web/search` endpoint with the `x-subscription-token` header and maps `web.results[]` into the seam's normalized `WebSearchResult`.

This is an **implementation** package: it registers a provider into `ctx.web`, it does not own the `ctx.web` key and it does not register a model-facing tool (that is `@deepseek-ai/dsh-tool-web`). Like `@deepseek-ai/dsh-llm-deepseek`, it is a function/namespace plugin (`inject: ['web']`) that registers its backend, not a default-export service.

## Config

| Key | Default | Meaning |
|---|---|---|
| `apiKey` | `$BRAVE_API_KEY` | Brave API key. Empty/absent makes the provider unavailable. |
| `baseURL` | `https://api.search.brave.com` | Endpoint base; `/res/v1/web/search` is appended. An unparseable value makes the provider unavailable. |
| `count` | (unset) | Default result count when a request carries no `maxResults`. Unset sends no default. Must be a positive integer. |

```yaml
- id: web-search-brave
  name: '@deepseek-ai/dsh-web-search-brave'
  config:
    apiKey: !!js process.env.BRAVE_API_KEY
```

## Mapping

Brave returns `web.results[]` and no generated answer, so `content` is omitted. Each result maps to a `WebSearchSource`: `url` ← `url`, `title` ← `title`, `snippet` ← `description` (a result with no description has no portable snippet and is dropped), `publishedAt` ← `page_age`. A request's `maxResults` wins over the configured `count` default; the outgoing `count` is clamped to Brave's documented maximum of 20 and the final bound is enforced by the seam. Provider failures (HTTP errors, network failure, unparseable or wrong-shape bodies) surface as `WebError` `WEB_PROVIDER_ERROR`, using the error envelope's `detail` text when present; an aborted request surfaces as `WEB_ABORTED`. HTTP redirects are rejected before the `Location` target is contacted and surface as `WEB_PROVIDER_ERROR`.

## Model Experience

Indirectly, through [`dsh-tool-web`](../tool-web/README.md), which retains this provider's `maxResults`-bounded URLs, titles, description snippets, and publication dates or its exact `Brave search aborted`, `Brave search request failed: <error>`, and `Brave returned an unprocessable response body: <error>` failures under the consumer's error wrapper while provider-private fields remain outside context.

#### KV Cache effect

No direct invalidation; the named consumer owns any request-prefix changes.

## Known Limitations and Deferred Work

- **A result with no non-blank `description` is dropped entirely** — no portable snippet to map, so fewer sources than the requested count can return.
- **Outgoing `count` is clamped to Brave's documented maximum of 20** — a request's `maxResults` above 20 fetches at most 20 sources; the seam still enforces the full bound on the way back.
- **Only `count` is exposed** — Brave's other controls (search type, freshness, geolocation, extra snippets, full-text contents) wait on provider-neutral Service Definition fields ([seam Agent Note](../../../.agents/notes/implemented/architecture/2026-06-24-web-capability-seam.md)).
- **Abort classification is error-shape-based** — only a `DOMException` named `AbortError` maps to `WEB_ABORTED`; an abort carrying a custom reason (e.g. `dsh-timeout`'s `TimeoutReason`) surfaces as `WEB_PROVIDER_ERROR`.
