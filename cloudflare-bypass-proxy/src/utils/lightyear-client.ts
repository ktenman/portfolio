import { Response } from 'express'
import { CurlOptions, CurlResult } from '../types'
import { CurlExecutionError, executeCurl } from './curl-executor'
import { logger } from './logger'

type LightyearErrorCode =
  | 'UPSTREAM_HTTP_ERROR'
  | 'UPSTREAM_EMPTY_RESPONSE'
  | 'UPSTREAM_UNEXPECTED_CONTENT_TYPE'
  | 'UPSTREAM_INVALID_JSON'
  | 'UPSTREAM_INVALID_PAYLOAD'
  | 'UPSTREAM_TIMEOUT'
  | 'UPSTREAM_NETWORK_ERROR'

export class LightyearResponseError extends Error {
  readonly upstreamStatus?: number
  readonly contentType?: string

  constructor(
    readonly code: LightyearErrorCode,
    readonly statusCode: number,
    response?: Pick<CurlResult, 'statusCode' | 'contentType'>
  ) {
    const requestFailure = [
      'UPSTREAM_HTTP_ERROR',
      'UPSTREAM_TIMEOUT',
      'UPSTREAM_NETWORK_ERROR',
    ].includes(code)
    super(
      requestFailure
        ? 'Lightyear upstream request failed'
        : 'Lightyear returned an invalid response'
    )
    this.upstreamStatus = response?.statusCode
    this.contentType = response?.contentType
  }
}

function parseLightyearJson(result: CurlResult): unknown {
  if (result.statusCode < 200 || result.statusCode >= 300) {
    throw new LightyearResponseError('UPSTREAM_HTTP_ERROR', result.statusCode, result)
  }
  if (!result.stdout.trim())
    throw new LightyearResponseError('UPSTREAM_EMPTY_RESPONSE', 502, result)
  const mediaType = result.contentType.split(';')[0].trim().toLowerCase()
  if (!/^application\/(?:json|[\w.+-]+\+json)$/.test(mediaType)) {
    throw new LightyearResponseError('UPSTREAM_UNEXPECTED_CONTENT_TYPE', 502, result)
  }
  try {
    return JSON.parse(result.stdout)
  } catch {
    throw new LightyearResponseError('UPSTREAM_INVALID_JSON', 502, result)
  }
}

function sendLightyearError(res: Response, error: unknown): void {
  const timedOut = error instanceof CurlExecutionError && error.timedOut
  const failure =
    error instanceof LightyearResponseError
      ? error
      : new LightyearResponseError(
          timedOut ? 'UPSTREAM_TIMEOUT' : 'UPSTREAM_NETWORK_ERROR',
          timedOut ? 504 : 502
        )
  const body = {
    error: failure.message,
    code: failure.code,
    upstreamStatus: failure.upstreamStatus,
    contentType: failure.contentType,
  }
  logger.error('Lightyear request failed', undefined, body)
  res.status(failure.statusCode).json(body)
}

export async function handleLightyearRequest(
  res: Response,
  options: CurlOptions,
  onSuccess: (data: unknown, result: CurlResult) => void
): Promise<void> {
  try {
    const result = await executeCurl(options)
    const data = parseLightyearJson(result)
    onSuccess(data, result)
    if (res.statusCode < 400) logger.info(`Lightyear request completed in ${result.duration}ms`)
  } catch (error) {
    sendLightyearError(res, error)
  }
}
