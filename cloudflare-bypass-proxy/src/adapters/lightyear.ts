import { Request, Response } from 'express'
import { ServiceAdapter } from '../types'
import { handleProxyRequest, validateParam, ResponseType } from '../utils/adapter-helper'
import { sanitizeLogInput } from '../utils/log-sanitizer'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'

const LIGHTYEAR_BASE_URL = 'https://lightyear.com/fetch'

async function handler(req: Request, res: Response): Promise<void> {
  const path = validateParam(req, res, 'path', 'query')
  if (!path) return

  const encodedPath = Buffer.from(path).toString('base64').replace(/=+$/, '')

  logger.info(`Fetching data for path: ${sanitizeLogInput(path)}`)
  logger.debug(`Encoded path: ${encodedPath}`)

  await handleProxyRequest(req, res, {
    url: `${LIGHTYEAR_BASE_URL}?path=${encodedPath}&withAPIKey=true`,
    timeout: 15000,
    maxBuffer: 1024 * 1024,
    responseType: ResponseType.JSON,
    headers: {
      'user-agent':
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36',
      referer: 'https://lightyear.com/',
      accept: '*/*',
      'accept-language': 'en-US,en;q=0.9',
    },
  })
}

export const lightyearAdapter: ServiceAdapter = {
  path: '/lightyear/fetch',
  method: 'GET',
  serviceName: 'Lightyear',
  middleware: [createRateLimiter()],
  handler,
}
