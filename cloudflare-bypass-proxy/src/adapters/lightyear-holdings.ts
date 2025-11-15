import { Request, Response } from 'express'
import { ServiceAdapter } from '../types'
import { handleProxyRequest, validateParam, ResponseType } from '../utils/adapter-helper'
import { sanitizeLogInput } from '../utils/log-sanitizer'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'

const LIGHTYEAR_ETF_BASE = 'https://lightyear.com/en/etf'

async function handler(req: Request, res: Response): Promise<void> {
  const path = validateParam(req, res, 'path', 'query')
  const page = validateParam(req, res, 'page', 'query')

  if (!path || !page) return

  logger.info(
    `Fetching Lightyear holdings for ETF: ${sanitizeLogInput(path)}, page: ${sanitizeLogInput(page)}`
  )

  await handleProxyRequest(req, res, {
    url: `${LIGHTYEAR_ETF_BASE}/${path}/holdings/${page}`,
    responseType: ResponseType.HTML,
    timeout: 15000,
    maxBuffer: 2 * 1024 * 1024,
  })
}

export const lightyearHoldingsAdapter: ServiceAdapter = {
  path: '/lightyear/etf/holdings',
  method: 'GET',
  serviceName: 'Lightyear Holdings',
  middleware: [createRateLimiter({ max: 30 })],
  handler,
}
