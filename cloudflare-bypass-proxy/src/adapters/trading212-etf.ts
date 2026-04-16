import { Request, Response } from 'express'
import { ServiceAdapter } from '../types'
import { handleProxyRequest, validateParam, ResponseType } from '../utils/adapter-helper'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'

const HOLDINGS_URL = 'https://live.services.trading212.com/rest/v2/exchange-traded-funds/holdings'
const SUMMARY_URL = 'https://live.services.trading212.com/rest/v2/exchange-traded-funds'

async function holdingsHandler(req: Request, res: Response): Promise<void> {
  const ticker = validateParam(req, res, 'ticker', 'query')
  if (!ticker) return
  logger.info(`Fetching ETF holdings for ticker: ${ticker}`)
  await handleProxyRequest(req, res, {
    url: `${HOLDINGS_URL}?ticker=${encodeURIComponent(ticker)}`,
    timeout: 10000,
    maxBuffer: 1024 * 1024,
    responseType: ResponseType.JSON,
  })
}

async function summaryHandler(req: Request, res: Response): Promise<void> {
  const ticker = validateParam(req, res, 'ticker', 'query')
  if (!ticker) return
  logger.info(`Fetching ETF summary for ticker: ${ticker}`)
  await handleProxyRequest(req, res, {
    url: `${SUMMARY_URL}?ticker=${encodeURIComponent(ticker)}`,
    timeout: 10000,
    maxBuffer: 1024 * 1024,
    responseType: ResponseType.JSON,
  })
}

export const trading212EtfHoldingsAdapter: ServiceAdapter = {
  path: '/trading212/etf-holdings',
  method: 'GET',
  serviceName: 'Trading212EtfHoldings',
  middleware: [createRateLimiter()],
  handler: holdingsHandler,
}

export const trading212EtfSummaryAdapter: ServiceAdapter = {
  path: '/trading212/etf-summary',
  method: 'GET',
  serviceName: 'Trading212EtfSummary',
  middleware: [createRateLimiter()],
  handler: summaryHandler,
}
