# Agent Note: Brave Search provider

Status: implemented

English | [中文](2026-08-27-web-search-brave-provider.zh.md)

## Problem

The web capability family registers search backends into `ctx.web`, and `dsh-tool-web` exposes one stable `web_search` tool no matter which backend executes. The Exa, Perplexity, and DeepSeek providers cover three backends, but a deployment that wants Brave Search — the other widely available API-backed search service — has no provider to mount.

A new search backend must fit the seam: it must not change the model-facing tool schema, must not introduce its own error vocabulary, and must honor the provider-selection, cancellation, and truncation semantics that `dsh-web` and `dsh-tool-web` own.

## Decision

`@deepseek-ai/dsh-web-search-brave` registers a `brave` search provider into `ctx.web`. The API key comes from the plugin's `apiKey` config or, absent that, from `$BRAVE_API_KEY` at plugin load; an empty key makes the provider report itself unavailable. `baseURL` defaults to the public endpoint `https://api.search.brave.com`; `count` supplies the default result count for requests carrying no `maxResults`.

Each search is a `GET` to `{baseURL}/res/v1/web/search` with a `q` parameter and, when defined, a `count` parameter clamped to the API's maximum of 20. The request carries `x-subscription-token`, `accept: application/json`, the harness user agent, the caller's abort signal, and `redirect: 'error'`. The provider never follows redirects: the request carries the credential, so a redirect would forward it to an arbitrary target, and the regression suite proves no redirect target is contacted for every status code in the redirect family.

`web.results[]` maps to `WebSearchSource` entries: `url`, `title`, `description` as the snippet, and `page_age` (ISO-8601) as `publishedAt`; entries without a URL or without a non-blank description are dropped. The provider omits `content` — Brave returns no generated answer — and always reports `truncated: false`, because `dsh-web` enforces the request's `maxResults` bound on the way back.

Non-2xx responses are parsed as Brave's `ErrorResponse` envelope and rejected with `WEB_PROVIDER_ERROR`, using `error.detail` as the message when non-empty and otherwise `Brave API error (HTTP <status>)`; an unparseable success body is likewise `WEB_PROVIDER_ERROR` with `Brave returned an unprocessable response body: <error>`. A caller abort at any point, including mid-body-parse, surfaces as `WEB_ABORTED`. The count clamp is upper-only: the tool layer already guarantees a positive integer `maxResults`, so a direct seam caller passing a degenerate count fails honestly at the API instead of being silently corrected.

The provider is a standalone opt-in: it ships as its own package with no row in the shipped base composition, which pins `deepseek-official`, following the Exa and Perplexity precedent.

## Alternatives considered

**Integrate the key with the settings service and the Web Models page.** Rejected because that integration exists to make the product's documented first-run path work: the DeepSeek provider reads `DEEPSEEK_API_KEY` through `ctx.credentials` because the browser stores it. Brave is not part of the shipped base composition and no UI stores its key, so the environment-variable pattern Exa and Perplexity established is the matching shape.

**Follow same-origin redirects (or the API's default redirect policy).** Rejected because the request carries the subscription token, so a redirect would forward the credential to an arbitrary target. `redirect: 'error'` turns that defect into a structured failure, and the regression pins it per status code.

**Request `extra_snippets` for richer result text.** Rejected because `description` is the canonical snippet field and the extra-snippet fields cost additional API quota without changing the result fields the tool renders.

**Clamp `count` to a lower bound of 1 as well.** Rejected because `dsh-tool-web` validates `maxResults` as a positive integer before any provider sees it, so a direct seam caller passing a degenerate count should fail at the API boundary rather than have the provider silently correct its input.

**Mount the provider in the shipped base composition or make it selectable from the Web Models page.** Rejected because the base composition pins `deepseek-official`; Exa and Perplexity are standalone opt-ins, and Brave follows the same precedent. A deployment that wants Brave adds the plugin row and sets `dsh-web`'s `searchProvider: brave` in a personal or `--config` overlay.

## Consequences

A deployment points `web_search` at Brave Search with one plugin row and one environment variable: no new tool schema, no new error codes, and no composition change beyond adding the row and selecting the id. Results reach the model with the same sources, titles, snippets, and publication dates as any other backend, and a Brave failure surfaces as the provider-specific message (`Brave search aborted`, `Brave search request failed: <error>`, or the API's error detail) under the tool's error wrapper.

In exchange, Brave-backed searches return sources without a generated answer; redirects on credentialed requests are a hard error rather than transport behavior; and the key is read at plugin load, so a rotated environment variable requires a restart, matching Exa and Perplexity. Unit tests pin mapping, availability, request construction, clamping, the error envelope, and abort; the redirect regression suite over local `node:http` servers proves no redirect target is contacted; and an optional end-to-end test self-skips without `$BRAVE_API_KEY` and exercises the live API when the key is present.

## Related

- [Web capability seam](../architecture/2026-06-24-web-capability-seam.md)
- [Default Web search in shipped compositions](2026-07-31-web-default-search.md)
