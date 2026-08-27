/**
 * Theme bootstrap row for the browser's pre-plugin interval. Each index
 * render embeds the current durable built-in preference; the browser resolves
 * only `system`, then writes the same DOM fields ui-layout's ThemePresenter
 * owns after the client plugin tree activates: root `color-scheme`, the body
 * palette attribute, and the `theme-color` meta. Android standalone PWAs fix
 * the navigation bar color at the initial page commit and do not follow later
 * meta updates, so the resolved base background (the design-platform neutral
 * bluish 00 / 950 values, also hardcoded by the boot page as its palette
 * fallbacks) must reach the meta before first paint.
 */

import type { IndexInjection } from '@deepseek-ai/dsh-host-webserver'
import { DEFAULT_PREFERENCE, type ThemePreference } from './theme-settings.ts'

/** Build the inline script body for one schema-validated built-in preference. */
function bootThemeScript(preference: ThemePreference): string {
  return `(() => {
  const preference = ${JSON.stringify(preference)}
  const systemDark = preference === 'system'
    && typeof matchMedia !== 'undefined'
    && matchMedia('(prefers-color-scheme: dark)').matches
  const dark = preference === 'dark' || systemDark
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light'
  document.body.toggleAttribute('data-ds-dark-theme', dark)
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) {
    meta.setAttribute('content', dark ? '#151517' : '#fff')
  } else {
    const bootMeta = document.createElement('meta')
    bootMeta.name = 'theme-color'
    bootMeta.content = dark ? '#151517' : '#fff'
    document.head.append(bootMeta)
  }
})()`
}

/**
 * The theme bootstrap as an injection row: an inline script immediately after
 * the opening body tag, before the shell mount and module script.
 * @param preference - Current Host-backed built-in preference.
 * @returns the body script row.
 */
export function bootThemeInjection(
  preference: ThemePreference = DEFAULT_PREFERENCE,
): IndexInjection {
  return { kind: 'script', placement: 'body', text: bootThemeScript(preference) }
}
