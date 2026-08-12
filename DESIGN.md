---
name: Portfolio
description: A calibrated instrument panel for one real multi-broker investment portfolio.
colors:
  signal-indigo: '#4361ee'
  signal-indigo-deep: '#3651d4'
  control-graphite: '#4b5563'
  control-graphite-deep: '#374151'
  gain: '#21c55d'
  loss: '#dc3545'
  loss-deep: '#c82333'
  loss-wash: '#fef2f2'
  loss-wash-deep: '#fecaca'
  warning: '#d97706'
  legacy-primary: '#007bff'
  status-success: '#28a745'
  status-danger: '#dc3545'
  status-info: '#17a2b8'
  status-warning: '#ffc107'
  ink: '#212529'
  ink-muted: '#6b7280'
  body-secondary: 'rgba(33, 37, 41, 0.75)'
  hairline: '#e2e8f0'
  hairline-strong: '#dee2e6'
  control-border: 'rgb(0 0 0 / 0.1)'
  surface: '#ffffff'
  surface-hover: '#f8fafc'
  surface-subtle: '#fcfcfd'
  surface-band: 'rgb(0 0 0 / 0.07)'
  paper: '#fafafa'
  white: '#ffffff'
  black: '#000000'
  gray-100: '#f8f9fa'
  gray-200: '#e9ecef'
  gray-300: '#dee2e6'
  gray-400: '#ced4da'
  gray-500: '#adb5bd'
  gray-600: '#6c757d'
  gray-700: '#495057'
  gray-800: '#343a40'
  gray-900: '#212529'
typography:
  headline:
    fontFamily: 'Avenir, Helvetica, Arial, sans-serif'
    fontSize: '1.5rem'
    fontWeight: 600
    lineHeight: 1.2
  title:
    fontFamily: 'Avenir, Helvetica, Arial, sans-serif'
    fontSize: '1.25rem'
    fontWeight: 600
    lineHeight: 1.3
  body:
    fontFamily: 'Avenir, Helvetica, Arial, sans-serif'
    fontSize: '1rem'
    fontWeight: 400
    lineHeight: 1.5
  data:
    fontFamily: 'Avenir, Helvetica, Arial, sans-serif'
    fontSize: '0.875rem'
    fontWeight: 600
    lineHeight: 1.4
  label:
    fontFamily: 'Avenir, Helvetica, Arial, sans-serif'
    fontSize: '0.75rem'
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: '0.05em'
  scale:
    glyph: '0.5rem'
    micro: '0.625rem'
    sort-arrow: '0.65rem'
    caption: '0.6875rem'
    label: '0.75rem'
    note: '0.8rem'
    dense: '0.8125rem'
    chart-tick: '0.85rem'
    data: '0.875rem'
    table-cell: '0.9rem'
    figure: '0.9375rem'
    lede: '0.95rem'
    body: '1rem'
    chart-title: '1.1rem'
    figure-large: '1.125rem'
    title: '1.25rem'
    headline: '1.5rem'
    hero-compact: '1.6rem'
    h3: '1.75rem'
    h2: '2rem'
    h1: '2.5rem'
rounded:
  xs: '0.25rem'
  control: '0.375rem'
  container: '0.5rem'
  pill: '999px'
spacing:
  xs: '0.25rem'
  sm: '0.5rem'
  base: '0.75rem'
  md: '1rem'
  lg: '1.5rem'
  xl: '3rem'
components:
  button-primary:
    backgroundColor: '{colors.signal-indigo}'
    textColor: '{colors.white}'
    rounded: '{rounded.container}'
    padding: '0.375rem 0.75rem'
    typography: '{typography.body}'
  button-primary-hover:
    backgroundColor: '{colors.signal-indigo-deep}'
    textColor: '{colors.white}'
  button-ghost:
    backgroundColor: 'rgb(0 0 0 / 0.02)'
    textColor: '{colors.gray-600}'
    rounded: '{rounded.container}'
    padding: '0.25rem 0.625rem'
    typography: '{typography.data}'
  button-ghost-hover:
    backgroundColor: 'rgb(67 97 238 / 0.08)'
    textColor: '{colors.signal-indigo}'
  button-add-new:
    backgroundColor: 'rgb(0 0 0 / 0.02)'
    textColor: '{colors.gray-700}'
    rounded: '{rounded.container}'
    padding: '0.5rem 1rem'
    typography: '{typography.body}'
  dialog-button:
    backgroundColor: '{colors.surface}'
    textColor: '{colors.ink-muted}'
    rounded: '{rounded.control}'
    padding: '0.5rem 1rem'
    typography: '{typography.data}'
  dialog-button-primary:
    backgroundColor: '{colors.control-graphite}'
    textColor: '{colors.white}'
  dialog-button-danger:
    backgroundColor: '{colors.loss}'
    textColor: '{colors.white}'
  chip-filter:
    backgroundColor: '{colors.surface}'
    textColor: '{colors.ink-muted}'
    rounded: '{rounded.control}'
    padding: '0.3125rem 0.625rem'
  chip-filter-active:
    backgroundColor: '{colors.control-graphite}'
    textColor: '{colors.white}'
  card:
    backgroundColor: '{colors.surface}'
    textColor: '{colors.ink}'
    rounded: '{rounded.container}'
    padding: '1.25rem'
  input:
    backgroundColor: '{colors.surface-subtle}'
    textColor: '{colors.ink}'
    rounded: '{rounded.container}'
    padding: '0.375rem 0.75rem'
    typography: '{typography.body}'
  table-header-cell:
    backgroundColor: '{colors.surface-subtle}'
    textColor: '{colors.ink-muted}'
    padding: '1rem 0.75rem'
    typography: '{typography.label}'
  modal-surface:
    backgroundColor: '{colors.gray-100}'
    textColor: '{colors.ink}'
    rounded: '{rounded.container}'
    padding: '1rem'
  toast:
    backgroundColor: '{colors.control-graphite}'
    textColor: '{colors.white}'
    rounded: '{rounded.control}'
    padding: '0.75rem'
    typography: '{typography.data}'
    width: '350px'
---

# Design System: Portfolio

## Overview

**Creative North Star: "The Instrument Panel"**

The domain's own word for the things this app tracks is _instrument_, and the interface takes that literally. A panel of calibrated readouts: the chrome is quiet, machined, and gets out of the way; the values are the only things allowed to be loud. Nothing on screen is decorative. Every saturated pixel is either a number's sign, the position you're currently in, or something asking to be pressed. When a price actually moves, the cell it lives in glows for three seconds and then goes back to being furniture — the one moment of animation in the system is a readout confirming it changed, not a flourish.

The material is thin and hard. Surfaces are white cards on a barely-warm paper ground, separated by 1px hairlines rather than by shadow. Depth exists only where something genuinely floats, and the five shadow tokens that survive are all at or under 8px of blur. Density is deliberately high: the dominant type size is 12px (used more than twice as often as any other step), weights sit at 500 and 600, and padding is measured in quarter-rem steps. This is an interface built for someone who already knows what every column means.

It has to work at two speeds without becoming two designs. On a phone the tables dissolve into label/value stacks that answer "where do I stand" in three seconds; on a desktop the same data returns as a dense 1350px grid you can sit with for an hour. The responsive behavior is not a degradation — the mobile card stack is a first-class, purpose-built rendering of the same truth.

**Key Characteristics:**

- Hairline-bordered, near-flat surfaces; shadow reserved for genuine float
- High density, small type (12–14px dominant), weight over size for hierarchy
- One reserved accent (Signal Indigo) for navigation and primary action; neutral graphite for everything else that's merely "on"
- Green and red mean gain and loss and nothing else
- Motion only confirms a state change; the reduced-motion path keeps only the spinner and the price flash
- Tables on desktop become label/value cards under 767px — the signature responsive move

## Colors

A machined neutral field — paper, white, and slate hairlines — punctuated by exactly one brand accent and two semantic signals. Saturated color is scarce by construction: on a typical screen the only colored pixels are the active nav underline and the sign of a number.

### Primary

- **Signal Indigo** (`#4361ee`): The single brand accent. It marks the active navigation item and its 2px underline indicator, fills the primary action button, tints the native checkbox via `accent-color`, draws every focus ring, and appears at 2–8% opacity as ghost-button-hover tint. It is never used for decoration, never for a background field, and never for text that isn't interactive.
- **Signal Indigo Deep** (`#3651d4`): The hover terminus. Primary buttons carry a 135° gradient between the two indigos and reverse the gradient direction on hover — the button appears to tilt rather than to brighten.

### Secondary

- **Control Graphite** (`#4b5563`): The working control color, and a deliberate refusal to spend the accent. Platform filter chips, dialog confirm buttons, and the toast background all turn graphite, not indigo. Selection state is not brand expression; a filter being on is a fact, not an event.
- **Control Graphite Deep** (`#374151`): The pressed and hovered state of any graphite control.

### Tertiary

- **Gain Green** (`#21c55d`): Positive profit, positive daily change, positive XIRR. Also the 20%-alpha wash of the three-second flash when a value ticks up.
- **Loss Red** (`#dc3545`): The negative counterpart, plus destructive actions (delete). Green and red carry meaning in the data layer and in the action layer; they carry it nowhere else.
- **Loss Deep** (`#c82333`): The gradient terminus of the danger button, mirroring the indigo pair.

### Neutral

- **Ink** (`#212529`): Primary text, and the darkest value in the system. Nothing is pure black.
- **Muted Ink** (`#6b7280`): Every label, every column header, every secondary line under a value, and the resting color of ghost and dialog buttons.
- **Body Secondary** (`rgba(33, 37, 41, 0.75)`): Ink at 75% — the de-emphasized text tone, applied through its text utility on build metadata and supporting copy. It is a translucent Ink, not a gray; a solid gray in its place reads visibly cooler.
- **Hairline** (`#e2e8f0`): The default 1px separator — chip borders, dialog button borders.
- **Hairline Strong** (`#dee2e6`): The table and card outer border, modal header/footer rules, a half-step darker so a data container reads as a container.
- **Control Border** (`rgb(0 0 0 / 0.1)`): The translucent stroke on buttons and form fields, so a control's edge darkens with whatever it sits on rather than fighting it.
- **Card White** (`#ffffff`): Every raised surface — cards, table backgrounds, chips, dialogs.
- **Surface Subtle** (`#fcfcfd`): The near-white fill of table cells, form fields, and dropdown menus — one imperceptible step off Card White, enough to keep an input from disappearing into the card behind it.
- **Surface Hover** (`#f8fafc`): The hover fill for neutral controls, cooler than the page ground so a hovered chip reads as lit rather than dimmed.
- **Paper** (`#fafafa`): The page ground. Warm-neutral and one step off white, so white cards separate without needing a shadow.

### Utility ramp

A nine-step neutral ramp (`gray-100` `#f8f9fa` → `gray-900` `#212529`) backs the parts of the component layer that predate the semantic names: `gray-600` is the mobile-card label color and the badge fill, `gray-200` the mobile-card border and the table header's 2px rule, `gray-100` the modal surface. Prefer a semantic token when one fits; reach into the ramp only where the component layer already does.

### Named Rules

**The Reserved Accent Rule.** Signal Indigo is spent on exactly three things: where you are (nav), what you're focused on (focus ring), and the one primary action on the screen. Anything that is merely selected, active, or toggled uses Control Graphite. If a screen has two indigo elements competing for "primary", one of them is wrong.

**The Signed-Number Rule.** Green and red are reserved for the sign of a value and for destructive intent. Never use them for status chips, category badges, brand accent, or emphasis. A red pixel on this interface means "you lost money" or "this will delete something", and it must never mean anything else.

**The Single-Source Rule.** Every color in the system is declared once, in the `@theme static` block of `ui/styles/theme.css`, and consumed as `var(--color-*)` or as a Tailwind utility derived from it. There is no second palette layer to reconcile against. A hardcoded near-miss hex in a component — an `#ef4444` beside Loss Red, an `#0d6efd` beside Signal Indigo — is not a shortcut, it is a fork. The pixel gate cannot police this: a near-miss green measures far below the comparator's threshold and passes silently, so the only defense is that the literal never gets written.

## Typography

**Body Font:** Avenir (with Helvetica, Arial, sans-serif) — declared once as `--font-sans` and set on `body`
**Mono Font:** `ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas` — used only inside the diversification config editor

**Character:** Geometric-humanist and quiet. Avenir's even, wide-aperture forms hold up at 12px, which is where most of this interface lives. There is no display face and no type personality by design — the type's job is to be legible at density and to disappear behind the number it's setting.

One stack is declared and it is set on `body`, so everything inherits it — including the toast layer, which teleports out of `#app` and would otherwise render in a different face than the app behind it. The mono stack is declared as `--font-mono` but the one place that needs it, the Monaco config editor, passes its own literal copy of the same list. That copy stays literal on purpose: Monaco measures glyph widths in a canvas context, where a `var()` does not resolve.

### Hierarchy

- **Headline** (600, 1.5rem, 1.2): Page and major section titles in routine use.
- **Title** (600, 1.25rem, 1.3): Card headers and section subheads.
- **Body** (400, 1rem, 1.5): Prose, form values, dialog text. The least-used role in a system made of tables.
- **Data** (600, 0.875rem, 1.4): The numeric readout — table cells, card values, the right-aligned figure in a mobile row. Weight 600 is what separates a value from its label; size is held constant.
- **Label** (600, 0.75rem, 0.05em tracking, uppercase): Table column headers and mobile card field names. Uppercase plus letterspacing plus Muted Ink is the system's most recognizable typographic gesture and appears on nearly every surface.

Headings additionally run a fluid ramp in the base layer: `h1`–`h4` are `calc()` expressions that grow with the viewport and snap to fixed `2.5 / 2 / 1.75 / 1.5rem` at `≥1200px`. The `h1`/`h2`/`h3` endpoints are the only reason 2.5rem, 2rem, and 1.75rem exist in the system.

### The recorded ramp

`typography.scale` in the frontmatter enumerates every literal size the UI actually renders, from the 0.5rem legend glyph to the 2.5rem `h1` endpoint — twenty-one steps. That is a record, not a menu. Five of them carry the roles above and account for the overwhelming majority of declarations; the rest (`0.65rem`, `0.8rem`, `0.85rem`, `0.9rem`, `0.9375rem`, `0.95rem`, `1.1rem`, `1.6rem`) are per-component one-offs that arrived with individual features and were never reconciled. They are documented so tooling stops reporting them as unknowns, and so the size of the drift is visible in one place. New work picks a role.

### Named Rules

**The Weight-Not-Size Rule.** Hierarchy is carried by weight (500 → 600 → 700) and color, not by scale. The gap between a label and its value is 100 weight units and one color step, not four points of size. Reaching for a larger font to create emphasis breaks the density the tables depend on.

**The Tabular Figures Rule.** Numeric columns must set `font-variant-numeric: tabular-nums`. It is set once, on `body` in the base layer, and inherits everywhere — which is exactly how it should stay. Any component that introduces its own `font-variant-numeric` (or a font stack without tabular figures) breaks the alignment of every right-aligned money column beneath it.

**The Documented-Step Rule.** A font size that is not already in `typography.scale` does not get added by writing it in a component. Either it maps to one of the five roles, or the ramp entry lands in `theme.css` and in this file first.

## Layout

A single centered column, `--container-app: min(1350px, 91vw)`, applied as a max-width utility on the nav bar and on all five route shells — never full-bleed and never a fixed max, so there is always a 4.5% gutter on each side at every viewport. Content sits directly on the page ground in white cards; there is no sidebar, no app shell chrome beyond the sticky top navigation.

Spacing runs on a 4px base: `0.25 / 0.5 / 1 / 1.5 / 3rem`, with `0.75rem` used freely as a half-step. A set of pixel-derived values (`0.125rem`, `0.3125rem`, `0.625rem`, `0.9375rem` — 2px, 5px, 10px, 15px) has leaked in from direct pixel thinking; prefer the named scale.

Breakpoints are declared as theme tokens (`--breakpoint-sm` through `--breakpoint-2xl`: 576 / 768 / 992 / 1200 / 1400px) and drive Tailwind's `sm:`–`2xl:` variants. Layout genuinely changes at three of them:

- **≥1200px**: the fluid heading ramp stops growing and locks to fixed sizes.
- **≥992px**: the navigation becomes sticky (`top: 0`, `z-index: var(--z-sticky)`) and picks up `--shadow-nav` — one of the few places shadow is allowed.
- **768–844px**: buttons that carry both an icon and a label tighten to `0.375rem 0.5rem`, keeping action rows from wrapping in the tablet band.
- **≤768px**: the signature move — every data table is hidden and replaced by a stack of mobile cards, and tap targets grow to `min-height: 44px`. A further compaction tier at 389–767px tightens card padding and value size again for small phones.

Ad-hoc breakpoints exist at 389, 480, 666, 767.98, and 844px, and a landscape-phone rule under 767px swaps the mobile cards back to the desktop table because there is width for it. These are real and load-bearing, but new work should reach for the token scale first.

### Named Rules

**The Two-Speeds Rule.** Every data surface ships two renderings of the same truth: a dense desktop table and a mobile label/value card stack. Neither is a fallback. If a new column is added to the table and not to the card, the phone view has silently lost information.

## Elevation & Depth

Almost flat. Structure is carried by 1px hairline borders and by the one-step tonal gap between the paper ground (`#fafafa`) and card white (`#ffffff`); the near-white `#fcfcfd` fill on table cells and inputs is a third step so faint it reads as material rather than as color.

What shadow remains is a whisper, and only where something genuinely sits above the page: a card at rest, a mobile card under the finger, a sticky navbar that has scrolled over content, a toast. There is no glass, no blur, and no backdrop filter anywhere in the system — the modal backdrop is a flat `rgba(0, 0, 0, 0.5)` wash from the native `<dialog>` element.

All five shadow tokens are in real use, and the heaviest is `0 0.5rem 1rem` at 15% — reserved for the two things that float free of the document, the toast and the dropdown.

### Shadow Vocabulary

- **Resting card** (`--shadow-card: 0 1px 3px rgb(0 0 0 / 0.05)`): Cards and the desktop table wrapper. Barely visible; its job is to keep white-on-white from vibrating.
- **Control** (`--shadow-control: 0 1px 2px rgb(0 0 0 / 0.04)`): Buttons and ghost controls, inverted to `inset 0 1px 1px rgb(0 0 0 / 0.04)` on press so the button reads as depressed rather than moved.
- **Lifted** (`--shadow-lifted: 0 2px 8px rgb(0 0 0 / 0.08)`): Mobile card hover and the dropdown menu — the strongest shadow in routine use.
- **Sticky nav** (`--shadow-nav: 0 2px 4px rgb(0 0 0 / 0.05)`): Applied at ≥992px only, when the bar is actually overlapping scrolled content.
- **Overlay** (`--shadow-overlay: 0 0.5rem 1rem rgb(0 0 0 / 0.15)`): Toasts. The only shadow allowed to be seen from across the room, because a toast has to arrive.

### Named Rules

**The Hairline-First Rule.** Reach for a 1px border before a shadow. Shadow is reserved for elements that genuinely float above the page — modals, the sticky navbar, a card under the finger. A shadow used to separate two adjacent surfaces is a border that hasn't been written yet.

## Shapes

Rectilinear and softly cornered, with no ornament, no clipping, and no non-rectangular silhouettes. Two radius tokens carry the system: `--radius-control` (`0.375rem`) on small controls — chips, small buttons, dialog buttons, badges, skeleton cells, toasts — and `--radius-container` (`0.5rem`) on containers — cards, table wrappers, inputs, modals, mobile cards. A `0.25rem` sub-control step exists for the smallest elements (checkbox, input-group end caps), and `50%` / `999px` produce the handful of true circles and pills. One-off `0.75rem` and `1.5rem` radii survive on the mobile card and the floating add button; treat them as debt, not as scale.

Borders are always exactly 1px, except the table header's `2px` bottom rule, which is the heaviest line in the system and marks the boundary between labels and data.

Groups of controls are spaced, not joined: adjacent ghost buttons take a flat `0.375rem` gap, and the platform chip row wraps at the same gap with a 1px × 1.25rem vertical rule dividing the filters from the select-all. The one place segmentation survives is the input group, where adjacent fields overlap by `-1px` and shed their inner corners so a field and its unit label read as a single machined control.

### Named Rules

**The Two-Radius Rule.** Controls get `0.375rem`; containers get `0.5rem`. A container inside a container does not get a smaller radius — it gets a hairline. Introducing a third radius for a single component fragments the form language.

## Components

### Buttons

- **Shape:** Softly cornered (`0.5rem`; `0.375rem` at small size), 1px `rgb(0 0 0 / 0.1)` border, weight 500, `--shadow-control`.
- **Primary:** A 135° gradient from Signal Indigo to Signal Indigo Deep, white text. On hover the gradient reverses direction rather than lightening. On press, `translateY(1px)` with an inset shadow.
- **Danger:** The same 135° gradient construction between Loss Red and Loss Deep. Destructive intent gets the identical mechanism, only the hue changes.
- **Ghost (the workhorse):** A 2%-black tint with a hairline border and Muted Ink text — visible at rest, unlike a true ghost button. Hover fills with 8%-alpha indigo and switches text to Signal Indigo. This is the default for most actions; the filled primary is rare.
- **Add new:** Prefixes its label with a `+` via `::before` at weight 300 and 70% opacity rather than an icon element; the `+` reaches full opacity on hover while the button tints 5%-alpha indigo.
- **Focus:** `outline: 2px solid` in Signal Indigo at 40% via `color-mix`, `outline-offset: 2px`. Focus is always an outline, never a glow.
- **Touch:** `min-height: 44px` below 768px. Between 768 and 844px, small ghost buttons tighten their horizontal padding to `0.5rem`.
- **Loading:** A `0.875rem` `currentColor` spinner rotates at `0.75s linear` alongside the label — the button never changes size while working.

### Chips

- **Style:** White fill, Hairline border, Muted Ink text at `0.75rem`/500, `0.3125rem 0.625rem` padding, `0.375rem` radius, `120ms ease` transition — a faster tier than the rest of the system, which makes filtering feel immediate.
- **State:** Active fills Control Graphite with white text (never indigo — see The Reserved Accent Rule). Hover lightens to Surface Hover with a `#cbd5e1` border; press adds `scale(0.98)`.
- **Grouping:** Laid out in a `0.375rem` gap wrap row, divided by a 1px × 1.25rem vertical separator before the select-all control. Below 768px the separator is hidden, the row goes full-width, and the container stacks.

### Cards / Containers

- **Corner Style:** Softly cornered (`0.5rem`).
- **Background:** Card White on the Paper ground.
- **Shadow Strategy:** Resting card only — see Elevation & Depth.
- **Border:** 1px `rgb(0 0 0 / 0.05)` on generic cards; Hairline Strong on data containers.
- **Internal Padding:** `1.25rem` for card bodies, `1rem` for mobile card bodies.

### Inputs / Fields

- **Style:** Surface Subtle fill, `rgb(0 0 0 / 0.1)` 1px stroke, `0.5rem` radius, `1rem`/400 text.
- **Focus:** Border shifts to Signal Indigo and a `2px` indigo-at-20% outline appears at `2px` offset. This system outlines, it does not glow.
- **Error:** `.is-invalid` swaps the border to Loss Red and reveals a `0.875em` Loss Red message beneath.
- **Transition:** `border-color` and `box-shadow` at 150ms.
- **Checkbox:** The native control, tinted with `accent-color: var(--color-signal-indigo)` rather than redrawn from a background SVG. Focus is the system's `:focus-visible` outline in indigo-at-40%, matching the field rule above rather than the old glow.

### Navigation

A horizontally scrollable white bar with a Hairline Strong bottom border. Links are Muted Ink; hover and active both go Signal Indigo, and active additionally goes bold with a 2px indigo underline that animates in via `transform: scaleX(0 → 1)` at 300ms — hover previews the same indicator. Sticky at ≥992px, where it picks up `--shadow-nav`. Below 768px the scrollbar is hidden entirely while scrolling stays enabled, and the nav gap tightens from `1rem` to `0.5rem`. The running build's commit hash (7 chars) and date sit at the right end at `0.75rem` in Body Secondary — deliberately visible engineering, per the product's own principle.

### Modals

Native `<dialog>` with `showModal()`, so the backdrop, top-layer stacking, and Escape handling are the platform's rather than the system's. The surface is `gray-100` at `0.5rem` radius with a `rgb(0 0 0 / 0.175)` border; header, body, and footer each take `1rem` padding, with Hairline Strong rules between and a `0.5rem` gap between footer buttons. `max-width: 500px` from 576px up, `800px` for the large variant from 992px. Focus is placed by an `autofocus` attribute on `.modal-content`, which is DOM-intrinsic and therefore survives being opened imperatively — do not replace it with a JS `focus()` call.

### Dialog Buttons

The confirm dialog does not use the main button vocabulary. Its buttons are `0.875rem`/500 on a white fill with a Hairline border and Muted Ink text at `0.5rem 1rem`, on the chip's fast `120ms` transition. Confirm fills Control Graphite; a destructive confirm fills Loss Red. Both press with `scale(0.98)`. The result is that a confirmation reads as a control panel decision, not a marketing call to action.

### Toasts

`350px` fixed-width panels pinned to the top right at `z-index: 1090`, stacked with a `1.5rem` gap, `pointer-events: none` on the container so they never block the page beneath. The background is driven by a single `--toast-bg` custom property: Control Graphite by default, and the four status colors for success / info / error / warning. White text at `0.875rem`, `0.375rem` radius, `--shadow-overlay`.

### Data Table (signature)

The defining surface. Column headers are `0.75rem`/600 uppercase with `0.05em` tracking in Muted Ink, `1rem 0.75rem` padding, over a 2px `gray-200` bottom rule. Body cells share the same padding at `0.9rem`, vertically centered, on the Surface Subtle fill. Row tracking is carried by banding, not by hover: `.table-striped` tints odd rows with `--color-surface-band` (`rgb(0 0 0 / 0.07)`) and tables that opt out get a flat field. Rows have no hover state — they are not clickable, and a hover tint on top of a band reads as a selection. Sortable headers carry a two-arrow indicator at `0.65rem` that sits at 30% opacity until the column is active. The whole table sits in a `0.5rem` rounded wrapper with a Hairline Strong border and `overflow-x: auto`, so wide tables scroll instead of clipping their last column.

### Mobile Data Card (signature)

Below 768px the table is replaced entirely. Each row becomes a white card (`0.5rem` radius, `gray-200` border) whose body is a stack of label/value pairs: the label left-aligned in `gray-600` at `--text-2xs` (`0.8125rem`)/500 with an 80px minimum width, the value right-aligned at weight 600. Pairs are divided by `gray-100` hairlines; the first and last shed their vertical padding. Hover lifts the card with `--shadow-lifted`. In landscape under 767px the stack is suppressed and the desktop table returns, because the width is there.

### Value Flash (signature)

When a price or portfolio value changes, its cell animates `pulse-increase` or `pulse-decrease` — a 3s ease-in-out background wash peaking at 20% alpha green or red at the midpoint, returning to transparent. It is the only ambient motion in the product and the only animation deliberately exempted from `prefers-reduced-motion` (alongside spinners), because it is information, not decoration. The wash is mixed from the exact semantic Gain Green and Loss Red, not an approximation of them.

### Skeleton Loading

Loading states are shape-matched placeholders, never spinners-in-place: table, list, form, and text-block variants that mirror the real layout's dimensions (40px headers, 20px cells, 38px inputs, 48px icons). A 1.5s gradient pulse sweeps them. The layout does not move when real data arrives.

### Charts

Series carry no point markers at rest (`radius: 0`, `hoverRadius: 4`) so a 30–61 point line reads as a trend rather than a beaded rope, at `borderWidth: 2` and `tension: 0.15` — enough curvature to smooth sampling noise, not enough to invent data between points. Every dataset sets `backgroundColor` equal to its `borderColor`; without it Chart.js fills legend swatches with its default grey and the key stops matching the lines. The legend is deliberately quiet: solid `8px` boxes at `11px` with `12px` padding, so at desktop width it collapses to a single line above the plot and the chart leads.

## Do's and Don'ts

### Do:

- **Do** reach for a 1px hairline before a shadow. If two surfaces need separating, that is a border.
- **Do** spend Signal Indigo (`#4361ee`) only on navigation state, focus rings, and the single primary action. Use Control Graphite (`#4b5563`) for anything that is merely selected or toggled.
- **Do** ship both renderings when adding a data column: the desktop table cell _and_ the mobile card label/value pair.
- **Do** carry hierarchy with weight (500 → 600 → 700) and color, not with size. Keep the type small.
- **Do** set uppercase `0.75rem`/600 with `0.05em` tracking in Muted Ink for every column header and field label — it is the system's most recognizable gesture.
- **Do** add every new token to the `@theme static` block in `ui/styles/theme.css` and consume it as `var(--color-*)` or its Tailwind utility. That block is the only palette.
- **Do** use outlines for focus (`2px solid`, `2px` offset), never a glow, and never suppress the focus ring on a control that can be tabbed to.
- **Do** let `font-variant-numeric: tabular-nums` inherit from `body`. Every money column depends on it.
- **Do** keep skeleton placeholders shape-matched to the real content so nothing reflows on load.
- **Do** hold the container at `--container-app` (`min(1350px, 91vw)`). The gutter is intentional at every width.

### Don't:

- **Don't** use green or red for anything other than the sign of a value or a destructive action. No green "active" chips, no red "new" badges.
- **Don't** hardcode a near-miss hex. An `#ef4444` beside Loss Red or an `#0d6efd` beside Signal Indigo is always a color the theme already names, and the screenshot gate will not catch it.
- **Don't** introduce a second font stack. One is declared, on `body`, and everything inherits it.
- **Don't** add a new radius value. Controls are `0.375rem`, containers are `0.5rem`.
- **Don't** add a font size that isn't already in `typography.scale`. The twenty-one recorded steps are a measurement of existing drift, not an invitation to widen it.
- **Don't** re-add shadows to buttons or table wrappers beyond `--shadow-control` and `--shadow-card`. The flatness is the material.
- **Don't** treat the mobile card stack as a degraded table. It is a designed rendering and it must carry every value the table carries.
- **Don't** animate anything that isn't confirming a state change. The value flash and the spinners are the whole motion budget.
- **Don't** put a `<style scoped>` block in a component to override a `components.css` rule and expect the layer order to explain it. Scoped styles are injected unlayered and therefore beat every `@layer`; that is why an override "works" and why it is invisible to anyone reading the cascade.
- **Don't** reach for a `gray-*` ramp value when a semantic token names the same role. The ramp exists for the component layer's older internals, not for new work.
