// @vitest-environment jsdom
/** The theme bootstrap injection row and the resulting pre-plugin browser theme. */
import { runInNewContext } from 'node:vm'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { bootThemeInjection } from '../src/boot-theme.ts'
import type { ThemePreference } from '../src/theme-settings.ts'

const DARK_ATTRIBUTE = 'data-ds-dark-theme'
const LIGHT_THEME_COLOR = '#fff'
const DARK_THEME_COLOR = '#151517'

function mockSystemDark(matches: boolean): void {
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches }) as MediaQueryList))
}

function executeBootstrap(preference?: ThemePreference): void {
  const row = bootThemeInjection(preference)
  if (row.kind !== 'script') throw new Error('theme bootstrap row is not a script')
  runInNewContext(row.text, { document, matchMedia: globalThis.matchMedia })
}

function themeColorMeta(): HTMLMetaElement | null {
  return document.head.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
}

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  document.documentElement.style.removeProperty('color-scheme')
  document.body.removeAttribute(DARK_ATTRIBUTE)
  document.head.querySelectorAll('meta[name="theme-color"]').forEach((node) => { node.remove() })
})

describe('theme bootstrap row', () => {
  it('is a body script row, so it runs before the shell mount', () => {
    mockSystemDark(false)
    const row = bootThemeInjection('dark')
    expect(row).toMatchObject({ kind: 'script', placement: 'body' })
    executeBootstrap('dark')
    expect(document.documentElement.style.colorScheme).toBe('dark')
    expect(document.body.hasAttribute(DARK_ATTRIBUTE)).toBe(true)
  })

  it('lets durable light override a dark OS and clears stale dark state', () => {
    document.body.setAttribute(DARK_ATTRIBUTE, '')
    mockSystemDark(true)
    executeBootstrap('light')
    expect(document.documentElement.style.colorScheme).toBe('light')
    expect(document.body.hasAttribute(DARK_ATTRIBUTE)).toBe(false)
  })

  it.each([
    [true, 'dark', true, DARK_THEME_COLOR],
    [false, 'light', false, LIGHT_THEME_COLOR],
  ] as const)('resolves system=%s to %s with the matching theme color', (matches, colorScheme, dark, themeColor) => {
    mockSystemDark(matches)
    executeBootstrap('system')
    expect(document.documentElement.style.colorScheme).toBe(colorScheme)
    expect(document.body.hasAttribute(DARK_ATTRIBUTE)).toBe(dark)
    expect(themeColorMeta()?.content).toBe(themeColor)
  })

  it('writes the resolved base background to the theme-color meta before the shell renders', () => {
    mockSystemDark(false)
    executeBootstrap('dark')
    expect(themeColorMeta()?.content).toBe(DARK_THEME_COLOR)
    document.head.querySelectorAll('meta[name="theme-color"]').forEach((node) => { node.remove() })
    executeBootstrap('light')
    expect(themeColorMeta()?.content).toBe(LIGHT_THEME_COLOR)
  })

  it('updates an existing theme-color meta in place instead of minting a second node', () => {
    const staticMeta = document.createElement('meta')
    staticMeta.name = 'theme-color'
    staticMeta.content = LIGHT_THEME_COLOR
    document.head.append(staticMeta)
    mockSystemDark(true)
    executeBootstrap('dark')
    expect(themeColorMeta()).toBe(staticMeta)
    expect(staticMeta.content).toBe(DARK_THEME_COLOR)
    expect(document.head.querySelectorAll('meta[name="theme-color"]')).toHaveLength(1)
  })

  it('defaults to system and falls back to light when matchMedia is unavailable', () => {
    vi.stubGlobal('matchMedia', undefined)
    executeBootstrap()
    expect(document.documentElement.style.colorScheme).toBe('light')
    expect(document.body.hasAttribute(DARK_ATTRIBUTE)).toBe(false)
    expect(themeColorMeta()?.content).toBe(LIGHT_THEME_COLOR)
  })
})
