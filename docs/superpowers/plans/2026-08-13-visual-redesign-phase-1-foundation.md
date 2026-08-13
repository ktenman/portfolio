# Visual Redesign — Phase 1: Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the app's colour and type foundation with the verified "Statement" OKLCH palette and its two typefaces, behind a contrast gate that is written first and fails before the palette lands.

**Architecture:** Two automated gates go in before any visual change — a Vitest gate that parses `ui/styles/theme.css` and proves every declared token pair meets its WCAG threshold, and a Playwright gate that measures contrast on rendered routes. Both fail against the current palette; that failure is the phase's evidence. The palette then lands as a **retone in place**: every existing token name keeps its name and gains a new value, so all 400+ existing consumers inherit the new world without being edited. Renaming and deletion belong to Phase 2.

**Tech Stack:** Tailwind CSS v4 (`@theme static`), Vite 8, Vitest, Playwright, Fontsource.

## Source of truth

Design spec: `docs/superpowers/specs/2026-08-13-portfolio-visual-redesign-design.md`. Where this plan and the spec disagree, the spec wins — except for the three deviations recorded in "Deviations from the spec" below, which were decided while writing this plan.

## Global Constraints

Every task's requirements implicitly include all of these.

**Palette rules (spec §1.2), enforced by the Task 1 gate:**

- `gain` and `loss` are locked to `L = 0.520`. Neither may be re-toned independently.
- `brass` is the only brand accent: nav active state, focus ring, primary action, active filter chip. Nothing else.
- `gain` and `loss` are reserved for signed monetary movement. They never decorate.
- `ink-faint` is not body text. Legitimate uses are chart axis ticks, disabled control glyphs, and decorative rules. Every piece of small text — labels, captions, the build hash, stat-card labels, table meta — uses `ink-soft`.
- State layers use `color-mix()` against the base token, never a second hardcoded colour.
- No component declares a colour.

**Project rules (AGENTS.md), non-negotiable:**

- **No code comments of any kind.** Not `//`, not `/* */`, not `/** */`. The only exception is TypeScript triple-slash directives.
- Commit subjects: uppercase imperative verb, max 50 characters, **no prefixes** — `feat:`, `fix:`, `chore:` are forbidden. `Add contrast gate for theme tokens` is correct; `feat: add contrast gate` is not. Ignore the `feat:` example in the writing-plans skill template; this project's convention overrides it.
- **Never** add "Generated with Claude Code", "Co-Authored-By: Claude", or any AI attribution to a commit message.
- **Never** hand-edit `ui/models/generated/domain-models.ts`.
- File names are kebab-case. Never add `-improved`, `-new`, `-refactored` suffixes; edit in place.
- Files: ideal 100–200 lines, refactor above 300.
- TypeScript strict mode, `const` unless reassignment is required, no `any`.

**After every UI change, both must pass:**

```bash
npm run lint-format
npm test -- --run
```

Known local-only noise: `npm run lint-format` runs `knip`, which scans untracked worktrees under `.claude/` and can exit 1 on files CI never sees. A knip failure that names only paths outside `ui/` is not this plan's regression — confirm the failing paths before chasing it.

## File Structure

| File                               | Status     | Responsibility                                                                                                                                                                |
| ---------------------------------- | ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ui/tests/contrast.ts`             | Create     | Pure colour math: OKLCH→linear RGB, luminance from OKLCH or from any computed colour string, alpha compositing, contrast ratio, sRGB gamut check. No test assertions, no DOM. |
| `ui/styles/theme-contrast.test.ts` | Create     | Vitest gate. Parses `theme.css`, asserts every declared token pair meets its threshold and no token clips sRGB.                                                               |
| `ui/tests/visual/palette.spec.ts`  | Rewrite    | Playwright gate. Measures rendered text contrast and the focus ring on stubbed routes.                                                                                        |
| `ui/styles/theme.css`              | Modify     | The `@theme static` block — the single palette. Gains Statement tokens; every legacy token is retoned in place.                                                               |
| `ui/styles/base.css`               | Modify     | Fluid type scale, display-font heading wiring.                                                                                                                                |
| `ui/styles/components.css:32,294`  | Modify     | Focus ring becomes solid `brass` instead of a 40%/20% wash.                                                                                                                   |
| `ui/main.ts`                       | Modify     | Fontsource CSS imports.                                                                                                                                                       |
| `ui/index.html`                    | Modify     | First-paint loader colours, favicon link.                                                                                                                                     |
| `ui/public/favicon.svg`            | Create     | Brass `P` in Georgia, no enclosing shape.                                                                                                                                     |
| `DESIGN.md`                        | Replace    | The visual world of record. Replaced wholesale, not edited (spec §4, Phase 1).                                                                                                |
| `docs/superpowers/baseline/*.png`  | Regenerate | All 41 baselines.                                                                                                                                                             |

## Deviations from the spec

Six decisions. The first four were made while writing this plan; each is a deliberate narrowing, not an omission. The fifth and sixth were made during execution.

1. **The gate grows per phase.** The spec describes Phase 0's gate as asserting "≥4.5:1 for text and ≥3:1 for borders, focus rings, and chart strokes". Phase 1 ships the text gate and the focus-ring gate. Control borders are excluded because `--color-control-border` is a form-control concern that Phase 2 restyles; asserting 3:1 on it now would fail a check nothing in this phase can satisfy. Decorative rules — table row dividers, card hairlines — are permanently excluded, because WCAG 1.4.11 covers boundaries that identify components and states, not visual structure. Chart strokes are canvas pixels and cannot be read from computed style; they are proven instead by the Task 1 token gate, which is deterministic and stronger.

2. **No hand-written font preload.** The spec says the faces are "preloaded". Vite emits fonts with hashed filenames, so a `<link rel="preload">` in `index.html` would need either a hardcoded hash or a new plugin dependency. This repo has already been burned once by a hand-written preload in this exact file (a `modulepreload` to `./main.ts` that Vite inlined as a `data:video/mp2t` URL). Fontsource sets `font-display: swap` and the CSS is in the critical bundle, so text paints immediately in the fallback and swaps. Upgrade path if first-paint measurement ever justifies it: a Vite plugin that reads the manifest and injects the preload with the real hash.

3. **Legacy tokens are retoned, not removed.** The spec says `theme.css` is "rewritten to the OKLCH ramp". Removing a token name does not raise an error in Tailwind v4 — the utility simply stops being generated and the style silently disappears. `--color-gray-600` alone has 41 usages; the gray ramp has 116. So Phase 1 keeps every legacy name and gives it a Statement value. Phase 2 removes names as it migrates their consumers.

4. **The chart palette is a Statement-toned categorical wheel, not Okabe-Ito and not a ramp.** Spec §1.1 says the Okabe-Ito palette "stays" and moves into `theme.css` as `--color-cat-1..10`. Mid-execution the human partner asked for the ETF charts to match the Statement colours, which overrides that line — but "match the Statement colours" was then read too literally, and three renders were needed to find the floor. A single-hue lightness ramp was tried sorted (read as one brown disc), interleaved so touching arcs sat seven steps apart (read as a rotating dark/light barber pole), and sorted again inside Task 7's gapped donut, where the human partner's verdict was final: _"if everything is brown on chart is hard to differentiate."_ The lightness-ramp premise is therefore **withdrawn entirely**. Sector, country and holding are categories, not ranks; lightness encodes order, and encoding order onto unordered data spends the one channel that could have separated them. Hue is the categorical channel. Task 6 ships eight hues at 45° spacing — `74 119 164 209 254 299 344 29`, starting at brass so the largest slice is the brand accent — each at two lightness levels: `oklch(0.55 0.09 h)` for the first eight slices and `oklch(0.75 0.07 h)` for the last eight. Sixteen entries, every one in sRGB gamut, the dark half at 4.4–4.9:1 against paper and the pale half at 2.07–2.20:1. This is not a rainbow: constant lightness and constant chroma within each half hold the set to one muted family, which is what "we cannot have tons of different colors" asks for — few _tones_, distinguishable _hues_. The 16-entry length also closes a live defect where `colors[index % colors.length]` against a 10-entry array gave two of 15 slices the same colour. Contrast is not the mechanism and never was: a 1px `hairline-strong` stroke reads 1.56:1 against the card and is a visual seam, not a WCAG 1.4.11 boundary. Colour is never the sole carrier because the legend states every label and percentage as full-contrast text and the tooltip names the hovered slice. `--color-cat-1..10` is never created.

5. **The rendered gate measures settled states.** It runs under `prefers-reduced-motion: reduce`, so it never samples a mid-animation or mid-transition frame. Those frames are real paint, but they are not states a reader sits with, and sampling them makes the gate non-deterministic — the same page fails a different element on each run depending on where the frame lands. Transitional colours are covered instead at the token level: every animation in this codebase interpolates between two tokens, and the Task 1 gate asserts both endpoints. Endpoints do not bound the middle in general — a background interpolating past the text's own luminance passes at both ends and touches 1.0 halfway — so each animation has to be checked on its own path rather than assumed safe. The two value flashes were: transparent to `gain-wash` over a white row runs 5.234 → 5.100 → 4.933 → 4.796 → 4.611, monotonic with its minimum at the endpoint the gate already asserts. Any later phase that adds an animation owes the same check.

6. **There is no display face. Headings are set in the body sans.** Spec §2 gives the redesign two faces and puts Instrument Serif on `h1`–`h6`. After Task 7 shipped, the human partner saw the rendered heading and asked for the old one back: _"maybe this font is a bit odd could you revert it back to old one."_ The serif is therefore removed — the `@fontsource/instrument-serif` dependency, its `main.ts` import, the `--font-display` token, and the `font-family` line in the `h1`–`h6` rule. Headings inherit `--font-sans` like the rest of the app. `font-weight` returns to 500 and `line-height` to 1.2, both pre-redesign values: 400 was chosen because the static serif ships one weight and 500 would have synthesised a fake bold, a constraint that dies with the face. What survives is the part the spec was actually buying — the fluid `clamp()` scale on `--text-display`/`--text-title`/`--text-heading` and the deletion of the per-breakpoint overrides. Instrument Sans stays as the body and heading face; this is a reversal of the display face only, not of Task 4. Delivered woff2 drops from ~50KB to ~29KB. `--font-display` no longer exists, so nothing may reference it.

---

### Task 1: Contrast math and the theme token gate

Written before any palette change. It fails on the current palette — that failure is the phase's evidence.

**Files:**

- Create: `ui/tests/contrast.ts`
- Create: `ui/styles/theme-contrast.test.ts`
- Modify: `vite.config.ts:23-28` (the `test` block — one line, see Step 2)

**Interfaces:**

- Consumes: nothing.
- Produces, from `ui/tests/contrast.ts`:
  - `interface Oklch { l: number; c: number; h: number }`
  - `isInSrgbGamut(color: Oklch): boolean`
  - `luminanceFromOklch(color: Oklch): number`
  - `luminanceFromRgbString(value: string): number`
  - `contrastRatio(a: number, b: number): number` — takes two **luminances**, not colours.
  - Task 2 imports `luminanceFromRgbString` and `contrastRatio` from this same file.

- [ ] **Step 1: Write the colour math**

Create `ui/tests/contrast.ts`:

```ts
export interface Oklch {
  l: number
  c: number
  h: number
}

function oklabToLinearRgb(l: number, a: number, b: number): [number, number, number] {
  const long = (l + 0.3963377774 * a + 0.2158037573 * b) ** 3
  const medium = (l - 0.1055613458 * a - 0.0638541728 * b) ** 3
  const short = (l - 0.0894841775 * a - 1.291485548 * b) ** 3
  return [
    4.0767416621 * long - 3.3077115913 * medium + 0.2309699292 * short,
    -1.2684380046 * long + 2.6097574011 * medium - 0.3413193965 * short,
    -0.0041960863 * long - 0.7034186147 * medium + 1.707614701 * short,
  ]
}

function oklchToLinearRgb({ l, c, h }: Oklch): [number, number, number] {
  const radians = (h * Math.PI) / 180
  return oklabToLinearRgb(l, c * Math.cos(radians), c * Math.sin(radians))
}

const GAMUT_TOLERANCE = 0.002

export function isInSrgbGamut(color: Oklch): boolean {
  return oklchToLinearRgb(color).every(
    channel => channel >= -GAMUT_TOLERANCE && channel <= 1 + GAMUT_TOLERANCE
  )
}

const clampUnit = (value: number): number => Math.min(1, Math.max(0, value))

const luminanceOf = ([red, green, blue]: [number, number, number]): number =>
  0.2126 * clampUnit(red) + 0.7152 * clampUnit(green) + 0.0722 * clampUnit(blue)

export function luminanceFromOklch(color: Oklch): number {
  return luminanceOf(oklchToLinearRgb(color))
}

const decodeSrgb = (channel: number): number =>
  channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4

const encodeSrgb = (channel: number): number =>
  channel <= 0.0031308 ? 12.92 * channel : 1.055 * channel ** (1 / 2.4) - 0.055

const toByte = (channel: number): number => Math.round(clampUnit(channel) * 255)

const numbersIn = (value: string): number[] => (value.match(/-?[\d.]+/g) ?? []).map(Number)

interface Rgba {
  red: number
  green: number
  blue: number
  alpha: number
}

const linearToRgba = ([red, green, blue]: [number, number, number], alpha: number): Rgba => ({
  red: toByte(encodeSrgb(red)),
  green: toByte(encodeSrgb(green)),
  blue: toByte(encodeSrgb(blue)),
  alpha,
})

function parseColor(value: string): Rgba {
  const channels = numbersIn(value)
  if (channels.length < 3 || channels.length > 4) {
    throw new Error(`Cannot read a colour from "${value}"`)
  }
  const [first, second, third, alpha = 1] = channels
  if (value.startsWith('oklch(')) {
    return linearToRgba(oklchToLinearRgb({ l: first, c: second, h: third }), alpha)
  }
  if (value.startsWith('oklab(')) {
    return linearToRgba(oklabToLinearRgb(first, second, third), alpha)
  }
  if (value.startsWith('color(srgb ')) {
    return { red: toByte(first), green: toByte(second), blue: toByte(third), alpha }
  }
  if (!value.startsWith('rgb')) {
    throw new Error(`Cannot read a colour from "${value}"`)
  }
  return { red: first, green: second, blue: third, alpha }
}

export function flattenLayers(layers: readonly string[]): string {
  const { red, green, blue } = layers.reduceRight<Rgba>(
    (below, layer) => {
      const top = parseColor(layer)
      return {
        red: top.red * top.alpha + below.red * (1 - top.alpha),
        green: top.green * top.alpha + below.green * (1 - top.alpha),
        blue: top.blue * top.alpha + below.blue * (1 - top.alpha),
        alpha: 1,
      }
    },
    { red: 255, green: 255, blue: 255, alpha: 1 }
  )
  return `rgb(${Math.round(red)}, ${Math.round(green)}, ${Math.round(blue)})`
}

export function luminanceFromRgbString(value: string): number {
  const { red, green, blue, alpha } = parseColor(value)
  if (alpha !== 1) {
    throw new Error(`Cannot measure the translucent colour "${value}"; flatten it first`)
  }
  return luminanceOf([decodeSrgb(red / 255), decodeSrgb(green / 255), decodeSrgb(blue / 255)])
}

export function contrastRatio(a: number, b: number): number {
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
}
```

WCAG contrast is defined on **composited** colours, so a translucent colour must be flattened onto everything beneath it before it is measured. `flattenLayers` takes the layers topmost-first, composites them source-over onto an opaque white backdrop, and returns an opaque `rgb()` string. `luminanceFromRgbString` refuses anything still carrying alpha: the two functions are complementary, and the refusal is what stops a caller silently measuring `rgba(0, 0, 0, 0.07)` as if it were black.

`parseColor` reads all four forms `getComputedStyle` actually returns, because CSS Color 4 resolved-value rules keep a colour in its own space rather than downgrading everything to `rgb()`:

| Declared as                                                      | Serialized by Chromium as                      | Channel range |
| ---------------------------------------------------------------- | ---------------------------------------------- | ------------- |
| hex, `rgb()`, `hsl()`, a named colour                            | `rgb(36, 30, 26)` / `rgba(36, 30, 26, 0.05)`   | 0–255         |
| `oklch(...)`                                                     | `oklch(0.24 0.012 60)` — verbatim              | OKLCH         |
| `color-mix(in srgb, <oklch> 40%, transparent)`                   | `color(srgb 0.553723 0.384927 0.122754 / 0.4)` | 0–1           |
| `color-mix(in oklab, ...)`, and any colour caught mid-transition | `oklab(0.53 0.027 0.094 / 0.4)`                | OKLAB         |

Task 3 moves every token to `oklch()`, so the last three forms are what the Task 2 gate will read from a retoned page — a parser that only accepts `rgb` throws before any contrast maths runs. The `color(srgb ` prefix is matched with its trailing space: `srgb` carries no digits of its own, but `color(display-p3 ...)` would otherwise contribute a stray `3` to the channel list. `oklab()` needs no separate matrix — `oklchToLinearRgb` is polar `oklab`, so both share `oklabToLinearRgb`, and `numbersIn`'s leading `-?` is what admits oklab's signed `a` and `b`. The oklab form is not hypothetical: CSS Color 4 interpolates colours in oklab by default, so every in-flight `transition: color` frame serializes this way. Everything is decoded through the same pipeline the token gate uses, then gamma-encoded to bytes, so both gates measure one colour pipeline rather than two.

The channel-count guard is hoisted above all four branches, and that placement is the point. A branch that prefix-matches a form it cannot fully read — `oklch(none 0.098 74)`, a serialization a future Chromium adds, anything with a missing channel — would otherwise destructure `undefined` into the matrix, produce `NaN`, and return it. Both of the Task 2 gate's failure filters are `<` comparisons, and every `<` against `NaN` is `false`, so an unreadable colour would be silently scored as **passing**. That is the one failure mode these gates cannot afford, and it is not theoretical: fail-loud parsing is exactly what surfaced the `color(srgb ` gap and then the `oklab(` gap, each as a thrown error rather than a false green. Guard once, before the branch, so no branch can be added later that skips it.

- [ ] **Step 2: Write the failing gate**

Create `ui/styles/theme-contrast.test.ts`. The `PAIRS` table is the palette's contract — a token pair not listed here is not guaranteed by anything.

The stylesheet is pulled in with Vite's native `?raw` import rather than `node:fs`. Do not substitute `readFileSync(new URL('./theme.css', import.meta.url))`: Vite's `assetImportMetaUrlPlugin` rewrites that exact pattern under happy-dom, and the rewritten URL throws `TypeError: Invalid URL` before any assertion runs. `?raw` also avoids needing `@types/node` on `ui/**`, which the root `tsconfig.json` does not provide. `ui/vite-env.d.ts` already references `vite/client`, so the import is typed.

`?raw` alone is not enough. Vitest's `CSSEnablerPlugin` replaces every import whose path matches `/\.(css|less|sass|scss|styl|stylus|pcss|postcss)($|\?)/` with `export default ""` unless the path is covered by `test.css.include`, and the default is `css: { include: [] }` — the `?raw` suffix does not exempt it. Without the config line below, `themeCss` is the empty string, `declaredTokens()` returns an empty map, and the gate measures nothing. In `vite.config.ts`, add one line to the `test` block, directly after `environment: 'happy-dom',`:

```ts
    css: { include: [/theme\.css/] },
```

Confirm it with `npm test -- --run ui/styles/theme-contrast.test.ts` before and after: the first test's failure list goes from every name in `PAIRS` to only the names Task 3 has yet to add.

That first test — `declares every token the contrast contract refers to` — is also the suite's non-vacuity guard, and the reason no separate stub check is needed. The gamut and contract tests both iterate the map, so both pass trivially when it is empty; the first test fails whenever it is, naming every missing token.

```ts
import { describe, expect, it } from 'vitest'
import themeCss from './theme.css?raw'
import { contrastRatio, isInSrgbGamut, luminanceFromOklch, type Oklch } from '../tests/contrast'

function declaredTokens(): Map<string, Oklch> {
  const pattern = /--color-([a-z0-9-]+):\s*oklch\(\s*([\d.]+)\s+([\d.]+)\s+([\d.]+)\s*\)/g
  const tokens = new Map<string, Oklch>()
  for (const [, name, l, c, h] of themeCss.matchAll(pattern)) {
    tokens.set(name, { l: Number(l), c: Number(c), h: Number(h) })
  }
  return tokens
}

const AA_TEXT = 4.5
const AA_NON_TEXT = 3

const PAIRS: ReadonlyArray<[string, string, number]> = [
  ['ink', 'surface', AA_TEXT],
  ['ink', 'paper', AA_TEXT],
  ['ink', 'surface-sunken', AA_TEXT],
  ['ink-soft', 'surface', AA_TEXT],
  ['ink-soft', 'paper', AA_TEXT],
  ['ink-soft', 'surface-sunken', AA_TEXT],
  ['ink-faint', 'surface', AA_NON_TEXT],
  ['brass', 'surface', AA_TEXT],
  ['brass', 'paper', AA_TEXT],
  ['brass-deep', 'surface', AA_TEXT],
  ['gain', 'surface', AA_TEXT],
  ['gain', 'paper', AA_TEXT],
  ['loss', 'surface', AA_TEXT],
  ['loss', 'paper', AA_TEXT],
  ['gain', 'surface-sunken', AA_TEXT],
  ['loss', 'surface-sunken', AA_TEXT],
  ['notice', 'surface', AA_TEXT],
  ['brass', 'brass-wash', AA_TEXT],
  ['gain', 'gain-wash', AA_TEXT],
  ['loss', 'loss-wash', AA_TEXT],
  ['notice', 'notice-wash', AA_TEXT],
  ['ink', 'loss-wash-deep', AA_TEXT],
  ['gray-500', 'surface', AA_TEXT],
  ['gray-500', 'paper', AA_TEXT],
  ['gray-600', 'surface', AA_TEXT],
  ['gray-600', 'paper', AA_TEXT],
  ['series-1', 'surface', AA_NON_TEXT],
  ['series-2', 'surface', AA_NON_TEXT],
  ['series-3', 'surface', AA_NON_TEXT],
  ['series-4', 'surface', AA_NON_TEXT],
  ['series-5', 'surface', AA_NON_TEXT],
  ['series-6', 'surface', AA_NON_TEXT],
]

describe('the Statement palette', () => {
  it('declares every token the contrast contract refers to', () => {
    const tokens = declaredTokens()
    const missing = [...new Set(PAIRS.flatMap(([a, b]) => [a, b]))].filter(
      name => !tokens.has(name)
    )
    expect(missing).toEqual([])
  })

  it('keeps every declared token inside the sRGB gamut', () => {
    const clipped = [...declaredTokens()]
      .filter(([, color]) => !isInSrgbGamut(color))
      .map(([name]) => name)
    expect(clipped).toEqual([])
  })

  it('meets its contrast contract on every declared pair', () => {
    const tokens = declaredTokens()
    const failures = PAIRS.filter(
      ([foreground, background]) => tokens.has(foreground) && tokens.has(background)
    )
      .map(([foreground, background, minimum]) => ({
        pair: `${foreground} on ${background}`,
        ratio: contrastRatio(
          luminanceFromOklch(tokens.get(foreground) as Oklch),
          luminanceFromOklch(tokens.get(background) as Oklch)
        ),
        minimum,
      }))
      .filter(({ ratio, minimum }) => ratio < minimum)
      .map(({ pair, ratio, minimum }) => ({ pair, ratio: Number(ratio.toFixed(2)), minimum }))
    expect(failures).toEqual([])
  })

  it('locks gain and loss to the same lightness so equal movements read with equal weight', () => {
    const tokens = declaredTokens()
    expect([tokens.get('gain')?.l, tokens.get('loss')?.l]).toEqual([0.52, 0.52])
  })
})
```

- [ ] **Step 3: Run it and confirm it fails for the right reason**

```bash
npm test -- --run ui/styles/theme-contrast.test.ts
```

Expected: FAIL. `theme.css` currently declares no `oklch()` values at all, so `declaredTokens()` returns an empty map and the first test reports every name in `PAIRS` as missing. That empty-map failure is the recorded evidence for this step — capture the output.

- [ ] **Step 4: Commit**

```bash
git add ui/tests/contrast.ts ui/styles/theme-contrast.test.ts vite.config.ts
git commit -m "Add contrast gate for palette tokens"
```

---

### Task 2: Rendered contrast gate

Replaces the current `palette.spec.ts`, which hardcodes `rgb(33, 197, 93)` and `rgb(220, 53, 69)` and would break on any palette change regardless of whether the new colours are legible.

**Files:**

- Rewrite: `ui/tests/visual/palette.spec.ts` (currently 34 lines; replace the whole file)

**Interfaces:**

- Consumes: `contrastRatio`, `flattenLayers`, `luminanceFromRgbString` from `ui/tests/contrast.ts` (Task 1); `freeze`, `openRoute` from `./settle`; `stubBuildInfo`, `stubEnums`, `stubInstruments`, `stubTransactions` from the existing fixtures.
- Produces: nothing imported elsewhere.

The browser closures collect raw colour **strings** and do no arithmetic. Every calculation — compositing, luminance, ratio — happens Node-side in `contrast.ts`, which is the one place the maths is verified. This is why each `page.evaluate` returns background _layers_ rather than a single resolved colour.

**Prerequisite:** the Vite dev server must be running at `http://localhost:61234`. Start it with `npm run dev:ui` in a separate shell, or `npm run test:setup` if backend-backed routes are also needed. `playwright.config.ts` declares no `webServer`, so nothing starts it for you.

- [ ] **Step 1: Replace the spec with a real gate**

Replace the entire contents of `ui/tests/visual/palette.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { contrastRatio, flattenLayers, luminanceFromRgbString } from '../contrast'
import { freeze, openRoute } from './settle'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { stubInstruments } from './instruments-fixture'
import { stubTransactions } from './transactions-fixture'

const AA_TEXT = 4.5
const AA_LARGE_TEXT = 3
const AA_NON_TEXT = 3
const TAB_STOPS = 15

const ROUTES = [
  { path: '/instruments', name: 'instruments', stub: stubInstruments },
  { path: '/transactions', name: 'transactions', stub: stubTransactions },
]

interface PaintedText {
  sample: string
  color: string[]
  background: string[]
  large: boolean
}

interface PaintedRing {
  target: string
  style: string
  width: number
  offset: number
  outlineColor: string
  background: string[]
}

test.beforeEach(async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await stubBuildInfo(page)
  await stubEnums(page)
})

for (const route of ROUTES) {
  test(`${route.name} paints every piece of text above its AA threshold`, async ({ page }) => {
    await route.stub(page)
    await openRoute(page, route.path)
    await freeze(page)

    const painted: PaintedText[] = await page.evaluate(() => {
      const backgroundLayers = (element: Element): string[] => {
        const layers: string[] = []
        for (let node: Element | null = element; node; node = node.parentElement) {
          layers.push(getComputedStyle(node).backgroundColor)
        }
        return layers
      }
      const ownText = (element: Element): string =>
        [...element.childNodes]
          .filter(node => node.nodeType === Node.TEXT_NODE)
          .map(node => node.textContent ?? '')
          .join('')
          .trim()
      return [...document.querySelectorAll('body *')]
        .filter(
          element =>
            ownText(element).length > 0 &&
            element.checkVisibility({
              opacityProperty: true,
              visibilityProperty: true,
              contentVisibilityAuto: true,
            })
        )
        .map(element => {
          const style = getComputedStyle(element)
          const size = Number.parseFloat(style.fontSize)
          const bold = Number.parseInt(style.fontWeight, 10) >= 700
          const background = backgroundLayers(element)
          return {
            sample: ownText(element).slice(0, 40),
            color: [style.color, ...background],
            background,
            large: size >= 24 || (size >= 18.66 && bold),
          }
        })
    })

    expect(painted.length).toBeGreaterThan(20)

    const seen = new Set<string>()
    const failures = painted
      .map(item => ({
        sample: item.sample,
        color: flattenLayers(item.color),
        background: flattenLayers(item.background),
        large: item.large,
      }))
      .filter(item => {
        const key = `${item.color}|${item.background}|${item.large}`
        if (seen.has(key)) return false
        seen.add(key)
        return true
      })
      .map(({ sample, color, background, large }) => ({
        sample,
        color,
        background,
        ratio: contrastRatio(luminanceFromRgbString(color), luminanceFromRgbString(background)),
        minimum: large ? AA_LARGE_TEXT : AA_TEXT,
      }))
      .filter(item => item.ratio < item.minimum)
      .map(item => ({ ...item, ratio: Number(item.ratio.toFixed(2)) }))

    expect(failures).toEqual([])
  })

  test(`${route.name} keeps every focus ring at 2px and above the non-text threshold`, async ({
    page,
  }) => {
    await route.stub(page)
    await openRoute(page, route.path)
    await freeze(page)

    const rings: PaintedRing[] = []
    for (let stop = 0; stop < TAB_STOPS; stop += 1) {
      await page.keyboard.press('Tab')
      const ring = await page.evaluate(async () => {
        await new Promise(requestAnimationFrame)
        await new Promise(requestAnimationFrame)
        const focused = document.activeElement
        if (!focused || focused === document.body) return null
        const layers: string[] = []
        for (let node: Element | null = focused.parentElement; node; node = node.parentElement) {
          layers.push(getComputedStyle(node).backgroundColor)
        }
        const style = getComputedStyle(focused)
        return {
          target: `${focused.tagName.toLowerCase()}.${focused.className}`.slice(0, 40),
          style: style.outlineStyle,
          width: Number.parseFloat(style.outlineWidth),
          offset: Number.parseFloat(style.outlineOffset),
          outlineColor: style.outlineColor,
          background: layers,
        }
      })
      if (ring) rings.push(ring)
    }

    expect(rings).toHaveLength(TAB_STOPS)

    const failures = rings
      .map(ring => {
        const background = flattenLayers(ring.background)
        const color = flattenLayers([ring.outlineColor, ...ring.background])
        return {
          target: ring.target,
          style: ring.style,
          width: ring.width,
          offset: ring.offset,
          color,
          ratio: contrastRatio(luminanceFromRgbString(color), luminanceFromRgbString(background)),
        }
      })
      .filter(
        ring =>
          ring.style === 'none' || ring.width < 2 || ring.offset <= 0 || ring.ratio < AA_NON_TEXT
      )
      .map(ring => ({ ...ring, ratio: Number(ring.ratio.toFixed(2)) }))

    expect(failures).toEqual([])
  })
}
```

Eight things in that code are load-bearing and easy to "tidy" into wrongness:

- **Every colour leaves the browser as a raw string.** The closures collect background _layers_ up the ancestor chain and hand them to `flattenLayers` Node-side. Do not resolve a single background inside `page.evaluate` — that is how the first version of this gate came to score `rgba(0, 0, 0, 0.07)` as if it were opaque black (1.36 against a true 14.3) and the focus ring's `color-mix(… 40%, transparent)` as if it were solid indigo (5.0 against a true 1.77, which would have made the ring assertion pass on the exact defect it exists to catch).
- **Both walks run to the root and never break early.** Stopping at the first opaque layer is exact but needs an alpha test, and a prefix test is not one: `.btn.btn-ghost` is `rgb(0 0 0 / 0.02)`, which is translucent and does not start with `rgba(`. Breaking on it composites a ghost button onto white instead of onto paper. Collecting every ancestor costs nothing and cannot be wrong: `flattenLayers` folds from the bottom up, so the first opaque layer discards everything beneath it anyway.
- **The ring's walk starts at `focused.parentElement`, not at `focused`.** An outline is painted outside the border edge, and the element's own background stops at that edge, so a ring never touches the thing it surrounds — `outline-offset: 2px` puts another 2px of parent background between them. Measuring a ring against its own element's fill invents a failure that no eye can see: the brass ring on `.platform-btn.active` scores 1.12 against the button's graphite, and 5.15 against the paper it is actually drawn on. Starting one level up does not weaken the gate — it still catches any focusable sitting on a genuinely dark container, which is the case WCAG 1.4.11 is about.
- **`ring.style === 'none'` is the assertion that catches "there is no ring".** Every other check reads a number that exists whether or not anything is painted, and their initial values are flattering: `outline-width`'s initial value is `medium`, which computes to `3px`, and `outline-color`'s is `currentColor`, which on this palette is ink. An element with no focus ring at all therefore reports a 3px ring in the darkest colour on the page — the gate scores it 15.79:1 and passes it. That is not hypothetical: it is what the first version of this gate did to the two `<input type="date">` on `/transactions`, certifying a nonexistent indicator as the best-contrasting one measured. Ask whether a ring exists before asking how good it is.
- **`ring.offset <= 0` is a failure, because the walk above depends on it.** Starting at the parent is correct only while a real gap separates the ring from the element's own fill; at `outline-offset: 0` the ring abuts that fill, and at a negative offset it is painted on top of it. Either way the parent background stops being the adjacent colour, and the gate would report the paper it never touches — a false pass on precisely the defect this assertion exists to catch. Both rules in `components.css` are `2px` today, so this costs nothing now; asserting it is what stops the invariant from being silently repealed by a later phase. Measure the assumption, do not inherit it.
- **`test.beforeEach` emulates `prefers-reduced-motion: reduce`.** Without it the gate samples whatever frame it lands on: mid-animation background colours from `instrument-table.vue`'s value flash, and mid-transition text colours, which Chromium serialises as `oklab(…)` because CSS Color 4 interpolates in oklab. Both are real paint, but neither is a state a reader ever sits with, and neither is reproducible run to run. `emulateMedia` routes through the app's own `base.css` reduced-motion block rather than injecting test-only CSS, so the gate measures settled states — the ones a human actually reads — and stays deterministic.
- **The ring closure is `async` and burns two animation frames before reading, and reduced motion does not make that redundant.** `prefers-reduced-motion` shortens the app's transitions to `1e-05s`; it does not make them instantaneous, and `page.keyboard.press('Tab')` resolves on the input event, not on the frame that renders its consequences. `.platform-btn` declares `transition: all`, so its outline animates from the initial `medium`/`0px` toward `2px`/`2px`, and a synchronous read samples the start of that animation: `3px` at offset `0` — which the two checks above correctly reject, as a failure that repairs itself in one frame. Two `requestAnimationFrame` round-trips inside the page put the read after style recalc and paint. Not a `waitForTimeout`: a fixed sleep is both slower and still a guess.
- **`TAB_STOPS` is 15, not 1.** One `Tab` lands on the first nav link and nothing else, so a single-stop version would measure one element forever and never see a button's ring. `expect(rings).toHaveLength(TAB_STOPS)` is the non-vacuity guard: it fails if `Tab` ever falls out of the document.

- [ ] **Step 2: Run it and confirm both failures**

```bash
npm run dev:ui
npm run visual -- palette.spec.ts --project=desktop
```

Expected: FAIL on all four tests. The text test reports at least `rgb(33, 197, 93)` on a near-white background at about 2.2 against a minimum of 4.5. The focus-ring test reports the nav links at `width: 1` — `components.css:29-31` scopes `:focus-visible` to `.btn`, `.btn-close` and `.form-check-input`, so an `<a class="nav-link">` falls back to Chromium's 1px UA outline. Task 3 Step 3 fixes both. Capture the outputs — they are this task's evidence.

Failures that name a `rgba(…)` background, or a ratio that disagrees with what the colours plainly look like on screen, mean the compositing is wrong, not the palette. Both gates report composited `rgb()` values only.

- [ ] **Step 3: Commit**

```bash
git add ui/tests/visual/palette.spec.ts
git commit -m "Replace palette spec with a contrast gate"
```

---

### Task 3: The Statement palette

Turns both gates green. Every legacy token keeps its name and gains a Statement value; the new Statement names are added alongside.

**Files:**

- Modify: `ui/styles/theme.css:13-81` (the `@theme static` block)
- Modify: `ui/styles/components.css:32` and `ui/styles/components.css:294` (focus ring)
- Modify: `ui/components/instruments/instrument-table.vue:703` and `:717` (the two hardcoded flash colours)
- Modify: `ui/components/instruments/instruments-view.vue:331-335` and `ui/components/etf/etf-breakdown.vue:419-422` (delete the two `:focus { outline: none }` rules that repeal the global ring)

**Interfaces:**

- Consumes: the token names asserted by `PAIRS` in `ui/styles/theme-contrast.test.ts` (Task 1).
- Produces: the full token set. Tasks 4, 5, 6 and every later phase consume `--color-paper`, `--color-surface`, `--color-ink`, `--color-ink-soft`, `--color-brass`, `--font-sans`.

- [ ] **Step 1: Replace the `@theme static` block**

In `ui/styles/theme.css`, replace lines 13–81 (from `@theme static {` through its closing `}`) with:

```css
@theme static {
  --color-*: initial;
  --color-transparent: transparent;
  --color-current: currentColor;
  --color-white: #ffffff;
  --color-black: #000000;

  --color-paper: oklch(0.985 0.006 85);
  --color-surface: oklch(1 0 0);
  --color-surface-sunken: oklch(0.965 0.008 85);
  --color-surface-hover: oklch(0.975 0.007 85);
  --color-surface-subtle: oklch(0.985 0.006 85);
  --color-surface-band: oklch(0.965 0.008 85);

  --color-hairline: oklch(0.905 0.008 85);
  --color-hairline-strong: oklch(0.855 0.01 85);
  --color-control-border: oklch(0.855 0.01 85);

  --color-ink: oklch(0.24 0.012 60);
  --color-ink-soft: oklch(0.5 0.014 60);
  --color-ink-faint: oklch(0.62 0.012 60);
  --color-ink-muted: oklch(0.5 0.014 60);
  --color-body-secondary: oklch(0.5 0.014 60);

  --color-brass: oklch(0.53 0.098 74);
  --color-brass-deep: oklch(0.46 0.098 74);
  --color-brass-wash: oklch(0.955 0.02 74);

  --color-gain: oklch(0.52 0.115 152);
  --color-gain-deep: oklch(0.45 0.115 152);
  --color-gain-wash: oklch(0.955 0.022 152);
  --color-loss: oklch(0.52 0.16 26);
  --color-loss-deep: oklch(0.45 0.16 26);
  --color-loss-wash: oklch(0.955 0.022 26);
  --color-loss-wash-deep: oklch(0.9 0.045 26);
  --color-notice: oklch(0.52 0.11 250);
  --color-notice-wash: oklch(0.955 0.022 250);

  --color-signal-indigo: oklch(0.53 0.098 74);
  --color-signal-indigo-deep: oklch(0.46 0.098 74);
  --color-control-graphite: oklch(0.5 0.014 60);
  --color-control-graphite-deep: oklch(0.24 0.012 60);
  --color-warning: oklch(0.46 0.098 74);

  --color-status-success: oklch(0.52 0.115 152);
  --color-status-danger: oklch(0.52 0.16 26);
  --color-status-info: oklch(0.52 0.11 250);
  --color-status-warning: oklch(0.53 0.098 74);

  --color-series-1: oklch(0.53 0.098 74);
  --color-series-2: oklch(0.52 0.115 152);
  --color-series-3: oklch(0.52 0.11 250);
  --color-series-4: oklch(0.52 0.16 26);
  --color-series-5: oklch(0.52 0.12 310);
  --color-series-6: oklch(0.58 0.07 200);

  --color-gray-100: oklch(0.975 0.007 85);
  --color-gray-200: oklch(0.93 0.008 85);
  --color-gray-300: oklch(0.905 0.008 85);
  --color-gray-400: oklch(0.855 0.01 85);
  --color-gray-500: oklch(0.55 0.014 60);
  --color-gray-600: oklch(0.5 0.014 60);
  --color-gray-700: oklch(0.4 0.014 60);
  --color-gray-800: oklch(0.32 0.013 60);
  --color-gray-900: oklch(0.24 0.012 60);

  --font-sans: 'Instrument Sans Variable', system-ui, sans-serif;
  --font-mono: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace;

  --text-2xs: 0.8125rem;
  --text-base: 0.9375rem;
  --text-heading: clamp(1.125rem, 1.05rem + 0.35vw, 1.25rem);
  --text-title: clamp(1.5rem, 1.3rem + 0.9vw, 1.875rem);
  --text-display: clamp(2.5rem, 1.6rem + 3.6vw, 3.5rem);

  --container-app: min(1350px, 91vw);

  --radius-control: 0.25rem;
  --radius-container: 0.5rem;

  --shadow-card: 0 1px 2px oklch(0.24 0.012 60 / 0.05);
  --shadow-control: 0 1px 2px oklch(0.24 0.012 60 / 0.04);
  --shadow-lifted: 0 2px 8px oklch(0.24 0.012 60 / 0.08);
  --shadow-nav: 0 1px 0 oklch(0.24 0.012 60 / 0.06);
  --shadow-overlay: 0 0.5rem 1.5rem oklch(0.24 0.012 60 / 0.14);

  --ease-standard: cubic-bezier(0.4, 0, 0.2, 1);

  --breakpoint-sm: 576px;
  --breakpoint-md: 768px;
  --breakpoint-lg: 992px;
  --breakpoint-xl: 1200px;
  --breakpoint-2xl: 1400px;
}
```

- [ ] **Step 2: Run the token gate and confirm it now passes**

```bash
npm test -- --run ui/styles/theme-contrast.test.ts
```

Expected: PASS, all four tests.

- [ ] **Step 3: Make the focus ring solid brass, and give it to everything focusable**

The ring fails 1.4.11 two different ways, and the Task 2 gate hits the second one first.

Where the rule applies at all it is drawn at 40% opacity, which composites to about 1.77:1 against paper. But the rule at `ui/styles/components.css:29-31` matches only `.btn`, `.btn-close`, and `.form-check-input`. Every other tab stop — nav links first among them — falls back to Chromium's 1px UA outline and fails the gate's `width >= 2` check before a colour is ever measured. Recolouring alone leaves the gate red.

Fix both at once, and stop enumerating class names: the rule should key off what is focusable, not off which components someone remembered.

Replace `ui/styles/components.css:29-32`:

```css
.btn:focus-visible,
.btn-close:focus-visible,
.form-check-input:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--color-signal-indigo) 40%, transparent);
```

with:

```css
a[href]:focus-visible,
button:focus-visible,
input:focus-within,
select:focus-visible,
textarea:focus-visible {
  outline: 2px solid var(--color-brass);
```

Leave the `outline-offset: 2px` and the closing brace that follow untouched.

This is a strict superset of the three classes it replaces — `.btn` is a `<button>` or an `<a href>`, `.btn-close` is a `<button>`, `.form-check-input` is an `<input>` — so nothing loses its ring. It deliberately uses element selectors rather than a bare `:focus-visible`: `.modal-content` at line 421 sets `outline: 0` on the dialog itself, and a universal rule would win on source order and paint a brass ring around the whole modal.

A global rule is only global if nothing local repeals it. Two components repeal this one — delete both rules outright.

In `ui/components/instruments/instruments-view.vue`, delete:

```css
.period-select:focus {
  outline: none;
  border-color: #4b5563;
  box-shadow: 0 0 0 3px rgba(75, 85, 99, 0.1);
}
```

In `ui/components/etf/etf-breakdown.vue`, delete:

```css
.search-input:focus {
  outline: none;
  border-color: #4b5563;
}
```

Delete each rule entirely, selector and braces included, not just the `outline` line. The `border-color` and `box-shadow` declarations are the substitute indicator these components invented in place of the ring; with the ring restored they are a second, weaker focus treatment on two controls out of dozens, and they are two of the five hardcoded colours the "no component declares a colour" constraint forbids. Everything else in those `<style>` blocks stays — the rest of the hardcoded palette in both files is Phase 2's to migrate.

Specificity is not why the override wins, so raising specificity in `components.css` would not fix it. Vue compiles scoped styles into the SFC's own `<style>` element, which is **unlayered**, and an unlayered declaration beats every layered one regardless of specificity. `components.css` is imported as `layer(components)`. There is no selector that reaches past that from inside a layer; the local rule has to go.

Note also that `outline: none` resets `outline-style`, `outline-width` and `outline-color`, but **not** `outline-offset` — that is a separate property, not part of the shorthand. So an element in this state reports the global rule's `outline-offset: 2px` alongside the component's `outline-style: none`, which is exactly the mixed reading the gate reports and a good reminder that a plausible-looking offset proves nothing about whether a ring exists.

The `input` line is the one exception, and it is not a typo. `<input type="date">` and `<input type="file">` render their controls in shadow DOM, and focus lands on a sub-field inside it. `document.activeElement` reports the host because focus retargets across the shadow boundary, but the host matches **neither `:focus` nor `:focus-visible`** — only `:focus-within`. `/transactions` has two date inputs, and under `:focus-visible` both are keyboard-reachable with no visible indicator whatsoever: a 2.4.7 failure, and the exact one the gate's new `outline-style` check now catches. `:focus-within` on an `<input>` is a superset of `:focus`, which is a superset of `:focus-visible`, so the only behaviour it adds elsewhere is a ring on mouse-click focus — which `.form-control:focus` at line 294 already gives every text field in the app. Verified in Chromium: with `:focus-within`, tabbing to the date input yields `outline-style: solid`, `2px`, offset `2px`, `oklch(0.53 0.098 74)`.

At line 294, replace:

```css
outline: 2px solid rgb(67 97 238 / 0.2);
```

with:

```css
outline: 2px solid var(--color-brass);
```

- [ ] **Step 4: Give the value flash a token**

`ui/components/instruments/instrument-table.vue` paints two colours of its own, in the keyframes at lines 703 and 717:

```css
50% {
  background-color: rgb(33 197 93 / 0.2);
}
```

```css
50% {
  background-color: rgb(220 53 69 / 0.2);
}
```

Replace those two values with `var(--color-gain-wash)` and `var(--color-loss-wash)`. Change nothing else in the file — not the durations, not the easing, not the class names.

This is the Global Constraint "no component declares a colour", and it is also the only way the flash can be measured. The flash lands on the same `<span class="total-value">` that carries the gain/loss text, so at the animation's 50% keyframe that text is briefly reading against the flash rather than against the row. A translucent green cannot be guaranteed: composited over a white row it leaves gain text at 4.45, over a striped row at 4.07 — and lowering the alpha never reaches 4.5 because the row beneath keeps changing. An opaque wash is base-independent, so it holds at 4.611 and 5.204 on every row, and `PAIRS` already asserts exactly those two pairs in Task 1's token gate.

Those two figures are computed in continuous OKLCH, which is the space the Task 1 gate works in: it reads the `oklch()` values out of `theme.css` and never quantises. Computing the same two pairs from their 8-bit hex renderings instead gives 4.637 and 5.181 — a real difference of about 0.03, not a rounding artefact, and the reason these numbers have been written both ways. Quote the continuous figures, because they are the ones the gate asserts. Both pairs clear 4.5 either way.

- [ ] **Step 5: Run the rendered gate and confirm both tests pass**

With `npm run dev:ui` running:

```bash
npm run visual -- palette.spec.ts --project=desktop
```

Expected: PASS, four tests (two routes × two assertions).

If the text test still reports failures, they are real and each names its sample text, colour, background and ratio. Read the reported background before reaching for the text colour: a background that is not a token means some component is still painting its own, and the fix belongs there. Where the text really is too light, move it to `ink-soft` — but never `gain` or `loss`, which the spec reserves for signed monetary movement. Do not raise the threshold and do not add an exclusion list.

- [ ] **Step 6: Run the full unit suite**

```bash
npm test -- --run
```

Expected: `ui/components/portfolio/portfolio-chart.test.ts` still passes — it asserts four hardcoded `borderColor` hexes (`#8884d8`, `#ffc658`, `#82ca9d`, `#ff7300`) that live in the component, not in `theme.css`, so this task does not touch them. Any other failure is a regression from this task.

- [ ] **Step 7: Commit**

```bash
git add ui/styles/theme.css ui/styles/components.css ui/components/instruments/instrument-table.vue
git commit -m "Retone palette to the Statement OKLCH ramp"
```

---

### Task 4: Typography

**Files:**

- Modify: `package.json` (two dependencies)
- Modify: `ui/main.ts:1-5`
- Modify: `ui/styles/base.css:42-93`

**Interfaces:**

- Consumes: `--font-sans`, `--text-base`, `--text-display`, `--text-title`, `--text-heading` from Task 3 (`theme.css:79-86`).
- Produces: `h1`–`h6` on the fluid scale, available to every later phase.

Deviation 6 governs this task: there is one face, not two. Instrument Serif and `--font-display` were reverted at the human partner's request after Task 7 shipped; headings inherit `--font-sans`.

- [ ] **Step 1: Install the face**

```bash
npm install --save-exact @fontsource-variable/instrument-sans@5.3.0
```

- [ ] **Step 2: Import it before the theme**

In `ui/main.ts`, replace lines 1–5 with:

```ts
import { createApp } from 'vue'
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from './app.vue'
import '@fontsource-variable/instrument-sans/wght.css'
import './styles/theme.css'
import router from './router/index'
```

`@fontsource-variable/instrument-sans` is a variable package, so it splits by **axis** rather than by subset — its entry points are `wght.css`, `wdth.css`, and `standard.css`, and there is no `latin.css` to import. `wght.css` is the narrowest of the three: one axis, two `@font-face` blocks (latin and latin-ext).

- [ ] **Step 3: Verify the import path resolves**

```bash
npm run build
```

Expected: PASS. A wrong subset path fails the Vite build loudly with `Failed to resolve import`. If it does, list the real files with `ls node_modules/@fontsource-variable/instrument-sans/` and use the matching name — remembering that the variable package offers axis names, not subset names.

- [ ] **Step 4: Replace the heading scale**

In `ui/styles/base.css`, replace lines 42–93 (the `h1`–`h6` block and the `@media (min-width: 1200px)` block that follows it) with:

```css
h1,
h2,
h3,
h4,
h5,
h6 {
  margin-bottom: 0.5rem;
  font-weight: 500;
  line-height: 1.2;
  text-wrap: balance;
}

h1 {
  font-size: var(--text-display);
}

h2 {
  font-size: var(--text-title);
}

h3 {
  font-size: var(--text-heading);
}

h4 {
  font-size: 1.125rem;
}

h5 {
  font-size: 1rem;
}

h6 {
  font-size: 0.9375rem;
}
```

The per-breakpoint overrides are deleted, not replaced — `clamp()` covers the same range continuously.

- [ ] **Step 5: Measure what the face actually costs**

The spec budgets 40–60KB of woff2 and calls this the redesign's largest performance cost. Measure it rather than assuming it:

```bash
npm run build && ls -l dist/assets/*.woff2 | awk '{ printf "%7.1f KB  %s\n", $5 / 1024, $9 }'
```

Expected: two files totalling about 40KB, of which **roughly 29KB is what a reader actually downloads**:

| File                                    | Size  | Fetched? |
| --------------------------------------- | ----- | -------- |
| `instrument-sans-latin-wght-normal`     | ~29KB | yes      |
| `instrument-sans-latin-ext-wght-normal` | ~11KB | no       |

Compare the **delivered** total against the spec's 40–60KB budget, not the bundled total. The latin-ext face rides along because `wght.css` declares it, but its `@font-face` carries a `unicode-range` that no character in this app falls inside, so no browser rendering these screens ever requests it. Summing every woff2 in `dist/` counts a file that is never sent.

Materially above 60KB _delivered_ means a subset wider than latin got bundled — check the import path in Step 2 before continuing. Record the per-file breakdown; it is this task's evidence.

- [ ] **Step 6: Run both gates and the unit suite**

```bash
npm test -- --run
npm run visual -- palette.spec.ts --project=desktop
```

Expected: PASS. Headings grow under the fluid scale. Weight 500 is safe to name because Instrument Sans is a variable face carrying the whole `wght` axis — nothing is synthesised.

- [ ] **Step 7: Commit**

```bash
git add package.json package-lock.json ui/main.ts ui/styles/base.css
git commit -m "Set Instrument Sans and the fluid type scale"
```

---

### Task 5: First paint and favicon

Without this, the page paints the old world for the moments before Vue mounts, then jumps.

**Files:**

- Modify: `ui/index.html:5,10-37`
- Create: `ui/public/favicon.svg`
- Delete: `ui/public/vite.svg` — Vite's root is `ui`, so `publicDir` is `ui/public`, and `ui/index.html:5` is its only reference in the repo

**Interfaces:**

- Consumes: the Statement hex equivalents of exactly three tokens — `paper` → `#fcfaf6`, `hairline` → `#e2dfda`, `brass` → `#8d621f`. The inline `<style>` runs before any stylesheet loads, so it cannot use `var()`; these are the only literal hexes this plan authorises, and each is the sRGB rendering of its Task 3 token, verified against `theme.css:20,27,37`. Do not introduce a fourth.
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Retokenize the loader**

In `ui/index.html`, replace the `<style>` block at lines 10–37 with:

```html
<style>
  html {
    color-scheme: light;
    background-color: #fcfaf6;
  }
  body {
    margin: 0;
  }
  .initial-loader {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    height: 100vh;
    background-color: #fcfaf6;
  }
  .initial-spinner {
    width: 3rem;
    height: 3rem;
    border: 0.25rem solid #e2dfda;
    border-top-color: #8d621f;
    border-radius: 50%;
    animation: spin 1s linear infinite;
  }
  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
```

- [ ] **Step 2: Add the favicon**

Create `ui/public/favicon.svg`:

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">
  <text x="16" y="24" font-family="Georgia, serif" font-size="28" fill="#8d621f" text-anchor="middle">P</text>
</svg>
```

In `ui/index.html`, replace line 5:

```html
<link href="/vite.svg" rel="icon" type="image/svg+xml" />
```

with:

```html
<link href="/favicon.svg" rel="icon" type="image/svg+xml" />
```

- [ ] **Step 3: Confirm no first-paint flash**

```bash
npm run dev:ui
```

Load `http://localhost:61234` with a hard refresh and watch the first paint. Expected: warm paper throughout, brass spinner arc, no white-to-paper or indigo-to-brass jump. `#fafafa` and `#4361ee` must not appear anywhere in the file:

```bash
grep -n "fafafa\|4361ee\|f8f9fa\|e9ecef" ui/index.html
```

Expected: no output.

- [ ] **Step 4: Commit**

```bash
git rm ui/public/vite.svg
git add ui/index.html ui/public/favicon.svg
git commit -m "Paint the first frame in Statement colours"
```

---

### Task 6: The chart palette

Pulled forward from Phase 2 at the human partner's request: the three pie charts on `/etf-breakdown` still render a saturated categorical palette that belongs to no palette in `theme.css`, and the surrounding chrome is already in the Statement world. It reads as a different application. The baselines in Task 9 must be recorded after this, not before.

The palette being replaced is Okabe-Ito, chosen for colour-vision safety. What replaces it is a categorical wheel of the same kind — eight hues, not ten — retuned to the Statement world's lightness and chroma so it reads as this application rather than as a default plotting library. Colour-vision safety is not carried by the hues themselves and never was on a pie: every legend row states its label and percentage as full-contrast text, and the tooltip names the hovered slice. That is the conformant channel. The hues exist so a sighted reader can tell one arc from its neighbour at a glance, which is the job a single-hue ramp was tried at three times and failed.

It also fixes a real defect. `etf-chart-service.ts` indexes with `colors[index % colors.length]` against a 10-entry array while `topCount` is 15, so a chart with more than ten slices gives two different slices the same colour. Sixteen entries makes that unreachable.

**Files:**

- Modify: `ui/constants/chart-colors.ts` (whole file)
- Create: `ui/constants/chart-colors.test.ts`
- Modify: `ui/components/etf/etf-breakdown-chart.vue:66,123-182`
- Modify: `ui/components/etf/etf-breakdown.vue:342-393,411-438`

**Interfaces:**

- Consumes: `--color-paper`, `--color-hairline`, `--color-hairline-strong`, `--color-ink`, `--color-ink-soft`, `--color-ink-muted`, `--color-brass`, `--color-brass-deep`, `--color-surface-hover`, `--color-white` from Task 3 (`theme.css:17,20,23,27-28,31-34,37-38`), and `contrastRatio`, `isInSrgbGamut`, `luminanceFromOklch`, `Oklch` from `ui/tests/contrast.ts` (Task 1).
- Produces: `CHART_COLORS` as a 16-entry ordered ramp and `OTHERS_COLOR` as a neutral, both consumed unchanged by `etf-chart-service.ts` — that file's signature does not move.

- [ ] **Step 1: Replace the palette with the Statement wheel**

Replace `ui/constants/chart-colors.ts` entirely:

```ts
export const CHART_COLORS = [
  'oklch(0.55 0.09 74)',
  'oklch(0.55 0.09 119)',
  'oklch(0.55 0.09 164)',
  'oklch(0.55 0.09 209)',
  'oklch(0.55 0.09 254)',
  'oklch(0.55 0.09 299)',
  'oklch(0.55 0.09 344)',
  'oklch(0.55 0.09 29)',
  'oklch(0.75 0.07 74)',
  'oklch(0.75 0.07 119)',
  'oklch(0.75 0.07 164)',
  'oklch(0.75 0.07 209)',
  'oklch(0.75 0.07 254)',
  'oklch(0.75 0.07 299)',
  'oklch(0.75 0.07 344)',
  'oklch(0.75 0.07 29)',
]

export const OTHERS_COLOR = 'oklch(0.91 0.005 85)'
```

Eight hues at 45° spacing around the OKLCH wheel, run twice: once deep, once pale. Lightness and chroma are constant inside each half, so the only thing that changes between neighbouring arcs is hue — which is the channel a category deserves. Holding L and C fixed is also what keeps this from becoming a rainbow: nothing in the set is louder than anything else in it, and the whole wheel sits at the same muted weight as `--color-brass`.

The wheel **starts at hue 74**, which is brass. Slices arrive sorted by size, so the largest one paints in the brand accent and the palette introduces itself with the colour the rest of the application already uses. Do not re-order the hues to start elsewhere.

The deep half takes the first eight slices and the pale half the last eight, so visual weight tracks data weight — big arcs read heavy, the tail recedes. Every one of the sixteen is inside the sRGB gamut; the deep half measures 4.4–4.9:1 against paper and the pale half 2.07–2.20:1 (all sixteen verified with `ui/tests/contrast.ts`, not estimated).

Three single-hue lightness ramps were tried before this and all three were rejected on sight — sorted, interleaved, and sorted again inside Task 7's gapped donut. A 0.024 lightness step between touching arcs is arithmetic, not perception, and no amount of whitespace between segments makes sixteen browns nameable apart. Do not reintroduce a ramp.

`OTHERS_COLOR` sits at lightness 0.91 and chroma 0.005 — off the wheel entirely, and paler than both halves. A residual is not a category: on Top Companies it is routinely the largest single arc, and any tone with real chroma makes the leftover the loudest thing on the chart.

`etf-chart-service.ts` keeps assigning `colors[index % colors.length]` and needs no change. Sixteen entries against `topCount = 15` still means no chart can reuse a colour. Do not round these differently or re-derive them.

- [ ] **Step 2: Write the gate**

Create `ui/constants/chart-colors.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { CHART_COLORS, OTHERS_COLOR } from './chart-colors'
import { contrastRatio, isInSrgbGamut, luminanceFromOklch, type Oklch } from '../tests/contrast'

const PAPER: Oklch = { l: 0.985, c: 0.006, h: 85 }
const TOP_COUNT = 15

function parseRampColor(value: string): Oklch {
  const parts = value.match(/^oklch\(([\d.]+) ([\d.]+) ([\d.]+)\)$/)
  if (!parts) {
    throw new Error(`Cannot read an oklch colour from "${value}"`)
  }
  return { l: Number(parts[1]), c: Number(parts[2]), h: Number(parts[3]) }
}

describe('the chart palette', () => {
  it('holds enough entries that the default slice count never reuses a colour', () => {
    expect(CHART_COLORS.length).toBeGreaterThanOrEqual(TOP_COUNT + 1)
  })

  it('contains no duplicate entries', () => {
    expect(new Set(CHART_COLORS).size).toEqual(CHART_COLORS.length)
  })

  it('separates every touching pair by hue or by lightness, including the closing seam', () => {
    const wheel = CHART_COLORS.map(parseRampColor)
    const touching = wheel.map((color, index) => [color, wheel[(index + 1) % wheel.length]])
    const muddy = touching.filter(([one, other]) => {
      const hueGap = Math.abs(one.h - other.h)
      return Math.min(hueGap, 360 - hueGap) < 30 && Math.abs(one.l - other.l) < 0.15
    })
    expect(muddy).toEqual([])
  })

  it('holds every entry to one of two lightness levels so no slice shouts over another', () => {
    const levels = new Set(CHART_COLORS.map(color => parseRampColor(color).l))
    expect(levels.size).toEqual(2)
  })

  it('renders every entry inside the sRGB gamut', () => {
    const outside = CHART_COLORS.filter(color => !isInSrgbGamut(parseRampColor(color)))
    expect(outside).toEqual([])
  })

  it('keeps every entry distinguishable from the paper it sits on', () => {
    const paper = luminanceFromOklch(PAPER)
    const ratios = CHART_COLORS.map(color =>
      contrastRatio(luminanceFromOklch(parseRampColor(color)), paper)
    )
    expect(Math.min(...ratios)).toBeGreaterThanOrEqual(1.7)
  })

  it('keeps the Others tone off the wheel and inside the gamut', () => {
    expect(CHART_COLORS).not.toContain(OTHERS_COLOR)
    expect(isInSrgbGamut(parseRampColor(OTHERS_COLOR))).toEqual(true)
  })

  it('keeps the Others tone lighter than every named slice so the residual recedes', () => {
    const lightest = Math.max(...CHART_COLORS.map(color => parseRampColor(color).l))
    expect(parseRampColor(OTHERS_COLOR).l).toBeGreaterThan(lightest)
  })
})
```

Run it:

```bash
npm test -- --run chart-colors
```

Expected: PASS, 8 tests. Those four import names are the real exports of `ui/tests/contrast.ts` — checked, not assumed. Note that `contrastRatio` takes two **luminances**, not two colour strings, and that `parseColor` in that file is private, which is why this test carries its own three-line parser.

The 1.7 floor is not WCAG. The pale half sits near 2.1:1 against paper by design — pushing it to 3:1 would darken the tail until it competed with the head, and the legend carries every label and value as full-contrast text, so colour is never the sole channel. The floor exists to catch a future edit that walks the pale half into the paper.

The 30°/0.15 thresholds in the touching-pair test are the perceptual claim this palette rests on, so they are asserted rather than assumed: at chroma 0.07 a 30° hue rotation is roughly a 0.036 chord in OKLab, comfortably above a just-noticeable difference, and the wheel's real spacing is 45°. The test also closes the pie's wrap seam — the last arc touches the first — which the earlier ramp gate never measured.

- [ ] **Step 3: Retone the chart component**

In `ui/components/etf/etf-breakdown-chart.vue`, the arc separator is currently a 2px pure-white gap. Replace both `borderWidth: 2` and `borderColor: '#ffffff'` with a 1px read of the live token:

```ts
          borderWidth: 1,
          borderColor: getComputedStyle(document.documentElement).getPropertyValue('--color-hairline-strong'),
          hoverBackgroundColor: props.chartData.map(item => item.color),
          hoverBorderColor: getComputedStyle(document.documentElement).getPropertyValue('--color-hairline-strong'),
```

The two `hover*` lines are load-bearing, and Step 5 is what proved it. Left unset, Chart.js derives the hovered arc's fill by passing the base colour through `getHoverColor()`, which parses in JavaScript via `@kurkle/color` — and that parser has no `oklch()` support, so it returns `undefined` and the canvas falls back to its default black. Measured directly:

```
color('oklch(0.44 0.082 72)').valid  →  false
getHoverColor('oklch(0.44 0.082 72)')  →  undefined
getHoverColor('#4e79a7')  →  '#326CAA'
```

Setting both explicitly means `getHoverColor` is never reached: the string goes straight to the canvas, which does understand `oklch()`. Hover stops changing the fill — the tooltip carries the feedback until Task 7 adds the pop-out and the centre readout.

`updateChartData` in the same file needs the matching line, right after the one that refreshes `backgroundColor`:

```ts
chart.data.datasets[0].hoverBackgroundColor = props.chartData.map(item => item.color)
```

That path runs whenever the platform filter changes the slice count. Refresh one array and not the other and a hover past the shorter array's end resolves to `undefined` again.

Everything else here is the spec's own remedy for a chart palette whose pale end falls below 3:1: the legend states every label and percentage as full-contrast text and the tooltip names the hovered slice, so colour is never the sole carrier. Do not claim the stroke satisfies WCAG 1.4.11 — `hairline-strong` reads 1.56:1 against the card, well under 3:1. It is a visual seam that keeps pale neighbours from bleeding together, and that is all it is asked to do. It must not be `--color-paper` — the canvas sits inside a `.card`, whose background is `--color-surface` (pure white), so a paper-coloured stroke would be an invisible warm line and would not bound the pale slices against the card at all. Reading the token rather than repeating its value keeps a single source of truth; `.trim()` the result if the engine returns leading whitespace.

Then in the same file's `<style scoped>` block, replace the four hardcoded values:

- `.card` `border: 1px solid #e0e0e0` → `border: 1px solid var(--color-hairline)`
- `.chart-title` `color: #1a1a1a` → `color: var(--color-ink)`
- `.legend-label` `color: #495057` → `color: var(--color-ink-soft)`
- `.legend-value` `color: #1a1a1a` → `color: var(--color-ink)`

And give the legend swatch an edge, so the palest ramp entries stay visible on paper. In `.legend-color`, add one declaration:

```css
border: 1px solid var(--color-hairline-strong);
```

- [ ] **Step 4: Retone the filter chips**

`ui/components/etf/etf-breakdown.vue` carries thirteen hardcoded colours in its `<style scoped>` block, including `#0072b2` — the old chart palette's first entry, reused as the active platform chip. Every substitution below is token-to-token, and each foreground/background pair is one the theme gate in `ui/styles/theme-contrast.test.ts` already asserts:

- `.etf-separator` `background-color: #d1d5db` → `var(--color-hairline-strong)`
- `.etf-btn` `border: 1px solid #e2e8f0` → `1px solid var(--color-hairline)`
- `.etf-btn` `background: white` → `var(--color-surface)`
- `.etf-btn:hover` `background: #f8fafc` → `var(--color-surface-hover)`
- `.etf-btn:hover` `border-color: #cbd5e1` → `var(--color-hairline-strong)`
- `.etf-btn:hover` `color: #4b5563` → `var(--color-ink)`
- `.etf-btn:active` `background: #f1f5f9` → `var(--color-surface-sunken)`
- `.etf-btn.active` `background: #4b5563` → `var(--color-ink)`, `border-color: #4b5563` → `var(--color-ink)`
- `.etf-btn.active:hover` `background: #374151` → `var(--color-ink)`, `border-color: #374151` → `var(--color-ink)`
- `:deep(.platform-btn.active)` `background: #0072b2` → `var(--color-brass)`, `border-color: #0072b2` → `var(--color-brass)`
- `:deep(.platform-btn.active:hover)` `background: #005a8c` → `var(--color-brass-deep)`, `border-color: #005a8c` → `var(--color-brass-deep)`
- `.search-input` `border: 1px solid #e2e8f0` → `1px solid var(--color-hairline)`
- `.search-input` `color: #374151` → `var(--color-ink)`
- `.search-input::placeholder` `color: #9ca3af` → `var(--color-ink-soft)`
- `.search-clear-btn` `color: #9ca3af` → `var(--color-ink-soft)`, and its `:hover` `color: #4b5563` → `var(--color-ink)`

Neither of those two takes `ink-faint`, even though it is the nearer match by lightness. Global Constraint 4 reserves `ink-faint` for chart ticks, disabled glyphs and decorative rules — a placeholder is small text and a clear button is an enabled control, so both take `ink-soft`. This darkens the placeholder noticeably; that is the constraint working, not a mistake.

Leave every `color: white` on an active chip alone — white on ink and white on brass are both already gated.

`.etf-btn.active:hover` and `.etf-btn.active` land on the same token by this mapping. Collapse them only if the rest of the two rules is identical; if the hover rule carries anything else, leave both in place.

Confirm the sweep is complete:

```bash
grep -n "#[0-9a-fA-F]\{3,6\}" ui/components/etf/etf-breakdown.vue ui/components/etf/etf-breakdown-chart.vue
```

Expected: no output.

- [ ] **Step 5: Prove the palette actually renders**

The palette is authored in `oklch()` and Chart.js writes `backgroundColor` straight to the canvas. That a bare canvas parses `oklch()` is already settled — measured in Chromium before this task was written: `fillStyle = 'oklch(0.44 0.082 72)'` reads back `rgb(110, 74, 22)`, `oklch(0.8 0.02 86)` reads back `rgb(195, 189, 176)`, and `oklch(0.62 0.012 60)` reads back `rgb(140, 133, 127)`, each matching the computed sRGB rendering exactly. You do not need to re-establish that.

What is **not** settled is whether Chart.js hands the string through untouched. It does for the resting fill and it does not for hover — that is exactly what this step caught, and Step 3's two `hover*` lines are the remedy. Re-run the check anyway: a canvas given a colour it cannot parse paints black, and no unit test would see it.

Start the dev server if it is not already up (`npm run dev:ui`, port 61234), open `http://localhost:61234/etf-breakdown` in the Playwright browser, wait for a `canvas` to be present, and sample it:

```js
const canvas = document.querySelector('canvas')
const ctx = canvas.getContext('2d')
ctx.getImageData(Math.round(canvas.width / 2), Math.round(canvas.height * 0.15), 1, 1).data
```

Report the RGBA you read, and hover one slice and read it again.

Expected: an opaque warm tone — the arc at top-centre is the first slice, which is brass. Transparent or black means Chart.js mangled the string — report BLOCKED with the reading rather than inventing a workaround; the fallback is a palette decision, not yours to take.

Do not skip this step. Report what you actually observed, not what you expected.

- [ ] **Step 6: Run the suites**

```bash
npm test -- --run
npm run lint-format
```

Expected: PASS. `npm run lint-format` rewrites `ui/models/generated/domain-models.ts` as a side effect — that file is generated, so restore it with `git checkout -- ui/models/generated/domain-models.ts` and never stage it.

If a component test asserts one of the old hexes, update the assertion to the new token and note it; if a test breaks for any other reason, that is a regression and you fix the code, not the test.

- [ ] **Step 7: Commit**

```bash
git add ui/constants/chart-colors.ts ui/constants/chart-colors.test.ts ui/components/etf/etf-breakdown-chart.vue ui/components/etf/etf-breakdown.vue
git commit -m "Retone the ETF charts to the Statement ramp"
```

---

### Task 7: The donut

Task 6 fixed the charts' colour. This fixes their form. The human partner pointed at Lightyear's fund-breakdown card (`https://lightyear.com/en/etf/QDVE:XETRA`) and asked for "something like this": one wide card, a thick donut with gapped segments, the active slice's name and share read out large in the hole, a two-column legend beside it with the value under the label, and a segmented control switching between the three breakdowns.

Today the page renders three narrow pie cards side by side in a `lg:grid-cols-3`, each with a cramped scrolling legend underneath. At a third of the container width neither the pie nor the legend has room. Consolidating to one card is what buys the donut its size and the legend its second column — the three breakdowns are still all reachable, one click apart, and the holdings table below is untouched.

Two things in the reference are deliberately **not** copied:

- **The "one saturated slice, the rest pastel" emphasis.** Task 6's ramp already encodes rank as lightness — darkest is largest. A second emphasis mechanic layered on top would fight the first. Hover is the emphasis here.
- **The `View all holdings` pill.** The full table is already on the page directly below the card. A button that scrolls to something already visible is furniture.

**Files:**

- Modify: `ui/components/etf/etf-breakdown-chart.vue` — pie → doughnut, centre readout, header slot
- Create: `ui/components/etf/etf-breakdown-legend.vue` — the two-column legend, extracted so both files stay under the 200-line component limit
- Modify: `ui/components/etf/etf-breakdown.vue` — three cards → one card plus the segmented control
- Modify: `ui/components/etf/etf-breakdown-chart-registration.test.ts`
- Modify: `ui/components/etf/etf-breakdown-chart.test.ts`

**Interfaces:**

- Consumes: `CHART_COLORS` and `OTHERS_COLOR` from Task 6 — unchanged, this task does not touch `ui/constants/chart-colors.ts` or `ui/services/etf-chart-service.ts`. Tokens from Task 3: `--color-ink`, `--color-ink-soft`, `--color-hairline`, `--color-hairline-strong`, `--color-brass`, `--color-brass-wash`, `--color-surface-hover`, `--radius-control`, `--font-sans`.
- Produces: `etf-breakdown-chart.vue` keeps its exact public contract — props `title: string` and `chartData: ChartDataItem[]`, and the `ChartDataItem` interface exported from its plain `<script lang="ts">` block. It gains one named slot, `actions`. `ChartDataItem` does not move file.

- [ ] **Step 1: Extract the legend**

Create `ui/components/etf/etf-breakdown-legend.vue`:

```vue
<template>
  <div class="chart-legend" @mouseleave="emit('leave')">
    <div
      v-for="(item, index) in items"
      :key="item.label"
      class="legend-item"
      :class="{ active: index === activeIndex }"
      @mouseenter="emit('hover', index)"
    >
      <img
        v-if="item.code"
        :src="`https://hatscripts.github.io/circle-flags/flags/${item.code.toLowerCase()}.svg`"
        :alt="item.code"
        class="legend-flag"
      />
      <span v-else class="legend-color" :style="{ backgroundColor: item.color }"></span>
      <span class="legend-label">{{ item.label }}</span>
      <span class="legend-value">{{ item.percentage }}%</span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import type { ChartDataItem } from './etf-breakdown-chart.vue'

defineProps<{
  items: ChartDataItem[]
  activeIndex: number
}>()

const emit = defineEmits<{
  hover: [index: number]
  leave: []
}>()
</script>

<style scoped>
.chart-legend {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.875rem 1.5rem;
  align-content: start;
  max-height: 22rem;
  overflow-y: auto;
}

.legend-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  column-gap: 0.625rem;
  row-gap: 0.0625rem;
  align-items: center;
}

.legend-color,
.legend-flag {
  grid-row: 1 / span 2;
}

.legend-color {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 1px solid var(--color-hairline-strong);
}

.legend-flag {
  width: 16px;
  height: 16px;
  border-radius: 50%;
}

.legend-label {
  grid-column: 2;
  font-size: 0.8125rem;
  color: var(--color-ink-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.legend-value {
  grid-column: 2;
  font-size: 1rem;
  font-weight: 500;
  color: var(--color-ink);
  font-variant-numeric: tabular-nums;
}

.legend-item.active .legend-label {
  color: var(--color-ink);
}

@media (max-width: 480px) {
  .chart-legend {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
```

The dot is a circle now rather than a 2px-radius square, and it keeps its `hairline-strong` border. The border is a visual seam, not a WCAG remedy: it reads 1.56:1 against the card and cannot bound anything to 3:1. What satisfies 1.4.11 is the legend stating every label and percentage as full-contrast text, so colour is never the sole carrier.

- [ ] **Step 2: Turn the pie into a donut**

In `ui/components/etf/etf-breakdown-chart.vue`, replace the template with:

```vue
<template>
  <div class="card border-0! shadow-[0_0.125rem_0.25rem_rgb(0_0_0/0.075)]">
    <div class="card-body p-6!">
      <div class="chart-header mb-4">
        <h5 class="chart-title">{{ title }}</h5>
        <slot name="actions" />
      </div>
      <div class="chart-body">
        <div class="chart-container">
          <canvas ref="chartCanvas"></canvas>
          <div v-if="activeItem" class="chart-centre" aria-hidden="true">
            <span class="chart-centre-label">{{ activeItem.label }}</span>
            <span class="chart-centre-value">{{ activeItem.percentage }}%</span>
          </div>
        </div>
        <etf-breakdown-legend
          :items="chartData"
          :active-index="activeIndex"
          @hover="focusSlice"
          @leave="clearSlice"
        />
      </div>
    </div>
  </div>
</template>
```

The readout is `aria-hidden` on purpose: it restates the label and percentage a screen reader already gets from the legend text, so announcing it again is duplication, not information.

Replace the plain `<script lang="ts">` block's registration line:

```ts
import { Chart, DoughnutController, ArcElement, Tooltip, Legend } from 'chart.js'

Chart.register(DoughnutController, ArcElement, Tooltip, Legend)
```

`ChartDataItem` below it is unchanged.

- [ ] **Step 3: Wire the centre readout**

In the `<script lang="ts" setup>` block, add the import, the state, and the hover handler, and change the chart config. The setup block becomes:

```ts
import { ref, computed, onMounted, watch, onBeforeUnmount } from 'vue'
import EtfBreakdownLegend from './etf-breakdown-legend.vue'

const props = defineProps<{
  title: string
  chartData: ChartDataItem[]
}>()

const chartCanvas = ref<HTMLCanvasElement | null>(null)
const activeIndex = ref(0)
let chart: Chart | null = null

const activeItem = computed(() => props.chartData[activeIndex.value] ?? props.chartData[0])

const focusSlice = (index: number) => {
  activeIndex.value = index
  if (!chart) return
  chart.setActiveElements([{ datasetIndex: 0, index }])
  chart.update('none')
}

const clearSlice = () => {
  activeIndex.value = 0
  if (!chart) return
  chart.setActiveElements([])
  chart.update('none')
}

const renderChart = () => {
  if (!chartCanvas.value || props.chartData.length === 0) return

  if (chart) {
    chart.destroy()
  }

  const colors = props.chartData.map(item => item.color)
  const hairline = getComputedStyle(document.documentElement)
    .getPropertyValue('--color-hairline-strong')
    .trim()

  chart = new Chart(chartCanvas.value, {
    type: 'doughnut',
    data: {
      labels: props.chartData.map(item => item.label),
      datasets: [
        {
          data: props.chartData.map(item => item.value),
          backgroundColor: colors,
          borderWidth: 1,
          borderColor: hairline,
          hoverBackgroundColor: colors,
          hoverBorderColor: hairline,
          spacing: 3,
          borderRadius: 4,
          hoverOffset: 6,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: true,
      animation: false,
      cutout: '64%',
      onHover: (_event, elements) => {
        activeIndex.value = elements.length > 0 ? elements[0].index : 0
      },
      plugins: {
        legend: {
          display: false,
        },
        tooltip: {
          callbacks: {
            label: context => {
              const label = context.label || ''
              const value = context.parsed || 0
              return `${label}: ${value.toFixed(2)}%`
            },
          },
        },
      },
    },
  })
}

const updateChartData = () => {
  activeIndex.value = 0
  if (!chart?.data?.datasets?.[0] || props.chartData.length === 0) {
    renderChart()
    return
  }
  const colors = props.chartData.map(item => item.color)
  chart.data.labels = props.chartData.map(item => item.label)
  chart.data.datasets[0].data = props.chartData.map(item => item.value)
  chart.data.datasets[0].backgroundColor = colors
  chart.data.datasets[0].hoverBackgroundColor = colors
  chart.update('none')
}
```

`hoverBackgroundColor` is refreshed alongside `backgroundColor` because this path now runs on every tab switch, and the three breakdowns have different lengths. Refreshing only one of them would leave the hover array short, and a hover past its end resolves to `undefined` — the same black arc Step 3 of Task 6 fixed, reintroduced through the back door.

`onMounted`, the `watch`, and `onBeforeUnmount` are unchanged. `borderWidth: 1` and the `hairline` colour stay exactly as Task 6 left them — `spacing` opens a gap between arcs and the stroke still bounds each one, so the boundary is carried twice, not moved.

**`spacing: 3` is structural, not decoration.** It is what makes the ring read as a set of discrete segments rather than as one continuous wheel, which is how the reference chart the human partner linked reads. It no longer carries slice separation — Task 6's palette does that with hue — so if Step 8 shows the gap reading as a hairline rather than as a gap, raise this number as a matter of form, not of legibility.

`colors` is hoisted into a local and shared by `backgroundColor` and `hoverBackgroundColor` deliberately. Task 6 landed on that shape so the two arrays cannot desync; Chart.js resolves per index and does not mutate the caller's array. Keep it.

- [ ] **Step 4: Restyle the card**

Replace the `<style scoped>` block. `.card`, `.chart-title` and the `@media` height override keep their Task 6 values; everything else is new, and every legend rule moves out to the component from Step 1:

```css
.card {
  border-radius: 0.5rem;
  overflow: hidden;
  border: 1px solid var(--color-hairline);
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}

.chart-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0;
}

.chart-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 1.5rem;
}

.chart-container {
  position: relative;
  height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-centre {
  position: absolute;
  inset: 22%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.125rem;
  pointer-events: none;
  text-align: center;
}

.chart-centre-label {
  max-width: 100%;
  font-size: 0.8125rem;
  line-height: 1.2;
  color: var(--color-ink-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chart-centre-value {
  font-size: 1.75rem;
  font-weight: 500;
  line-height: 1.1;
  color: var(--color-ink);
  font-variant-numeric: tabular-nums;
}

@media (min-width: 768px) {
  .chart-body {
    grid-template-columns: minmax(0, 20rem) minmax(0, 1fr);
    align-items: center;
  }
}

@media (max-width: 768px) {
  .chart-container {
    height: 250px;
  }
}
```

The value inherits `--font-sans` and states `tabular-nums` explicitly: it changes on every hover, and proportional figures make the centre of the donut jitter under the cursor.

- [ ] **Step 5: Collapse three cards into one**

In `ui/components/etf/etf-breakdown.vue`, replace the `charts-section` block:

```vue
    <div v-if="!isLoading && holdings.length > 0" class="charts-section mb-6">
      <etf-breakdown-chart title="Fund Breakdown" :chart-data="activeChartData">
        <template #actions>
          <div class="breakdown-tabs" role="group" aria-label="Breakdown dimension">
            <button
              v-for="tab in breakdownTabs"
              :key="tab.key"
              class="breakdown-tab"
              :class="{ active: activeTab === tab.key }"
              :aria-pressed="activeTab === tab.key"
              type="button"
              @click="activeTab = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>
        </template>
      </etf-breakdown-chart>
    </div>
```

Add to its `<script lang="ts" setup>`, beside the existing `sectorChartData` / `companyChartData` / `countryChartData` computeds:

```ts
const breakdownTabs = [
  { key: 'sectors', label: 'Sectors' },
  { key: 'companies', label: 'Top holdings' },
  { key: 'countries', label: 'Countries' },
] as const

type BreakdownTab = (typeof breakdownTabs)[number]['key']

const activeTab = ref<BreakdownTab>('sectors')

const activeChartData = computed(() => {
  if (activeTab.value === 'companies') return companyChartData.value
  if (activeTab.value === 'countries') return countryChartData.value
  return sectorChartData.value
})
```

`ref` and `computed` are already imported in this file; confirm before adding a duplicate import. The default tab is `sectors`, which is the dimension the leftmost card showed before — the page opens on the same content it opens on today.

Add to its `<style scoped>`, matching spec §1.4's `filter-chip` primitive so the tabs read as the same family as the ETF and platform chips above them:

```css
.breakdown-tabs {
  display: flex;
  gap: 0.25rem;
}

.breakdown-tab {
  padding: 0.3125rem 0.75rem;
  border: 1px solid transparent;
  border-radius: var(--radius-control);
  background: transparent;
  font-size: 0.8125rem;
  color: var(--color-ink-soft);
  cursor: pointer;
}

.breakdown-tab:hover {
  background: var(--color-surface-hover);
  color: var(--color-ink);
}

.breakdown-tab.active {
  border-color: var(--color-brass);
  background: var(--color-brass-wash);
  color: var(--color-brass);
}
```

State is carried by fill _and_ border, not colour alone. `brass` on `brass-wash` measures 4.71:1, which clears AA for this size.

- [ ] **Step 6: Update the two chart tests**

`ui/components/etf/etf-breakdown-chart-registration.test.ts` asserts the controller that is registered. Replace both test bodies and names:

```ts
it('should register the doughnut controller so the donut chart renders', () => {
  expect(() => Chart.registry.getController('doughnut')).not.toThrow()
})

it('should register the arc element used by donut segments', () => {
  expect(() => Chart.registry.getElement('arc')).not.toThrow()
})
```

`ui/components/etf/etf-breakdown-chart.test.ts` mocks `chart.js` with an object literal. Its factory exports `PieController`, which the component no longer imports — replace that key with `DoughnutController: vi.fn(),`. Vitest's mock factory throws on an import of a name the factory does not return, so leaving `PieController` in place is not harmless: the component's `DoughnutController` import fails and every test in the file errors.

Two tests added by Task 6's fix round already read the captured config through `vi.mocked(Chart).mock.calls[0][1]`, and the mock now returns `data: config.data` so the update path is reachable. Both keep passing against the donut config unchanged — do not rewrite them. But note what that mock does **not** carry: `setActiveElements`. `focusSlice` and `clearSlice` call it, so a test that fires `mouseenter` on a legend row throws `chart.setActiveElements is not a function`. If you add such a test, add `setActiveElements: vi.fn()` to the mock's return object first. Do not add it speculatively if you write no such test.

Then add one test to the `rendering` describe block, covering the readout the donut exists for:

```ts
it('should read out the largest slice in the centre of the donut', () => {
  const wrapper = mount(EtfBreakdownChart, {
    props: {
      title: 'Sector Allocation',
      chartData: mockChartData,
    },
  })

  expect(wrapper.find('.chart-centre-label').text()).toBe('Apple')
  expect(wrapper.find('.chart-centre-value').text()).toBe('25.50%')
})
```

- [ ] **Step 7: Run the suite**

```bash
npm test -- --run
```

Expected: PASS, 54 files. `etf-breakdown.test.ts:181` reaches for `findAllComponents(EtfBreakdownChart)[0]` and asserts the sector data reaches it — there is now exactly one chart component and it defaults to the sector dimension, so that assertion holds unchanged. If it fails, the default tab is wrong, not the test.

```bash
npm run lint-format
```

Expected: clean. It rewrites `ui/models/generated/domain-models.ts`; restore that file with `git checkout -- ui/models/generated/domain-models.ts` and never stage it.

- [ ] **Step 8: Look at it**

With `npm run dev:ui` running, open `http://localhost:61234/etf-breakdown` at 1440px and at 390px.

Confirm, and report what you see:

- One card, donut left and legend right at desktop, stacked at mobile.
- Visible gaps between segments with rounded ends, and a hairline edge on each.
- The hole reads the largest slice's name and percentage on load; moving the cursor over a segment or a legend row changes both the readout and which legend row is dark.
- All three tabs switch the chart, and the active one is brass on brass-wash.
- No horizontal scrollbar at 390px.

If the donut renders as a filled pie, `cutout` did not apply — report BLOCKED with the rendered `chart.options.cutout` rather than guessing at a fix.

- [ ] **Step 9: Commit**

```bash
git add ui/components/etf/etf-breakdown-chart.vue ui/components/etf/etf-breakdown-legend.vue ui/components/etf/etf-breakdown.vue ui/components/etf/etf-breakdown-chart-registration.test.ts ui/components/etf/etf-breakdown-chart.test.ts
git commit -m "Rebuild the ETF breakdown as one donut card"
```

---

### Task 8: Replace DESIGN.md

The spec is explicit that this file is **replaced**, not edited, so that Phase 2 onward has one authority describing the world that actually exists. The current file is 32KB describing the palette this phase just removed.

**Files:**

- Replace: `DESIGN.md`

**Interfaces:**

- Consumes: the token values from Task 3 and the type decisions from Task 4.
- Produces: the reference every later phase reads before touching a component.

- [ ] **Step 1: Read what is there now**

```bash
wc -l DESIGN.md && head -60 DESIGN.md
```

Note its section structure and any project-specific conventions worth carrying forward — the _structure_ may be reused, the _palette and type content_ must not be.

- [ ] **Step 2: Write the replacement**

Replace `DESIGN.md` wholesale. It must contain, at minimum:

- The complete token table from spec §1.1 — grounds, foreground and signal, washes, quantitative series — with the OKLCH value, the hex rendering, and the measured contrast against surface and paper for every token. Omit spec §1.1's `--color-cat-1..10` row: Deviation 4 replaced it with the Task 6 ramp and those tokens do not exist in `theme.css`.
- The six palette rules from spec §1.2 verbatim, including the `ink-faint` prohibition.
- The type scale from spec §1.3, with one correction: Deviation 6 removed the display face, so describe **one** face — Instrument Sans, body and headings alike — not spec §1.3's two. Record the `clamp()` values, the heading weight of 500, and the `tabular-nums` requirement on every figure. Do not name `--font-display`; it does not exist in `theme.css`.
- The material rules from spec §1.4: hairline-first, `--radius-control: 0.25rem`, warm-tinted shadows, the 2px brass nav rule.
- The chart palette: the sixteen OKLCH entries in `ui/constants/chart-colors.ts` — eight hues at 45° spacing starting from brass (74), run at `L 0.55 / C 0.09` for the first eight slices and `L 0.75 / C 0.07` for the last eight. Record why it is a hue wheel and not a lightness ramp: sector, country and holding are categories rather than ranks, and three single-hue ramps were rendered and rejected before this. Record `OTHERS_COLOR` as deliberately paler and greyer than every entry — the residual is often the largest single slice and must recede, not lead. Record that the pale half sits below 3:1 against paper by design and that what carries the information is the legend stating every label and value as full-contrast text — the `--color-hairline-strong` swatch stroke is a visual seam at 1.56:1, not the accessibility remedy.
- The ETF breakdown card from Task 7: one card with a segmented control over three dimensions, a `64%`-cutout donut with `spacing: 3` and `borderRadius: 4`, the `aria-hidden` centre readout that follows hover and rests on the largest slice, and the two-column legend with the value under the label. Record why the readout is hidden from assistive tech — the legend already states the same label and percentage as text — and that the segment gap does not replace the per-arc `hairline-strong` stroke, it sits alongside it.
- A note that Phase 1 retoned the legacy token names in place and that Phase 2 removes them as it migrates consumers, so both naming systems are live and neither is a fork.

Do not describe components that do not exist yet. Phase 2 adds them to this file as it builds them.

- [ ] **Step 3: Commit**

```bash
git add DESIGN.md
git commit -m "Replace DESIGN.md with the Statement world"
```

---

### Task 9: Re-record the baselines

Every one of the 41 baselines changes: colour and type move everywhere, structure moves nowhere. That expectation is the review criterion.

**Files:**

- Regenerate: `docs/superpowers/baseline/*.png` (41 files)

**Interfaces:**

- Consumes: everything above.
- Produces: the visual floor Phase 2 diffs against.

- [ ] **Step 1: Bring up the full environment**

```bash
npm run test:setup
```

The backend is required, not optional: `/diversification` and `/calculator` still read the live database rather than a stub, so their captures need a real API. This is a known harness gap, not something this task fixes.

- [ ] **Step 2: Confirm the gates pass before recording**

```bash
npm test -- --run
npm run visual -- palette.spec.ts
```

Expected: PASS. Never re-record baselines over a failing gate — the images would enshrine the failure.

- [ ] **Step 3: Re-record**

```bash
npm run visual:update
```

- [ ] **Step 4: Review every changed image**

```bash
git status --short docs/superpowers/baseline/
```

Expected: 41 modified, 0 added, 0 deleted. A new or deleted file means a test name changed, which this phase does not do — stop and find out why.

Open the images and check, at each of the three viewports:

- Backgrounds are warm paper, not white or `#fafafa`.
- Headings are set in Instrument Sans, on the fluid scale — larger than before, same face as the body.
- Gain figures are the deep green `#287b46`, not `#21c55d`.
- No element moved, wrapped differently, or changed height — except headings, which grow under the new scale.

`/diversification` and `/calculator` read live data, so their figures may differ for reasons unrelated to this phase. Judge those two on colour and type only.

- [ ] **Step 5: Tear down and commit**

```bash
npm run test:cleanup
git add docs/superpowers/baseline/
git commit -m "Re-record baselines for the Statement palette"
```

---

## Phase exit criteria

The spec notes that nine test files assert on style class names. Phase 1 changes token _values_ and no class names, so none of them should move — if one does, it is a regression from this phase, not expected churn.

All of these, together, before Phase 2 opens:

- [ ] `npm test -- --run` — 54 test files pass (52 existing plus `theme-contrast.test.ts` and `chart-colors.test.ts`).
- [ ] `npm run visual` — the full suite passes at all three viewports, including both new contrast tests on both routes.
- [ ] `npm run lint-format` — clean, discounting knip findings outside `ui/`.
- [ ] `npm run check-unused` — clean, same knip caveat.
- [ ] `npm run build` — clean, and the woff2 a reader actually downloads sits inside the spec's 40–60KB budget. Count the two latin faces; the latin-ext face bundled alongside them is gated behind a `unicode-range` this app never hits.
- [ ] `grep -rn "4361ee\|21c55d\|fafafa" ui/styles ui/index.html` returns nothing.
- [ ] `DESIGN.md` describes the palette that is actually in `theme.css`.
- [ ] 41 baselines re-recorded and visually reviewed.

## What Phase 2 inherits

Written down so the next plan does not rediscover it:

- Both naming systems are live. Statement names (`ink-soft`, `brass`, `surface-sunken`) and legacy names (`gray-600`, `signal-indigo`, `control-graphite`) resolve to the same values. Phase 2 migrates consumers to Statement names and deletes each legacy name **only** once its usage count reaches zero.
- The hardcoded hexes in the remaining component files are untouched. They are Phase 2's work. Tasks 6 and 7 cleared the `ui/components/etf/` files early, at the human partner's request.
- `ui/constants/chart-colors.ts` is still a second palette file, now holding the Statement ramp rather than Okabe-Ito. Phase 2 moves it into `theme.css` as `--color-cat-*`. Task 6 left the values there rather than in `theme.css` because Chart.js needs concrete colour strings and resolving sixteen custom properties at render time is more plumbing than the move is worth today.
- The contrast gate covers rendered text and the focus ring. Phase 2 extends it to control borders once form controls are restyled.
- `portfolio-chart.test.ts` still asserts four hardcoded chart hexes and will break in Phase 2 when those move to tokens.
