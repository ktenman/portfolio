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
    return this.makeRequest(this.apiUrl)
  }

  @CachePut(CACHE_KEYS.INSTRUMENTS)
  async saveInstrument(instrument: Instrument): Promise<Instrument> {
    return this.makeRequest(this.apiUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(instrument),
    })
  }

  @CacheEvict(CACHE_KEYS.INSTRUMENTS)
  async updateInstrument(id: number, instrument: Instrument): Promise<Instrument> {
    return this.makeRequest(`${this.apiUrl}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(instrument),
    })
  }

  @CacheEvict(CACHE_KEYS.INSTRUMENTS)
  async deleteInstrument(id: number): Promise<void> {
    await this.makeRequest(`${this.apiUrl}/${id}`, {
      method: 'DELETE',
    })
  }

  private async makeRequest(url: string, options: RequestInit = {}): Promise<any> {
    const response = await fetch(url, {
      ...options,
      redirect: 'manual', // This prevents automatic redirect following
    })

    if (response.type === 'opaqueredirect' || response.status === 302) {
      // Handle redirect manually
      const location = response.headers.get('Location')
      if (location) {
        window.location.href = location
        throw new Error('Redirecting to login page')
      }
    }

    if (!response.ok) {
      const errorData = await response.json()
      throw new ApiError(
        response.status,
        errorData.message || 'An error occurred',
        errorData.debugMessage || 'No debug message provided',
        errorData.validationErrors || {}
      )
    }

    return response.json()
  }
}
