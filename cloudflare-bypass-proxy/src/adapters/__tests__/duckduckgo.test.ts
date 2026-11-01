import express from 'express'
import request from 'supertest'
import { listen } from '../../__tests__/listen'
import { duckduckgoAdapter, duckduckgoImageAdapter } from '../duckduckgo'
import { logger } from '../../utils/logger'

const mockFetchPage = vi.fn()
const mockFetchImage = vi.fn()

vi.mock('../../utils/image-utils', async () => ({
  ...(await vi.importActual('../../utils/image-utils')),
  fetchPublicHtml: (...args: unknown[]) => mockFetchPage(...args),
  fetchPublicImage: (...args: unknown[]) => mockFetchImage(...args),
}))
vi.mock('timers/promises', () => ({ setTimeout: vi.fn().mockResolvedValue(undefined) }))

const SEARCH = 'https://html.duckduckgo.com/html/?q=NVDA%20NVIDIA%20logo%20png'
const FIRST = 'https://logos.example/nvidia'
const SECOND = 'https://brand.example/nvidia'
const APPLE = 'https://html.duckduckgo.com/html/?q=Apple%20logo%20png'
const MICROSOFT = 'https://html.duckduckgo.com/html/?q=Microsoft%20logo%20png'
const NO_RESULTS = '<div class="no-results">No results.</div>'
const CHALLENGE = '<form action="/anomaly.js">Select all squares containing a duck</form>'
const fetched = (url: string) =>
  mockFetchPage.mock.calls.filter(([called]) => called === url).length

function searchResults(...urls: string[]): string {
  return urls
    .map(
      url =>
        `<a class="result__a" href="//duckduckgo.com/l/?uddg=${encodeURIComponent(url)}">NVIDIA logo</a>`
    )
    .join('')
}

function serve(pages: Record<string, string | string[]>) {
  mockFetchPage.mockImplementation(async (url: string) => {
    const page = pages[url]
    if (!page) throw new Error(`No page for ${url}`)
    if (typeof page === 'string') return { url, html: page }
    return { url, html: page.length > 1 ? page.shift() : page[0] }
  })
}

function control(name: string, url: string): Record<string, string> {
  const page = `https://logos.example/${name}`
  return { [url]: searchResults(page), [page]: `<img src="/logo.png" alt="${name} logo">` }
}

function application() {
  return express()
    .use(express.json())
    .post(duckduckgoAdapter.path, duckduckgoAdapter.handler)
    .get(duckduckgoImageAdapter.path, duckduckgoImageAdapter.handler)
}

const searchFor = async (query = 'NVDA NVIDIA', maxResults?: number) =>
  request(await listen(application()))
    .post('/duckduckgo/images')
    .send({ query, maxResults })

const imagesOf = (response: { body: { results: { image: string }[] } }) =>
  response.body.results.map(result => result.image)

describe('DuckDuckGo logo search', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('finds branded raster images through DuckDuckGo HTML result pages', async () => {
    serve({
      [SEARCH]: searchResults(FIRST),
      [FIRST]: `
        <img src="/navigation.png" alt="Navigation">
        <img src="/nvidia.svg" alt="NVIDIA logo">
        <img src="/logo.png" alt="NVIDIA green logo">
      `,
    })

    const response = await searchFor()

    expect(response.body).toEqual({
      success: true,
      results: [
        {
          image: 'https://logos.example/logo.png',
          thumbnail: 'https://logos.example/logo.png',
          title: 'NVIDIA green logo',
        },
      ],
    })
  })

  it('takes candidates from each result page before filling the result limit', async () => {
    const page = `
        <img src="/one.png" alt="NVIDIA logo">
        <img src="/two.png" alt="NVIDIA logo">
        <img src="/three.png" alt="NVIDIA logo">
      `
    serve({ [SEARCH]: searchResults(FIRST, SECOND), [FIRST]: page, [SECOND]: page })

    const response = await searchFor('NVDA NVIDIA', 2)

    expect(imagesOf(response)).toEqual([
      'https://logos.example/one.png',
      'https://brand.example/one.png',
    ])
  })

  it('uses lazy images and the final page URL after a redirect', async () => {
    mockFetchPage.mockImplementation(async (url: string) =>
      url === SEARCH
        ? { url, html: searchResults(FIRST) }
        : {
            url: 'https://cdn.example/brands/nvidia/',
            html: '<img src="data:image/gif;base64,AA" data-src="logo.png" alt="NVIDIA logo">',
          }
    )

    const response = await searchFor()

    expect(response.body.results[0].image).toBe('https://cdn.example/brands/nvidia/logo.png')
  })

  it('does not label a bot challenge as a successful empty search', async () => {
    mockFetchPage.mockResolvedValue({
      url: SEARCH,
      html: CHALLENGE,
    })

    const response = await searchFor()

    expect({ status: response.status, success: response.body.success }).toEqual({
      status: 502,
      success: false,
    })
  })

  it('returns an empty list for a genuine search with no results', async () => {
    serve({ [SEARCH]: NO_RESULTS, ...control('Apple', APPLE) })

    const response = await searchFor()

    expect({ status: response.status, body: response.body }).toEqual({
      status: 200,
      body: { success: true, results: [] },
    })
  })

  it('keeps candidates from other pages when one page fails', async () => {
    serve({
      [SEARCH]: searchResults(FIRST, SECOND),
      [SECOND]: '<img src="/logo.png" alt="NVIDIA logo">',
    })

    const response = await searchFor()

    expect(imagesOf(response)).toEqual(['https://brand.example/logo.png'])
  })

  it('refuses to parse a page with too many tags', async () => {
    serve({
      [SEARCH]: searchResults(FIRST, SECOND),
      [FIRST]: `${'<p>'.repeat(20000)}<img src="/hidden.png" alt="NVIDIA logo">`,
      [SECOND]: '<img src="/logo.png" alt="NVIDIA logo">',
    })

    const response = await searchFor()

    expect(imagesOf(response)).toEqual(['https://brand.example/logo.png'])
  })

  it('parses a page at the tag limit', async () => {
    serve({
      [SEARCH]: searchResults(FIRST),
      [FIRST]: `${'<p>'.repeat(19999)}<img src="/logo.png" alt="NVIDIA logo">`,
    })

    const response = await searchFor()

    expect(imagesOf(response)).toEqual(['https://logos.example/logo.png'])
  })

  it('deduplicates images repeated as metadata and inline images', async () => {
    serve({
      [SEARCH]: searchResults(FIRST),
      [FIRST]: `
        <title>NVIDIA logo</title>
        <meta property="og:image" content="/nvidia.png">
        <img src="/nvidia.png" alt="NVIDIA logo">
        <a href="/nvidia.png">NVIDIA PNG</a>
      `,
    })

    const response = await searchFor()

    expect(response.body.results).toHaveLength(1)
  })

  it('does not offer non-web URLs or unrelated page images', async () => {
    serve({
      [SEARCH]: searchResults(FIRST),
      [FIRST]: `
        <img src="file:///etc/nvidia.png" alt="NVIDIA logo">
        <img src="javascript:alert(1)" alt="NVIDIA logo">
        <img src="/apple.png" alt="Apple logo">
        <img src="https://localhost/nvidia.png" alt="NVIDIA logo">
      `,
    })

    const response = await searchFor()

    expect(response.body.results).toEqual([])
  })

  it.each([undefined, '', '   ', 42])('rejects an invalid search query %j', async query => {
    const response = await request(await listen(application()))
      .post('/duckduckgo/images')
      .send({ query })

    expect(response.status).toBe(400)
  })

  it('excludes company photographs without a logo label', async () => {
    serve({
      [SEARCH]: searchResults(FIRST),
      [FIRST]: `
        <title>NVIDIA investor relations</title>
        <meta property="og:image" content="/nvidia-team.jpg">
        <img src="/hpc-nvidia-menu.jpg" alt="High Performance Computing">
        <img src="/nvidia-portrait.jpg" alt="NVIDIA CEO">
      `,
    })

    const response = await searchFor()

    expect(response.body.results).toEqual([])
  })

  it('rejects stock illustrations that only mention logo as a possible use', async () => {
    const search = 'https://html.duckduckgo.com/html/?q=AAPL%20Apple%20logo%20png'
    serve({
      [search]: searchResults(FIRST),
      [FIRST]: `
        <img src="/fruit.png" alt="Caramel apple on a stick. Traditional Halloween kids sweets. Flat simple element, color illustration. For logo, sticker, print, web design, scrapbooking png">
        <img src="/apple.png" alt="Apple Computer Logo Black Gradient iPhone Icon Transparent Background png">
      `,
    })

    const response = await searchFor('AAPL Apple')

    expect(imagesOf(response)).toEqual(['https://logos.example/apple.png'])
  })

  it('downloads image bytes only through the public destination validator', async () => {
    const bytes = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0xff, 0x00])
    mockFetchImage.mockResolvedValue({ content: bytes, type: 'image/png' })

    const response = await request(await listen(application()))
      .get('/duckduckgo/image')
      .query({ url: 'https://logos.example/logo.png?version=2&size=large' })

    expect(response.status).toBe(200)
    expect(response.headers['content-type']).toContain('image/png')
    expect(response.body).toEqual(bytes)
    expect(mockFetchImage).toHaveBeenCalledWith(
      'https://logos.example/logo.png?version=2&size=large'
    )
  })

  it('rejects an image when public destination validation fails', async () => {
    mockFetchImage.mockRejectedValue(new Error('Logo page must resolve to a public address'))

    const response = await request(await listen(application()))
      .get('/duckduckgo/image')
      .query({ url: 'http://127.0.0.1.nip.io/nvidia-logo.png' })

    expect(response.status).toBe(502)
  })

  it('rejects an image request without a URL', async () => {
    const response = await request(await listen(application())).get('/duckduckgo/image')

    expect(response.status).toBe(400)
    expect(mockFetchImage).not.toHaveBeenCalled()
  })

  it('rejects an image request with a repeated URL parameter', async () => {
    const response = await request(await listen(application())).get(
      '/duckduckgo/image?url=https://logos.example/a.png&url=https://logos.example/b.png'
    )

    expect(response.status).toBe(400)
    expect(mockFetchImage).not.toHaveBeenCalled()
  })

  it('rejects an image request with an overlong URL', async () => {
    const response = await request(await listen(application()))
      .get('/duckduckgo/image')
      .query({ url: `https://logos.example/${'a'.repeat(2048)}.png` })

    expect(response.status).toBe(400)
    expect(mockFetchImage).not.toHaveBeenCalled()
  })

  it('bounds page fetching when DuckDuckGo returns many pages', async () => {
    const pages = Array.from({ length: 10 }, (_, index) => `https://logos.example/page-${index}/`)
    const page = '<img src="nvidia-logo.png" alt="NVIDIA logo">'
    serve({
      [SEARCH]: searchResults(...pages),
      ...Object.fromEntries(pages.map(url => [url, page])),
    })

    const response = await searchFor('NVDA NVIDIA', 100)

    expect(imagesOf(response)).toEqual([
      'https://logos.example/page-0/nvidia-logo.png',
      'https://logos.example/page-1/nvidia-logo.png',
      'https://logos.example/page-2/nvidia-logo.png',
      'https://logos.example/page-3/nvidia-logo.png',
    ])
  })

  it('matches issuer names containing an ampersand', async () => {
    const search = 'https://html.duckduckgo.com/html/?q=L%26G%20logo%20png'
    serve({ [search]: searchResults(FIRST), [FIRST]: '<img src="/issuer.png" alt="L&amp;G logo">' })

    const response = await searchFor('L&G')

    expect(imagesOf(response)).toEqual(['https://logos.example/issuer.png'])
  })

  it('reports unavailable search when result pages and both controls fail', async () => {
    serve({ [SEARCH]: searchResults(FIRST) })

    const response = await searchFor()

    expect({ status: response.status, success: response.body.success }).toEqual({
      status: 502,
      success: false,
    })
  })

  it('retries a search page that says no results', async () => {
    serve({
      [SEARCH]: [NO_RESULTS, searchResults(FIRST)],
      [FIRST]: '<img src="/logo.png" alt="NVIDIA logo">',
    })

    const response = await searchFor()

    expect({
      status: response.status,
      results: response.body.results.length,
      searches: fetched(SEARCH),
    }).toEqual({ status: 200, results: 1, searches: 2 })
  })

  it('does not run a control when the search has candidates', async () => {
    serve({
      [SEARCH]: searchResults(FIRST),
      [FIRST]: '<img src="/logo.png" alt="NVIDIA logo">',
    })

    await searchFor()

    expect(fetched(APPLE)).toBe(0)
  })

  it('does not search for Microsoft when Apple gives a candidate', async () => {
    serve({ [SEARCH]: NO_RESULTS, ...control('Apple', APPLE) })

    await searchFor()

    expect(fetched(MICROSOFT)).toBe(0)
  })

  it('searches for Microsoft when Apple gives no candidate', async () => {
    serve({ [SEARCH]: NO_RESULTS, [APPLE]: NO_RESULTS, ...control('Microsoft', MICROSOFT) })

    const response = await searchFor()

    expect({
      status: response.status,
      results: response.body.results,
      searches: fetched(MICROSOFT),
    }).toEqual({ status: 200, results: [], searches: 1 })
  })

  it('moves to the next control when one throws', async () => {
    serve({ [SEARCH]: NO_RESULTS, ...control('Microsoft', MICROSOFT) })

    const response = await searchFor()

    expect({
      status: response.status,
      results: response.body.results,
      searches: fetched(MICROSOFT),
    }).toEqual({ status: 200, results: [], searches: 1 })
  })

  it('reports unavailable search when neither control gives a candidate', async () => {
    serve({ [SEARCH]: NO_RESULTS, [APPLE]: NO_RESULTS, [MICROSOFT]: NO_RESULTS })

    const response = await searchFor()

    expect({ status: response.status, error: response.body.error }).toEqual({
      status: 502,
      error: 'DuckDuckGo logo search is unavailable',
    })
  })

  it('logs why each control failed when the search has no results', async () => {
    const warn = vi.spyOn(logger, 'warn')
    serve({ [SEARCH]: NO_RESULTS })

    await searchFor()

    expect(warn.mock.calls.flat()).toEqual(
      expect.arrayContaining([
        `DuckDuckGo search did not finish for Apple: No page for ${APPLE}`,
        `DuckDuckGo search did not finish for Microsoft: No page for ${MICROSOFT}`,
      ])
    )
  })

  it('answers empty when a blocked name passes a control', async () => {
    serve({ [SEARCH]: CHALLENGE, ...control('Apple', APPLE) })

    const response = await searchFor()

    expect({ status: response.status, results: response.body.results }).toEqual({
      status: 200,
      results: [],
    })
  })

  it('treats a bot challenge on the retry as a failed search', async () => {
    serve({ [SEARCH]: [NO_RESULTS, CHALLENGE], ...control('Apple', APPLE) })

    const response = await searchFor()

    expect({
      status: response.status,
      results: response.body.results,
      controls: fetched(APPLE),
    }).toEqual({ status: 200, results: [], controls: 1 })
  })

  it('answers empty when every result page of a name fails and a control passes', async () => {
    serve({ [SEARCH]: searchResults(FIRST), ...control('Apple', APPLE) })

    const response = await searchFor()

    expect({ status: response.status, results: response.body.results }).toEqual({
      status: 200,
      results: [],
    })
  })

  it('ignores generic words when matching a logo', async () => {
    serve({
      'https://html.duckduckgo.com/html/?q=Hyundai%20Motor%20logo%20png': searchResults(FIRST),
      [FIRST]: `
        <img src="/toyota.png" alt="Toyota Motor logo">
        <img src="/hyundai.png" alt="Hyundai logo">
      `,
    })

    const response = await searchFor('Hyundai Motor')

    expect(response.body.results.map((result: { title: string }) => result.title)).toEqual([
      'Hyundai logo',
    ])
  })

  it('matches every word of a name made only of generic words', async () => {
    serve({
      'https://html.duckduckgo.com/html/?q=Capital%20Power%20Corp%20logo%20png':
        searchResults(FIRST),
      [FIRST]: '<img src="/logo.png" alt="Capital Power logo">',
    })

    const response = await searchFor('Capital Power Corp')

    expect(response.body.results).toHaveLength(1)
  })
})
