import { Request, Response } from 'express'
import puppeteer, { Browser, Page } from 'puppeteer'
import { ServiceAdapter } from '../types'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'
import { randomUUID } from 'crypto'

interface SessionData {
  cookies: string
  regNr: string
  createdAt: number
}

const sessions = new Map<string, SessionData>()
const SESSION_TTL_MS = 5 * 60 * 1000

let browserInstance: Browser | null = null

async function getBrowser(): Promise<Browser> {
  if (!browserInstance || !browserInstance.connected) {
    browserInstance = await puppeteer.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
    })
  }
  return browserInstance
}

function cleanupExpiredSessions(): void {
  const now = Date.now()
  for (const [sessionId, data] of sessions.entries()) {
    if (now - data.createdAt > SESSION_TTL_MS) {
      sessions.delete(sessionId)
    }
  }
}

const cleanupTimer = setInterval(cleanupExpiredSessions, 60000)
cleanupTimer.unref()

async function setupPage(page: Page): Promise<void> {
  await page.setUserAgent(
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
  )
  await page.setViewport({ width: 1280, height: 800, deviceScaleFactor: 1 })
}

async function dismissCookieConsent(page: Page): Promise<void> {
  try {
    const acceptBtn = await page.$('#onetrust-accept-btn-handler')
    if (acceptBtn) {
      await acceptBtn.click()
      await new Promise(resolve => setTimeout(resolve, 500))
    }
  } catch {
    logger.debug('Auto24: No cookie consent popup found')
  }
}

async function getCaptchaHandler(req: Request, res: Response): Promise<void> {
  const regNr = req.body.regNr as string

  if (!regNr) {
    res.status(400).json({ error: 'Missing regNr parameter' })
    return
  }

  const sessionId = randomUUID()
  let page: Page | null = null

  try {
    logger.info(`Auto24: Starting CAPTCHA capture for ${regNr}`)

    const browser = await getBrowser()
    page = await browser.newPage()
    await setupPage(page)

    await page.goto('https://www.auto24.ee/ostuabi/?t=soiduki-turuhinna-paring', {
      waitUntil: 'networkidle2',
      timeout: 30000,
    })

    await new Promise(resolve => setTimeout(resolve, 1000))
    await dismissCookieConsent(page)

    const inputSelector = 'input[name="vpc_reg_nr"]'
    await page.waitForSelector(inputSelector, { timeout: 10000 })
    await page.type(inputSelector, regNr)
    await page.click('button[name="vpc_reg_search"]')

    logger.info('Auto24: Waiting for CAPTCHA...')

    try {
      await page.waitForSelector('#vpc_captcha', { timeout: 10000 })
    } catch {
      const pageContent = await page.content()
      const priceMatch = pageContent.match(
        /<div[^>]*class=["'][^"']*result[^"']*["'][^>]*>[\s\S]*?<b>([^<]+)<\/b>/i
      )
      if (priceMatch) {
        logger.info(`Auto24: Price found directly: ${priceMatch[1]}`)
        res.json({
          status: 'success',
          price: priceMatch[1].trim(),
          message: 'No CAPTCHA required, price found directly',
        })
        return
      }
      throw new Error('CAPTCHA element not found and no price found')
    }

    await page.waitForFunction(
      `(() => {
        const img = document.querySelector('#vpc_captcha');
        return img && img.complete && img.naturalHeight > 0;
      })()`,
      { timeout: 10000 }
    )

    await new Promise(resolve => setTimeout(resolve, 1000))

    const captchaElement = await page.$('#vpc_captcha')
    if (!captchaElement) {
      throw new Error('CAPTCHA element not found')
    }

    const screenshot = await captchaElement.screenshot({ type: 'png' })
    const captchaBase64 = Buffer.from(screenshot).toString('base64')

    const cookies = await page.cookies()
    const cookieString = cookies.map(c => `${c.name}=${c.value}`).join('; ')

    sessions.set(sessionId, {
      cookies: cookieString,
      regNr,
      createdAt: Date.now(),
    })

    logger.info(`Auto24: Session ${sessionId} created with browser-rendered CAPTCHA`)

    res.json({
      sessionId,
      captchaImage: captchaBase64,
      message: 'CAPTCHA required',
    })
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'Unknown error'
    logger.error(`Auto24 getCaptcha error: ${errorMessage}`)
    res.status(500).json({ error: 'Failed to get CAPTCHA', details: errorMessage })
  } finally {
    if (page) {
      await page.close().catch(() => {})
    }
  }
}

async function submitCaptchaHandler(req: Request, res: Response): Promise<void> {
  const { sessionId, solution } = req.body as { sessionId: string; solution: string }

  if (!sessionId || !solution) {
    res.status(400).json({ error: 'Missing sessionId or solution parameter' })
    return
  }

  const session = sessions.get(sessionId)
  if (!session) {
    res.status(400).json({ error: 'Invalid or expired session' })
    return
  }

  let page: Page | null = null

  try {
    logger.info(`Auto24: Submitting CAPTCHA solution '${solution}' for session ${sessionId}`)

    const browser = await getBrowser()
    page = await browser.newPage()
    await setupPage(page)

    const cookiePairs = session.cookies.split('; ')
    const cookies = cookiePairs.map(pair => {
      const [name, value] = pair.split('=')
      return { name, value, domain: '.auto24.ee' }
    })
    await page.setCookie(...cookies)

    const regNrEncoded = encodeURIComponent(session.regNr)
    const solutionEncoded = encodeURIComponent(solution)
    const submitUrl = `https://www.auto24.ee/ostuabi/?t=soiduki-turuhinna-paring&vpc_reg_nr=${regNrEncoded}&checksec1=${solutionEncoded}&vpc_reg_search=1`

    await page.goto(submitUrl, {
      waitUntil: 'networkidle2',
      timeout: 30000,
    })

    await new Promise(resolve => setTimeout(resolve, 1000))
    await dismissCookieConsent(page)

    const pageContent = await page.content()

    const priceMatch = pageContent.match(
      /<div[^>]*class=["'][^"']*result[^"']*["'][^>]*>[\s\S]*?<b>([^<]+)<\/b>/i
    )
    const altMatch = pageContent.match(/(\d+\s*€\s*kuni\s*\d+\s*€)/i)

    const price = priceMatch ? priceMatch[1].trim() : altMatch ? altMatch[1].trim() : null

    if (price) {
      logger.info(`Auto24: Price found: ${price}`)
      res.json({
        status: 'success',
        price,
      })
    } else if (pageContent.includes('vpc_captcha') || pageContent.includes('checksec1')) {
      logger.warn('Auto24: CAPTCHA still present, solution may be incorrect')
      res.json({
        status: 'captcha_failed',
        message: 'CAPTCHA solution incorrect, please try again',
      })
    } else {
      logger.warn('Auto24: Could not extract price from response')
      res.status(500).json({
        error: 'Could not extract price',
        html: pageContent.slice(0, 2000),
      })
    }
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'Unknown error'
    logger.error(`Auto24 submitCaptcha error: ${errorMessage}`)
    res.status(500).json({ error: 'Failed to submit CAPTCHA', details: errorMessage })
  } finally {
    sessions.delete(sessionId)
    if (page) {
      await page.close().catch(() => {})
    }
  }
}

export const auto24PuppeteerCaptchaAdapter: ServiceAdapter = {
  path: '/auto24/captcha',
  method: 'POST',
  serviceName: 'Auto24PuppeteerCaptcha',
  middleware: [createRateLimiter({ windowMs: 60000, max: 10 })],
  handler: getCaptchaHandler,
}

export const auto24PuppeteerSubmitAdapter: ServiceAdapter = {
  path: '/auto24/submit',
  method: 'POST',
  serviceName: 'Auto24PuppeteerSubmit',
  middleware: [createRateLimiter({ windowMs: 60000, max: 10 })],
  handler: submitCaptchaHandler,
}
