/**
 * The launcher icons are opaque because Android composes the PWA icon over
 * its own background and masks it into the launcher shape. These cases pin
 * the mark geometry (control-point box, the any/maskable scales, the centered
 * layout) and the freshness gate: a committed PNG that no longer matches the
 * favicon mark fails here — run `pnpm run gen-web-icons` and re-commit.
 */

import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import sharp from 'sharp'
import { describe, expect, it } from 'vitest'
import {
  anyScale,
  faviconMarkData,
  generateWebIcons,
  iconSvg,
  maskableScale,
  pathBox,
} from './gen-web-icons.ts'

const ICONS = join(import.meta.dirname, '..', 'apps', 'web', 'public', 'icons')
const NAMES = ['icon-192.png', 'icon-512.png', 'icon-maskable-512.png'] as const

describe('launcher icon geometry', () => {
  it('boxes a path by its control points', () => {
    expect(pathBox('M0 0 L10 5 4 20 Z')).toEqual({ x: 0, y: 0, width: 10, height: 20 })
    // A curve over-encloses: the box spans its control points, not its ink.
    expect(pathBox('M0 0 C10 -5 20 25 0 10 Z')).toEqual({ x: 0, y: -5, width: 20, height: 30 })
  })

  it('rejects path data without paired coordinates', () => {
    expect(() => pathBox('M0')).toThrow(/unpaired coordinates/)
  })

  it('fits the any mark inside its span, width- or height-constrained', () => {
    // 50-unit canvas at 88% span: the wide mark is width-constrained…
    expect(anyScale({ x: 0, y: 0, width: 50, height: 10 })).toBeCloseTo(0.88)
    // …and the tall mark height-constrained.
    expect(anyScale({ x: 0, y: 0, width: 10, height: 50 })).toBeCloseTo(0.88)
  })

  it('keeps the maskable mark inside the 80% safe zone', () => {
    // 20×20 box: half-diagonal 10√2, safe radius 20 → scale 20/(10√2) = √2.
    expect(maskableScale({ x: 0, y: 0, width: 20, height: 20 })).toBeCloseTo(1.41421)
    // A box already at the safe diameter keeps its far corner on the circle.
    expect(maskableScale({ x: 0, y: 0, width: 40, height: 40 })).toBeCloseTo(0.70711)
  })

  it('lays the mark out centered on the opaque field', () => {
    const box = { x: 2, y: 5, width: 10, height: 6 }
    const svg = iconSvg('M2 5', box, 2, 512)
    // translate = (canvas − span)/2 − boxOrigin·scale: (50 − 20)/2 − 4 = 11,
    // (50 − 12)/2 − 10 = 9 — the scaled box spans 15..35 × 19..31, centered.
    expect(svg).toContain('width="512" height="512" viewBox="0 0 50 50"')
    expect(svg).toContain('translate(11 9) scale(2)')
    expect(svg).toContain('<rect width="50" height="50" fill="#0F1115"/>')
    expect(svg).toContain('fill="#FFFFFF" d="M2 5"')
  })

  it('reads the mark from the favicon and rejects a favicon without one', () => {
    expect(faviconMarkData('<svg><path id="path" d="M1 2Z"/></svg>')).toBe('M1 2Z')
    expect(() => faviconMarkData('<svg><rect/></svg>')).toThrow(/carries no <path/)
  })
})

it('reproduces the committed launcher icons pixel-for-pixel from the favicon mark', async () => {
  const out = await mkdtemp(join(tmpdir(), 'dsh-web-icons-'))
  try {
    await generateWebIcons(out)
    for (const name of NAMES) {
      const committed = sharp(await readFile(join(ICONS, name)))
      const generated = sharp(await readFile(join(out, name)))
      const [a, b] = await Promise.all([committed.raw().toBuffer(), generated.raw().toBuffer()])
      expect(a.equals(b), `${name} drifted from the favicon mark; run pnpm run gen-web-icons`).toBe(true)
      expect((await generated.metadata()).width).toBe(name === 'icon-192.png' ? 192 : 512)
    }
  } finally {
    await rm(out, { recursive: true, force: true })
  }
})
