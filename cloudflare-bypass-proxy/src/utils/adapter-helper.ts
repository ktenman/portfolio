import { Request, Response } from 'express'
import { executeCurl } from './curl-executor'
import { logger } from './logger'

export enum ResponseType {
  JSON = 'json',
  HTML = 'html',
}

interface AdapterConfig {
  url: string
  timeout?: number
  maxBuffer?: number
  responseType?: ResponseType
  headers?: Record<string, string>
}

export async function handleProxyRequest(
  req: Request,
  res: Response,
  config: AdapterConfig
): Promise<void> {
  try {
    const start = Date.now()
    const { stdout } = await executeCurl({
      url: config.url,
      timeout: config.timeout,
      maxBuffer: config.maxBuffer,
      headers: config.headers,
    })

    const duration = Date.now() - start
    logger.info(`Request completed in ${duration}ms`)

    if (config.responseType === ResponseType.HTML) {
      res.type('html').send(stdout)
    } else {
      res.json(JSON.parse(stdout))
    }
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'Unknown error'
    logger.error(errorMessage)
    res.status(500).json({
      error: 'Failed to fetch data',
      message: errorMessage,
    })
  }
}

export function validateParam(
  req: Request,
  res: Response,
  paramName: string,
  paramType: 'query' | 'params'
): string | null {
  const value = paramType === 'query' ? req.query[paramName] : req.params[paramName]

  if (!value || (paramType === 'query' && typeof value !== 'string')) {
    res.status(400).json({ error: `Missing ${paramName} parameter` })
    return null
  }

  return value as string
}
