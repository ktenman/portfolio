import { Request, Response } from 'express'
import { ServiceAdapter } from '../types'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'
import { execFile } from 'child_process'
import { promisify } from 'util'
import * as crypto from 'crypto'
import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'

const execFileAsync = promisify(execFile)
const CURL = process.env.CURL_BINARY || '/usr/local/bin/curl_ff117'
const ALLOWED_DOMAINS = (process.env.FETCH_ALLOWED_DOMAINS || 'auto24.ee')
  .split(',')
  .map(d => d.trim().toLowerCase())

function isUrlAllowed(urlString: string): { allowed: boolean; reason?: string } {
  try {
    const url = new URL(urlString)

    if (url.protocol !== 'https:') {
      return { allowed: false, reason: 'Only HTTPS URLs are allowed' }
    }

    const hostname = url.hostname.toLowerCase()

    if (hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1') {
      return { allowed: false, reason: 'Localhost URLs are not allowed' }
    }

    if (/^(10\.|172\.(1[6-9]|2[0-9]|3[01])\.|192\.168\.)/.test(hostname)) {
      return { allowed: false, reason: 'Private IP addresses are not allowed' }
    }

    const isAllowed = ALLOWED_DOMAINS.some(
      domain => hostname === domain || hostname.endsWith(`.${domain}`)
    )

    if (!isAllowed) {
      return { allowed: false, reason: `Domain not in allowlist: ${hostname}` }
    }

    return { allowed: true }
  } catch {
    return { allowed: false, reason: 'Invalid URL format' }
  }
}

interface FetchRequest {
  url: string
  method?: 'GET' | 'POST'
  headers?: Record<string, string>
  cookies?: string
  saveCookies?: boolean
  returnType?: 'html' | 'json' | 'base64'
}

interface FetchResponse {
  success: boolean
  data: string | object | null
  cookies?: string
  error?: string
  contentType?: string
}

async function executeCurl(
  url: string,
  options: {
    method?: string
    headers?: Record<string, string>
    cookieFile?: string
    saveCookies?: boolean
    outputFile?: string
  } = {}
): Promise<{ stdout: string; cookieFile?: string }> {
  const { method = 'GET', headers, cookieFile, saveCookies, outputFile } = options

  const args = ['-s', '-L']

  if (method === 'POST') {
    args.push('-X', 'POST')
  }

  const tempCookieFile = cookieFile || path.join(os.tmpdir(), `fetch_cookies_${Date.now()}.txt`)

  if (cookieFile || saveCookies) {
    args.push('-b', tempCookieFile)
    if (saveCookies) {
      args.push('-c', tempCookieFile)
    }
  }

  if (headers) {
    for (const [key, value] of Object.entries(headers)) {
      args.push('-H', `${key}: ${value}`)
    }
  }

  if (outputFile) {
    args.push('-o', outputFile)
  }

  args.push(url)

  const { stdout } = await execFileAsync(CURL, args, {
    timeout: 30000,
    maxBuffer: 5 * 1024 * 1024,
  })

  return { stdout, cookieFile: tempCookieFile }
}

async function handler(req: Request, res: Response): Promise<void> {
  const body = req.body as FetchRequest

  if (!body.url) {
    res.status(400).json({ success: false, error: 'URL is required' } as FetchResponse)
    return
  }

  const urlValidation = isUrlAllowed(body.url)
  if (!urlValidation.allowed) {
    logger.warn(`URL rejected: ${body.url} - ${urlValidation.reason}`)
    res.status(403).json({ success: false, error: urlValidation.reason } as FetchResponse)
    return
  }

  logger.info(`Fetching: ${body.url}`)

  const tempDir = os.tmpdir()
  const sessionId = `${Date.now()}_${crypto.randomBytes(16).toString('hex')}`
  const cookieFile = path.join(tempDir, `cookies_${sessionId}.txt`)

  try {
    if (body.cookies) {
      fs.writeFileSync(cookieFile, body.cookies)
    }

    const useCookieFile = body.cookies || body.saveCookies

    if (body.returnType === 'base64') {
      const outputFile = path.join(tempDir, `output_${sessionId}.bin`)

      await executeCurl(body.url, {
        method: body.method,
        headers: body.headers,
        cookieFile: useCookieFile ? cookieFile : undefined,
        saveCookies: body.saveCookies,
        outputFile,
      })

      const fileData = fs.readFileSync(outputFile)
      const base64Data = fileData.toString('base64')

      let cookies: string | undefined
      if (body.saveCookies && fs.existsSync(cookieFile)) {
        cookies = fs.readFileSync(cookieFile, 'utf-8')
      }

      fs.unlinkSync(outputFile)

      res.json({
        success: true,
        data: base64Data,
        cookies,
        contentType: 'base64',
      } as FetchResponse)
    } else {
      const result = await executeCurl(body.url, {
        method: body.method,
        headers: body.headers,
        cookieFile: useCookieFile ? cookieFile : undefined,
        saveCookies: body.saveCookies,
      })

      let cookies: string | undefined
      if (body.saveCookies && fs.existsSync(cookieFile)) {
        cookies = fs.readFileSync(cookieFile, 'utf-8')
      }

      let data: string | object = result.stdout

      if (body.returnType === 'json') {
        try {
          data = JSON.parse(result.stdout)
        } catch {
          logger.warn('Failed to parse response as JSON')
        }
      }

      res.json({
        success: true,
        data,
        cookies,
        contentType: body.returnType || 'html',
      } as FetchResponse)
    }
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'Unknown error'
    logger.error(`Fetch failed: ${errorMessage}`)
    res.status(500).json({
      success: false,
      data: null,
      error: errorMessage,
    } as FetchResponse)
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

export const fetchAdapter: ServiceAdapter = {
  path: '/fetch',
  method: 'POST',
  serviceName: 'GenericFetch',
  middleware: [createRateLimiter({ max: 60 })],
  handler,
}
