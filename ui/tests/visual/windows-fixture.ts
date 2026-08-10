import {
  type AnnualWindowsDto,
  type XirrWindowsDto,
} from '../../models/generated/domain-models'
import { API_ENDPOINTS } from '../../constants/api'
import { apiRoute, type RouteStub } from './stub'

const PERIODS = [
  { period: '1M', fromDate: '2026-07-07' },
  { period: '3M', fromDate: '2026-05-07' },
  { period: '6M', fromDate: '2026-02-07' },
  { period: '1Y', fromDate: '2025-08-07' },
  { period: '2Y', fromDate: '2024-08-07' },
  { period: '3Y', fromDate: null },
]

const XIRR = [0.689492, -0.085556, 0.6839, 0.329316, 0.259209, 0.246688]
const ANNUAL = [0.252707, 0.385203, -0.034291, 0.187415, 0.204862, 0.198337]

const XIRR_WINDOWS: XirrWindowsDto = {
  windows: PERIODS.map((window, index) => ({ ...window, xirr: XIRR[index] })),
}

const ANNUAL_WINDOWS: AnnualWindowsDto = {
  windows: PERIODS.map((window, index) => ({ ...window, annualReturn: ANNUAL[index] })),
}

export const stubWindows: RouteStub = async page => {
  await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_XIRR_WINDOWS), route =>
    route.fulfill({ json: XIRR_WINDOWS })
  )
  await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_ANNUAL_WINDOWS), route =>
    route.fulfill({ json: ANNUAL_WINDOWS })
  )
}
