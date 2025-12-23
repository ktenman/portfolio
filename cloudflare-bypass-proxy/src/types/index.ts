import { Request, Response, RequestHandler } from 'express'

export interface CurlOptions {
  url: string
  timeout?: number
  maxBuffer?: number
  headers?: Record<string, string>
  method?: string
  body?: string
}

export interface CurlResult {
  stdout: string
  duration: number
}

export interface RateLimitConfig {
  windowMs: number
  max: number
}

export interface ServiceAdapter {
  path: string
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  serviceName: string
  middleware?: RequestHandler[]
  handler: (req: Request, res: Response) => Promise<void>
}
