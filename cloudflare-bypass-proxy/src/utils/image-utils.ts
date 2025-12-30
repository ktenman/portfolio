import { execFile } from 'child_process'
import { promisify } from 'util'

const execFileAsync = promisify(execFile)
const CURL = process.env.CURL_BINARY || 'curl'

export interface ImageSearchRequest {
  query: string
  maxResults?: number
}

export interface ImageResult {
  image: string
  thumbnail: string
  title: string
  width: number
  height: number
}

export interface ImageSearchResponse {
  success: boolean
  results: ImageResult[]
  error?: string
}

export async function executeCurl(
  url: string,
  headers: Record<string, string> = {}
): Promise<string> {
  const args = ['-s', '-L', '--max-time', '30']
  for (const [key, value] of Object.entries(headers)) {
    args.push('-H', `${key}: ${value}`)
  }
  args.push(url)
  const { stdout } = await execFileAsync(CURL, args, {
    timeout: 35000,
    maxBuffer: 10 * 1024 * 1024,
  })
  return stdout
}
