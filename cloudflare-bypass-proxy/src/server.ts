import express from 'express'
import expressRequestId from 'express-request-id'
import { adapters } from './adapters'
import { logger } from './utils/logger'
import { requestContext, requestContextMiddleware } from './middleware/request-context'

const app = express()
const PORT = process.env.PORT || 3000

app.use(expressRequestId())
app.use(requestContextMiddleware)

app.get('/health', (_req, res) => {
  res.json({ status: 'healthy' })
})

adapters.forEach((adapter) => {
  const method = adapter.method.toLowerCase() as 'get' | 'post' | 'put' | 'delete' | 'patch'
  logger.info(`Registering route: ${adapter.method} ${adapter.path}`, 'Server')

  const middlewares = adapter.middleware || []

  const wrappedHandler = (req: express.Request, res: express.Response) => {
    requestContext.getStore()!.service = adapter.serviceName
    return adapter.handler(req, res)
  }

  app[method](adapter.path, ...middlewares, wrappedHandler)
})

app.listen(PORT, () => {
  logger.info(`Cloudflare Bypass Proxy listening on port ${PORT}`, 'Server')
  logger.info(`Registered ${adapters.length} adapters`, 'Server')
  adapters.forEach((a) => logger.info(`  - ${a.method} ${a.path}`, 'Server'))
})
