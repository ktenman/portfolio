import { Request, Response } from 'express'
import { ServiceAdapter } from '../types'
import { validateParam } from '../utils/adapter-helper'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'
import { execFile } from 'child_process'
import { promisify } from 'util'
import * as cheerio from 'cheerio'
import * as crypto from 'crypto'
import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'

const execFileAsync = promisify(execFile)
const CURL = process.env.CURL_BINARY || '/usr/local/bin/curl_ff117'
const CAPTCHA_SOLVER_URL = process.env.CAPTCHA_SOLVER_URL || 'http://captcha-solver:8000'
const AUTO24_BASE_URL = 'https://www.auto24.ee'
const AUTO24_PRICE_PAGE = `${AUTO24_BASE_URL}/ostuabi/?t=soiduki-turuhinna-paring`
const MAX_RETRIES = 20

interface Auto24PriceResult {
  registrationNumber: string
  marketPrice: string | null
  error: string | null
  attempts?: number
}

interface LookupResult {
  success: boolean
  marketPrice: string | null
  error: string | null
  isInvalidCaptcha: boolean
  vehicleNotFound: boolean
}

async function executeCurlWithCookies(
  url: string,
  options: {
    timeout?: number
    maxBuffer?: number
    headers?: Record<string, string>
    cookieFile?: string
    saveCookies?: boolean
  } = {}
): Promise<string> {
  const { timeout = 15000, maxBuffer = 2 * 1024 * 1024, headers, cookieFile, saveCookies } = options

  const args = ['-s', '-L']

  if (cookieFile) {
    args.push('-b', cookieFile)
    if (saveCookies) {
      args.push('-c', cookieFile)
    }
  }

  if (headers) {
    for (const [key, value] of Object.entries(headers)) {
      args.push('-H', `${key}: ${value}`)
    }
  }

  args.push(url)

  const { stdout } = await execFileAsync(CURL, args, { timeout, maxBuffer })
  return stdout
}

async function downloadImageAsBase64(url: string, cookieFile: string): Promise<string> {
  const tempDir = os.tmpdir()
  const imageFile = path.join(
    tempDir,
    `captcha_image_${Date.now()}_${crypto.randomBytes(8).toString('hex')}.png`
  )

  try {
    const args = ['-s', '-L', '-b', cookieFile, '-o', imageFile, url]
    await execFileAsync(CURL, args, {
      timeout: 10000,
      maxBuffer: 1024 * 1024,
    })

    const imageBuffer = fs.readFileSync(imageFile)
    return imageBuffer.toString('base64')
  } finally {
    try {
      if (fs.existsSync(imageFile)) {
        fs.unlinkSync(imageFile)
      }
    } catch {
      logger.warn(`Failed to cleanup image file: ${imageFile}`)
    }
  }
}

async function solveCaptcha(base64Image: string): Promise<string | null> {
  const uuid = crypto.randomUUID()
  const payload = JSON.stringify({
    uuid,
    imageBase64: base64Image,
  })

  const tempDir = os.tmpdir()
  const payloadFile = path.join(
    tempDir,
    `captcha_payload_${Date.now()}_${crypto.randomBytes(8).toString('hex')}.json`
  )

  try {
    fs.writeFileSync(payloadFile, payload)

    const args = [
      '-s',
      '-X',
      'POST',
      '-H',
      'Content-Type: application/json',
      '-d',
      `@${payloadFile}`,
      `${CAPTCHA_SOLVER_URL}/predict`,
    ]

    const { stdout } = await execFileAsync('/usr/bin/curl', args, {
      timeout: 30000,
      maxBuffer: 1024 * 1024,
    })

    logger.debug(`Captcha solver response: ${stdout}`)
    const response = JSON.parse(stdout)
    const prediction = response.prediction || response.result || response.text || null
    const confidence = response.confidence || 0
    logger.info(`Captcha prediction: ${prediction} (confidence: ${(confidence * 100).toFixed(1)}%)`)
    return prediction
  } catch (error) {
    logger.error(
      `Captcha solver failed: ${error instanceof Error ? error.message : 'Unknown error'}`
    )
    return null
  } finally {
    try {
      if (fs.existsSync(payloadFile)) {
        fs.unlinkSync(payloadFile)
      }
    } catch {
      logger.warn(`Failed to cleanup payload file: ${payloadFile}`)
    }
  }
}

async function attemptPriceLookup(
  regNumber: string,
  defaultHeaders: Record<string, string>,
  cookieFile: string
): Promise<LookupResult> {
  const initialPage = await executeCurlWithCookies(AUTO24_PRICE_PAGE, {
    cookieFile,
    saveCookies: true,
    headers: defaultHeaders,
  })

  const $ = cheerio.load(initialPage)
  const captchaUrl = $('#vpc_captcha').attr('src')

  if (!captchaUrl) {
    logger.error('Captcha image not found on page')
    return {
      success: false,
      marketPrice: null,
      error: 'Captcha image not found',
      isInvalidCaptcha: false,
      vehicleNotFound: false,
    }
  }

  const fullCaptchaUrl = captchaUrl.startsWith('http')
    ? captchaUrl
    : `${AUTO24_BASE_URL}${captchaUrl}`
  logger.debug(`Downloading captcha from: ${fullCaptchaUrl}`)

  const base64Image = await downloadImageAsBase64(fullCaptchaUrl, cookieFile)

  const captchaResult = await solveCaptcha(base64Image)
  if (!captchaResult) {
    return {
      success: false,
      marketPrice: null,
      error: 'Failed to solve captcha',
      isInvalidCaptcha: true,
      vehicleNotFound: false,
    }
  }

  const priceUrl = `${AUTO24_PRICE_PAGE}&vpc_reg_nr=${encodeURIComponent(regNumber)}&checksec1=${encodeURIComponent(captchaResult)}&vpc_reg_search=1`

  const pricePage = await executeCurlWithCookies(priceUrl, {
    cookieFile,
    headers: {
      ...defaultHeaders,
      referer: AUTO24_PRICE_PAGE,
    },
  })

  const price$ = cheerio.load(pricePage)

  const errorMessage = price$('div.vehicl_price_request .errorMessage').text().trim()

  if (errorMessage.includes('Vale kontrollkood') || errorMessage.includes('kontroll')) {
    logger.warn(`Invalid captcha detected: ${errorMessage}`)
    return {
      success: false,
      marketPrice: null,
      error: errorMessage,
      isInvalidCaptcha: true,
      vehicleNotFound: false,
    }
  }

  if (errorMessage.includes('Ei leitud sõidukit')) {
    return {
      success: true,
      marketPrice: null,
      error: null,
      isInvalidCaptcha: false,
      vehicleNotFound: true,
    }
  }

  const priceElement = price$('b.color')
  const marketPrice = priceElement.first().text().trim() || null

  if (marketPrice) {
    return {
      success: true,
      marketPrice,
      error: null,
      isInvalidCaptcha: false,
      vehicleNotFound: false,
    }
  }

  return {
    success: false,
    marketPrice: null,
    error: 'Price not found in response',
    isInvalidCaptcha: false,
    vehicleNotFound: false,
  }
}

async function handler(req: Request, res: Response): Promise<void> {
  const regNumber = validateParam(req, res, 'regNumber', 'query')
  if (!regNumber) return

  const sanitizedRegNumber = regNumber.toUpperCase().replace(/[^A-Z0-9]/g, '')
  logger.info(`Fetching market price for registration number: ${sanitizedRegNumber}`)

  const tempDir = os.tmpdir()
  let attempt = 0
  let lastError: string | null = null

  const defaultHeaders = {
    'user-agent':
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36',
    accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
    'accept-language': 'et-EE,et;q=0.9,en;q=0.8',
  }

  while (attempt < MAX_RETRIES) {
    attempt++
    const cookieFile = path.join(
      tempDir,
      `auto24_cookies_${Date.now()}_${crypto.randomBytes(8).toString('hex')}.txt`
    )

    try {
      logger.info(`Attempt ${attempt}/${MAX_RETRIES} for ${sanitizedRegNumber}`)

      const result = await attemptPriceLookup(sanitizedRegNumber, defaultHeaders, cookieFile)

      if (result.vehicleNotFound) {
        logger.info(`Vehicle not found for registration number: ${sanitizedRegNumber}`)
        res.json({
          registrationNumber: sanitizedRegNumber,
          marketPrice: null,
          error: 'Vehicle not found',
          attempts: attempt,
        } as Auto24PriceResult)
        return
      }

      if (result.success && result.marketPrice) {
        logger.info(
          `Market price for ${sanitizedRegNumber}: ${result.marketPrice} (found on attempt ${attempt})`
        )
        res.json({
          registrationNumber: sanitizedRegNumber,
          marketPrice: result.marketPrice,
          error: null,
          attempts: attempt,
        } as Auto24PriceResult)
        return
      }

      if (result.isInvalidCaptcha) {
        logger.warn(`Invalid captcha on attempt ${attempt}, retrying...`)
        lastError = result.error
        continue
      }

      lastError = result.error
    } catch (error) {
      lastError = error instanceof Error ? error.message : 'Unknown error'
      logger.error(`Attempt ${attempt} failed: ${lastError}`)
    } finally {
      try {
        if (fs.existsSync(cookieFile)) {
          fs.unlinkSync(cookieFile)
        }
      } catch {
        logger.warn(`Failed to cleanup cookie file: ${cookieFile}`)
      }
    }
  }

  logger.error(`All ${MAX_RETRIES} attempts failed for ${sanitizedRegNumber}`)
  res.status(500).json({
    registrationNumber: sanitizedRegNumber,
    marketPrice: null,
    error: `Failed after ${MAX_RETRIES} attempts: ${lastError}`,
    attempts: attempt,
  } as Auto24PriceResult)
}

export const auto24Adapter: ServiceAdapter = {
  path: '/auto24/price',
  method: 'GET',
  serviceName: 'Auto24',
  middleware: [createRateLimiter({ max: 30 })],
  handler,
}
