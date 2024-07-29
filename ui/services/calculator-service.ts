import { ApiError } from '../models/api-error'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'

export class CalculatorService {
  private apiUrl = '/api/calculator'

  @Cacheable(CACHE_KEYS.XIRR)
  async getXirr(): Promise<number> {
    const response = await fetch(`${this.apiUrl}`)
    if (!response.ok) {
      throw await this.handleErrorResponse(response)
    }
    const xirr = await response.json()
    return Number(xirr)
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
