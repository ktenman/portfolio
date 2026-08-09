import { chromium } from '@playwright/test'

const [route, selector, ...flags] = process.argv.slice(2)

if (!route || !selector) {
  console.error(
    'usage: node scripts/computed-style.mjs <route> <selector> [--width=1440] [--state=hover|focus] [--pseudo=::before]'
  )
  process.exit(1)
}

const flag = name => flags.find(f => f.startsWith(`--${name}=`))?.slice(name.length + 3)
const width = Number(flag('width') ?? 1440)
const state = flag('state')
const pseudo = flag('pseudo') ?? null

if (!Number.isFinite(width) || width <= 0) {
  console.error(`--width must be a positive number, got ${flag('width')}`)
  process.exit(1)
}

const SELECTOR_TIMEOUT_MS = 15000
const STATE_TIMEOUT_MS = 5000

const PROPERTIES = [
  'display',
  'position',
  'width',
  'height',
  'min-width',
  'min-height',
  'margin-top',
  'margin-right',
  'margin-bottom',
  'margin-left',
  'padding-top',
  'padding-right',
  'padding-bottom',
  'padding-left',
  'font-family',
  'font-size',
  'font-weight',
  'line-height',
  'letter-spacing',
  'text-transform',
  'text-align',
  'color',
  'background-color',
  'background-image',
  'border-top-width',
  'border-right-width',
  'border-bottom-width',
  'border-left-width',
  'border-top-color',
  'border-right-color',
  'border-bottom-color',
  'border-left-color',
  'border-radius',
  'outline-width',
  'outline-style',
  'outline-color',
  'outline-offset',
  'box-shadow',
  'opacity',
  'transform',
  'transition',
  'animation',
  'content',
  'flex-direction',
  'align-items',
  'justify-content',
  'gap',
]

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width, height: 900 } })
await page.goto(`http://localhost:61234${route}`)

const found = await page
  .waitForSelector(selector, { state: 'attached', timeout: SELECTOR_TIMEOUT_MS })
  .catch(() => null)

if (!found) {
  console.error(`no element matched ${selector} on ${route}`)
  await browser.close()
  process.exit(1)
}

if (state === 'hover') await page.locator(selector).first().hover({ timeout: STATE_TIMEOUT_MS })
if (state === 'focus') await page.locator(selector).first().focus({ timeout: STATE_TIMEOUT_MS })

await page.evaluate(async () => {
  await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  document.getAnimations().forEach(animation => {
    if (!animation.effect || animation.effect.getComputedTiming().iterations === Infinity) {
      animation.cancel()
      return
    }
    animation.finish()
  })
})

const result = await page.$$eval(
  selector,
  (elements, { properties, pseudoElement }) =>
    elements.map(element => {
      const computed = getComputedStyle(element, pseudoElement)
      return Object.fromEntries(properties.map(p => [p, computed.getPropertyValue(p)]))
    }),
  { properties: PROPERTIES, pseudoElement: pseudo }
)

if (result.length === 0) {
  console.error(`no element matched ${selector} on ${route}`)
  await browser.close()
  process.exit(1)
}

if (result.length > 1) {
  console.error(`${selector} matched ${result.length} elements on ${route}, printing all`)
}

console.log(JSON.stringify(result.length === 1 ? result[0] : result, null, 2))
await browser.close()
