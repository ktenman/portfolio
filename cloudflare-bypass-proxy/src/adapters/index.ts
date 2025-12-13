import { ServiceAdapter } from '../types'
import { trading212Adapter } from './trading212'
import { wisdomTreeAdapter } from './wisdom-tree'
import { lightyearAdapter } from './lightyear'
import { lightyearHoldingsAdapter } from './lightyear-holdings'
import { genericFetchAdapter } from './generic-fetch'
import { auto24PuppeteerCaptchaAdapter, auto24PuppeteerSubmitAdapter } from './auto24-puppeteer'

export const adapters: ServiceAdapter[] = [
  trading212Adapter,
  wisdomTreeAdapter,
  lightyearAdapter,
  lightyearHoldingsAdapter,
  genericFetchAdapter,
  auto24PuppeteerCaptchaAdapter,
  auto24PuppeteerSubmitAdapter,
]
