import { Request, Response } from 'express'
import { ServiceAdapter } from '../types'
import { handleProxyRequest, validateParam, ResponseType } from '../utils/adapter-helper'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'

const TRADING212_URL = 'https://live.services.trading212.com/public-instrument-cache/v1/prices'

async function handler(req: Request, res: Response): Promise<void> {
  const tickers = validateParam(req, res, 'tickers', 'query')
  if (!tickers) return

  logger.info(`Fetching prices for tickers: ${tickers}`)

  await handleProxyRequest(req, res, {
    url: `${TRADING212_URL}?tickers=${tickers}`,
    timeout: 10000,
    maxBuffer: 1024 * 1024,
    responseType: ResponseType.JSON,
  })
}

export const trading212Adapter: ServiceAdapter = {
  path: '/prices',
  method: 'GET',
  serviceName: 'Trading212',
  middleware: [createRateLimiter()],
  handler,
}
