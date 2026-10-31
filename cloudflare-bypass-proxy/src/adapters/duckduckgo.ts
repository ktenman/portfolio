import { Request, Response } from 'express'
import { load, CheerioAPI } from 'cheerio'
import { setTimeout as delay } from 'timers/promises'
import { ServiceAdapter } from '../types'
import { createRateLimiter } from '../middleware/rate-limiter'
import { logger } from '../utils/logger'
import { errorMessage, validateParam } from '../utils/adapter-helper'
import {
  fetchPublicHtml,
  fetchPublicImage,
  isPublicWebUrl,
  ImageResult,
  SearchPage,
} from '../utils/image-utils'

const MAX_PAGES = 4
const MAX_RESULTS = 24
const MAX_PAGE_IMAGES = 8
const RETRY_DELAY_MS = 1000
const CONTROL_QUERIES = ['Apple', 'Microsoft']
const RASTER_IMAGE = /\.(png|jpe?g|webp)(?:\?|$)/i
const WORDS = /[\p{L}\p{N}]+(?:&[\p{L}\p{N}]+)*/gu
const LOGO_WORD = /^(logos?|icons?|brands?|brandmarks?|marks?|symbols?)$/i
const IGNORED_WORDS = new Set([
  'logo',
  'logos',
  'png',
  'transparent',
  'inc',
  'corp',
  'corporation',
  'ltd',
  'limited',
  'plc',
  'co',
  'of',
  'the',
  'and',
  'group',
  'bank',
  'motor',
  'motors',
  'energy',
  'holding',
  'holdings',
  'company',
  'technology',
  'technologies',
  'international',
  'global',
  'national',
  'industries',
  'industry',
  'industrial',
  'financial',
  'investment',
  'power',
  'electric',
  'electronics',
  'resources',
  'services',
  'systems',
  'capital',
  'securities',
  'insurance',
  'pharmaceutical',
  'development',
  'construction',
  'engineering',
  'materials',
  'mining',
  'steel',
  'chemical',
  'properties',
  'real',
  'estate',
  'trust',
  'reit',
  'new',
  'sa',
  'ag',
  'ab',
  'as',
  'nv',
  'se',
  'spa',
  'pcl',
  'bhd',
  'tbk',
  'de',
])

function tokens(text: string): string[] {
  return text.toLowerCase().match(WORDS) || []
}

function words(query: string): string[] {
  const all = tokens(query).filter(word => word.length > 1)
  const distinctive = all.filter(word => !IGNORED_WORDS.has(word))
  return distinctive.length ? distinctive : all
}

function matchesLogoBrand(text: string, terms: string[]): boolean {
  const found = tokens(text)
  return found.some(
    (token, index) =>
      terms.includes(token) &&
      found.slice(Math.max(0, index - 3), index + 4).some(word => LOGO_WORD.test(word))
  )
}

function webUrl(value: string | undefined, base: string): string | null {
  if (!value) return null
  try {
    const url = new URL(value, base)
    url.hash = ''
    return isPublicWebUrl(url.href) ? url.href : null
  } catch {
    return null
  }
}

function resultPages(page: SearchPage): string[] {
  const $ = load(page.html)
  if ($('form[action*="anomaly"], .anomaly-modal').length)
    throw new Error('DuckDuckGo returned a bot challenge')
  const results = $('.result__a')
    .map((_, element) => {
      const link = webUrl($(element).attr('href'), page.url)
      if (!link) return null
      const url = new URL(link)
      return webUrl(url.searchParams.get('uddg') || link, page.url)
    })
    .get()
    .filter(url => !/(^|\.)duckduckgo\.com$/.test(new URL(url).hostname))
  if (!results.length && !$('.no-results, .result--no-result').length)
    throw new Error('DuckDuckGo returned no recognizable search results')
  return [...new Set(results)].slice(0, MAX_PAGES)
}

function imageCandidate(
  value: string | undefined,
  title: string,
  page: SearchPage,
  terms: string[]
): ImageResult | null {
  const url = webUrl(value, page.url)
  if (!url || !RASTER_IMAGE.test(url)) return null
  const label = `${new URL(url).pathname} ${title}`
  if (!matchesLogoBrand(label, terms)) return null
  return { image: url, thumbnail: url, title: title.slice(0, 200) }
}

function metadataImages($: CheerioAPI, page: SearchPage, terms: string[]): ImageResult[] {
  const title = $('head > title').first().text().trim()
  return $('meta[property="og:image"], meta[name="twitter:image"]')
    .map((_, element) => imageCandidate($(element).attr('content'), title, page, terms))
    .get()
}

function pageImages(page: SearchPage, terms: string[]): ImageResult[] {
  const $ = load(page.html)
  const images = $('img, a')
    .map((_, element) => {
      const node = $(element)
      const value = node.attr('data-src') || node.attr('src') || node.attr('href')
      const title = node.attr('alt') || node.attr('title') || node.text().trim()
      return imageCandidate(value, title, page, terms)
    })
    .get()
  return [
    ...new Map(
      [...images, ...metadataImages($, page, terms)].map(image => [image.image, image])
    ).values(),
  ]
}

function interleave(pages: ImageResult[][], limit: number): ImageResult[] {
  const results = new Map<string, ImageResult>()
  for (let index = 0; index < MAX_PAGE_IMAGES; index++) {
    for (const page of pages) {
      const image = page[index]
      if (image && results.size < limit) results.set(image.image, image)
    }
  }
  return [...results.values()]
}

async function searchPages(query: string): Promise<string[]> {
  const url = `https://html.duckduckgo.com/html/?q=${encodeURIComponent(`${query} logo png`)}`
  const urls = resultPages(await fetchPublicHtml(url))
  if (urls.length) return urls
  await delay(RETRY_DELAY_MS)
  const retried = resultPages(await fetchPublicHtml(url))
  if (!retried.length) logger.info(`DuckDuckGo found no results twice for: ${query}`)
  return retried
}

async function find(query: string, limit: number): Promise<ImageResult[]> {
  const urls = await searchPages(query)
  const terms = words(query)
  const responses = await Promise.allSettled(
    urls.map(async url => pageImages(await fetchPublicHtml(url), terms))
  )
  const pages = responses.flatMap(response =>
    response.status === 'fulfilled' ? [response.value] : []
  )
  const failures = responses.length - pages.length
  if (failures) logger.warn(`DuckDuckGo logo pages failed: ${failures} of ${urls.length}`)
  return interleave(pages, limit)
}

async function attempt(query: string, limit: number): Promise<ImageResult[]> {
  return find(query, limit).catch((error: unknown) => {
    logger.warn(`DuckDuckGo search did not finish for ${query}: ${errorMessage(error)}`)
    return []
  })
}

async function controlPasses(): Promise<boolean> {
  for (const control of CONTROL_QUERIES) {
    const results = await attempt(control, 1)
    if (results.length) return true
  }
  return false
}

async function search(query: string, limit: number): Promise<ImageResult[]> {
  const results = await attempt(query, limit)
  if (results.length || (await controlPasses())) return results
  throw new Error('DuckDuckGo found nothing for the control searches')
}

async function handler(req: Request, res: Response): Promise<void> {
  const query = typeof req.body?.query === 'string' ? req.body.query.trim() : ''
  if (!query || query.length > 256) {
    res
      .status(400)
      .json({ success: false, results: [], error: 'A query of 1 to 256 characters is required' })
    return
  }
  const limit = Number.isInteger(req.body.maxResults)
    ? Math.max(1, Math.min(req.body.maxResults, MAX_RESULTS))
    : 10
  try {
    const results = await search(query, limit)
    logger.info(`DuckDuckGo returned ${results.length} logo candidates for: ${query}`)
    res.json({ success: true, results })
  } catch (error) {
    logger.warn(`DuckDuckGo logo search failed: ${errorMessage(error)}`)
    res
      .status(502)
      .json({ success: false, results: [], error: 'DuckDuckGo logo search is unavailable' })
  }
}

export const duckduckgoAdapter: ServiceAdapter = {
  path: '/duckduckgo/images',
  method: 'POST',
  serviceName: 'DuckDuckGoImages',
  middleware: [createRateLimiter({ max: 60 })],
  handler,
}

async function imageHandler(req: Request, res: Response): Promise<void> {
  const url = validateParam(req, res, 'url', 'query')
  if (!url) return
  if (url.length > 2048) {
    res.status(400).json({ error: 'Image URL is too long' })
    return
  }
  try {
    const image = await fetchPublicImage(url)
    res.type(image.type).send(image.content)
  } catch (error) {
    logger.debug(`DuckDuckGo image download failed: ${errorMessage(error)}`)
    res.status(502).json({ error: 'Logo image is unavailable' })
  }
}

export const duckduckgoImageAdapter: ServiceAdapter = {
  path: '/duckduckgo/image',
  method: 'GET',
  serviceName: 'DuckDuckGoImage',
  middleware: [createRateLimiter({ max: 600 })],
  handler: imageHandler,
}
