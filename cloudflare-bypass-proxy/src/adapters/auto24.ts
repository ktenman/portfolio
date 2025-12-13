import { Request, Response } from 'express'
import { execFile } from 'child_process'
import { promisify } from 'util'
import { ServiceAdapter } from '../types'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'
import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'
import { randomUUID } from 'crypto'

const execFileAsync = promisify(execFile)
const CURL = process.env.CURL_BINARY || '/usr/local/bin/curl_ff117'

interface SessionData {
  cookieFile: string
  regNr: string
  createdAt: number
}

const sessions = new Map<string, SessionData>()
const SESSION_TTL_MS = 5 * 60 * 1000

function cleanupExpiredSessions(): void {
  const now = Date.now()
  for (const [sessionId, data] of sessions.entries()) {
    if (now - data.createdAt > SESSION_TTL_MS) {
      try {
        if (fs.existsSync(data.cookieFile)) {
          fs.unlinkSync(data.cookieFile)
        }
      } catch {
        logger.warn(`Failed to delete cookie file: ${data.cookieFile}`)
      }
      sessions.delete(sessionId)
    }
  }
}

const cleanupTimer = setInterval(cleanupExpiredSessions, 60000)
cleanupTimer.unref()

async function executeCurlWithCookies(
  url: string,
  cookieFile: string,
  options: {
    method?: 'GET' | 'POST'
    data?: Record<string, string>
    includeHeaders?: boolean
  } = {}
): Promise<{ body: string; headers?: string }> {
  const { method = 'GET', data, includeHeaders = false } = options

  const args = ['-s', '-L', '-c', cookieFile, '-b', cookieFile]

  if (includeHeaders) {
    args.push('-i')
  }

  args.push(
    '-H',
    'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0',
    '-H',
    'Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
    '-H',
    'Accept-Language: en-US,en;q=0.5'
  )

  if (method === 'POST' && data) {
    args.push('-X', 'POST')
    const formData = Object.entries(data)
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
      .join('&')
    args.push('-d', formData)
    args.push('-H', 'Content-Type: application/x-www-form-urlencoded')
  }

  args.push(url)

  const { stdout } = await execFileAsync(CURL, args, {
    timeout: 30000,
    maxBuffer: 5 * 1024 * 1024,
  })

  if (includeHeaders) {
    const headerBodySplit = stdout.indexOf('\r\n\r\n')
    if (headerBodySplit !== -1) {
      return {
        headers: stdout.slice(0, headerBodySplit),
        body: stdout.slice(headerBodySplit + 4),
      }
    }
  }

  return { body: stdout }
}

function extractCaptchaImageUrl(html: string): string | null {
  const match = html.match(
    /<img[^>]+src=["']([^"']+secimg\.php[^"']+)["'][^>]+id=["']vpc_captcha["']/
  )
  if (match) return match[1]

  const altMatch = html.match(/<img[^>]+id=["']vpc_captcha["'][^>]+src=["']([^"']+)["']/)
  if (altMatch) return altMatch[1]

  const srcMatch = html.match(/src=["']([^"']+secimg\.php[^"']+)["']/)
  if (srcMatch) return srcMatch[1]

  const genericMatch = html.match(/<img[^>]+src=["']([^"']+)["'][^>]+id=["']vpc_captcha["']/)
  if (genericMatch) return genericMatch[1]

  return null
}

function extractPrice(html: string): string | null {
  const priceMatch = html.match(
    /<div[^>]*class=["'][^"']*result[^"']*["'][^>]*>[\s\S]*?<b>([^<]+)<\/b>/i
  )
  if (priceMatch) return priceMatch[1].trim()

  const altMatch = html.match(/(\d+\s*€\s*kuni\s*\d+\s*€)/i)
  if (altMatch) return altMatch[1].trim()

  return null
}

function extractCarInfo(html: string): string | null {
  const carMatch = html.match(/<div[^>]*class=["'][^"']*result[^"']*["'][^>]*>([\s\S]*?)<\/div>/i)
  if (carMatch) {
    const text = carMatch[1]
      .replace(/<[^>]+>/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
    return text || null
  }
  return null
}

async function getCaptchaHandler(req: Request, res: Response): Promise<void> {
  const regNr = req.body.regNr as string

  if (!regNr) {
    res.status(400).json({ error: 'Missing regNr parameter' })
    return
  }

  const sessionId = randomUUID()
  const cookieFile = path.join(os.tmpdir(), `auto24_${sessionId}.txt`)

  try {
    const baseUrl = 'https://www.auto24.ee/ostuabi/?t=soiduki-turuhinna-paring'
    logger.info(`Auto24: Initializing session for ${regNr}`)

    await executeCurlWithCookies(baseUrl, cookieFile)

    const regNrEncoded = encodeURIComponent(regNr)
    const searchUrl = `${baseUrl}&vpc_reg_nr=${regNrEncoded}&vpc_reg_search=1`
    logger.info(`Auto24: Submitting registration number to get CAPTCHA`)
    const { body: searchHtml } = await executeCurlWithCookies(searchUrl, cookieFile)

    const captchaUrl = extractCaptchaImageUrl(searchHtml)
    if (!captchaUrl) {
      const price = extractPrice(searchHtml)
      if (price) {
        res.json({
          status: 'success',
          price,
          carInfo: extractCarInfo(searchHtml),
          message: 'No CAPTCHA required, price found directly',
        })
        return
      }
      logger.warn('Auto24: Could not find CAPTCHA image in response')
      logger.debug(`Auto24: HTML snippet: ${searchHtml.slice(0, 2000)}`)
      res
        .status(500)
        .json({ error: 'Could not find CAPTCHA image', html: searchHtml.slice(0, 1000) })
      return
    }

    const fullCaptchaUrl = captchaUrl.startsWith('http')
      ? captchaUrl
      : `https://www.auto24.ee${captchaUrl}`

    logger.info(`Auto24: Found CAPTCHA at ${fullCaptchaUrl}`)

    const captchaArgs = [
      '-s',
      '-L',
      '-c',
      cookieFile,
      '-b',
      cookieFile,
      '-H',
      'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0',
      fullCaptchaUrl,
    ]

    const { stdout: captchaBuffer } = await execFileAsync(CURL, captchaArgs, {
      timeout: 10000,
      maxBuffer: 1024 * 1024,
      encoding: 'buffer' as BufferEncoding,
    })

    const captchaBase64 = Buffer.from(captchaBuffer).toString('base64')

    sessions.set(sessionId, {
      cookieFile,
      regNr,
      createdAt: Date.now(),
    })

    const cookieContent = fs.existsSync(cookieFile)
      ? fs.readFileSync(cookieFile, 'utf-8')
      : 'No cookies'
    logger.info(`Auto24: Session ${sessionId} created with CAPTCHA`)
    logger.debug(`Auto24: Cookies: ${cookieContent}`)

    res.json({
      sessionId,
      captchaImage: captchaBase64,
      captchaUrl: fullCaptchaUrl,
      message: 'CAPTCHA required',
    })
  } catch (error) {
    try {
      if (fs.existsSync(cookieFile)) {
        fs.unlinkSync(cookieFile)
      }
    } catch {
      logger.warn(`Failed to cleanup cookie file: ${cookieFile}`)
    }

    const errorMessage = error instanceof Error ? error.message : 'Unknown error'
    logger.error(`Auto24 getCaptcha error: ${errorMessage}`)
    res.status(500).json({ error: 'Failed to get CAPTCHA', details: errorMessage })
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

  try {
    logger.info(`Auto24: Submitting CAPTCHA solution '${solution}' for session ${sessionId}`)

    const cookieContent = fs.existsSync(session.cookieFile)
      ? fs.readFileSync(session.cookieFile, 'utf-8')
      : 'No cookies'
    logger.debug(`Auto24: Submit cookies: ${cookieContent}`)

    const regNrEncoded = encodeURIComponent(session.regNr)
    const solutionEncoded = encodeURIComponent(solution)
    const submitUrl = `https://www.auto24.ee/ostuabi/?t=soiduki-turuhinna-paring&vpc_reg_nr=${regNrEncoded}&checksec1=${solutionEncoded}&vpc_reg_search=1`
    logger.info(`Auto24: Submit URL: ${submitUrl}`)

    const { body: resultHtml } = await executeCurlWithCookies(submitUrl, session.cookieFile)

    logger.debug(`Auto24: Submit response length: ${resultHtml.length}`)
    const titleMatch = resultHtml.match(/<title>([^<]+)<\/title>/i)
    logger.info(`Auto24: Response page title: ${titleMatch ? titleMatch[1] : 'unknown'}`)

    const price = extractPrice(resultHtml)
    const carInfo = extractCarInfo(resultHtml)

    if (price) {
      logger.info(`Auto24: Price found: ${price}`)
      res.json({
        status: 'success',
        price,
        carInfo,
      })
    } else if (resultHtml.includes('vpc_captcha') || resultHtml.includes('checksec1')) {
      logger.warn('Auto24: CAPTCHA still present, solution may be incorrect')
      res.json({
        status: 'captcha_failed',
        message: 'CAPTCHA solution incorrect, please try again',
      })
    } else {
      logger.warn('Auto24: Could not extract price from response')
      res.status(500).json({
        error: 'Could not extract price',
        html: resultHtml.slice(0, 2000),
      })
    }
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'Unknown error'
    logger.error(`Auto24 submitCaptcha error: ${errorMessage}`)
    res.status(500).json({ error: 'Failed to submit CAPTCHA', details: errorMessage })
  } finally {
    try {
      if (fs.existsSync(session.cookieFile)) {
        fs.unlinkSync(session.cookieFile)
      }
    } catch {
      logger.warn(`Failed to cleanup cookie file: ${session.cookieFile}`)
    }
    sessions.delete(sessionId)
  }
}

export const auto24CaptchaAdapter: ServiceAdapter = {
  path: '/auto24/captcha',
  method: 'POST',
  serviceName: 'Auto24Captcha',
  middleware: [createRateLimiter({ windowMs: 60000, max: 10 })],
  handler: getCaptchaHandler,
}

export const auto24SubmitAdapter: ServiceAdapter = {
  path: '/auto24/submit',
  method: 'POST',
  serviceName: 'Auto24Submit',
  middleware: [createRateLimiter({ windowMs: 60000, max: 10 })],
  handler: submitCaptchaHandler,
}
