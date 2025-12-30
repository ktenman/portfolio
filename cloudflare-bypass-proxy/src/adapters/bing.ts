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

function safeDecodeUri(str: string): string {
  try {
    return decodeURIComponent(str.replace(/\+/g, ' '))
  } catch {
    return str
  }
}

function extractImageResults(html: string, maxResults: number): ImageResult[] {
  const results: ImageResult[] = []
  const pattern = /murl&quot;:&quot;(https?:\/\/[^&"]+)/g
  const titlePattern = /t&quot;:&quot;([^&]+)&quot;/g
  let match
  const titles: string[] = []
  while ((match = titlePattern.exec(html)) !== null) {
    titles.push(safeDecodeUri(match[1]))
  }
  let index = 0
  while ((match = pattern.exec(html)) !== null && results.length < maxResults) {
    const imageUrl = safeDecodeUri(match[1])
    const title = titles[index] || ''
    try {
      const parsedUrl = new URL(imageUrl)
      const hostname = parsedUrl.hostname.toLowerCase()
      const isHttp = parsedUrl.protocol === 'http:' || parsedUrl.protocol === 'https:'
      const isBingHost = hostname === 'bing.com' || hostname.endsWith('.bing.com')
      if (isHttp && !isBingHost) {
        results.push({
          image: imageUrl,
          thumbnail: imageUrl,
          title,
          width: 0,
          height: 0,
        })
      }
    } catch {
      // Invalid URL, skip it
    }
    index++
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
  logger.info(`Bing image search: ${body.query}`)
  try {
    const encodedQuery = encodeURIComponent(query)
    const url = `https://www.bing.com/images/async?q=${encodedQuery}&first=0&count=35&qft=+filterui:imagesize-medium`
    const headers = {
      'User-Agent':
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
      Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
      'Accept-Language': 'en-US,en;q=0.5',
    }
    const html = await executeCurl(url, headers)
    const results = extractImageResults(html, maxResults)
    logger.info(`Bing returned ${results.length} results for: ${body.query}`)
    res.json({ success: true, results } as ImageSearchResponse)
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'Unknown error'
    logger.error(`Bing search failed: ${errorMessage}`)
    res
      .status(500)
      .json({ success: false, results: [], error: errorMessage } as ImageSearchResponse)
  }
}

export const bingAdapter: ServiceAdapter = {
  path: '/bing/images',
  method: 'POST',
  serviceName: 'BingImages',
  middleware: [createRateLimiter({ max: 120 })],
  handler,
}
