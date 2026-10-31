import { execFile } from 'child_process'
import { promisify } from 'util'
import { lookup } from 'dns/promises'
import { BlockList, isIP } from 'net'
import { setTimeout as delay } from 'timers/promises'

const execFileAsync = promisify(execFile)
const CURL = process.env.CURL_BINARY || '/usr/local/bin/curl_chrome152'
const MAX_PAGE_BYTES = 2 * 1024 * 1024
const privateAddresses = new BlockList()
const globalAddresses = new BlockList()
globalAddresses.addSubnet('2000::', 3, 'ipv6')
for (const [address, prefix] of [
  ['0.0.0.0', 8],
  ['10.0.0.0', 8],
  ['100.64.0.0', 10],
  ['127.0.0.0', 8],
  ['169.254.0.0', 16],
  ['172.16.0.0', 12],
  ['192.0.0.0', 24],
  ['192.0.2.0', 24],
  ['192.168.0.0', 16],
  ['198.18.0.0', 15],
  ['198.51.100.0', 24],
  ['203.0.113.0', 24],
  ['224.0.0.0', 4],
  ['240.0.0.0', 4],
] as const)
  privateAddresses.addSubnet(address, prefix)
privateAddresses.addSubnet('2001::', 23, 'ipv6')
privateAddresses.addSubnet('2001:db8::', 32, 'ipv6')
privateAddresses.addSubnet('2002::', 16, 'ipv6')

export interface SearchPage {
  url: string
  html: string
}

interface PublicContent {
  url: string
  content: Buffer
  type: string
}

function isPublicAddress(address: string): boolean {
  if (isIP(address) === 4) return !privateAddresses.check(address, 'ipv4')
  return globalAddresses.check(address, 'ipv6') && !privateAddresses.check(address, 'ipv6')
}

function bareHostname(url: URL): string {
  return url.hostname.replace(/^\[|\]$/g, '')
}

export function isPublicWebUrl(value: string): boolean {
  try {
    const url = new URL(value)
    const hostname = bareHostname(url).replace(/\.$/, '')
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.port || url.password)
      return false
    if (isIP(hostname)) return isPublicAddress(hostname)
    return (
      hostname.includes('.') && !/\.(localhost|local|internal|test|invalid)\.?$/i.test(hostname)
    )
  } catch {
    return false
  }
}

async function resolvePublicAddress(url: URL, remaining: number): Promise<string> {
  const timer = new AbortController()
  const addresses = await Promise.race([
    lookup(bareHostname(url), { all: true }),
    delay(remaining, undefined, { signal: timer.signal }).then(() => {
      throw new Error('Logo page DNS lookup timed out')
    }),
  ]).finally(() => timer.abort())
  if (!addresses.length || addresses.some(result => !isPublicAddress(result.address))) {
    throw new Error('Logo page must resolve to a public address')
  }
  return (addresses.find(result => result.family === 4) || addresses[0]).address
}

async function downloadContent(url: URL, deadline: number): Promise<Buffer> {
  const address = await resolvePublicAddress(url, Math.max(1, deadline - Date.now()))
  const remaining = deadline - Date.now()
  if (remaining <= 0) throw new Error('Logo page request timed out')
  const resolved = isIP(address) === 6 ? `[${address}]` : address
  const port = url.protocol === 'https:' ? '443' : '80'
  const args = [
    '-sS',
    '--globoff',
    '--compressed',
    '--noproxy',
    '*',
    '--proto',
    '=http,https',
    '--max-time',
    String(remaining / 1000),
    '--max-filesize',
    String(MAX_PAGE_BYTES),
    '--resolve',
    `${url.hostname}:${port}:${resolved}`,
    '-w',
    '\n%{http_code}\t%{redirect_url}\t%{content_type}',
    url.href,
  ]
  const { stdout } = await execFileAsync(CURL, args, {
    timeout: remaining + 500,
    maxBuffer: MAX_PAGE_BYTES + 1024,
    encoding: 'buffer',
  })
  return stdout
}

async function fetchPublicContent(
  value: string,
  contentType: RegExp,
  timeout: number
): Promise<PublicContent> {
  const deadline = Date.now() + timeout
  for (let redirects = 0; redirects <= 3; redirects++) {
    if (!isPublicWebUrl(value)) throw new Error('Logo page URL must be public HTTP or HTTPS')
    const url = new URL(value)
    const response = await downloadContent(url, deadline)
    const boundary = response.lastIndexOf('\n')
    const [status, location, type] = response
      .subarray(boundary + 1)
      .toString()
      .split('\t')
    if (Number(status) >= 300 && Number(status) < 400 && location) {
      value = new URL(location, url).href
      continue
    }
    if (status !== '200' || !contentType.test(type))
      throw new Error(`Logo page returned HTTP ${status} (${type})`)
    return { url: url.href, content: response.subarray(0, boundary), type }
  }
  throw new Error('Logo page redirected too many times')
}

export async function fetchPublicHtml(value: string): Promise<SearchPage> {
  const page = await fetchPublicContent(value, /^text\/html\b/i, 8000)
  return { url: page.url, html: page.content.toString() }
}

export async function fetchPublicImage(value: string): Promise<PublicContent> {
  const image = await fetchPublicContent(
    value,
    /^(image\/(png|jpeg|webp)|application\/octet-stream)(?:;|$)/i,
    4000
  )
  const type = imageType(image.content)
  if (!type) throw new Error('Logo image has an unsupported format')
  return { ...image, type }
}

function imageType(content: Buffer): string | null {
  if (content.subarray(0, 8).equals(Buffer.from('89504e470d0a1a0a', 'hex'))) return 'image/png'
  if (content.subarray(0, 3).equals(Buffer.from('ffd8ff', 'hex'))) return 'image/jpeg'
  if (content.toString('ascii', 0, 4) === 'RIFF' && content.toString('ascii', 8, 12) === 'WEBP')
    return 'image/webp'
  return null
}

export interface ImageResult {
  image: string
  thumbnail: string
  title: string
}
