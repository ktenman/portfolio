import { Request, Response } from 'express'
import { ServiceAdapter } from '../types'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'
import {
  ImageSearchRequest,
  ImageResult,
  ImageSearchResponse,
  executeCurl,
} from '../utils/image-utils'

async function getVqd(query: string): Promise<string | null> {
  const url = `https://duckduckgo.com/?t=h_&q=${encodeURIComponent(query)}&ia=images&iax=images`
  const headers = {
    'User-Agent':
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
  }
  try {
    const html = await executeCurl(url, headers)
    const vqdMatch = html.match(/vqd=["']?([^"'&]+)["']?/)
    if (vqdMatch) return vqdMatch[1]
    const vqdMatch2 = html.match(/vqd=([\d-]+)/)
    return vqdMatch2?.[1] ?? null
  } catch {
    return null
  }
}

function extractImageResults(json: string, maxResults: number): ImageResult[] {
  const results: ImageResult[] = []
  try {
    const data = JSON.parse(json)
    const images = data.results || []
    for (const img of images) {
      if (results.length >= maxResults) break
      if (img.image && img.image.startsWith('http')) {
        results.push({
          image: img.image,
          thumbnail: img.thumbnail || img.image,
          title: img.title || '',
          width: img.width || 0,
          height: img.height || 0,
        })
      }
    }
  } catch {
    logger.warn('Failed to parse DuckDuckGo response as JSON')
  }
  return results
}

async function handler(req: Request, res: Response): Promise<void> {
  const body = req.body as ImageSearchRequest
  if (!body.query || body.query.trim() === '') {
    res
      .status(400)
      .json({ success: false, results: [], error: 'Query is required' } as ImageSearchResponse)
    return
  }
  const query = `${body.query.trim()} logo transparent png`
  const maxResults = body.maxResults || 10
  logger.info(`DuckDuckGo image search: ${body.query}`)
  try {
    const vqd = await getVqd(query)
    if (!vqd) {
      logger.warn('Failed to get DuckDuckGo vqd token')
      res.json({
        success: false,
        results: [],
        error: 'Failed to initialize search',
      } as ImageSearchResponse)
      return
    }
    const encodedQuery = encodeURIComponent(query)
    const url = `https://duckduckgo.com/i.js?l=us-en&o=json&q=${encodedQuery}&vqd=${vqd}&f=,,,,,&p=1`
    const headers = {
      'User-Agent':
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
      Accept: 'application/json,*/*;q=0.8',
      Referer: 'https://duckduckgo.com/',
    }
    const json = await executeCurl(url, headers)
    const results = extractImageResults(json, maxResults)
    logger.info(`DuckDuckGo returned ${results.length} results for: ${body.query}`)
    res.json({ success: true, results } as ImageSearchResponse)
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'Unknown error'
    logger.error(`DuckDuckGo search failed: ${errorMessage}`)
    res
      .status(500)
      .json({ success: false, results: [], error: errorMessage } as ImageSearchResponse)
  }
}

export const duckDuckGoAdapter: ServiceAdapter = {
  path: '/duckduckgo/images',
  method: 'POST',
  serviceName: 'DuckDuckGoImages',
  middleware: [createRateLimiter({ max: 30 })],
  handler,
}
