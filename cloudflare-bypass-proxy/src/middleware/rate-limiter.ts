import rateLimit from 'express-rate-limit'
import { RateLimitConfig } from '../types'

const DEFAULT_RATE_LIMIT: RateLimitConfig = {
  windowMs: 60 * 1000,
  max: 120,
}

export function createRateLimiter(config?: Partial<RateLimitConfig>) {
  const finalConfig = { ...DEFAULT_RATE_LIMIT, ...config }

  return rateLimit({
    windowMs: finalConfig.windowMs,
    max: finalConfig.max,
    message: { error: 'Too many requests, please try again later.' },
    standardHeaders: true,
    legacyHeaders: false,
  })
}
