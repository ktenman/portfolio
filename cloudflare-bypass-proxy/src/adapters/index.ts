import { ServiceAdapter } from '../types'
import { trading212Adapter } from './trading212'
import { wisdomTreeAdapter } from './wisdom-tree'
import { lightyearAdapter } from './lightyear'
import { lightyearHoldingsAdapter } from './lightyear-holdings'

export const adapters: ServiceAdapter[] = [
  trading212Adapter,
  wisdomTreeAdapter,
  lightyearAdapter,
  lightyearHoldingsAdapter,
]
