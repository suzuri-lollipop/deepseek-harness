/**
 * `BraveSearchProvider`: a `WebSearchProvider` backed by the Brave Search API
 * (`GET /res/v1/web/search` with the `x-subscription-token` header). It maps
 * `description` to `snippet`, maps `page_age` to `publishedAt`, drops entries
 * without a snippet, and omits `content` because Brave returns no generated
 * answer.
 * @module @deepseek-ai/dsh-web-search-brave/provider
 */

import { WebError } from '@deepseek-ai/dsh-web'
import type {
  WebSearchProvider,
  WebSearchRequest,
  WebSearchResult,
  WebSearchSource,
} from '@deepseek-ai/dsh-web'
import type { BraveErrorResponse, BraveSearchResponse, BraveWebResult } from './types.ts'

/** Stable id this provider registers under. */
export const BRAVE_PROVIDER_ID = 'brave'

/** Default Brave Search endpoint; `/res/v1/web/search` is the operation. */
export const BRAVE_DEFAULT_BASE_URL = 'https://api.search.brave.com'

/**
 * Upper bound of Brave's documented `count` parameter. The request layer
 * clamps to it; the seam still enforces the request's full `maxResults` bound
 * on the way back.
 */
export const BRAVE_MAX_COUNT = 20

/** Attribution header sent on every request. Bump with the package version. */
const USER_AGENT = 'deepseek-harness/0.0.1'

/** Resolved provider options (the plugin's `apply` supplies env-var and constant defaults). */
export interface BraveSearchProviderOptions {
  /** Brave API key. Empty/absent makes the provider unavailable. */
  apiKey: string
  /** Endpoint base; `/res/v1/web/search` is appended. */
  baseURL: string
  /** Default result count when a request carries no `maxResults`. */
  count?: number
}

/**
 * Map one Brave result to a normalized source, or `undefined` when it carries
 * no portable snippet (an entry without a `description` is dropped — the seam
 * has no other field to derive a snippet from, and inventing one would lie).
 *
 * @param result - one entry of Brave's `web.results[]`.
 * @returns the normalized source, or `undefined` when the entry has no URL or
 *   no non-blank `description`.
 */
export function mapBraveResult(result: BraveWebResult): WebSearchSource | undefined {
  const url = result.url
  if (url == null || url.length === 0) return undefined
  const snippet = result.description
  if (snippet == null || snippet.trim().length === 0) return undefined
  return {
    url,
    ...result.title != null && result.title.length > 0 ? { title: result.title } : {},
    snippet,
    ...result.page_age != null && result.page_age.length > 0 ? { publishedAt: result.page_age } : {},
  }
}

/**
 * Map a Brave response envelope to a normalized search result.
 *
 * @param response - the parsed `GET /res/v1/web/search` response body.
 * @returns the normalized result; snippet-less entries are dropped
 *   ({@link mapBraveResult}).
 */
export function mapBraveResponse(response: BraveSearchResponse): WebSearchResult {
  const sources = (response.web?.results ?? [])
    .map(mapBraveResult)
    .filter((source): source is WebSearchSource => source !== undefined)
  // Brave returns no generated answer, so `content` is omitted. The web service owns the
  // final `maxResults` truncation, so this provider reports `truncated: false`.
  return { sources, truncated: false }
}

/** The Brave-backed search provider; HTTP redirects fail as `WEB_PROVIDER_ERROR`. */
export class BraveSearchProvider implements WebSearchProvider {
  readonly id = BRAVE_PROVIDER_ID

  constructor(private readonly options: BraveSearchProviderOptions) {}

  available(): boolean {
    return this.options.apiKey.length > 0
      && isValidBaseUrl(this.options.baseURL)
      && (this.options.count === undefined || isPositiveInteger(this.options.count))
  }

  async search(request: WebSearchRequest, signal?: AbortSignal): Promise<WebSearchResult> {
    // A per-request bound wins over the configured default; either may be absent.
    const count = request.maxResults ?? this.options.count
    let response: Response
    try {
      response = await fetch(this.searchUrl(request.query, count), {
        method: 'GET',
        redirect: 'error',
        headers: {
          'accept': 'application/json',
          'x-subscription-token': this.options.apiKey,
          'user-agent': USER_AGENT,
        },
        ...signal !== undefined ? { signal } : {},
      })
    } catch (error: unknown) {
      if (isAbortError(error)) throw new WebError('Brave search aborted', 'WEB_ABORTED', { cause: error })
      throw new WebError(`Brave search request failed: ${String(error)}`, 'WEB_PROVIDER_ERROR', { cause: error })
    }

    if (!response.ok) {
      const status = response.status
      let message = `Brave API error (HTTP ${status})`
      try {
        const parsed = await response.json() as BraveErrorResponse
        const detail = parsed.error?.detail
        if (detail !== undefined && detail.length > 0) message = detail
      } catch (error: unknown) {
        // An abort fired mid-body must surface as WEB_ABORTED, not be swallowed
        // into a generic HTTP-error message — cancellation is not a provider
        // error (the seam's cancellation contract).
        if (isAbortError(error)) throw new WebError('Brave search aborted', 'WEB_ABORTED', { cause: error })
        // Otherwise: the HTTP status is already captured in `message` above; a
        // malformed/non-JSON error body (normal for gateway 5xx) can only
        // cost a richer provider message, never the real error.
      }
      throw new WebError(message, 'WEB_PROVIDER_ERROR')
    }

    try {
      const payload = await response.json() as BraveSearchResponse
      return mapBraveResponse(payload)
    } catch (error: unknown) {
      if (isAbortError(error)) throw new WebError('Brave search aborted', 'WEB_ABORTED', { cause: error })
      throw new WebError(`Brave returned an unprocessable response body: ${String(error)}`, 'WEB_PROVIDER_ERROR', { cause: error })
    }
  }

  /**
   * Build the request URL. `q` is always sent; `count` is clamped to
   * {@link BRAVE_MAX_COUNT}, the API's documented maximum (values above it
   * are rejected with a validation error).
   * @param query - the search query.
   * @param count - the effective result count, if any.
   * @returns the absolute request URL.
   */
  private searchUrl(query: string, count: number | undefined): string {
    const url = new URL(`${this.options.baseURL}/res/v1/web/search`)
    url.searchParams.set('q', query)
    if (count !== undefined) url.searchParams.set('count', String(Math.min(count, BRAVE_MAX_COUNT)))
    return url.toString()
  }
}

// These predicates are intentionally local: exporting generic internals
// from the public web seam would add more API than these pure checks.
/* jscpd:ignore-start */
/** True when `baseURL` parses as an absolute URL (a cheap local config check). */
function isValidBaseUrl(baseURL: string): boolean {
  return URL.canParse(baseURL)
}

/** True for a request limit that can be sent to Brave (a positive whole number). */
function isPositiveInteger(value: number): boolean {
  return Number.isInteger(value) && value > 0
}

/** True for a fetch/`AbortSignal` abort, surfaced as `WEB_ABORTED`. */
function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}
/* jscpd:ignore-end */
