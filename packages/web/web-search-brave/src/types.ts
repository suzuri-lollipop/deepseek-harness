/**
 * Wire types for the Brave Search API (`GET https://api.search.brave.com/res/v1/web/search`).
 * Types only — no runtime code. The success envelope nests web results under
 * `web.results[]`; each entry carries a URL, an optional title, an optional
 * `description` snippet, and an optional ISO-8601 `page_age` timestamp.
 * Failures use a separate envelope: `{ type: 'ErrorResponse', error: { code,
 * detail, status } }`.
 *
 * @module @deepseek-ai/dsh-web-search-brave/types
 */

/** One entry of Brave's `web.results[]`. */
export interface BraveWebResult {
  title?: string | null
  url?: string | null
  /** The result's search snippet; the provider maps it to `snippet`. */
  description?: string | null
  /** Publication/crawl timestamp as an ISO-8601 string, when known. */
  page_age?: string | null
}

/** Brave's success search response envelope. */
export interface BraveSearchResponse {
  type?: string
  query?: { original?: string }
  web?: { results?: BraveWebResult[] }
}

/** Brave's error response envelope (best-effort; fields vary by failure). */
export interface BraveErrorResponse {
  type?: string
  error?: {
    code?: string
    detail?: string
    status?: number
    meta?: Record<string, unknown>
  }
}
