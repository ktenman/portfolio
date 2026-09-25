import { execFile } from 'child_process'
import { promisify } from 'util'
import { CurlOptions, CurlResult } from '../types'

const execFileAsync = promisify(execFile)
const CURL = process.env.CURL_BINARY || '/usr/local/bin/curl_ff117'

export class CurlExecutionError extends Error {
  constructor(readonly timedOut: boolean) {
    super(timedOut ? 'curl request timed out' : 'curl request failed')
  }
}

function buildCurlArgs({ url, headers, method, body }: CurlOptions): string[] {
  const args = ['-s', '--write-out', '\n%{http_code}\n%{content_type}']
  if (method && method.toUpperCase() !== 'GET') args.push('-X', method.toUpperCase())
  for (const [key, value] of Object.entries(headers || {})) {
    args.push('-H', `${key}: ${value}`)
  }
  if (body) args.push('-d', body)
  args.push(url)
  return args
}

function parseCurlResult(stdout: string, duration: number): CurlResult {
  const metadata = /\n([1-5]\d{2})\n([^\r\n]*)$/.exec(stdout)
  if (!metadata) throw new CurlExecutionError(false)
  return {
    stdout: stdout.slice(0, metadata.index),
    duration,
    statusCode: Number(metadata[1]),
    contentType: metadata[2],
  }
}

function isTimeout(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  const { code, killed, signal } = error as { code?: unknown; killed?: boolean; signal?: string }
  return (
    code === 28 || code === 'ETIMEDOUT' || (code == null && killed === true && signal === 'SIGTERM')
  )
}

export async function executeCurl(options: CurlOptions): Promise<CurlResult> {
  const start = Date.now()
  try {
    const { stdout } = await execFileAsync(CURL, buildCurlArgs(options), {
      timeout: options.timeout ?? 10000,
      maxBuffer: options.maxBuffer ?? 1024 * 1024,
    })
    return parseCurlResult(stdout, Date.now() - start)
  } catch (error) {
    throw new CurlExecutionError(isTimeout(error))
  }
}

export async function execCurl(options: CurlOptions): Promise<string> {
  const result = await executeCurl(options)
  return result.stdout
}
