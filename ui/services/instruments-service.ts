import { httpClient } from '../utils/http-client'
import type { Instrument } from '../models/instrument'

export const instrumentsService = {
  getAll: () => httpClient.get<Instrument[]>('/instruments').then(res => res.data),

  create: (data: Partial<Instrument>) =>
    httpClient.post<Instrument>('/instruments', data).then(res => res.data),

  update: (id: number | string, data: Partial<Instrument>) =>
    httpClient.put<Instrument>(`/instruments/${id}`, data).then(res => res.data),
}
