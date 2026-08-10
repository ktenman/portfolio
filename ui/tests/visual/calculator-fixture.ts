import { type CalculationResult } from '../../models/generated/domain-models'
import { API_ENDPOINTS } from '../../constants/api'
import { apiRoute, type RouteStub } from './stub'

const CASH_FLOW_COUNT = 139
const FIRST_CASH_FLOW_MS = Date.UTC(2016, 0, 8)
const CASH_FLOW_INTERVAL_MS = 28 * 86400000

const RESULT: CalculationResult = {
  cashFlows: Array.from({ length: CASH_FLOW_COUNT }, (_, index) => ({
    date: new Date(FIRST_CASH_FLOW_MS + index * CASH_FLOW_INTERVAL_MS).toISOString().slice(0, 10),
    amount: 22.5 - 96 * Math.exp(-index / 4.5) + 2.4 * Math.sin(index / 3.1),
  })),
  median: 19.847302516384927,
  average: 15.632948170255413,
  total: 164272.83910456281,
}

export const stubCalculator: RouteStub = async page => {
  await page.route(apiRoute(API_ENDPOINTS.CALCULATOR), route => route.fulfill({ json: RESULT }))
}
