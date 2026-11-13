import { Request, Response } from 'express'
import { CurlOptions } from '../types'
import { executeCurl } from './curl-executor'
import { logger } from './logger'

export enum ResponseType {
  JSON = 'json',
  HTML = 'html',
}

interface AdapterConfig extends CurlOptions {
  responseType?: ResponseType
}

export async function handleProxyRequest(
  req: Request,
  res: Response,
  config: AdapterConfig
): Promise<void> {
  const { responseType, ...curlOptions } = config
  try {
    const start = Date.now()
    const { stdout, statusCode } = await executeCurl(curlOptions)

    const duration = Date.now() - start
    logger.info(`Request completed in ${duration}ms`)

    if (statusCode < 200 || statusCode >= 300) {
      sendFailure(res, statusCode, `Upstream ${config.url} returned ${statusCode}`, {
        upstreamStatus: statusCode,
      })
      return
    }

    if (responseType === ResponseType.HTML) {
      res.type('html').send(stdout)
    } else {
      res.json(JSON.parse(stdout))
    }
  } catch (error) {
    sendFailure(res, 500, errorMessage(error))
  }
}

function sendFailure(
  res: Response,
  status: number,
  message: string,
  details: Record<string, unknown> = {}
): void {
  logger.error(message)
  res.status(status).json({ error: 'Failed to fetch data', message, ...details })
}

export function validateParam(
  req: Request,
  res: Response,
  paramName: string,
  paramType: 'query' | 'params'
): string | null {
  const value = paramType === 'query' ? req.query[paramName] : req.params[paramName]

  if (!value || typeof value !== 'string') {
    res.status(400).json({ error: `Missing ${paramName} parameter` })
    return null
  }

  return value
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Unknown error'
}
