import { httpClient } from '../utils/http-client'
import type {
  AllocationDto,
  DiversificationCalculatorResponseDto,
  EtfDetailDto,
} from '../models/generated/domain-models'
import { API_ENDPOINTS } from '../constants'

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
}
