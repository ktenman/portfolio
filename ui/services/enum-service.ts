import { httpClient } from '../utils/http-client'

interface EnumValues {
  platforms: string[]
  providers: string[]
  transactionTypes: string[]
  categories: string[]
  currencies: string[]
}

export const enumService = {
  getAll: () => httpClient.get<EnumValues>('/enums').then(res => res.data),
}
