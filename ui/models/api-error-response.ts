export interface ApiErrorResponse {
  status: string
  message: string
  debugMessage: string
  validationErrors: Record<string, string>
}
