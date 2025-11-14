import { AsyncLocalStorage } from 'async_hooks'
import { Request, Response, NextFunction } from 'express'

interface RequestContext {
  requestId: string
  service?: string
}

export const requestContext = new AsyncLocalStorage<RequestContext>()

export function requestContextMiddleware(req: Request, res: Response, next: NextFunction): void {
  requestContext.run({ requestId: req.id }, () => {
    next()
  })
}
