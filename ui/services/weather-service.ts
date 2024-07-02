import {WeatherForecastResponse} from '../models/weather-forecast'
import {ApiError} from '../models/api-error'

export class WeatherService {
  private readonly baseUrl = '/api/weather/forecast'

  async getWeatherForecast(location: string): Promise<WeatherForecastResponse> {
    const url = `${this.baseUrl}?location=${encodeURIComponent(location)}`

    const response = await fetch(url)

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new ApiError(
        response.status,
        errorData?.message ?? 'Failed to fetch weather forecast',
        errorData?.debugMessage ?? `HTTP error! status: ${response.status}`,
        errorData?.validationErrors ?? {}
      )
    }

    try {
      return await response.json()
    } catch (error) {
      throw new Error('Failed to parse weather forecast data')
    }
  }
}
