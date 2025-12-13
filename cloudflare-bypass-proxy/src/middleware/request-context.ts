import { AsyncLocalStorage } from 'async_hooks'
import { Request, Response, NextFunction } from 'express'
import { randomUUID } from 'crypto'

interface RequestContext {
  requestId: string
  service?: string
}

export const requestContext = new AsyncLocalStorage<RequestContext>()

export function requestContextMiddleware(req: Request, res: Response, next: NextFunction): void {
  const requestId = (req as Request & { id?: string }).id || randomUUID()
  requestContext.run({ requestId }, () => {
    next()
  })
}
