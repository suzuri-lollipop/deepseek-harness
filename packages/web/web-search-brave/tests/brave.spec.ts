import { afterEach, describe, expect, it, vi } from 'vitest'
import { Context } from '@deepseek-ai/cordis'
import { WebRuntime } from '@deepseek-ai/dsh-web'
import * as bravePlugin from '../src/index.ts'
import {
  BRAVE_DEFAULT_BASE_URL,
  BRAVE_PROVIDER_ID,
  BraveSearchProvider,
  mapBraveResult,
  mapBraveResponse,
} from '../src/provider.ts'

const options = { apiKey: 'brave-key', baseURL: 'https://api.brave.test' }

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'content-type': 'application/json' }, ...init })
}

afterEach(() => { vi.unstubAllGlobals() })

describe('BraveSearchProvider mapping', () => {
  it('maps url, title, description and page_age into the normalized source', () => {
    expect(mapBraveResult({
      title: 'Brave', url: 'https://brave.com', description: 'A search engine', page_age: '2026-08-01T00:00:00Z',
    })).toEqual({
      url: 'https://brave.com',
      title: 'Brave',
      snippet: 'A search engine',
      publishedAt: '2026-08-01T00:00:00Z',
    })
  })

  it('omits null or empty optionals and drops entries without a url or snippet', () => {
    expect(mapBraveResult({ url: 'https://a.test' })).toBeUndefined()
    expect(mapBraveResult({ url: 'https://a.test', description: '   ' })).toBeUndefined()
    expect(mapBraveResult({ description: 'no url' })).toBeUndefined()
    expect(mapBraveResult({ url: '', description: 'empty url' })).toBeUndefined()
    expect(mapBraveResult({ url: 'https://a.test', description: 'd', title: null, page_age: null }))
      .toEqual({ url: 'https://a.test', snippet: 'd' })
  })

  it('maps the response envelope, dropping snippet-less entries, with no content', () => {
    expect(mapBraveResponse({
      type: 'search',
      query: { original: 'q' },
      web: { results: [
        { title: 'One', url: 'https://a.test', description: 'first' },
        { url: 'https://b.test' },
        { title: 'Three', url: 'https://c.test', description: 'third', page_age: '2026-01-02T03:04:05Z' },
      ] },
    })).toEqual({
      sources: [
        { url: 'https://a.test', title: 'One', snippet: 'first' },
        { url: 'https://c.test', title: 'Three', snippet: 'third', publishedAt: '2026-01-02T03:04:05Z' },
      ],
      truncated: false,
    })
  })

  it('tolerates a body with no web results', () => {
    expect(mapBraveResponse({})).toEqual({ sources: [], truncated: false })
  })
})

describe('BraveSearchProvider availability', () => {
  it('requires a key, a parseable base URL and a whole count of at least 1', () => {
    expect(new BraveSearchProvider({ ...options, apiKey: '' }).available()).toBe(false)
    expect(new BraveSearchProvider({ ...options, baseURL: 'not a url' }).available()).toBe(false)
    expect(new BraveSearchProvider({ ...options, count: 0 }).available()).toBe(false)
    expect(new BraveSearchProvider({ ...options, count: 1.5 }).available()).toBe(false)
    expect(new BraveSearchProvider({ ...options, count: 1 }).available()).toBe(true)
    expect(new BraveSearchProvider(options).available()).toBe(true)
  })
})

describe('BraveSearchProvider request mapping', () => {
  it('sends q and count as query params with the x-subscription-token header', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ web: { results: [] } }))
    vi.stubGlobal('fetch', fetchMock)

    const provider = new BraveSearchProvider({ ...options, count: 3 })
    await provider.search({ query: 'hello', maxResults: 5 })

    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('https://api.brave.test/res/v1/web/search?q=hello&count=5')
    expect(init).toMatchObject({ method: 'GET', redirect: 'error' })
    expect((init.headers as Record<string, string>)['x-subscription-token']).toBe('brave-key')
    expect((init.headers as Record<string, string>)['accept']).toBe('application/json')
  })

  it('falls back to the configured count when a request omits maxResults', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ web: { results: [] } }))
    vi.stubGlobal('fetch', fetchMock)
    await new BraveSearchProvider({ ...options, count: 7 }).search({ query: 'q' })
    const [url] = fetchMock.mock.calls[0] as unknown as [string]
    expect(url).toBe('https://api.brave.test/res/v1/web/search?q=q&count=7')
  })

  it('lets a request maxResults win over the configured count', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ web: { results: [] } }))
    vi.stubGlobal('fetch', fetchMock)
    await new BraveSearchProvider({ ...options, count: 7 }).search({ query: 'q', maxResults: 2 })
    const [url] = fetchMock.mock.calls[0] as unknown as [string]
    expect(url).toBe('https://api.brave.test/res/v1/web/search?q=q&count=2')
  })

  it('clamps the outgoing count to Brave\'s documented maximum of 20', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ web: { results: [] } }))
    vi.stubGlobal('fetch', fetchMock)
    await new BraveSearchProvider(options).search({ query: 'q', maxResults: 50 })
    const [url] = fetchMock.mock.calls[0] as unknown as [string]
    expect(url).toBe('https://api.brave.test/res/v1/web/search?q=q&count=20')
  })

  it('omits count when neither maxResults nor a configured default is set', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ web: { results: [] } }))
    vi.stubGlobal('fetch', fetchMock)
    await new BraveSearchProvider(options).search({ query: 'q' })
    const [url] = fetchMock.mock.calls[0] as unknown as [string]
    expect(url).toBe('https://api.brave.test/res/v1/web/search?q=q')
  })

  it('forwards the abort signal', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ web: { results: [] } }))
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()
    await new BraveSearchProvider(options).search({ query: 'q' }, controller.signal)
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(init.signal).toBe(controller.signal)
  })
})

describe('BraveSearchProvider error handling', () => {
  it('maps an HTTP error to WEB_PROVIDER_ERROR with the envelope detail', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({
      type: 'ErrorResponse',
      error: { code: 'SUBSCRIPTION_TOKEN_INVALID', detail: 'The provided subscription token is invalid.', status: 422 },
    }, { status: 422 })))
    await expect(new BraveSearchProvider(options).search({ query: 'q' }))
      .rejects.toThrow(expect.objectContaining({ code: 'WEB_PROVIDER_ERROR', message: 'The provided subscription token is invalid.' }))
  })

  it('keeps a status-line message when the error body is not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('gateway down', { status: 502 })))
    await expect(new BraveSearchProvider(options).search({ query: 'q' }))
      .rejects.toThrow(expect.objectContaining({ code: 'WEB_PROVIDER_ERROR', message: 'Brave API error (HTTP 502)' }))
  })

  it('keeps the status-line message when the error envelope carries no detail', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ type: 'ErrorResponse', error: { code: 'INTERNAL' } }, { status: 500 })))
    await expect(new BraveSearchProvider(options).search({ query: 'q' }))
      .rejects.toThrow(expect.objectContaining({ code: 'WEB_PROVIDER_ERROR', message: 'Brave API error (HTTP 500)' }))
  })

  it('maps a network failure to WEB_PROVIDER_ERROR', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('connection refused'))))
    await expect(new BraveSearchProvider(options).search({ query: 'q' }))
      .rejects.toThrow(expect.objectContaining({ code: 'WEB_PROVIDER_ERROR' }))
  })

  it('maps an abort to WEB_ABORTED', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new DOMException('aborted', 'AbortError'))))
    await expect(new BraveSearchProvider(options).search({ query: 'q' }))
      .rejects.toThrow(expect.objectContaining({ code: 'WEB_ABORTED' }))
  })

  it('maps an unparseable success body to WEB_PROVIDER_ERROR', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('not json', { status: 200 })))
    await expect(new BraveSearchProvider(options).search({ query: 'q' }))
      .rejects.toThrow(expect.objectContaining({ code: 'WEB_PROVIDER_ERROR' }))
  })

  it('maps a well-formed body of the wrong shape to WEB_PROVIDER_ERROR, not a raw TypeError', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ web: { results: {} } }, { status: 200 })))
    await expect(new BraveSearchProvider(options).search({ query: 'q' }))
      .rejects.toThrow(expect.objectContaining({ code: 'WEB_PROVIDER_ERROR' }))
  })

  it('surfaces an abort during success-body parse as WEB_ABORTED, not provider error', async () => {
    const body = { json: () => Promise.reject(new DOMException('aborted', 'AbortError')), ok: true, status: 200 }
    vi.stubGlobal('fetch', vi.fn(async () => body as unknown as Response))
    await expect(new BraveSearchProvider(options).search({ query: 'q' }))
      .rejects.toThrow(expect.objectContaining({ code: 'WEB_ABORTED' }))
  })

  it('surfaces an abort during error-body parse as WEB_ABORTED', async () => {
    const body = { json: () => Promise.reject(new DOMException('aborted', 'AbortError')), ok: false, status: 500 }
    vi.stubGlobal('fetch', vi.fn(async () => body as unknown as Response))
    await expect(new BraveSearchProvider(options).search({ query: 'q' }))
      .rejects.toThrow(expect.objectContaining({ code: 'WEB_ABORTED' }))
  })
})

describe('web-search-brave plugin registration', () => {
  it('registers the provider into ctx.web (HMR-safe)', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ web: { results: [] } })))
    const ctx = new Context()
    await ctx.plugin(WebRuntime, { searchProvider: BRAVE_PROVIDER_ID })
    const fiber = await ctx.plugin(bravePlugin, { apiKey: 'brave-key' })
    await expect(ctx.web.search({ query: 'q' })).resolves.toMatchObject({ sources: [], truncated: false })
    await fiber.dispose()
    await expect(ctx.web.search({ query: 'q' }))
      .rejects.toThrow(expect.objectContaining({ code: 'WEB_PROVIDER_CONFIGURED_MISSING' }))
  })

  it('has no default export (namespace plugin export shape)', () => {
    expect('default' in bravePlugin).toBe(false)
  })

  it('threads baseURL and count config into the request', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ web: { results: [] } }))
    vi.stubGlobal('fetch', fetchMock)
    const ctx = new Context()
    await ctx.plugin(WebRuntime, { searchProvider: BRAVE_PROVIDER_ID })
    const fiber = await ctx.plugin(bravePlugin, { apiKey: 'brave-key', baseURL: 'https://api.brave.example', count: 4 })
    await ctx.web.search({ query: 'q' })
    const [url] = fetchMock.mock.calls[0] as unknown as [string]
    expect(url).toBe('https://api.brave.example/res/v1/web/search?q=q&count=4')
    await fiber.dispose()
  })

  it('falls back to $BRAVE_API_KEY and the default base URL when config omits them', async () => {
    const prev = process.env.BRAVE_API_KEY
    process.env.BRAVE_API_KEY = 'env-key'
    try {
      const fetchMock = vi.fn(async () => jsonResponse({ web: { results: [] } }))
      vi.stubGlobal('fetch', fetchMock)
      const ctx = new Context()
      await ctx.plugin(WebRuntime, { searchProvider: BRAVE_PROVIDER_ID })
      const fiber = await ctx.plugin(bravePlugin, {})
      await ctx.web.search({ query: 'q' })
      const [url] = fetchMock.mock.calls[0] as unknown as [string]
      expect(url).toBe(`${BRAVE_DEFAULT_BASE_URL}/res/v1/web/search?q=q`)
      await fiber.dispose()
    } finally {
      if (prev === undefined) delete process.env.BRAVE_API_KEY
      else process.env.BRAVE_API_KEY = prev
    }
  })

  it('is unavailable when neither config nor env supplies a key', async () => {
    const prev = process.env.BRAVE_API_KEY
    delete process.env.BRAVE_API_KEY
    try {
      const ctx = new Context()
      await ctx.plugin(WebRuntime, { searchProvider: BRAVE_PROVIDER_ID })
      await ctx.plugin(bravePlugin, {})
      await expect(ctx.web.search({ query: 'q' }))
        .rejects.toThrow(expect.objectContaining({ code: 'WEB_PROVIDER_CONFIGURED_UNAVAILABLE' }))
    } finally {
      if (prev !== undefined) process.env.BRAVE_API_KEY = prev
    }
  })
})
