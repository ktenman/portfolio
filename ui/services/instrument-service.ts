import {ApiError} from '../models/api-error'
import {Instrument} from '../models/instrument'

export class InstrumentService {
  private readonly baseUrl = '/api/instruments'

  async saveInstrument(instrument: Instrument): Promise<Instrument> {
    const response = await fetch(this.baseUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(instrument),
    })

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new ApiError(
        response.status,
        errorData?.message ?? 'Failed to save instrument',
        errorData?.debugMessage ?? `HTTP error! status: ${response.status}`,
        errorData?.validationErrors ?? {}
      )
    }

    return response.json()
  }

  async getAllInstruments(): Promise<Instrument[]> {
    const response = await fetch(this.baseUrl)

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new ApiError(
        response.status,
        errorData?.message ?? 'Failed to fetch instruments',
        errorData?.debugMessage ?? `HTTP error! status: ${response.status}`,
        errorData?.validationErrors ?? {}
      )
    }

    return response.json()
  }
}
