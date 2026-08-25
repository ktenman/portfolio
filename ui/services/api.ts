import { httpClient } from '../utils/http-client'
import {
  type AllocationDto,
  type AnnualWindowsDto,
  BenchmarkIndex,
  type BenchmarkPointDto,
  type CalculationResult,
  type DiversificationCalculatorResponseDto,
  type EnumsResponse,
  type EtfDetailDto,
  type EtfHoldingBreakdownDto,
  type InstrumentDto,
  type InstrumentsResponse,
  type PortfolioSummaryDto,
  type RangeChangeDto,
  TimeRange,
  type TransactionsWithSummaryDto,
  type XirrWindowsDto,
} from '../models/generated/domain-models'
import type { Page } from '../models/page'
import type { CachedState } from '../components/diversification/types'
import { API_ENDPOINTS } from '../constants'

const LOGO_SEARCH_TIMEOUT = 60000

const platformParams = (platforms?: string[]) => (platforms?.length ? { platforms } : {})

interface BuildInfo {
  hash: string
  time: string
}

export interface LogoCandidateDto {
  thumbnailUrl: string
  title: string
  index: number
  imageDataUrl?: string
}

export interface LogoReplacementRequest {
  holdingUuid: string
  candidateIndex: number
}

export interface LogoReplacementResponse {
  success: boolean
  message: string
}

export const enumService = {
  getAll: () => httpClient.get<EnumsResponse>(API_ENDPOINTS.ENUMS),
}

export const transactionsService = {
  getAll: (platforms?: string[], fromDate?: string, untilDate?: string) =>
    httpClient.get<TransactionsWithSummaryDto>(API_ENDPOINTS.TRANSACTIONS, {
      params: {
        ...platformParams(platforms),
        ...(fromDate ? { fromDate } : {}),
        ...(untilDate ? { untilDate } : {}),
      },
    }),

  getPlatforms: () => httpClient.get<string[]>(API_ENDPOINTS.TRANSACTIONS_PLATFORMS),
}

export const instrumentsService = {
  getAll: (platforms?: string[], period: TimeRange = TimeRange.ONE_DAY) =>
    httpClient.get<InstrumentsResponse>(API_ENDPOINTS.INSTRUMENTS, {
      params: { period, ...platformParams(platforms) },
    }),

  create: (data: Partial<InstrumentDto>) =>
    httpClient.post<InstrumentDto>(API_ENDPOINTS.INSTRUMENTS, data),

  update: (id: number | string, data: Partial<InstrumentDto>) =>
    httpClient.put<InstrumentDto>(`${API_ENDPOINTS.INSTRUMENTS}/${id}`, data),

  refreshPrices: () =>
    httpClient.post<{ status: string }>(API_ENDPOINTS.INSTRUMENTS_REFRESH_PRICES),
}

export const portfolioSummaryService = {
  getHistorical: (page: number, size: number, platforms?: string[]) =>
    httpClient.get<Page<PortfolioSummaryDto>>(API_ENDPOINTS.PORTFOLIO_SUMMARY_HISTORICAL, {
      params: { page, size, ...platformParams(platforms) },
    }),

  getCurrent: (platforms?: string[]) =>
    httpClient.get<PortfolioSummaryDto>(API_ENDPOINTS.PORTFOLIO_SUMMARY_CURRENT, {
      params: platformParams(platforms),
    }),

  getSeries: (range: TimeRange, platforms?: string[]) =>
    httpClient.get<PortfolioSummaryDto[]>(API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES, {
      params: { range, ...platformParams(platforms) },
    }),

  getBenchmark: (range: TimeRange, index: BenchmarkIndex) =>
    httpClient.get<BenchmarkPointDto[]>(API_ENDPOINTS.PORTFOLIO_SUMMARY_BENCHMARK, {
      params: { range, index },
    }),

  getRangeChange: (range: TimeRange, platforms?: string[]) =>
    httpClient.get<RangeChangeDto>(API_ENDPOINTS.PORTFOLIO_SUMMARY_RANGE_CHANGE, {
      params: { range, ...platformParams(platforms) },
    }),

  getXirrWindows: (platforms?: string[]) =>
    httpClient.get<XirrWindowsDto>(API_ENDPOINTS.PORTFOLIO_SUMMARY_XIRR_WINDOWS, {
      params: platformParams(platforms),
    }),

  getAnnualWindows: (platforms?: string[]) =>
    httpClient.get<AnnualWindowsDto>(API_ENDPOINTS.PORTFOLIO_SUMMARY_ANNUAL_WINDOWS, {
      params: platformParams(platforms),
    }),

  recalculate: () =>
    httpClient.post<{ message: string }>(API_ENDPOINTS.PORTFOLIO_SUMMARY_RECALCULATE, undefined, {
      timeout: 60000,
    }),
}

export const diversificationService = {
  getAvailableEtfs: () =>
    httpClient.get<EtfDetailDto[]>(`${API_ENDPOINTS.DIVERSIFICATION}/available-etfs`),

  calculate: (allocations: AllocationDto[]) =>
    httpClient.post<DiversificationCalculatorResponseDto>(
      `${API_ENDPOINTS.DIVERSIFICATION}/calculate`,
      { allocations }
    ),

  getConfig: () =>
    httpClient.get<CachedState>(`${API_ENDPOINTS.DIVERSIFICATION}/config`).catch(() => null),

  saveConfig: (config: CachedState) =>
    httpClient.put<CachedState>(`${API_ENDPOINTS.DIVERSIFICATION}/config`, config),
}

export const etfBreakdownService = {
  getBreakdown: (etfSymbols?: string[], platforms?: string[]) =>
    httpClient.get<EtfHoldingBreakdownDto[]>(API_ENDPOINTS.ETF_BREAKDOWN, {
      params: { ...(etfSymbols?.length ? { etfSymbols } : {}), ...platformParams(platforms) },
    }),
}

export const logoService = {
  getCandidates: (holdingUuid: string) =>
    httpClient.get<LogoCandidateDto[]>(`${API_ENDPOINTS.LOGOS}/${holdingUuid}/candidates`, {
      timeout: LOGO_SEARCH_TIMEOUT,
    }),

  searchByName: (name: string) =>
    httpClient.get<LogoCandidateDto[]>(`${API_ENDPOINTS.LOGOS}/search`, {
      params: { name },
      timeout: LOGO_SEARCH_TIMEOUT,
    }),

  replaceLogo: (request: LogoReplacementRequest) =>
    httpClient.post<LogoReplacementResponse>(`${API_ENDPOINTS.LOGOS}/replace`, request),

  prefetchCandidates: (holdingUuids: string[]) =>
    httpClient.post<void>(`${API_ENDPOINTS.LOGOS}/prefetch`, { holdingUuids }),

  getLogoUrl: (uuid: string): string => `/api${API_ENDPOINTS.LOGOS}/${uuid}`,
}

export const utilityService = {
  getCalculationResult: () => httpClient.get<CalculationResult>(API_ENDPOINTS.CALCULATOR),

  getBuildInfo: () => httpClient.get<BuildInfo>(API_ENDPOINTS.BUILD_INFO),
}
