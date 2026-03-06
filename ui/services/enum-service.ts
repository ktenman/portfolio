import { httpClient } from '../utils/http-client'
import type { EnumsResponse } from '../models/generated/domain-models'
import { API_ENDPOINTS } from '../constants'

export const enumService = {
  getAll: () => httpClient.get<EnumsResponse>(API_ENDPOINTS.ENUMS),
}
