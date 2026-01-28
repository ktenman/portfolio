import { httpClient } from '../utils/http-client'
import { API_ENDPOINTS } from '../constants'

const LOGO_SEARCH_TIMEOUT = 60000

export interface LogoCandidateDto {
  thumbnailUrl: string
  title: string
  index: number
  imageDataUrl?: string
}

export interface LogoReplacementRequest {
  holdingUuid: string
  candidateIndex: number
}

export interface LogoReplacementResponse {
  success: boolean
  message: string
}

export const logoService = {
  getCandidates: (holdingUuid: string) =>
    httpClient.get<LogoCandidateDto[]>(`${API_ENDPOINTS.LOGOS}/${holdingUuid}/candidates`, {
      timeout: LOGO_SEARCH_TIMEOUT,
    }),

  searchByName: (name: string) =>
    httpClient.get<LogoCandidateDto[]>(`${API_ENDPOINTS.LOGOS}/search`, {
      params: { name },
      timeout: LOGO_SEARCH_TIMEOUT,
    }),

  replaceLogo: (request: LogoReplacementRequest) =>
    httpClient.post<LogoReplacementResponse>(`${API_ENDPOINTS.LOGOS}/replace`, request),

  prefetchCandidates: (holdingUuids: string[]) =>
    httpClient.post<void>(`${API_ENDPOINTS.LOGOS}/prefetch`, { holdingUuids }),
}
