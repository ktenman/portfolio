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
