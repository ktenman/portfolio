interface ValidationErrors {
  [key: string]: string
}

/* eslint-disable no-unused-vars */
export class ApiError {
  constructor(
    public status: number | string,
    public message: string,
    public debugMessage: string,
    public validationErrors: ValidationErrors = {}
  ) {}
}

/* eslint-enable no-unused-vars */
