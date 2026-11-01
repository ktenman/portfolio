import type { Application } from 'express'
import { once } from 'node:events'

export async function listen(app: Application) {
  const server = app.listen(0, '127.0.0.1')
  await once(server, 'listening')
  onTestFinished(() => void server.close())
  return server
}
