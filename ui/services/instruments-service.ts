import { httpClient } from '../utils/http-client'
import type { Instrument } from '../models/instrument'

export const instrumentsService = {
  getAll: (platforms?: string[]) => {
    const params = platforms && platforms.length > 0 ? { platforms } : {}
    return httpClient.get<Instrument[]>('/instruments', { params }).then(res => res.data)
  },

  create: (data: Partial<Instrument>) =>
    httpClient.post<Instrument>('/instruments', data).then(res => res.data),

  update: (id: number | string, data: Partial<Instrument>) =>
    httpClient.put<Instrument>(`/instruments/${id}`, data).then(res => res.data),

  refreshPrices: () =>
    httpClient.post<{ status: string }>('/instruments/refresh-prices').then(res => res.data),
}
