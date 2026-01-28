import { httpClient } from '../utils/http-client'
import {
  type InstrumentDto,
  type InstrumentsResponse,
  PriceChangePeriod,
} from '../models/generated/domain-models'
import { API_ENDPOINTS } from '../constants'

export const instrumentsService = {
  getAll: (platforms?: string[], period: PriceChangePeriod = PriceChangePeriod.P24H) => {
    const params: Record<string, any> = { period }
    if (platforms && platforms.length > 0) {
      params.platforms = platforms
    }
    return httpClient.get<InstrumentsResponse>(API_ENDPOINTS.INSTRUMENTS, { params })
  },

  create: (data: Partial<InstrumentDto>) =>
    httpClient.post<InstrumentDto>(API_ENDPOINTS.INSTRUMENTS, data),

  update: (id: number | string, data: Partial<InstrumentDto>) =>
    httpClient.put<InstrumentDto>(`${API_ENDPOINTS.INSTRUMENTS}/${id}`, data),

  refreshPrices: () =>
    httpClient.post<{ status: string }>(API_ENDPOINTS.INSTRUMENTS_REFRESH_PRICES),
}
