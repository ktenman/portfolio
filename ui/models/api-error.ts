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
}
