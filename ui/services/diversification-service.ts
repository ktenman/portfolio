import { httpClient } from '../utils/http-client'
import type {
  AllocationDto,
  DiversificationCalculatorResponseDto,
  EtfDetailDto,
} from '../models/generated/domain-models'
import { API_ENDPOINTS } from '../constants'
import type { CachedState } from '../components/diversification/types'

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
