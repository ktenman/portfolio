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

export const DONUT_COLORS = [
  'oklch(0.5 0.095 80)',
  'oklch(0.65 0.072 175)',
  'oklch(0.35 0.079 340)',
  'oklch(0.8 0.066 250)',
  'oklch(0.5 0.078 175)',
  'oklch(0.65 0.081 340)',
  'oklch(0.35 0.076 250)',
  'oklch(0.8 0.078 80)',
  'oklch(0.5 0.088 340)',
  'oklch(0.65 0.077 250)',
  'oklch(0.35 0.067 80)',
  'oklch(0.8 0.061 175)',
  'oklch(0.5 0.084 250)',
  'oklch(0.65 0.092 80)',
  'oklch(0.35 0.061 175)',
  'oklch(0.8 0.069 340)',
]

export const CHART_COLORS_DARK = [
  'oklch(0.76 0.09 74)',
  'oklch(0.76 0.09 119)',
  'oklch(0.76 0.09 164)',
  'oklch(0.76 0.09 209)',
  'oklch(0.76 0.09 254)',
  'oklch(0.76 0.09 299)',
  'oklch(0.76 0.09 344)',
  'oklch(0.76 0.09 29)',
  'oklch(0.54 0.07 74)',
  'oklch(0.54 0.07 119)',
  'oklch(0.54 0.07 164)',
  'oklch(0.54 0.07 209)',
  'oklch(0.54 0.07 254)',
  'oklch(0.54 0.07 299)',
  'oklch(0.54 0.07 344)',
  'oklch(0.54 0.07 29)',
]

export const DONUT_COLORS_DARK = [
  'oklch(0.76 0.095 80)',
  'oklch(0.66 0.072 175)',
  'oklch(0.86 0.079 340)',
  'oklch(0.56 0.066 250)',
  'oklch(0.76 0.078 175)',
  'oklch(0.66 0.081 340)',
  'oklch(0.86 0.07 250)',
  'oklch(0.56 0.078 80)',
  'oklch(0.76 0.088 340)',
  'oklch(0.66 0.077 250)',
  'oklch(0.86 0.067 80)',
  'oklch(0.56 0.061 175)',
  'oklch(0.76 0.084 250)',
  'oklch(0.66 0.092 80)',
  'oklch(0.86 0.061 175)',
  'oklch(0.56 0.069 340)',
]

const DARK_COUNTERPARTS = new Map([
  ...CHART_COLORS.map((color, index) => [color, CHART_COLORS_DARK[index]] as const),
  ...DONUT_COLORS.map((color, index) => [color, DONUT_COLORS_DARK[index]] as const),
])

const ALPHA = / \/ [\d.]+\)$/

export const withAlpha = (color: string, alpha: number): string =>
  color.replace(')', ` / ${alpha})`)

export function schemeColor(color: string, dark: boolean): string {
  if (!dark) return color
  const opaque = color.replace(ALPHA, ')')
  const counterpart = DARK_COUNTERPARTS.get(opaque)
  if (!counterpart) return color
  return color.replace(opaque.slice(0, -1), counterpart.slice(0, -1))
}
