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
    httpClient
      .get<EtfDetailDto[]>(`${API_ENDPOINTS.DIVERSIFICATION}/available-etfs`)
      .then(res => res.data),

  calculate: (allocations: AllocationDto[]) =>
    httpClient
      .post<DiversificationCalculatorResponseDto>(`${API_ENDPOINTS.DIVERSIFICATION}/calculate`, {
        allocations,
      })
      .then(res => res.data),

  getConfig: () =>
    httpClient
      .get<CachedState>(`${API_ENDPOINTS.DIVERSIFICATION}/config`)
      .then(res => res.data)
      .catch(() => null),

  saveConfig: (config: CachedState) =>
    httpClient
      .put<CachedState>(`${API_ENDPOINTS.DIVERSIFICATION}/config`, config)
      .then(res => res.data),
}
