import { type Locator, type Page } from '@playwright/test'

const SETTLE_POLL_INTERVAL_MS = 400
const SETTLE_REQUIRED_SAMPLES = 4
const SETTLE_TIMEOUT_MS = 30000

const FREEZE_STYLES = [
  '.currency-value { overflow: hidden; }',
  '.monaco-editor .lines-content .core-guide-indent.indent-active { box-shadow: 1px 0 0 0 var(--indent-color) inset; }',
].join('\n')

export async function waitForValueToSettle(
  page: Page,
  label: string,
  measure: () => Promise<number>
): Promise<void> {
  const deadline = Date.now() + SETTLE_TIMEOUT_MS
  let lastValue = await measure()
  let stableSamples = 1
  while (stableSamples < SETTLE_REQUIRED_SAMPLES) {
    if (Date.now() > deadline) {
      throw new Error(
        `${label} did not settle within ${SETTLE_TIMEOUT_MS}ms, last measured ${lastValue}`
      )
    }
    await page.waitForTimeout(SETTLE_POLL_INTERVAL_MS)
    const value = await measure()
    stableSamples = value === lastValue ? stableSamples + 1 : 1
    lastValue = value
  }
}

function waitForScrollHeightToSettle(page: Page): Promise<void> {
  return waitForValueToSettle(page, 'Page height in px', () =>
    page.evaluate(() => document.documentElement.scrollHeight)
  )
}

export function waitForBoxHeightToSettle(page: Page, target: Locator): Promise<void> {
  return waitForValueToSettle(page, 'Element height in px', async () =>
    Math.round((await target.boundingBox())?.height ?? -1)
  )
}

export async function openRoute(page: Page, path: string): Promise<void> {
  await page.goto(path)
  await page.waitForLoadState('networkidle')
  await waitForScrollHeightToSettle(page)
}

export async function freeze(page: Page): Promise<void> {
  await page.route('**/api/**', route => route.abort())
  await page.addStyleTag({ content: FREEZE_STYLES })
}

export async function settleAndFreeze(page: Page): Promise<void> {
  await waitForScrollHeightToSettle(page)
  await freeze(page)
}
