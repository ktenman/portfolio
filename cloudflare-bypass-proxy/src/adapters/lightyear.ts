import { Request, Response } from 'express'
import { ServiceAdapter } from '../types'
import { handleProxyRequest, validateParam, ResponseType } from '../utils/adapter-helper'
import { sanitizeLogInput } from '../utils/log-sanitizer'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'
import { execCurl } from '../utils/curl-executor'

const LIGHTYEAR_FETCH_URL = 'https://lightyear.com/fetch'
const LIGHTYEAR_BATCH_URL = 'https://api.lightyear.com/v1/instrument/batch'

async function fetchHandler(req: Request, res: Response): Promise<void> {
  const path = validateParam(req, res, 'path', 'query')
  if (!path) return

  const encodedPath = Buffer.from(path).toString('base64').replace(/=+$/, '')

  logger.info(`Fetching data for path: ${sanitizeLogInput(path)}`)
  logger.debug(`Encoded path: ${encodedPath}`)

  await handleProxyRequest(req, res, {
    url: `${LIGHTYEAR_FETCH_URL}?path=${encodedPath}&withAPIKey=true`,
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

async function batchHandler(req: Request, res: Response): Promise<void> {
  const instrumentIds = req.body

  if (!Array.isArray(instrumentIds) || instrumentIds.length === 0) {
    res.status(400).json({ error: 'Request body must be a non-empty array of instrument IDs' })
    return
  }

  const startTime = Date.now()
  logger.info(`Fetching batch instrument data for ${instrumentIds.length} instruments`)

  try {
    const result = await execCurl({
      url: LIGHTYEAR_BATCH_URL,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: '*/*',
        'Accept-Language': 'en',
        Origin: 'https://lightyear.com',
        Referer: 'https://lightyear.com/',
        'User-Agent':
          'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36',
      },
      body: JSON.stringify(instrumentIds),
      timeout: 30000,
      maxBuffer: 5 * 1024 * 1024,
    })

    const data: unknown = JSON.parse(result)
    const duration = Date.now() - startTime
    if (!Array.isArray(data)) {
      logger.error(`Batch fetch returned non-array response after ${duration}ms`)
      res.status(502).json({ error: 'Invalid response format from Lightyear API' })
      return
    }
    logger.info(
      `Batch fetch completed: ${data.length}/${instrumentIds.length} instruments returned in ${duration}ms`
    )
    res.json(data)
  } catch (error) {
    const duration = Date.now() - startTime
    logger.error(`Failed to fetch batch instrument data after ${duration}ms: ${error}`)
    res.status(500).json({ error: 'Failed to fetch batch instrument data' })
  }
}

export const lightyearAdapter: ServiceAdapter = {
  path: '/lightyear/fetch',
  method: 'GET',
  serviceName: 'Lightyear',
  middleware: [createRateLimiter({ max: 120 })],
  handler: fetchHandler,
}

export const lightyearBatchAdapter: ServiceAdapter = {
  path: '/lightyear/batch',
  method: 'POST',
  serviceName: 'Lightyear Batch',
  middleware: [createRateLimiter({ max: 120 })],
  handler: batchHandler,
}

interface SearchResult {
  results?: Array<{
    instrument?: {
      id?: string
      symbol?: string
      exchange?: string
      currency?: string
    }
  }>
}

// Exchange mapping must match LightyearScrapingProperties.EXCHANGE_MAPPING in Kotlin
const EXCHANGE_MAPPING: Record<string, string> = {
  GER: 'XETRA',
  AEX: 'AMS',
  MIL: 'MIL',
  LON: 'LSE',
}

async function lookupUuidHandler(req: Request, res: Response): Promise<void> {
  const symbol = validateParam(req, res, 'symbol', 'query')
  if (!symbol) return
  const parts = symbol.split(':')
  const ticker = parts[0]
  const exchange = parts[1]?.toUpperCase()
  const currency = parts[2]?.toUpperCase()
  const targetExchange = exchange ? EXCHANGE_MAPPING[exchange] || exchange : undefined
  logger.info(
    `Looking up UUID for symbol: ${sanitizeLogInput(symbol)} (ticker: ${ticker}, exchange: ${targetExchange || 'any'}, currency: ${currency || 'any'})`
  )

  let result: string
  try {
    const searchPath = `/v1/instrument/search?value=${encodeURIComponent(ticker)}`
    const encodedPath = Buffer.from(searchPath).toString('base64').replace(/=+$/, '')
    const searchUrl = `${LIGHTYEAR_FETCH_URL}?path=${encodedPath}&withAPIKey=true`
    result = await execCurl({
      url: searchUrl,
      timeout: 15000,
      maxBuffer: 1024 * 1024,
      headers: {
        'User-Agent':
          'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36',
        Accept: '*/*',
        'Accept-Language': 'en',
        Referer: 'https://lightyear.com/',
      },
    })
  } catch (error) {
    logger.error(`Network error looking up UUID for ${sanitizeLogInput(symbol)}: ${error}`)
    res.status(502).json({ error: 'Failed to connect to Lightyear API', symbol })
    return
  }

  let data: SearchResult
  try {
    data = JSON.parse(result)
  } catch (error) {
    logger.error(`Failed to parse response for ${sanitizeLogInput(symbol)}: ${error}`)
    res.status(502).json({ error: 'Invalid response from Lightyear API', symbol })
    return
  }

  if (!data.results || data.results.length === 0) {
    logger.warn(`No instruments found for symbol: ${sanitizeLogInput(symbol)}`)
    res.status(404).json({ error: 'Instrument not found', symbol })
    return
  }

  const tickerMatches = data.results.filter(
    r => r.instrument?.symbol?.toUpperCase() === ticker.toUpperCase()
  )
  let match = tickerMatches[0]
  if (tickerMatches.length > 1 && targetExchange) {
    const exchangeMatch = tickerMatches.find(
      r => r.instrument?.exchange?.toUpperCase() === targetExchange
    )
    if (exchangeMatch) match = exchangeMatch
  }
  if (tickerMatches.length > 1 && !match && currency) {
    const currencyMatch = tickerMatches.find(
      r => r.instrument?.currency?.toUpperCase() === currency
    )
    if (currencyMatch) match = currencyMatch
  }

  const instrument = match?.instrument
  if (!instrument || !instrument.id) {
    logger.warn(`No matching instrument found for symbol: ${sanitizeLogInput(symbol)}`)
    res.status(404).json({ error: 'Matching instrument not found', symbol })
    return
  }

  logger.info(
    `Found UUID ${instrument.id} for symbol: ${sanitizeLogInput(symbol)} (exchange: ${instrument.exchange})`
  )
  res.json({ symbol, uuid: instrument.id })
}

export const lightyearLookupAdapter: ServiceAdapter = {
  path: '/lightyear/lookup',
  method: 'GET',
  serviceName: 'Lightyear Lookup',
  middleware: [createRateLimiter({ max: 120 })],
  handler: lookupUuidHandler,
}
