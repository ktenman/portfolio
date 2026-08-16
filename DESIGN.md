---
name: Portfolio
description: A private-bank statement for one real multi-broker investment portfolio.
colors:
  paper: '#fcfaf6'
  surface: '#ffffff'
  surface-sunken: '#f6f3ed'
  surface-hover: '#f9f6f2'
  surface-subtle: '#fcfaf6'
  surface-band: '#f6f3ed'
  hairline: '#e2dfda'
  hairline-strong: '#d2cfc8'
  control-border: '#d2cfc8'
  ink: '#241e1a'
  ink-soft: '#69625b'
  ink-faint: '#8c857f'
  ink-muted: '#69625b'
  body-secondary: '#69625b'
  brass: '#8d621f'
  brass-deep: '#784e00'
  brass-wash: '#f8eee2'
  gain: '#287b46'
  gain-deep: '#086733'
  gain-wash: '#e6f5e9'
  loss: '#b33834'
  loss-deep: '#9b1e20'
  loss-wash: '#ffebe8'
  loss-wash-deep: '#fbd3cf'
  notice: '#316ca5'
  notice-wash: '#e5f2ff'
  series-1: '#8d621f'
  series-2: '#287b46'
  series-3: '#316ca5'
  series-4: '#b33834'
  series-5: '#7e539c'
  series-6: '#42878b'
  signal-indigo: '#8d621f'
  signal-indigo-deep: '#784e00'
  control-graphite: '#69625b'
  control-graphite-deep: '#241e1a'
  status-success: '#287b46'
  status-danger: '#b33834'
  status-info: '#316ca5'
  status-warning: '#8d621f'
  gray-100: '#f9f6f2'
  gray-200: '#eae7e2'
  gray-300: '#e2dfda'
  gray-400: '#d2cfc8'
  gray-500: '#78706a'
  gray-600: '#69625b'
  gray-700: '#4e4640'
  gray-800: '#38312c'
  gray-900: '#241e1a'
  white: '#ffffff'
typography:
  sans: "'Geist Variable', system-ui, sans-serif"
  mono: "'Geist Mono Variable', ui-monospace, SFMono-Regular, Menlo, monospace"
  display:
    fontSize: 'clamp(2.5rem, 1.6rem + 3.6vw, 3.5rem)'
    fontWeight: 550
    lineHeight: 1.2
  title:
    fontSize: 'clamp(1.5rem, 1.3rem + 0.9vw, 1.875rem)'
    fontWeight: 550
    lineHeight: 1.2
  heading:
    fontSize: 'clamp(1.125rem, 1.05rem + 0.35vw, 1.25rem)'
    fontWeight: 550
    lineHeight: 1.2
  control:
    fontSize: '1rem'
    lineHeight: 1.5
  body:
    fontSize: '0.9375rem'
    fontWeight: 400
    lineHeight: 1.5
  control-sm:
    fontSize: '0.875rem'
  dense:
    fontSize: '0.8125rem'
    fontWeight: 400
  label:
    fontSize: '0.75rem'
    fontWeight: 600
    letterSpacing: '0.05em'
    textTransform: 'uppercase'
rounded:
  control: '0.25rem'
  container: '0.5rem'
  circle: '50%'
container:
  app: 'min(1350px, 91vw)'
shadows:
  card: '0 1px 2px oklch(0.24 0.012 60 / 0.05)'
  control: '0 1px 2px oklch(0.24 0.012 60 / 0.04)'
  lifted: '0 2px 8px oklch(0.24 0.012 60 / 0.08)'
  nav: '0 1px 0 oklch(0.24 0.012 60 / 0.06)'
  overlay: '0 0.5rem 1.5rem oklch(0.24 0.012 60 / 0.14)'
breakpoints:
  sm: '576px'
  md: '768px'
  lg: '992px'
  xl: '1200px'
  2xl: '1400px'
components:
  page-shell:
    class: 'mx-auto mt-4 w-full max-w-app px-3'
    maxWidth: '{container.app}'
  card:
    backgroundColor: '{colors.surface}'
    rounded: '{rounded.container}'
    padding: '1.25rem'
    shadow: '{shadows.card}'
  card-shell:
    backgroundColor: '{colors.surface}'
    borderColor: '{colors.hairline}'
    rounded: '{rounded.container}'
    padding: '0.75rem 1rem'
    shadow: '{shadows.card}'
  table-header-cell:
    textColor: '{colors.brass-deep}'
    backgroundColor: '{colors.surface-subtle}'
    typography: '{typography.label}'
    padding: '1rem 0.75rem'
    whiteSpace: 'nowrap'
  table-row-band:
    backgroundColor: '{colors.surface-band}'
  filter-chip:
    backgroundColor: '{colors.surface-sunken}'
    textColor: '{colors.ink-soft}'
    borderColor: 'transparent'
    rounded: '{rounded.container}'
    padding: '0.3125rem 0.6875rem'
  filter-chip-active:
    backgroundColor: '{colors.brass-wash}'
    textColor: '{colors.brass-deep}'
    borderColor: '{colors.brass}'
    boxShadow: 'inset 0 0 0 1px {colors.brass-wash}'
  button-primary:
    background: 'linear-gradient(135deg, {colors.brass} 0%, {colors.brass-deep} 100%)'
    textColor: '{colors.white}'
    rounded: '{rounded.container}'
  button-ghost-hover:
    backgroundColor: '{colors.brass-wash}'
    textColor: '{colors.brass-deep}'
  input:
    backgroundColor: '{colors.surface-subtle}'
    borderColor: '{colors.control-border}'
    rounded: '{rounded.container}'
  focus-ring:
    outline: '2px solid {colors.brass}'
    outlineOffset: '2px'
  nav-indicator:
    height: '0.125rem'
    backgroundColor: '{colors.brass}'
  toast:
    backgroundColor: '{colors.control-graphite}'
    textColor: '{colors.white}'
    rounded: '{rounded.control}'
    width: '350px'
---

# Design System: Portfolio

## Overview

**Creative North Star: "The Statement"**

A private-bank statement, not a trading terminal. Warm archival paper, deep ink, one metal accent. Nothing
glows; things are printed. The interface is a document you sit with, and the numbers are the only things on it
that were ever meant to be read closely.

The material is paper and ink. Grounds are warm at hue 85 — a barely-there ochre cast that keeps white cards
from reading as clinical — and every ink is warm at hue 60, so nothing on screen is the blue-grey that the
previous world defaulted to. Structure comes from hairlines, not shadow. Where an edge exists it is 1px and
warm; where elevation exists it is a tinted whisper derived from the ink itself, never from neutral black.

Colour is scarce and it is spent on meaning. There is exactly one accent, brass, and it marks three things:
where you are, what you are focused on, and what is currently filtering the data. Green and red mean gain and
loss, at matched lightness so a loss cannot look louder than an equal gain. Everything else is ink on paper.

Density is deliberately high and the type carries it: one face, weight 550 for headings rather than 600 or 700,
and `tabular-nums` inherited from `body` so every money column aligns. This is an interface built for someone
who already knows what every column means.

**Key characteristics:**

- Warm grounds (hue 85) and warm inks (hue 60); no neutral grey anywhere in the ramp
- Hairline-bordered, near-flat surfaces; shadow only where something genuinely floats
- One accent — brass — for navigation, focus, and active filters. Nothing else
- Gain and loss locked to the same lightness, so equal magnitudes carry equal weight
- Every colour in the product resolves to a token; components declare none, and the stylesheet layer holds
  raw hex in exactly one declaration
- Tables on desktop become label/value cards under 768px — the signature responsive move

## Colours

Every token is authored in OKLCH in the `@theme static` block of `ui/styles/theme.css`. The OKLCH column below
is transcribed from that file character for character; the hex column is its sRGB rendering, and the ratios are
WCAG 2.x contrast computed with the project's own contrast math in `ui/tests/contrast.ts` — the same code the
palette gate asserts against.

`ui/styles/components.css` is now a nine-line barrel of `@import`s, and the component layer it pulls in lives in
`ui/styles/components/`: `buttons.css`, `surfaces.css`, `forms.css`, `modals.css`, `feedback.css`,
`navigation.css`, `controls.css`, `mobile-cards.css`, `motion.css`. Citations below name the partial, never the
barrel. `--transition-fast` / `-base` / `-slow` / `-chip` and the `--z-*` scale are declared in a `:root` block
at the head of `base.css`, not in `@theme static`, because Tailwind has no namespace for them.

### Grounds

| token                     | oklch                   | hex       | on surface | on paper |
| ------------------------- | ----------------------- | --------- | ---------- | -------- |
| `--color-paper`           | `oklch(0.985 0.006 85)` | `#fcfaf6` | 1.04       | 1.00     |
| `--color-surface`         | `oklch(1 0 0)`          | `#ffffff` | 1.00       | 1.04     |
| `--color-surface-sunken`  | `oklch(0.965 0.008 85)` | `#f6f3ed` | 1.11       | 1.06     |
| `--color-surface-hover`   | `oklch(0.975 0.007 85)` | `#f9f6f2` | 1.07       | 1.03     |
| `--color-surface-subtle`  | `oklch(0.985 0.006 85)` | `#fcfaf6` | 1.04       | 1.00     |
| `--color-surface-band`    | `oklch(0.965 0.008 85)` | `#f6f3ed` | 1.11       | 1.06     |
| `--color-hairline`        | `oklch(0.905 0.008 85)` | `#e2dfda` | 1.33       | 1.27     |
| `--color-hairline-strong` | `oklch(0.855 0.01 85)`  | `#d2cfc8` | 1.56       | 1.49     |
| `--color-control-border`  | `oklch(0.855 0.01 85)`  | `#d2cfc8` | 1.56       | 1.49     |

`--color-paper` is the page ground, set on `body`. `--color-surface` is every card, table, chip, and dialog.
`--color-surface-subtle` shares paper's value and is the fill of table cells, form fields, and dropdown menus.
`--color-surface-band` is an **opaque warm band**, not a translucent ink overlay — it is what `.table-striped`
tints odd rows with, and the reason table banding no longer reads blue. `--color-control-border` is the same
value as `--color-hairline-strong`, named separately because a control's edge is a different decision from a
container's edge.

### Foreground and signal

| token                | oklch                   | hex       | on surface | on paper |
| -------------------- | ----------------------- | --------- | ---------- | -------- |
| `--color-ink`        | `oklch(0.24 0.012 60)`  | `#241e1a` | 16.48      | 15.79    |
| `--color-ink-soft`   | `oklch(0.5 0.014 60)`   | `#69625b` | 6.02       | 5.77     |
| `--color-ink-faint`  | `oklch(0.62 0.012 60)`  | `#8c857f` | 3.65       | 3.50     |
| `--color-brass`      | `oklch(0.53 0.098 74)`  | `#8d621f` | 5.37       | 5.15     |
| `--color-brass-deep` | `oklch(0.46 0.098 74)`  | `#784e00` | 7.26       | 6.95     |
| `--color-gain`       | `oklch(0.52 0.115 152)` | `#287b46` | 5.22       | 5.00     |
| `--color-gain-deep`  | `oklch(0.45 0.115 152)` | `#086733` | 7.04       | 6.75     |
| `--color-loss`       | `oklch(0.52 0.16 26)`   | `#b33834` | 5.98       | 5.72     |
| `--color-loss-deep`  | `oklch(0.45 0.16 26)`   | `#9b1e20` | 8.08       | 7.74     |
| `--color-notice`     | `oklch(0.52 0.11 250)`  | `#316ca5` | 5.49       | 5.26     |

Every one of these clears 4.5:1 as text on both grounds, and every one clears 4.5:1 under white text as a fill:
brass 5.37, brass-deep 7.26, gain 5.22, loss 5.98, notice 5.49, ink 16.48. That is what makes `text-white` on a
gain-filled control legal, which it was not in the previous world at 2.28:1.

`--color-ink-faint` is the one token in this group that is not body text. See palette rule 4.

### Washes

Badge fills, row tints, alert backgrounds, and the price-flash animation. Each carries its own ink at AA.

| token                    | oklch                    | hex       | on surface | on paper | own ink on it             |
| ------------------------ | ------------------------ | --------- | ---------- | -------- | ------------------------- |
| `--color-brass-wash`     | `oklch(0.955 0.02 74)`   | `#f8eee2` | 1.14       | 1.09     | 6.36 with `brass-deep`    |
| `--color-gain-wash`      | `oklch(0.955 0.022 152)` | `#e6f5e9` | 1.13       | 1.08     | 4.61 with `gain`          |
| `--color-loss-wash`      | `oklch(0.955 0.022 26)`  | `#ffebe8` | 1.15       | 1.10     | 7.04 with `loss-deep`     |
| `--color-loss-wash-deep` | `oklch(0.9 0.045 26)`    | `#fbd3cf` | 1.37       | 1.31     | border only, never a fill |
| `--color-notice-wash`    | `oklch(0.955 0.022 250)` | `#e5f2ff` | 1.14       | 1.09     | 4.82 with `notice`        |

`--color-brass-wash` is the busiest of the four, and its consumer map has grown to fifteen declarations. In the
stylesheet layer: the active filter chip's fill and its `inset` ring (`controls.css:106,108,114`), the ghost
button's hover, the add-new button's hover, and one more button hover (`buttons.css:118,143,152`), the warning
alert's background (`feedback.css:23`), and — new since the last revision — the native customizable `<select>`'s
option hover and `:checked` fill (`forms.css:98,103`). In components: the diversification calculator's
refreshing status pill (`diversification-calculator.vue:462`), the allocation table's active display-mode
segment (`allocation-table.vue:853`), the instruments toggle's focus ring (`instruments-view.vue:300`), the logo
modal's selected thumbnail (`logo-replacement-modal.vue:186`), and the ETF breakdown's active dimension tab plus
its search clear-button hover (`etf-breakdown.vue:467,539`).

`--color-loss-wash-deep` is a border colour only: the danger alert's (`feedback.css:12`) and the
destructive-button hovers — `.remove-btn:hover` at `allocation-card.vue:312` and `allocation-table.vue:876`,
`.action-btn.danger:hover` at `allocation-table.vue:738`.

### Quantitative series

Six hue-spread tokens for series where a quantity is measured rather than a category is named. All clear 3:1
on surface for WCAG 1.4.11, which the theme contrast test asserts. No component consumes them yet — the line
charts currently draw from `CHART_COLORS` — so treat this group as the declared, gated home for series colour
rather than as a description of what is on screen today.

| token              | oklch                   | hex       | on surface | on paper |
| ------------------ | ----------------------- | --------- | ---------- | -------- |
| `--color-series-1` | `oklch(0.53 0.098 74)`  | `#8d621f` | 5.37       | 5.15     |
| `--color-series-2` | `oklch(0.52 0.115 152)` | `#287b46` | 5.22       | 5.00     |
| `--color-series-3` | `oklch(0.52 0.11 250)`  | `#316ca5` | 5.49       | 5.26     |
| `--color-series-4` | `oklch(0.52 0.16 26)`   | `#b33834` | 5.98       | 5.72     |
| `--color-series-5` | `oklch(0.52 0.12 310)`  | `#7e539c` | 5.83       | 5.58     |
| `--color-series-6` | `oklch(0.58 0.07 200)`  | `#42878b` | 4.15       | 3.97     |

Series 1 through 4 are the brass, gain, notice, and loss values exactly. That is deliberate: a portfolio-value
line and a profit figure should not be two different greens.

### Legacy aliases

These names survive from the previous world. Phase 1 retoned them in place rather than deleting them, so that
no consumer had to change in the same commit as the palette. They are aliases, not a second palette.

| token                           | oklch                   | hex       | resolves to  |
| ------------------------------- | ----------------------- | --------- | ------------ |
| `--color-signal-indigo`         | `oklch(0.53 0.098 74)`  | `#8d621f` | `brass`      |
| `--color-signal-indigo-deep`    | `oklch(0.46 0.098 74)`  | `#784e00` | `brass-deep` |
| `--color-control-graphite`      | `oklch(0.5 0.014 60)`   | `#69625b` | `ink-soft`   |
| `--color-control-graphite-deep` | `oklch(0.24 0.012 60)`  | `#241e1a` | `ink`        |
| `--color-ink-muted`             | `oklch(0.5 0.014 60)`   | `#69625b` | `ink-soft`   |
| `--color-body-secondary`        | `oklch(0.5 0.014 60)`   | `#69625b` | `ink-soft`   |
| `--color-status-success`        | `oklch(0.52 0.115 152)` | `#287b46` | `gain`       |
| `--color-status-danger`         | `oklch(0.52 0.16 26)`   | `#b33834` | `loss`       |
| `--color-status-info`           | `oklch(0.52 0.11 250)`  | `#316ca5` | `notice`     |
| `--color-status-warning`        | `oklch(0.53 0.098 74)`  | `#8d621f` | `brass`      |

There is no `--color-warning`. The doc asserted one in an earlier revision and the code never had it; the
warning alert reads `--color-status-warning`, which resolves to brass. Do not add one back to make the alias
table look symmetrical.

**`--color-signal-indigo` and `--color-signal-indigo-deep` are not a second accent.** They hold the brass
values and exist only so Tailwind keeps generating the utilities their remaining consumers use. Phase 2 deletes
them once every consumer migrates, so scope from the real count, not from a sample: **sixteen references across
ten files.** In the stylesheet layer — the primary button gradient, both stops in both directions
(`buttons.css:58,59,66,67`), the form-control focus border (`forms.css:26`), and the checkbox `accent-color`
(`forms.css:129`). In components — `nav-bar.vue:104,117` (active link colour and the indicator fill),
`app.vue:66` and `loading-spinner.vue:56` (spinner `border-top-color`), `data-table.vue:349` (the sort-indicator
colour), `instrument-table.vue:477` (the XIRR value link's hover — not a sort indicator), `config-dialog.vue:242`
(the file drop zone's hover border), and `logo-replacement-modal.vue:185` (the selected thumbnail's border —
that file has no drag-and-drop). The only uses of the generated Tailwind utility rather than the custom property
are the two `text-signal-indigo` spinners in `diversification-calculator.vue:27,77`. Nothing new may reference
them.

Retoning the aliases in place also closed the old contrast failures without touching a component:
`status-warning` under white toast text moved from 1.63:1 to 5.37:1, and `gray-500` from 2.07:1 to 4.87:1.

### Neutral ramp

The nine-step ramp is retoned onto the same warm grounds and inks. It backs the parts of the component layer
that predate the semantic names.

| token              | oklch                   | hex       | on surface | on paper |
| ------------------ | ----------------------- | --------- | ---------- | -------- |
| `--color-gray-100` | `oklch(0.975 0.007 85)` | `#f9f6f2` | 1.07       | 1.03     |
| `--color-gray-200` | `oklch(0.93 0.008 85)`  | `#eae7e2` | 1.23       | 1.18     |
| `--color-gray-300` | `oklch(0.905 0.008 85)` | `#e2dfda` | 1.33       | 1.27     |
| `--color-gray-400` | `oklch(0.855 0.01 85)`  | `#d2cfc8` | 1.56       | 1.49     |
| `--color-gray-500` | `oklch(0.55 0.014 60)`  | `#78706a` | 4.87       | 4.66     |
| `--color-gray-600` | `oklch(0.5 0.014 60)`   | `#69625b` | 6.02       | 5.77     |
| `--color-gray-700` | `oklch(0.4 0.014 60)`   | `#4e4640` | 9.24       | 8.85     |
| `--color-gray-800` | `oklch(0.32 0.013 60)`  | `#38312c` | 12.72      | 12.18    |
| `--color-gray-900` | `oklch(0.24 0.012 60)`  | `#241e1a` | 16.48      | 15.79    |

Prefer a semantic token when one fits; reach into the ramp only where the component layer already does.

### The one literal colour

`--color-white: #ffffff` (`theme.css:17`) is the **only** raw hex declaration in the entire stylesheet layer,
and it is structural rather than expressive: white is text on a filled control. There is no `--color-black`;
the neutral `rgb(0 0 0 / α)` tints below are written inline and have never had a token behind them.
`ui/components/**/*.vue` contains **zero** raw hex colours, and so do all nine partials under
`ui/styles/components/`.

Three exceptions exist and are deliberate:

- `ui/index.html` hardcodes `#fcfaf6`, `#e2dfda`, and `#8d621f` in the pre-mount loader `<style>` — four
  declarations of three distinct values, at `:13,24,29,30`. It paints before any stylesheet has loaded, so a
  `var()` there would resolve to nothing and the first frame would flash the wrong world. Those three literals
  are paper, hairline, and brass, and they must be updated by hand if those tokens ever move.
- `ui/public/favicon.svg` sets a `P` in `#8d621f` on no enclosing shape. An SVG file has no access to the theme.
- The `.form-select` chevron (`forms.css:37`) carries a URL-encoded `%23241e1a` stroke inside its data URI.
  A data URI cannot read a `var()`, and the `mask` trick `.btn-close` uses would mask away the field's own
  fill, so the literal is unavoidable — but it is `--color-ink` rendered to hex, not a leftover. It is the one
  colour in the stylesheet that a hex grep does not find, and it must be updated by hand if ink ever moves.

Sixteen neutral black tints also remain — ten in the component partials (`surfaces.css:9`, `modals.css:30`,
`mobile-cards.css:7`, `motion.css:14,15,16`, `navigation.css:20`, `buttons.css:40,98,125`) and six in
components (`calculator.vue:191`, `modal-shell.vue:119`, `data-table.vue:318`, `instruments-view.vue:288`,
`etf-breakdown-table.vue:2`, `etf-breakdown-chart.vue:2`) — carrying hover films, the button press inset, the
card and modal borders, the skeleton gradient, the nav link's resting colour, the modal backdrop, and a few
shadows. They are written three ways, and a grep must cover all three: twelve use `rgb(0 0 0 / α)`; two use the
comma form `rgba(0, 0, 0, α)` (`modal-shell.vue`, `instruments-view.vue`); and two sit inside Tailwind arbitrary
values as `rgb(0_0_0/0.075)`, where the class syntax forbids spaces (`etf-breakdown-chart.vue:2`,
`etf-breakdown-table.vue:2`). They are achromatic and therefore harmless on a warm ground, but they are not
tokens; Phase 2 removes them.

### Palette rules

These are enforceable constraints, not guidance. They are reproduced from the design spec verbatim.

1. **Gain and loss are locked to `L = 0.520`.** Equal magnitudes carry equal visual weight. Neither may be
   re-toned independently.
2. **Brass is the only brand accent.** Nav active state, focus ring, primary action, active filter chip.
   Nothing else.
3. **Gain and loss are reserved for signed monetary movement.** They never decorate, never indicate
   non-financial success or failure. The shipped code diverges here, and the divergence is inherited, not
   chosen: `--color-loss` also carries destructive intent (`.dialog-btn.danger` at `controls.css:44,45`,
   `.btn-danger` at `buttons.css:86,90`, `.action-btn.danger:hover` at `allocation-table.vue:739`,
   `.remove-btn:hover` at `allocation-card.vue:313` and `allocation-table.vue:877`) and validation error
   (`.is-invalid` at `forms.css:115`, `.invalid-feedback` at `forms.css:122`, `.total-value.invalid` at
   `allocation-table.vue:692`). **Eight sites, all `loss`, none `gain`** — up from six, so the divergence is
   growing rather than shrinking. Either the rule loses "never indicate non-financial failure" or those sites
   move to a dedicated danger token; do not resolve it by restating the rule more loosely elsewhere.
4. **`ink-faint` is not body text.** At 3.65:1 it satisfies 1.4.11 non-text and AA large text (≥24px, or
   ≥18.66px bold) only. Its legitimate uses are chart axis ticks, disabled control glyphs, and decorative
   rules. **Every piece of small text — labels, captions, the build hash, stat-card labels, table meta — uses
   `ink-soft` at 6.02:1.** This rule is what prevents the current `gray-500` mistake recurring under a new name.
   Today it has four consumers and none of them is one of the sanctioned three: the config dialog's empty-state
   glyph (`config-dialog.vue:253`), two receded figures in `etf-breakdown-table.vue:404,461`, and the ETF
   breakdown's resting search icon (`etf-breakdown.vue:490`). All four are large or non-text, so they pass — but
   the rule's own examples describe an intent, not the current map. The decorative rule it names is `hr`, which
   draws in `hairline-strong` at 25% opacity (`base.css:91`).
5. **State layers use `color-mix()`** against the base token. Hover and active states are never a second
   hardcoded colour. Like `--color-series-*`, this is declared and gated, and it now has exactly one consumer —
   which is not a state layer: the summary chart's loading veil, `color-mix(in srgb, var(--color-surface) 72%,
transparent)` at `portfolio-summary.vue:275`. Every hover in the app still resolves to a second token —
   `.btn-ghost:hover` and `.btn-add-new:hover` both swap to `brass-wash` (`buttons.css:118`, `:143`). Phase 2 is
   what makes the rule true. The veil is nonetheless the precedent to copy: mix against a token, never against a
   literal.
6. **No component declares a colour.** Every value resolves to a token.

## Typography

**One face.** `--font-sans: 'Geist Variable', system-ui, sans-serif`, declared once and set on
`body`, so everything inherits it — including the toast layer, which teleports out of `#app` and would
otherwise render in a different stack than the app behind it. It ships self-hosted via
`@fontsource-variable/geist` 5.3.0, imported as `wght.css` in `ui/main.ts`.

Geist is here because the owner asked for Lightyear's typography. Lightyear sets Season Sans VF
(`seasonVFFont`), a Displaay commercial licence that cannot be reused; Geist (Vercel, OFL) is the closest
free grotesque and carries a continuous 100–900 `wght` axis, which is what the rest of this section
depends on. What was actually copied from Lightyear is not the outlines but the weight discipline —
measured off `lightyear.com/en/etf/QDVE:XETRA`, their h1 is 24px/550, their h2 20px/565, and their 48px
price is 550 with `-0.48px` tracking. Nothing there is 400 and nothing is 700.

There is no display face. A serif was specified and built, then removed once it was seen rendering a euro
figure; body and headings are the same typeface, and hierarchy is carried by size, weight, and colour rather
than by a change of voice. Do not reintroduce a display face without rendering it first.

**Mono** is `--font-mono: 'Geist Mono Variable', ui-monospace, SFMono-Regular, Menlo, monospace`, and it is no
longer a stack of system fallbacks: `@fontsource-variable/geist-mono` 5.3.0 ships self-hosted alongside the
text face and is imported next to it in `ui/main.ts`. The token has exactly one consumer — the diversification
config editor, where Monaco is handed `fontFamily: 'var(--font-mono)'` (`config-dialog.vue:119`). Monaco writes
that through to an inline style, so the `var()` does resolve; the literal copy of the stack that used to sit
there is gone.

Geist Mono is **not** a second voice, and it is the one exception the "one face" rule tolerates. It is the
monospaced sibling of the same superfamily, drawn to the same skeleton, and it appears on exactly one surface
that is a code editor. Nothing outside that editor may reference `--font-mono`; a monospaced figure in a table
would break `tabular-nums` alignment against the text face beside it.

### The scale

Fluid via `clamp()`, so there are no per-breakpoint font-size overrides to keep in sync.

| token            | value                                        | applied to     |
| ---------------- | -------------------------------------------- | -------------- |
| `--text-display` | `clamp(2.5rem, 1.6rem + 3.6vw, 3.5rem)`      | `h1`           |
| `--text-title`   | `clamp(1.5rem, 1.3rem + 0.9vw, 1.875rem)`    | `h2`           |
| `--text-heading` | `clamp(1.125rem, 1.05rem + 0.35vw, 1.25rem)` | `h3`           |
| `--text-control` | `1rem`                                       | controls       |
| `--text-base`    | `0.9375rem`                                  | `h6`           |
| `--text-sm`      | `0.875rem`                                   | small controls |
| `--text-2xs`     | `0.8125rem`                                  | dense text     |

`--text-control` is the resting size of an interactive control — `.btn`, `.form-control`, `.form-select`,
`.input-group-text`, `.dropdown-menu`. `--text-sm` is their `-sm` variant, plus `.toast` and `.dialog-btn`.
Both are one step off `--text-base` in opposite directions and exist because the control layer was drawn to
Bootstrap's sizing before this system replaced it; they are documented rather than migrated, since collapsing
them onto `--text-base` would resize every button and select in the app.

`--text-sm` restates Tailwind's default value, so the `text-sm` utility is unchanged. `--text-control` does
not collide: Tailwind's `1rem` step is `text-base`, which this system overrides to `0.9375rem`.

`h4` is `1.125rem` and `h5` is `1rem`, both literal. The rest of the numeric ramp is Tailwind's default,
inherited rather than overridden; only `--color-*` is reset to `initial` in `@theme static`.

### Rules of setting

**Headings are weight 550.** `h1`–`h6` share `font-weight: 550`, `line-height: 1.2`, `margin-bottom: 0.5rem`,
and `text-wrap: balance`. Not 600, not bold. In a document made of tables, a 600 heading over a 600 figure
flattens the hierarchy it was meant to create. 550 is not a rounding artefact of 500: it is Lightyear's
heading weight to the number, reachable only because Geist's `wght` axis is continuous, and it is the half
step that keeps a heading distinct from the 600 label gesture beneath it. `h1` additionally carries
`letter-spacing: -0.01em`, matching the `-0.48px` Lightyear sets on its 48px display figure; the title and
heading tiers stay at normal tracking, as Lightyear's 24px h1 does.

**Display figures are weight 550, not 400.** `.stat-value` (now in the shared `stat-card.vue:25`) and
`.chart-centre-value` (`etf-breakdown-chart.vue`) are the two places a number is set at `--text-title`, and
both sit at 550. They were 400 until the Lightyear comparison, where a 30px total at 400 read as thin
against the same figure at 550 — the single most visible difference between the two interfaces once the
typeface matched. A number large enough to be the point of its card is not body text. The summary chart's
range-change readout follows the same rule one step down: `range-change-header.vue:26` sets `--text-control` at
550, so the figure floating over the chart carries the same weight as a stat-card value without competing
with the title above it.

**Every figure is `tabular-nums`.** `font-variant-numeric: tabular-nums` is set once, on `body`, and inherits
everywhere. That is exactly how it should stay. Any component that introduces its own `font-variant-numeric`,
or a font stack without tabular figures, breaks the alignment of every right-aligned money column beneath it
and makes digits jitter during the price-flash animation.

**The label gesture.** Uppercase, weight 600, `0.05em` letter-spacing. It is the system's most recognizable
typographic move and it appears on every table header and every mobile card field name. The two differ in size
and colour: table headers are `0.75rem` in `--color-brass-deep` (`surfaces.css:42`), mobile card labels
`0.8125rem` in `--color-gray-700` (`mobile-cards.css:29`). The card label is the larger of the two because it
stands alone beside its value rather than at the head of an aligned column.

Those two are the gesture. Twelve uppercase label rules are live in total, so **ten of them are variants**, in
five distinct specs. The full census, because a sample of it has been wrong twice:

| spec                            | sites                                                                                             |
| ------------------------------- | ------------------------------------------------------------------------------------------------- |
| `0.75rem` / 600 / `0.05em`      | **the gesture** — `surfaces.css:42` (brass-deep), `mobile-cards.css:29` at `0.8125rem` (gray-700) |
| `0.75rem` / 500 / `0.05em`      | `stat-card.vue:16`, `transactions-view.vue:234`, `breakdown-card.vue:39` — all `ink-muted`        |
| `0.75rem` / 500 / `0.5px`       | `calculator.vue:151` (ink-soft), `allocation-table.vue:673` (gray-600)                            |
| `0.6875rem` / unset / `0.025em` | `allocation-card.vue:242`, `transaction-table.vue:300`, `instrument-table.vue:630` — all gray-600 |
| `0.6875rem` / 500 / `0.05em`    | `instrument-table.vue:529` (gray-600)                                                             |
| `0.875rem` / 700 / `0.025em`    | `instrument-table.vue:454` (gray-800) — the table footer's totals label                           |

A thirteenth label is missing from that table because it is not a rule: `currency-split-card.vue:7` sets the
same `0.75rem` / 500 / `0.05em` spec in Tailwind utilities — `text-xs font-medium tracking-wider text-gray-600
uppercase` — so a `text-transform: uppercase` grep does not find it at all. Its `gray-600` and the row above's
`ink-muted` resolve to the same `oklch(0.5 0.014 60)`, so it is a naming variant rather than a visual one.

`0.5px` is not `0.05em` — at `0.75rem` it is `0.0417em`. The three `0.75rem / 500 / 0.05em` rules carry the same
six declarations as the shared `stat-card.vue`, which is the consolidation target: extracting that component
already retired the copies that used to live in `etf-breakdown-stats.vue` and `diversification-stats.vue`, and
`transactions-view.vue` and `breakdown-card.vue` are the two that can still fold into it — `transactions-view.vue`
for free, since only its declaration order differs, and `breakdown-card.vue` at the cost of a `margin-bottom` that
is `0.75rem` there and `0.25rem` in the shared component. Normalizing the rest is a Phase 2 task with a
visual-baseline cost, not a drive-by; until then, do not cite "the label gesture" as if the code speaks with one
voice.

## Layout

**Every top-level view uses the same shell**: `class="mx-auto mt-4 w-full max-w-app px-3"`, where `max-w-app`
resolves to `--container-app: min(1350px, 91vw)`. All five route components and `crud-layout` carry it
verbatim; the calculator adds `pb-20 md:pb-0` for its mobile action bar and nothing else. The nav bar and the
app's `<main>` wrapper use the same container without the top margin. There is no full-bleed surface, so a
4.5% gutter holds on each side until the viewport passes roughly 1483px and the 1350px cap takes over.

Spacing runs on Tailwind's `0.25rem` base; no spacing token is overridden. A set of pixel-derived values
(`0.125rem`, `0.3125rem`, `0.625rem`, `0.9375rem` — 2px, 5px, 10px, 15px) has leaked in from direct pixel
thinking; prefer the named scale.

Breakpoints are theme tokens — `--breakpoint-sm` `576px`, `md` `768px`, `lg` `992px`, `xl` `1200px`, `2xl`
`1400px` — driving Tailwind's `sm:`–`2xl:` variants. Layout genuinely changes in three places:

- **≥992px**: the navigation becomes sticky (`top: 0`, `z-index: var(--z-sticky)`) and picks up `--shadow-nav`.
- **768–844px**: `.btn.btn-ghost.btn-sm` becomes a centred inline-flex at `0.375rem 0.5rem`, keeping action
  rows from wrapping in the narrow tablet band.
- **≤768px**: the signature move — every data table is hidden and replaced by a stack of mobile cards, and tap
  targets grow to `min-height: 44px`. A further tier at 389–767px tightens card padding and value size for
  small phones.

Ad-hoc breakpoints exist at 389, 480, 575, 666, 767.98, 769, 844, and 1024px, plus two `max-height: 500px`
rules, and a landscape-phone rule under 767px swaps the mobile cards back to the desktop table because there is
width for it. These are real and load-bearing, but new work should reach for the token scale first.

**The Two-Speeds Rule.** Every data surface ships two renderings of the same truth: a dense desktop table and a
mobile label/value card stack. Neither is a fallback. If a new column is added to the table and not to the
card, the phone view has silently lost information.

## Material

Hairline-first, shadow-second — printed paper does not float.

**Radii.** `--radius-control: 0.25rem` for filter chips, small and close buttons, dialog buttons, badges,
alerts, toasts, and small input-group addons; `--radius-container: 0.5rem` for full-size buttons, cards, table
wrappers, inputs and selects, modals, dropdown menus, and the base skeleton class. The control radius tightened
from `0.375rem` in this phase: crisper edges read as printed rather than as app chrome. A `50%` produces the
handful of true circles — spinners, the legend swatch, the flag glyphs. A `0.75rem` one-off survives on
`.mobile-card` (`mobile-cards.css:6`), but `data-table.vue:206` overrides it back to `--radius-container` for
every card the shared table renders — which is all of them today, so the one-off is currently unreachable.
Treat it as debt, not as scale. Two skeleton variants in `skeleton-loader.vue:80,118` override the base to
`--radius-control`, and being unlayered scoped styles they win — so a skeleton cell is not reliably the
container radius.

**Borders are 1px, always.** The table header's old 2px rule is gone; header and body cells now share a single
`--color-hairline-strong` bottom border. Generic cards take a `rgb(0 0 0 / 0.05)` hairline, data containers
take `--color-hairline-strong`, and the `.card-shell` tiles take `--color-hairline`. Three treatments where
two would do; the third is worth collapsing.

**Shadows are warm-tinted**, derived from `oklch(0.24 0.012 60 / α)` — the ink itself, not neutral black. All
five are in real use and the heaviest is the toast at 14%.

| token              | value                                         | used for                                             |
| ------------------ | --------------------------------------------- | ---------------------------------------------------- |
| `--shadow-card`    | `0 1px 2px oklch(0.24 0.012 60 / 0.05)`       | resting cards, desktop table wrapper                 |
| `--shadow-control` | `0 1px 2px oklch(0.24 0.012 60 / 0.04)`       | buttons and ghost controls                           |
| `--shadow-lifted`  | `0 2px 8px oklch(0.24 0.012 60 / 0.08)`       | dropdown menu, mobile card hover, ETF breakdown rows |
| `--shadow-nav`     | `0 1px 0 oklch(0.24 0.012 60 / 0.06)`         | sticky navbar at ≥992px                              |
| `--shadow-overlay` | `0 0.5rem 1.5rem oklch(0.24 0.012 60 / 0.14)` | toasts, the native select's picker                   |

`--shadow-lifted` has four consumers, not two: `navigation.css:53` (the dropdown menu),
`data-table.vue:211` (the mobile card's hover), and `etf-breakdown-table.vue:436,474`. `--shadow-overlay` has
two: the toast (`feedback.css:109`) and the customizable `<select>` picker (`forms.css:68`). Both extra pairs
are consistent with the tokens' stated roles — record them so the next reader does not conclude a component
invented its own elevation.

`--shadow-nav` is a hairline in shadow's clothing: a 1px rule at zero blur, so the sticky bar reads as an edge
rather than as a floating panel. There is no glass, no blur, and no backdrop filter anywhere; the modal
backdrop is the native `<dialog>` element's own.

**The active nav is a 2px brass rule**, not a filled pill. `.nav-indicator` is `0.125rem` tall, full width,
brass, and animates in with `transform: scaleX(0 → 1)` over 300ms; hover previews the same indicator. The
active link additionally goes brass and bold.

**Focus is always a brass outline**: `outline: 2px solid var(--color-brass)`, `outline-offset: 2px`, on every
link, button, input, select, and textarea. Never a glow, and never suppressed on anything tabbable.

**The select's dropdown is the browser's, restyled — not a rebuilt one.** `forms.css:54–111` opts into the
native customizable select behind `@supports (appearance: base-select)`, so the whole block is inert on engines
that do not have it and the field falls back to the chevron data URI above it. Inside, `::picker(select)` takes
`--color-surface`, a `--color-hairline-strong` hairline, `--radius-container`, and `--shadow-overlay`;
`::picker-icon` is hidden because the field draws its own chevron; `option` gets `--radius-control` and
`0.375rem 0.5rem`; `option:hover`, `option:focus`, and `option:checked` all take `--color-brass-wash` with
`--color-brass-deep` text, with `:checked` additionally at weight 550; and `option::checkmark` is brass. Opening
and closing are a 150ms fade and a `0.25rem` translate, with `display` and `overlay` transitioned
`allow-discrete` and the entry state declared in `@starting-style` — the only way to animate a top-layer element
in and out without JavaScript.

This is the pattern to reach for whenever a control needs a popup: **use the platform's element and style its
parts**, so keyboard behaviour, typeahead, scroll containment, and the top layer come from the browser rather
than from a component that has to reimplement all four. No custom listbox exists in this codebase and none
should be added.

**Table headers are quiet.** `.table thead th` is uppercase `0.75rem` / weight 600 / `0.05em` letter-spacing in
`--color-brass-deep`, with `white-space: nowrap` and **no background fill** — it inherits `--color-surface-subtle`
from the shared `.table th, .table td` rule. The brass wash it used to carry was removed because it collided
with the active filter chips directly above it. The chips are the brass-wash element on a data surface; the
header is brass text on the cell fill.

**Row tracking is carried by banding, not hover.** `.table-striped` tints odd rows with `--color-surface-band`,
an opaque warm band at 1.11:1 against surface. Rows have no hover state — they are not clickable, and a hover
tint on top of a band reads as a selection. One surface still breaks this: `etf-breakdown-table.vue:528` tints
`tbody tr:hover` with `--color-surface-hover`. It is debt, not precedent.

**Filter chips are the brass-wash element.** They are `.platform-btn` and `.etf-btn` in the source, styled as
one rule (`controls.css:74`). At rest the chip is **recessed, not outlined**: `--color-surface-sunken` fill, a
`1px solid transparent` border, `--color-ink-soft` text, `0.75rem` / weight 500, `0.3125rem 0.6875rem` padding,
`--radius-container`, on a fast `120ms` transition (`--transition-chip`). Hover is where the border appears —
`--color-hairline-strong` over `--color-surface-hover`, text to `--color-ink` — so the chip rises out of the
page rather than filling in. Active: `--color-brass-wash` fill, `--color-brass` border, `--color-brass-deep`
text at 6.36:1, plus an `inset 0 0 0 1px var(--color-brass-wash)` ring that thickens the edge to two effective
pixels without shifting layout. State is carried by fill _and_ border _and_ that ring, never by colour alone.
Press adds `scale(0.96)`. The transparent resting border is load-bearing: it reserves the hover border's box so
nothing reflows on hover, and removing it would make every chip row jump by 2px.

**The active-segment gesture now spans four surfaces**, and all four are the same three declarations —
`--color-brass-wash` fill, `--color-brass` border, `--color-brass-deep` text. Beyond the filter chips
(`controls.css:103`) it drives the ETF breakdown's dimension tabs (`etf-breakdown.vue:465`), the allocation
table's display-mode toggle (`allocation-table.vue:849`), and the summary chart's time-range row, which reuses
`.platform-btn` verbatim rather than restyling it (`chart-range-filter.vue`). Only the resting states differ:
tabs rest transparent so the row does not gain three boxes, the display-mode toggle rests sunken with a real
hairline and joined radii, and the range row inherits the chip exactly. A fifth surface that needs "selected"
should reach for one of these four, not invent a sixth resting treatment.

**The value flash** is the one piece of ambient motion. When a price or portfolio value changes, its cell
animates `pulse-increase` or `pulse-decrease` — a 3s ease-in-out background wash peaking at `--color-gain-wash`
or `--color-loss-wash` at the midpoint and returning to transparent. It is deliberately exempted from
`prefers-reduced-motion`, alongside the spinners, because it is information rather than decoration. It is mixed
from the exact semantic washes, never from an approximation of them.

## Charts

### The categorical palette

`ui/constants/chart-colors.ts` holds two palettes. `CHART_COLORS` is a sixteen-entry hue wheel: eight hues at
45° spacing starting from brass at 74, run twice — once at `L 0.55 / C 0.09` and once at `L 0.75 / C 0.07`.
It serves the line and bar charts. The breakdown donut has its own, `DONUT_COLORS`, described below.

| index | oklch                  | hex       | on surface | on paper |
| ----- | ---------------------- | --------- | ---------- | -------- |
| 0     | `oklch(0.55 0.09 74)`  | `#91692f` | 4.93       | 4.72     |
| 1     | `oklch(0.55 0.09 119)` | `#6c7939` | 4.75       | 4.55     |
| 2     | `oklch(0.55 0.09 164)` | `#368263` | 4.63       | 4.44     |
| 3     | `oklch(0.55 0.09 209)` | `#16808e` | 4.68       | 4.48     |
| 4     | `oklch(0.55 0.09 254)` | `#4b74a5` | 4.85       | 4.64     |
| 5     | `oklch(0.55 0.09 299)` | `#79659f` | 5.02       | 4.81     |
| 6     | `oklch(0.55 0.09 344)` | `#965b7e` | 5.10       | 4.88     |
| 7     | `oklch(0.55 0.09 29)`  | `#9f5c53` | 5.06       | 4.85     |
| 8     | `oklch(0.75 0.07 74)`  | `#c9a87c` | 2.24       | 2.15     |
| 9     | `oklch(0.75 0.07 119)` | `#a9b582` | 2.19       | 2.10     |
| 10    | `oklch(0.75 0.07 164)` | `#84bca2` | 2.16       | 2.07     |
| 11    | `oklch(0.75 0.07 209)` | `#77bbc6` | 2.17       | 2.08     |
| 12    | `oklch(0.75 0.07 254)` | `#90b1da` | 2.22       | 2.12     |
| 13    | `oklch(0.75 0.07 299)` | `#b4a5d5` | 2.27       | 2.18     |
| 14    | `oklch(0.75 0.07 344)` | `#cf9db9` | 2.30       | 2.20     |
| 15    | `oklch(0.75 0.07 29)`  | `#d79e95` | 2.29       | 2.19     |

`OTHERS_COLOR` is `oklch(0.91 0.005 85)` — `#e3e1de`, 1.31:1 on surface. It is off the wheel entirely: paler
than every entry and nearly achromatic. That is deliberate. The residual bucket is often the largest single
slice in an ETF breakdown, and a large slice in a saturated hue would lead the chart with the one category that
carries no information. It must recede, not lead.

**Why a wheel and not a lightness ramp.** Sector, country, and holding are categories, not ranks. A single-hue
ramp encodes an order that the data does not have, and it makes adjacent slices of unrelated categories look
related. Three single-hue ramps were rendered and rejected before this palette. The wheel's own constraint is
that no touching pair may be close in both hue and lightness — including the closing seam between index 15 and
index 0 — which is what the palette test asserts, along with two lightness levels only, sRGB gamut, and no
duplicates.

Sixteen entries is not arbitrary. `chart-colors.test.ts` pins the floor at `TOP_COUNT + 1` with
`TOP_COUNT = 15`, and `etf-chart-service.ts` defaults `topCount` to 15, so a default breakdown fills fifteen
slices plus Others and neither palette wraps or reuses a colour within one chart. There is no headroom left:
raising `topCount` above 15 wraps the wheel.

**Accessibility.** `CHART_COLORS`' pale half sits below 3:1 against both grounds by design — the whole point of
the second run is that it is quieter than the first. What carries the information is **the legend, which states
every label and every percentage as full-contrast text**: labels in `--color-ink-soft` at 6.02:1, values in
`--color-ink` at 16.48:1. Colour is never the sole carrier. The 1px `--color-hairline-strong` stroke on each
legend swatch is a **visual seam at 1.56:1** that keeps a pale swatch from bleeding into the card behind it;
it is not the WCAG 1.4.11 remedy and must not be described as one. The donut's arcs carry no stroke at all —
see below.

### The donut palette

`DONUT_COLORS` is sixteen entries built as **four hues × four lightness tiers**: brass `80`, teal `175`,
slate `250`, dusty rose `340`, each at `L` 0.35 / 0.50 / 0.65 / 0.80. Chroma is warmth-weighted so brass reads
richest (`C` 0.061–0.095 overall), and index 0 is pinned to `oklch(0.5 0.095 80)` — the signature brass — so the
largest slice always leads in the brand colour. The hues avoid the reserved signals: teal at 175 sits 23° off
`gain`, rose at 340 sits 46° off `loss`.

The hue spacing is the whole point. An earlier earth palette placed four hues 26° apart, which puts a
same-tier adjacent-hue pair 0.02 apart in OKLab — close enough that users read two slices as one colour. At
75–95° spacing the same pair is 0.12 apart. Measured: **min pairwise 0.077, min ring-adjacent 0.190.**
`chart-colors.test.ts` gates both, with `TELLABLE_APART = 0.06` as the floor.

Every entry clears **3:1 against the card** (min 3.42, at the `L 0.80` tier) — unlike `CHART_COLORS`' pale run,
which does not. `DONUT_OTHERS_COLOR` is `oklch(0.925 0.008 85)` — `#e9e6e0`, 1.64:1 — lighter than every named
slice and nearly achromatic, for the same reason `OTHERS_COLOR` is.

The tier order is constrained twice, and both constraints exist because a screenshot caught the alternative:
the palest tier may not appear in the first three indices, and index 0 is fixed. Without them a 21% slice drew
the `L 0.80` tier and the chart's largest category read as its faintest.

### Line and bar charts

In `components/portfolio/portfolio-chart.vue`, series carry no point markers at rest — `radius: 0`,
`hoverRadius: 5` — so a long daily series reads as a trend rather than as a beaded rope, at `borderWidth: 2` and
`tension: 0.4`. That is the intended treatment for every line chart, but only the portfolio chart sets it today:
`charts/line-chart.vue` sets `borderWidth: 2` and nothing else, and the bar chart is likewise unmigrated. The
portfolio chart draws its four series from `CHART_COLORS` indices 0, 1, 3, and 5 rather than from consecutive
entries, so no two lines on it are 45° neighbours. Every dataset sets `backgroundColor` from its own
`borderColor` — as the colour itself, or via `withAlpha` for the one filled series. Without it Chart.js fills
legend swatches with its default grey and the key stops matching the lines.

### The summary chart's range controls

The chart on `/` carries three pieces around it, and their arrangement is the design decision worth preserving.

**The range row is chips, not a select.** `chart-range-filter.vue` renders all fourteen `TimeRange` values as
`.platform-btn` inside `.platform-buttons` — the same classes the platform filters use, with no additional
styling of its own. Fourteen chips wrap onto two rows on a phone, and that is the intended outcome: every range
stays one tap away, which a `<select>` would cost two. It sits **below** the chart, because it changes what the
chart already showed rather than introducing it.

**The change figure floats over the plot, not above it.** `range-change-header.vue` is absolutely positioned at
the chart's top-left inside `.chart-frame` and is `pointer-events: none`, so it reads as an annotation on the
plot rather than as another header competing with the card's title. It carries a real U+2212 minus for negative
values — not a hyphen — and takes its colour from the shared `getGainLossClass`.

**Loading is a veil, not a swap.** While a new range resolves, `.chart-veil` covers the plot at
`color-mix(in srgb, var(--color-surface) 72%, transparent)` with the spinner centred on it. The old chart stays
visible and dimmed underneath, so the card does not collapse and reflow the page beneath it. 72% is the point:
opaque enough that the stale figures cannot be misread as current, transparent enough that the shape of the
series is still there to orient against.

### The ETF breakdown card

One card, three dimensions, one chart. The dimension is chosen by a segmented control in the card's `actions`
slot — Sectors, Top holdings, Countries — where the active segment is the filter-chip treatment exactly:
`--color-brass-wash` fill, `--color-brass` border, `--color-brass-deep` text (`etf-breakdown.vue:465`); the
inactive segments are `--color-ink-soft` on transparent with a transparent border, so the row does not gain
three boxes.

**The search field is the one place the system draws a leading-icon input.** Below the card,
`.search-input-wrapper` holds a `--color-ink-faint` glyph that goes brass on `:focus-within` — the wrapper
carries the state, not the input — over a `--radius-container` field, with a `--radius-control` clear button
that takes `--color-brass-wash` on hover and appears only when the query is non-empty. The query itself lives in
`useLocalStorage('portfolio_etf_search')` and is debounced 200ms, so it survives a reload and a result count
renders beside it once results settle. If another surface needs search, copy this treatment rather than putting
a magnifier inside a plain `.form-control`: the focus-within colour change is what tells you the field is live.

The chart is a doughnut with `cutout: '83.333%'`, `spacing: 6`, `borderRadius: 0`, and `borderWidth: 0`.
Chart.js's own legend and tooltip are both disabled and animation is off.

That cutout makes the ring one sixth of the radius — thin, so the arcs read as a measured band rather than a
pie. Selection grows the active arc **inward**, not outward: Chart.js has no per-arc `innerRadius`, so a local
`growSelectedInward` plugin sets the hovered arc's inner radius one further `1/6` step in on `afterUpdate`,
doubling its thickness while its outer edge stays put. Unselected arcs simultaneously drop to 30% alpha via
`withAlpha`. `hoverOffset` is deliberately unset — pulling a slice out of the ring breaks the band.

**The centre readout** sits inside the cutout: the label at `0.8125rem` in `--color-ink-soft`, the percentage
under it at `var(--text-title)` / weight 550 in `--color-ink` with `tabular-nums` inherited. 550 is the same
weight `stat-card.vue` sets, so the figure in the ring and the figures in the cards beside it read as one tier.
It follows the hovered slice — driven
by both the chart's `onHover` and the legend's `mouseenter` — and rests on index 0, which is the largest slice
because the chart service sorts descending. It is `aria-hidden="true"`. That is not an oversight: the legend
beside it already states the same label and the same percentage as real text, so exposing the readout would
make a screen reader announce every value twice, once as a duplicate that changes under the mouse.

**The legend** is a two-column grid with the value under the label. Each row leads with a 10px circular swatch
— or a 16px circular flag on the countries dimension — then the label in `--color-ink-soft` at
`var(--text-base)`, with the value beneath it in `--color-ink` at `1.0625rem` / weight 500 with `tabular-nums`.
That `1.0625rem` is off the scale — 17px, one step above `--text-control` with no token behind it — and it is
the legend's one piece of debt; it exists because the value had to out-weigh its own label without reaching
`--text-title`, which is the centre readout's size. Add a token before copying it anywhere else.
It collapses to one column under 480px. It is a focusable `role="region"` labelled "Breakdown legend", taking
the standard 2px brass focus ring, and hovering a row drives the chart's active slice in the same direction the
chart drives it.

Note that `spacing: 6` — the gap between arcs — **replaced** the per-arc stroke rather than joining it. The
stroke was doing two jobs badly: separating neighbouring slices, and separating a pale slice from the card. A
6px gap does the first cleanly, and `DONUT_COLORS` clearing 3:1 against the card removes the need for the
second. The legend swatches keep their stroke; the arcs no longer have one.

## Two naming systems, one palette

Phase 1 retoned the legacy token names in place rather than deleting them. `--color-signal-indigo` holds the
brass value, `--color-control-graphite` holds `ink-soft`, `--color-status-*` hold the semantic signals, and the
`--color-gray-*` ramp holds warm neutrals. Every one of them resolves to a value in the tables above.

This means both naming systems are live, and **neither is a fork**. A fork is two sources of truth; this is one
source with two sets of names pointing into it. Phase 2 deletes the legacy names as it migrates their
consumers. Until then:

- New work uses the semantic names — `brass`, `ink-soft`, `gain`, `loss`, `notice`, `hairline`.
- Existing code keeps its legacy name until the component is migrated. Do not do a find-and-replace sweep
  outside a Phase 2 task; a rename with no visual delta is invisible in the pixel gate and steals review
  attention from the phase that is actually changing pixels.

## Do's and Don'ts

### Do

- **Do** reach for a 1px hairline before a shadow. If two surfaces need separating, that is a border.
- **Do** spend brass only on navigation state, focus rings, the primary action, and the active filter chip.
  If a screen has two brass elements competing for "primary", one of them is wrong.
- **Do** add every new token to the `@theme static` block in `ui/styles/theme.css`, authored in OKLCH, and
  consume it as `var(--color-*)` or its Tailwind utility. That block is the only palette.
- **Do** keep gain and loss at `L = 0.520`. They are matched so an equal loss cannot look louder than a gain.
- **Do** use `ink-soft` for small text. `ink-faint` is for axis ticks, disabled glyphs, and rules.
- **Do** set the uppercase `0.75rem` / 600 / `0.05em` label for every column header and field label.
- **Do** let `font-variant-numeric: tabular-nums` inherit from `body`. Every money column depends on it.
- **Do** ship both renderings when adding a data column: the desktop table cell _and_ the mobile card pair.
- **Do** hold the page shell at `mx-auto mt-4 w-full max-w-app px-3`. The gutter is intentional.
- **Do** reuse `.platform-btn` when a new surface needs a selected-one-of-many row. The summary chart's
  time-range filter does exactly that and adds no CSS; a fifth resting treatment is what would be wrong.
- **Do** state a chart's values as text in its legend. The colours are an index into the legend, not the data.
- **Do** add a new route to `ui/tests/visual/palette.spec.ts` when you build one. It walks every visible text
  node for AA and the first fifteen tab stops for the focus ring, on three viewports, against stubbed data.
  It covers `/instruments`, `/transactions`, and `/etf-breakdown`; `/summary`, `/calculator`, and
  `/diversification` are still unwatched. This gate catches what the screenshot comparator structurally
  cannot — a colour that moved but stayed close.

### Don't

- **Don't** write a hex literal in a component or a stylesheet. There is exactly one in `theme.css` and four
  declarations of three distinct values in `index.html` (`#fcfaf6` paints both the page and the shell), and
  every one of them has a reason recorded above. A near-miss hex is not a shortcut, it is a
  fork, and the pixel gate cannot police it: the visual comparator runs at `maxDiffPixels: 0` but leaves
  Playwright's per-pixel `threshold` at its default, so a colour that is merely close counts as zero diff
  pixels and passes silently.
- **Don't** treat `--color-signal-indigo` as a second accent. It is brass under an old name and it is leaving.
- **Don't** use gain or loss for anything other than the sign of a value. No green "active" chips, no red
  "new" badges. The eight inherited danger and validation uses of `loss` are listed under palette rule 3; they
  are a known divergence, not a licence to add more — and the count has already grown once.
- **Don't** introduce a second text face. One is declared, on `body`, and everything inherits it. The serif was
  built and removed; rebuilding it needs a rendered screenshot, not a rationale. Geist Mono is the single
  sanctioned exception and it is confined to the Monaco config editor.
- **Don't** add a radius value. Controls are `0.25rem`, containers are `0.5rem`. A container inside a container
  does not get a smaller radius — it gets a hairline. One `0.375rem` radius predates the rule and still stands,
  at `allocation-table.vue:712`; the two that used to sit beside it in `etf-breakdown.vue` and
  `instruments-view.vue` are gone. It is debt, not precedent. (`0.375rem` as padding or gap is fine and
  common — the rule is about radii only.)
- **Don't** put a background fill back on `.table thead th`. It was removed because it collided with the active
  filter chips, which are the brass-wash element on a data surface.
- **Don't** describe the legend swatch stroke as an accessibility feature. It is a 1.56:1 seam. The legend text
  is what satisfies 1.4.11. The donut's arcs carry no stroke at all — don't add one back to "help contrast";
  the gap and the palette's 3:1 floor already do that job.
- **Don't** build a custom listbox, popover, or modal. `<select>` is styled through `::picker(select)` and the
  dialogs are native `<dialog>`; both give you the top layer, the keyboard model, and scroll containment for
  free. A hand-rolled replacement has to reimplement all three and will get one of them wrong.
- **Don't** animate anything that isn't confirming a state change. The value flash, the spinners, and the
  select picker's 150ms open are the whole motion budget.
- **Don't** put a `<style scoped>` block in a component to override a `components.css` rule and expect the
  layer order to explain it. Scoped styles are injected unlayered and therefore beat every `@layer`; that is
  why an override "works" and why it is invisible to anyone reading the cascade.
- **Don't** reach for a `gray-*` value when a semantic token names the same role. The ramp exists for the
  component layer's older internals, not for new work.
