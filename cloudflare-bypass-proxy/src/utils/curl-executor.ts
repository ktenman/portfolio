import { execFile } from 'child_process'
import { promisify } from 'util'
import { CurlOptions, CurlResult } from '../types'

const execFileAsync = promisify(execFile)
const CURL = process.env.CURL_BINARY || '/usr/local/bin/curl_ff117'

export async function executeCurl({
  url,
  timeout = 10000,
  maxBuffer = 1024 * 1024,
  headers,
  method,
  body,
}: CurlOptions): Promise<CurlResult> {
  const start = Date.now()

  try {
    const args = ['-s']

    if (method && method.toUpperCase() !== 'GET') {
      args.push('-X', method.toUpperCase())
    }

    if (headers) {
      for (const [key, value] of Object.entries(headers)) {
        args.push('-H', `${key}: ${value}`)
      }
    }

    if (body) {
      args.push('-d', body)
    }

    args.push(url)

    const { stdout } = await execFileAsync(CURL, args, {
      timeout,
      maxBuffer,
    })

    const duration = Date.now() - start
    return { stdout, duration }
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'Unknown error'
    throw new Error(`curl execution failed: ${errorMessage}`)
  }
}

export async function execCurl(options: CurlOptions): Promise<string> {
  const result = await executeCurl(options)
  return result.stdout
}
