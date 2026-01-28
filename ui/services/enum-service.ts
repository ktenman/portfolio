import { httpClient } from '../utils/http-client'
import { API_ENDPOINTS } from '../constants'

interface EnumValues {
  platforms: string[]
  providers: string[]
  transactionTypes: string[]
  categories: string[]
  currencies: string[]
}

export const enumService = {
  getAll: () => httpClient.get<EnumValues>(API_ENDPOINTS.ENUMS),
}
