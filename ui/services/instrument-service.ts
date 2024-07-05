// src/services/instrument-service.ts

import { Instrument } from '../models/instrument'
import { ApiError } from '../models/api-error'

export class InstrumentService {
  private apiUrl = '/api/instruments' // Adjust this URL as needed

  async getAllInstruments(): Promise<Instrument[]> {
    const response = await fetch(this.apiUrl)
    if (!response.ok) {
      throw await this.handleErrorResponse(response)
    }
    return response.json()
  }

  async saveInstrument(instrument: Instrument): Promise<Instrument> {
    const response = await fetch(this.apiUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(instrument),
    })
    if (!response.ok) {
      throw await this.handleErrorResponse(response)
    }
    return response.json()
  }

  async updateInstrument(id: number, instrument: Instrument): Promise<Instrument> {
    const response = await fetch(`${this.apiUrl}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(instrument),
    })
    if (!response.ok) {
      throw await this.handleErrorResponse(response)
    }
    return response.json()
  }

  async deleteInstrument(id: number): Promise<void> {
    const response = await fetch(`${this.apiUrl}/${id}`, {
      method: 'DELETE',
    })
    if (!response.ok) {
      throw await this.handleErrorResponse(response)
    }
  }

  private async handleErrorResponse(response: Response): Promise<ApiError> {
    const errorData = await response.json()
    return new ApiError(
      response.status,
      errorData.message || 'An error occurred',
      errorData.debugMessage || 'No debug message provided',
      errorData.validationErrors || {}
    )
  }
}
