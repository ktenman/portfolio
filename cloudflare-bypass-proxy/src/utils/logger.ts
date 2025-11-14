import { requestContext } from '../middleware/request-context'

enum LogLevel {
  DEBUG = 'DEBUG',
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR',
}

interface LogEntry {
  timestamp: string
  level: LogLevel
  service?: string
  requestId?: string
  message: string
  data?: unknown
}

class Logger {
  private generateRequestId(): string {
    return Math.random().toString(36).substring(2, 9)
  }

  private formatLogEntry(entry: LogEntry): string {
    const { timestamp, level, service, requestId, message, data } = entry
    const servicePrefix = service ? `[${service}] ` : ''
    const requestIdPrefix = requestId ? `[${requestId}] ` : ''
    const dataString = data ? ` ${JSON.stringify(data)}` : ''
    return `[${timestamp}] [${level}] ${servicePrefix}${requestIdPrefix}${message}${dataString}`
  }

  private log(
    level: LogLevel,
    message: string,
    service?: string,
    requestId?: string,
    data?: unknown
  ): void {
    const store = requestContext.getStore()
    const contextRequestId = store?.requestId
    const contextService = store?.service

    const finalRequestId = requestId || contextRequestId
    const finalService = service || contextService

    const entry: LogEntry = {
      timestamp: new Date().toISOString(),
      level,
      service: finalService,
      requestId: finalRequestId,
      message,
      data,
    }

    const formattedMessage = this.formatLogEntry(entry)

    switch (level) {
      case LogLevel.ERROR:
        console.error(formattedMessage)
        break
      case LogLevel.WARN:
        console.warn(formattedMessage)
        break
      default:
        console.log(formattedMessage)
    }
  }

  info(
    message: string,
    service?: string,
    requestIdOrData?: string | unknown,
    data?: unknown
  ): void {
    const [requestId, finalData] = this.parseParams(requestIdOrData, data)
    this.log(LogLevel.INFO, message, service, requestId, finalData)
  }

  warn(
    message: string,
    service?: string,
    requestIdOrData?: string | unknown,
    data?: unknown
  ): void {
    const [requestId, finalData] = this.parseParams(requestIdOrData, data)
    this.log(LogLevel.WARN, message, service, requestId, finalData)
  }

  error(
    message: string,
    service?: string,
    requestIdOrData?: string | unknown,
    data?: unknown
  ): void {
    const [requestId, finalData] = this.parseParams(requestIdOrData, data)
    this.log(LogLevel.ERROR, message, service, requestId, finalData)
  }

  debug(
    message: string,
    service?: string,
    requestIdOrData?: string | unknown,
    data?: unknown
  ): void {
    const [requestId, finalData] = this.parseParams(requestIdOrData, data)
    this.log(LogLevel.DEBUG, message, service, requestId, finalData)
  }

  private parseParams(
    requestIdOrData?: string | unknown,
    data?: unknown
  ): [string | undefined, unknown] {
    if (typeof requestIdOrData === 'string') {
      return [requestIdOrData, data]
    }
    return [undefined, requestIdOrData]
  }

  generateId(): string {
    return this.generateRequestId()
  }
}

export const logger = new Logger()
