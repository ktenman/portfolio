import { type AnnualWindowsDto, type XirrWindowsDto } from '../../models/generated/domain-models'
import { API_ENDPOINTS } from '../../constants/api'
import { apiRoute, type RouteStub } from './stub'

const WINDOWS = [
  { period: '1M', fromDate: '2026-07-07', xirr: 0.689492, annualReturn: 0.252707 },
  { period: '3M', fromDate: '2026-05-07', xirr: -0.085556, annualReturn: 0.385203 },
  { period: '6M', fromDate: '2026-02-07', xirr: 0.6839, annualReturn: -0.034291 },
  { period: '1Y', fromDate: '2025-08-07', xirr: 0.329316, annualReturn: 0.187415 },
  { period: '2Y', fromDate: '2024-08-07', xirr: 0.259209, annualReturn: 0.204862 },
  { period: '3Y', fromDate: null, xirr: 0.246688, annualReturn: 0.198337 },
]

const XIRR_WINDOWS: XirrWindowsDto = {
  windows: WINDOWS.map(({ period, fromDate, xirr }) => ({ period, fromDate, xirr })),
}

const ANNUAL_WINDOWS: AnnualWindowsDto = {
  windows: WINDOWS.map(({ period, fromDate, annualReturn }) => ({
    period,
    fromDate,
    annualReturn,
  })),
}

export const stubWindows: RouteStub = async page => {
  await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_XIRR_WINDOWS), route =>
    route.fulfill({ json: XIRR_WINDOWS })
  )
  await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_ANNUAL_WINDOWS), route =>
    route.fulfill({ json: ANNUAL_WINDOWS })
  )
}
