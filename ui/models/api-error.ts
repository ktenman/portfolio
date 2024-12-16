/* eslint-disable no-unused-vars */
export class ApiError extends Error {
  constructor(
    public status: number,
    public message: string,
    public debugMessage: string,
    public validationErrors: Record<string, string> = {}
  ) {
    super(message)
    this.name = 'ApiError'
  }

  static isApiError(error: unknown): error is ApiError {
    return error instanceof ApiError
  }
}

/* eslint-enable no-unused-vars */
