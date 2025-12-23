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

    const data = JSON.parse(result)
    res.json(data)
  } catch (error) {
    logger.error(`Failed to fetch batch instrument data: ${error}`)
    res.status(500).json({ error: 'Failed to fetch batch instrument data' })
  }
}

export const lightyearAdapter: ServiceAdapter = {
  path: '/lightyear/fetch',
  method: 'GET',
  serviceName: 'Lightyear',
  middleware: [createRateLimiter({ max: 240 })],
  handler: fetchHandler,
}

export const lightyearBatchAdapter: ServiceAdapter = {
  path: '/lightyear/batch',
  method: 'POST',
  serviceName: 'Lightyear',
  middleware: [createRateLimiter({ max: 60 })],
  handler: batchHandler,
}
