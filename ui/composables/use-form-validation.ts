import { ref, reactive, computed, ComputedRef, Ref } from 'vue'

type ValidationRule<T> = (value: T) => string | true
type ValidationRules<T> = {
  [K in keyof T]?: ValidationRule<T[K]>
}

interface UseFormValidationReturn<T> {
  formData: Ref<T>
  errors: Record<string, string>
  isValid: ComputedRef<boolean>
  validate: () => boolean
  validateField: (field: keyof T) => boolean
  reset: () => void
  setFieldValue: <K extends keyof T>(field: K, value: T[K]) => void
}

export function useFormValidation<T extends Record<string, any>>(
  initialData: T,
  rules: ValidationRules<T> = {}
): UseFormValidationReturn<T> {
  const formData = ref<T>({ ...initialData }) as Ref<T>
  const errors = reactive<Record<string, string>>({})

  const isValid = computed(() => Object.keys(errors).length === 0)

  const validateField = (field: keyof T): boolean => {
    const rule = rules[field]
    if (!rule) return true

    const result = rule(formData.value[field])
    if (result === true) {
      delete errors[field as string]
      return true
    } else {
      errors[field as string] = result
      return false
    }
  }

  const validate = (): boolean => {
    Object.keys(errors).forEach(key => delete errors[key])

    let allValid = true
    Object.keys(rules).forEach(field => {
      const isFieldValid = validateField(field as keyof T)
      if (!isFieldValid) allValid = false
    })

    return allValid
  }

  const reset = () => {
    formData.value = { ...initialData }
    Object.keys(errors).forEach(key => delete errors[key])
  }

  const setFieldValue = <K extends keyof T>(field: K, value: T[K]) => {
    formData.value[field] = value
    if (rules[field]) {
      validateField(field)
    }
  }

  return {
    formData,
    errors,
    isValid,
    validate,
    validateField,
    reset,
    setFieldValue,
  }
}

export const validators = {
  required:
    (message = 'This field is required') =>
    (value: any) =>
      (value !== null && value !== undefined && value !== '') || message,

  minLength: (min: number, message?: string) => (value: string) =>
    value.length >= min || message || `Must be at least ${min} characters`,

  maxLength: (max: number, message?: string) => (value: string) =>
    value.length <= max || message || `Must be at most ${max} characters`,

  min: (min: number, message?: string) => (value: number) =>
    value >= min || message || `Must be at least ${min}`,

  max: (max: number, message?: string) => (value: number) =>
    value <= max || message || `Must be at most ${max}`,

  email:
    (message = 'Invalid email address') =>
    (value: string) =>
      /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) || message,

  pattern:
    (pattern: RegExp, message = 'Invalid format') =>
    (value: string) =>
      pattern.test(value) || message,
}
