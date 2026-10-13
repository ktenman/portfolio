import type { Page as BrowserPage } from '@playwright/test'
import { API_ENDPOINTS } from '../../constants/api'
import type { PortfolioSummaryDto } from '../../models/generated/domain-models'
import { addDays, daysBetween } from '../../utils/iso-dates'
import { apiRoute } from './stub'

export const monthlyHistoryFixture = (years = 7): PortfolioSummaryDto[] => {
  const first = `${2027 - years - 1}-12-31`
  const last = '2026-10-12'
  let profit = 9000
  return Array.from({ length: daysBetween(first, last) + 1 }, (_, index) => {
    const date = addDays(first, index)
    const month = Number(date.slice(5, 7))
    profit += Math.sin(index / 9) * 85 + (month === 3 ? -95 : 45)
    return {
      date,
      totalValue: 100000 + index * 12 + profit,
      totalProfit: profit,
      realizedProfit: index * 2,
      unrealizedProfit: profit - index * 2,
      xirrAnnualReturn: 0.18 + month / 1000,
      earningsPerDay: 43.21,
      earningsPerMonth: 1314.99,
      totalProfitChange24h: null,
    }
  })
}

export const monthlyGapFixture = () =>
  monthlyHistoryFixture().filter(
    row =>
      !row.date.startsWith('2021-08') &&
      !(row.date >= '2022-12-20' && row.date <= '2023-01-02') &&
      !row.date.startsWith('2024-')
  )

export const stubMonthlyHistory = async (page: BrowserPage, rows: PortfolioSummaryDto[]) => {
  await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES), route =>
    route.fulfill({ json: rows })
  )
  await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_CURRENT), route =>
    route.fulfill({ json: rows[rows.length - 1] })
  )
  await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_HISTORICAL), route =>
    route.fulfill({
      json: {
        content: rows.slice(-30).reverse(),
        totalPages: 1,
        totalElements: 30,
        size: 30,
        number: 0,
      },
    })
  )
}
