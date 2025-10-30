import { httpClient } from '../utils/http-client'
import { type InstrumentDto, PriceChangePeriod } from '../models/generated/domain-models'

export const instrumentsService = {
  getAll: (platforms?: string[], period: PriceChangePeriod = PriceChangePeriod.P24H) => {
    const params: Record<string, any> = { period }
    if (platforms && platforms.length > 0) {
      params.platforms = platforms
    }
    return httpClient.get<InstrumentDto[]>('/instruments', { params }).then(res => res.data)
  },

  create: (data: Partial<InstrumentDto>) =>
    httpClient.post<InstrumentDto>('/instruments', data).then(res => res.data),

  update: (id: number | string, data: Partial<InstrumentDto>) =>
    httpClient.put<InstrumentDto>(`/instruments/${id}`, data).then(res => res.data),

  refreshPrices: () =>
    httpClient.post<{ status: string }>('/instruments/refresh-prices').then(res => res.data),
}
