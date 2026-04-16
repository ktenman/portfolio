import { ServiceAdapter } from '../types'
import { trading212Adapter } from './trading212'
import { trading212EtfHoldingsAdapter, trading212EtfSummaryAdapter } from './trading212-etf'
import { lightyearAdapter, lightyearBatchAdapter, lightyearLookupAdapter } from './lightyear'
import { auto24Adapter } from './auto24'
import { fetchAdapter } from './fetch'
import { bingAdapter } from './bing'

export const adapters: ServiceAdapter[] = [
  trading212Adapter,
  trading212EtfHoldingsAdapter,
  trading212EtfSummaryAdapter,
  lightyearAdapter,
  lightyearBatchAdapter,
  lightyearLookupAdapter,
  auto24Adapter,
  fetchAdapter,
  bingAdapter,
]
