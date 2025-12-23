import { ServiceAdapter } from '../types'
import { trading212Adapter } from './trading212'
import { wisdomTreeAdapter } from './wisdom-tree'
import { lightyearAdapter, lightyearBatchAdapter, lightyearLookupAdapter } from './lightyear'
import { auto24Adapter } from './auto24'
import { fetchAdapter } from './fetch'

export const adapters: ServiceAdapter[] = [
  trading212Adapter,
  wisdomTreeAdapter,
  lightyearAdapter,
  lightyearBatchAdapter,
  lightyearLookupAdapter,
  auto24Adapter,
  fetchAdapter,
]
