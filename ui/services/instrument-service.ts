import { Instrument } from '../models/instrument'
import { ApiError } from '../models/api-error'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CachePut } from '../decorators/cache-put.decorator'
import { CacheEvict } from '../decorators/cache-evict.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'

export class InstrumentService {
  private apiUrl = '/api/instruments'

  @Cacheable(CACHE_KEYS.INSTRUMENTS)
  async getAllInstruments(): Promise<Instrument[]> {
    const response = await fetch(this.apiUrl)
    return this.handleResponse(response)
  }

  @CachePut(CACHE_KEYS.INSTRUMENTS)
  async saveInstrument(instrument: Instrument): Promise<Instrument> {
    const response = await fetch(this.apiUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(instrument),
    })
    return this.handleResponse(response)
  }

  @CacheEvict(CACHE_KEYS.INSTRUMENTS)
  async updateInstrument(id: number, instrument: Instrument): Promise<Instrument> {
    const response = await fetch(`${this.apiUrl}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(instrument),
    })
    return this.handleResponse(response)
  }

  @CacheEvict(CACHE_KEYS.INSTRUMENTS)
  async deleteInstrument(id: number): Promise<void> {
    const response = await fetch(`${this.apiUrl}/${id}`, {
      method: 'DELETE',
    })
    await this.handleResponse(response)
  }

  private async handleResponse(response: Response): Promise<any> {
    if (response.ok) {
      return response.json()
    }

    if (response.status === 302 || response.status === 304) {
      // The backend is requesting a redirect, follow it
      window.location.href = response.headers.get('Location') || '/login'
      throw new Error('Redirecting as requested by the server')
    }

    const errorData = await response.json()
    throw new ApiError(
      response.status,
      errorData.message || 'An error occurred',
      errorData.debugMessage || 'No debug message provided',
      errorData.validationErrors || {}
    )
  }
}
