import { Request, Response } from 'express'
import { ServiceAdapter } from '../types'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'
import { execFile } from 'child_process'
import { promisify } from 'util'
import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'

const execFileAsync = promisify(execFile)
const CURL = process.env.CURL_BINARY || '/usr/local/bin/curl_ff117'
const ALLOWED_DOMAINS = (process.env.FETCH_ALLOWED_DOMAINS || 'auto24.ee')
  .split(',')
  .map(d => d.trim().toLowerCase())

function convertNetscapeCookiesToHeader(cookies: string): string {
  const cookiePairs: string[] = []
  for (const line of cookies.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const parts = trimmed.split('\t')
    if (parts.length < 7) continue
    const name = parts[5]
    const value = parts[6]
    if (name && value && /^[\w-]+$/.test(name) && !/[<>{}|\\^`\n\r]/.test(value)) {
      cookiePairs.push(`${name}=${value}`)
    }
  }
  return cookiePairs.join('; ')
}

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
    cookieHeader?: string
    saveCookiesFile?: string
    outputFile?: string
  } = {}
): Promise<{ stdout: string }> {
  const { method = 'GET', headers, cookieHeader, saveCookiesFile, outputFile } = options
  const args = ['-s', '-L']
  if (method === 'POST') {
    args.push('-X', 'POST')
  }
  if (cookieHeader) {
    args.push('-H', `Cookie: ${cookieHeader}`)
  }
  if (saveCookiesFile) {
    args.push('-c', saveCookiesFile)
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
  return { stdout }
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

  const cookieHeader = body.cookies ? convertNetscapeCookiesToHeader(body.cookies) : undefined
  let secureTempDir: string | undefined
  let saveCookiesFile: string | undefined

  if (body.saveCookies || body.returnType === 'base64') {
    secureTempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'fetch-'))
    if (body.saveCookies) {
      saveCookiesFile = path.join(secureTempDir, 'cookies.txt')
    }
  }

  try {
    if (body.returnType === 'base64') {
      const outputFile = path.join(secureTempDir!, 'output.bin')
      await executeCurl(body.url, {
        method: body.method,
        headers: body.headers,
        cookieHeader,
        saveCookiesFile,
        outputFile,
      })
      const fileData = fs.readFileSync(outputFile)
      const base64Data = fileData.toString('base64')
      let cookies: string | undefined
      if (saveCookiesFile && fs.existsSync(saveCookiesFile)) {
        cookies = fs.readFileSync(saveCookiesFile, 'utf-8')
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
        cookieHeader,
        saveCookiesFile,
      })
      let cookies: string | undefined
      if (saveCookiesFile && fs.existsSync(saveCookiesFile)) {
        cookies = fs.readFileSync(saveCookiesFile, 'utf-8')
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
    if (secureTempDir) {
      try {
        fs.rmSync(secureTempDir, { recursive: true, force: true })
      } catch {
        logger.warn(`Failed to cleanup temp files in: ${secureTempDir}`)
      }
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
