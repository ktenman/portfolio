declare module 'express-request-id' {
  import { RequestHandler } from 'express'

  interface Options {
    setHeader?: boolean
    headerName?: string
    generator?: (request: Express.Request) => string
  }

  function expressRequestId(options?: Options): RequestHandler
  export = expressRequestId
}
