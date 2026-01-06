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
    httpClient
      .get<LogoCandidateDto[]>(`${API_ENDPOINTS.LOGOS}/${holdingUuid}/candidates`, {
        timeout: LOGO_SEARCH_TIMEOUT,
      })
      .then(res => res.data),

  searchByName: (name: string) =>
    httpClient
      .get<LogoCandidateDto[]>(`${API_ENDPOINTS.LOGOS}/search`, {
        params: { name },
        timeout: LOGO_SEARCH_TIMEOUT,
      })
      .then(res => res.data),

  replaceLogo: (request: LogoReplacementRequest) =>
    httpClient
      .post<LogoReplacementResponse>(`${API_ENDPOINTS.LOGOS}/replace`, request)
      .then(res => res.data),

  prefetchCandidates: (holdingUuids: string[]) =>
    httpClient.post(`${API_ENDPOINTS.LOGOS}/prefetch`, { holdingUuids }).then(res => res.data),
}
