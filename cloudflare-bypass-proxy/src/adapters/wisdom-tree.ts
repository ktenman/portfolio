import { Request, Response } from 'express'
import { ServiceAdapter } from '../types'
import { handleProxyRequest, validateParam, ResponseType } from '../utils/adapter-helper'
import { sanitizeLogInput } from '../utils/log-sanitizer'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'

const WISDOMTREE_BASE_URL = 'https://www.wisdomtree.eu/en-gb/global/etf-details/modals/all-holdings'

async function handler(req: Request, res: Response): Promise<void> {
  const etfId = validateParam(req, res, 'etfId', 'params')
  if (!etfId) return

  logger.info(`Fetching holdings for ${sanitizeLogInput(etfId)}`)

  await handleProxyRequest(req, res, {
    url: `${WISDOMTREE_BASE_URL}?id={${etfId}}`,
    timeout: 15000,
    maxBuffer: 2 * 1024 * 1024,
    responseType: ResponseType.HTML,
  })
}

export const wisdomTreeAdapter: ServiceAdapter = {
  path: '/wisdomtree/holdings/:etfId',
  method: 'GET',
  serviceName: 'WisdomTree',
  middleware: [createRateLimiter({ max: 30 })],
  handler,
}
