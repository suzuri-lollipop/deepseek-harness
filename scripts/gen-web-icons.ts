/**
 * Regenerates the PWA launcher icons committed under
 * `apps/web/public/icons/` from the whale mark in
 * `apps/web/public/favicon.svg`.
 *
 * The launcher icons are opaque by design. Android composes an installed
 * PWA's launcher icon over its own background and masks it into the launcher
 * shape, so the theme-aware transparent favicon — white under a dark color
 * scheme — resolves to a white mark on a white field. Each PNG is a solid
 * brand-ink field with the mark knocked out in white instead; the `maskable`
 * variant keeps the mark inside the 80% safe zone so launcher masking never
 * clips it.
 *
 * Run `pnpm run gen-web-icons` after the favicon mark changes;
 * `gen-web-icons.spec.ts` fails when a committed icon drifts from the mark.
 */

import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import sharp from 'sharp'

const root = resolve(import.meta.dirname, '..')
const FAVICON = join(root, 'apps/web/public/favicon.svg')
const ICONS = join(root, 'apps/web/public/icons')

/** Opaque field: brand ink `--dsw-static-neutral-bluish-1000`, rgb(15, 17, 21). */
const FIELD = '#0F1115'
/** Mark knockout color. */
const MARK = '#FFFFFF'
/** Favicon viewBox edge in native units. */
const CANVAS = 50
/** Raster edges of the launcher icons in pixels. */
const LARGE_EDGE = 512
const SMALL_EDGE = 192
/** Share of the canvas edge the `any` mark may span. */
const ANY_SPAN = 0.88
/** Maskable safe zone: the centered circle 80% of the edge across. */
const SAFE_RADIUS = CANVAS * 0.4

/** Axis-aligned bounding box in path coordinates. */
export interface PathBox {
  /** Left edge of the box. */
  readonly x: number
  /** Top edge of the box. */
  readonly y: number
  /** Box width. */
  readonly width: number
  /** Box height. */
  readonly height: number
}

/**
 * @param svg - favicon.svg source text
 * @returns the whale mark's path data
 */
export function faviconMarkData(svg: string): string {
  const data = /<path[^>]*\sd="([^"]+)"/.exec(svg)?.[1]
  if (data === undefined) throw new Error('favicon.svg carries no <path d="…">')
  return data
}

/**
 * Control-point bounding box of a path's coordinate pairs. A curve stays
 * inside the convex hull of its control points, so the box can over-enclose
 * the ink; the surplus only costs margin.
 * @param data - SVG path data
 * @returns the box enclosing every control point
 */
export function pathBox(data: string): PathBox {
  const numbers = data.match(/-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g)
  if (numbers === null || numbers.length === 0 || numbers.length % 2 !== 0) {
    throw new Error(`unpaired coordinates in path data: ${data.slice(0, 32)}…`)
  }
  let x = Infinity
  let y = Infinity
  let right = -Infinity
  let bottom = -Infinity
  for (let i = 0; i < numbers.length; i += 2) {
    const px = Number(numbers[i])
    const py = Number(numbers[i + 1])
    if (px < x) x = px
    if (py < y) y = py
    if (px > right) right = px
    if (py > bottom) bottom = py
  }
  return { x, y, width: right - x, height: bottom - y }
}

/**
 * @param box - the mark's bounding box
 * @returns the uniform scale that fits the mark inside `ANY_SPAN` of the canvas
 */
export function anyScale(box: PathBox): number {
  return Math.min(ANY_SPAN * CANVAS / box.width, ANY_SPAN * CANVAS / box.height)
}

/**
 * @param box - the mark's bounding box
 * @returns the uniform scale that keeps the whole box inside the maskable safe
 *   zone, i.e. every point of the box within `SAFE_RADIUS` of the canvas center
 */
export function maskableScale(box: PathBox): number {
  return SAFE_RADIUS / Math.hypot(box.width / 2, box.height / 2)
}

/**
 * Six-decimal number for SVG attributes: exact at 512px raster and keeps the
 * document deterministic and short.
 * @param value - a finite number
 * @returns the decimal text
 */
function fixed(value: number): string {
  return (Math.round(value * 1e6) / 1e6).toString()
}

/**
 * @param data - the mark's path data
 * @param box - the mark's bounding box
 * @param scale - uniform scale in canvas units
 * @param edge - raster edge in pixels
 * @returns the opaque icon document: full-bleed field plus the centered white mark
 */
export function iconSvg(data: string, box: PathBox, scale: number, edge: number): string {
  const tx = (CANVAS - box.width * scale) / 2 - box.x * scale
  const ty = (CANVAS - box.height * scale) / 2 - box.y * scale
  return [
    `<svg xmlns="http://www.w3.org/2000/svg" width="${edge}" height="${edge}" viewBox="0 0 ${CANVAS} ${CANVAS}">`,
    `  <rect width="${CANVAS}" height="${CANVAS}" fill="${FIELD}"/>`,
    `  <path transform="translate(${fixed(tx)} ${fixed(ty)}) scale(${fixed(scale)})" fill="${MARK}" d="${data}"/>`,
    '</svg>',
    '',
  ].join('\n')
}

/**
 * Render the three launcher icons from the committed favicon mark.
 * @param outDir - destination directory, created when missing
 * @returns nothing; writes icon-192.png, icon-512.png, icon-maskable-512.png
 */
export async function generateWebIcons(outDir: string): Promise<void> {
  const data = faviconMarkData(await readFile(FAVICON, 'utf8'))
  const box = pathBox(data)
  await mkdir(outDir, { recursive: true })
  const large = await sharp(Buffer.from(iconSvg(data, box, anyScale(box), LARGE_EDGE))).png().toBuffer()
  const small = await sharp(large).resize(SMALL_EDGE, SMALL_EDGE).png().toBuffer()
  const maskable = await sharp(Buffer.from(iconSvg(data, box, maskableScale(box), LARGE_EDGE))).png().toBuffer()
  await Promise.all([
    writeFile(join(outDir, 'icon-192.png'), small),
    writeFile(join(outDir, 'icon-512.png'), large),
    writeFile(join(outDir, 'icon-maskable-512.png'), maskable),
  ])
}

/**
 * CLI entry: regenerate the committed launcher icons.
 * @returns nothing
 */
export async function main(): Promise<void> {
  await generateWebIcons(ICONS)
  console.log('gen-web-icons: wrote icon-192.png, icon-512.png, icon-maskable-512.png under apps/web/public/icons/')
}

// Run only when invoked as a script, not when imported by a test.
if (process.argv[1] && import.meta.filename === resolve(process.argv[1])) {
  void main()
}
