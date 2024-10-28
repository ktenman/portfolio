import { Instrument } from '../models/instrument'
import { ApiClient } from './api-client.ts'

export class InstrumentService {
  private apiUrl = '/api/instruments'

  async getAllInstruments(): Promise<Instrument[]> {
    return ApiClient.get<Instrument[]>(this.apiUrl)
  }

  async saveInstrument(instrument: Instrument): Promise<Instrument> {
    return ApiClient.post<Instrument>(this.apiUrl, instrument)
  }

  async updateInstrument(id: number, instrument: Instrument): Promise<Instrument> {
    return ApiClient.put<Instrument>(`${this.apiUrl}/${id}`, instrument)
  }

  async deleteInstrument(id: number): Promise<void> {
    await ApiClient.delete(`${this.apiUrl}/${id}`)
  }
}
