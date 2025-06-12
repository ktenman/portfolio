import { InstrumentService } from './instrument-service'
import { TransactionService } from './transaction-service'
import { PortfolioSummaryService } from './portfolio-summary-service'
import { UtilityService } from './utility-service'
import { CacheService } from './cache-service'

class ServiceRegistry {
  private services = new Map<string, any>()
  private factories = new Map<string, () => any>()

  registerFactory<T>(name: string, factory: () => T): void {
    this.factories.set(name, factory)
  }

  get<T>(name: string): T {
    if (!this.services.has(name)) {
      const factory = this.factories.get(name)
      if (!factory) {
        throw new Error(`Service '${name}' not registered`)
      }
      this.services.set(name, factory())
    }
    return this.services.get(name)
  }

  clear(): void {
    this.services.clear()
  }

  has(name: string): boolean {
    return this.factories.has(name)
  }
}

export const serviceRegistry = new ServiceRegistry()

serviceRegistry.registerFactory('cache', () => CacheService.getInstance())
serviceRegistry.registerFactory('instruments', () => new InstrumentService())
serviceRegistry.registerFactory('transactions', () => new TransactionService())
serviceRegistry.registerFactory('portfolioSummary', () => new PortfolioSummaryService())
serviceRegistry.registerFactory('utility', () => new UtilityService())

export const getInstrumentService = () => serviceRegistry.get<InstrumentService>('instruments')
export const getTransactionService = () => serviceRegistry.get<TransactionService>('transactions')
export const getPortfolioSummaryService = () =>
  serviceRegistry.get<PortfolioSummaryService>('portfolioSummary')
export const getUtilityService = () => serviceRegistry.get<UtilityService>('utility')
export const getCacheService = () => serviceRegistry.get<CacheService>('cache')
