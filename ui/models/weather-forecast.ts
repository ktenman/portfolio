import { WeatherForecast } from './weather-forecast-response'

export interface WeatherForecastResponse {
  [location: string]: WeatherForecast[]
}
