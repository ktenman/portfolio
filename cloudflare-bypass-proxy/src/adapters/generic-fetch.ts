import { Request, Response } from 'express'
import { ServiceAdapter } from '../types'
import { handleProxyRequest, ResponseType } from '../utils/adapter-helper'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'

async function handler(req: Request, res: Response): Promise<void> {
  const url = req.query.url as string
  const format = (req.query.format as string)?.toLowerCase() || 'html'

  if (!url) {
    res.status(400).json({ error: 'Missing url parameter' })
    return
  }

  try {
    new URL(url)
  } catch {
    res.status(400).json({ error: 'Invalid URL format' })
    return
  }

  const responseType = format === 'json' ? ResponseType.JSON : ResponseType.HTML

  logger.info(`Fetching URL: ${url} (format: ${format})`)

  const headersParam = req.query.headers as string
  let customHeaders: Record<string, string> | undefined

  if (headersParam) {
    try {
      customHeaders = JSON.parse(headersParam)
    } catch {
      res.status(400).json({ error: 'Invalid headers JSON format' })
      return
    }
  }

  await handleProxyRequest(req, res, {
    url,
    timeout: 30000,
    maxBuffer: 5 * 1024 * 1024,
    responseType,
    headers: customHeaders,
  })
}

export const genericFetchAdapter: ServiceAdapter = {
  path: '/fetch',
  method: 'GET',
  serviceName: 'GenericFetch',
  middleware: [createRateLimiter({ windowMs: 60000, max: 30 })],
  handler,
}
