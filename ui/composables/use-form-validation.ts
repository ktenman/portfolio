import { computed, ref } from 'vue'

export interface ValidationRule {
  required?: boolean
  min?: number
  max?: number
  pattern?: RegExp
  custom?: (value: any) => boolean | string
}

export interface ValidationRules {
  [field: string]: ValidationRule
}

export const useFormValidation = <T extends Record<string, any>>(rules: ValidationRules) => {
  const errors = ref<Record<string, string>>({})
  const touched = ref<Record<string, boolean>>({})

  const validateField = (field: string, val: any): string | null => {
    const fieldRules = rules[field]
    if (!fieldRules) return null

    if (fieldRules.required && (!val || val === '')) {
      return `${field} is required`
    }

    if (fieldRules.min !== undefined && typeof val === 'number' && val < fieldRules.min) {
      return `${field} must be at least ${fieldRules.min}`
    }

    if (fieldRules.max !== undefined && typeof val === 'number' && val > fieldRules.max) {
      return `${field} must be at most ${fieldRules.max}`
    }

    if (fieldRules.pattern && typeof val === 'string' && !fieldRules.pattern.test(val)) {
      return `${field} format is invalid`
    }

    if (fieldRules.custom) {
      const result = fieldRules.custom(val)
      if (typeof result === 'string') return result
      if (!result) return `${field} is invalid`
    }

    return null
  }

  const validate = (data: T): boolean => {
    const newErrors: Record<string, string> = {}
    let isValid = true

    Object.keys(rules).forEach(field => {
      const error = validateField(field, data[field])
      if (error) {
        newErrors[field] = error
        isValid = false
      }
    })

    errors.value = newErrors
    return isValid
  }

  const validateSingleField = (field: string, val: any) => {
    const error = validateField(field, val)
    if (error) {
      errors.value = { ...errors.value, [field]: error }
    } else {
      const { [field]: omit, ...rest } = errors.value
      errors.value = rest
      void omit
    }
  }

  const touch = (field: string) => {
    touched.value = { ...touched.value, [field]: true }
  }

  const reset = () => {
    errors.value = {}
    touched.value = {}
  }

  const isValid = computed(() => Object.keys(errors.value).length === 0)

  const getError = (field: string) => {
    return touched.value[field] ? errors.value[field] : undefined
  }

  return {
    errors,
    touched,
    validate,
    validateSingleField,
    touch,
    reset,
    isValid,
    getError,
  }
}
