import { ref, reactive, computed, watch, Ref, ComputedRef } from 'vue'

type ValidationRule<T> = (value: T) => string | true
type ValidationRules<T> = {
  [K in keyof T]?: ValidationRule<T[K]>
}

interface UseEnhancedFormOptions<T> {
  initialData: T | Ref<T>
  rules?: ValidationRules<T>
  validateOnChange?: boolean
  resetOnSubmit?: boolean
}

interface UseEnhancedFormReturn<T> {
  formData: Ref<T>
  errors: Record<string, string>
  isValid: ComputedRef<boolean>
  isDirty: ComputedRef<boolean>
  isSubmitting: Ref<boolean>
  validate: () => boolean
  validateField: (field: keyof T) => boolean
  clearError: (field: keyof T) => void
  clearErrors: () => void
  setFieldValue: <K extends keyof T>(field: K, value: T[K]) => void
  setValues: (values: Partial<T>) => void
  reset: () => void
  handleSubmit: (onSubmit: (data: T) => void | Promise<void>) => Promise<void>
}

export function useEnhancedForm<T extends Record<string, any>>(
  options: UseEnhancedFormOptions<T>
): UseEnhancedFormReturn<T> {
  const {
    initialData,
    rules = {} as ValidationRules<T>,
    validateOnChange = true,
    resetOnSubmit = false,
  } = options

  const getInitialData = () => {
    return ref(initialData).value
  }

  const formData = ref<T>({ ...getInitialData() }) as Ref<T>
  const errors = reactive({} as Record<string, string>)
  const isSubmitting = ref(false)
  const originalData = ref<T>({ ...getInitialData() })

  const isValid = computed(() => Object.keys(errors).length === 0)
  const isDirty = computed(
    () => JSON.stringify(formData.value) !== JSON.stringify(originalData.value)
  )

  const validateField = (field: keyof T): boolean => {
    const rule = rules[field]
    if (!rule) return true

    const result = rule(formData.value[field])
    if (result === true) {
      delete errors[String(field)]
      return true
    } else {
      errors[String(field)] = result
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

  const clearError = (field: keyof T) => {
    delete errors[String(field)]
  }

  const clearErrors = () => {
    Object.keys(errors).forEach(key => delete errors[key])
  }

  const setFieldValue = <K extends keyof T>(field: K, value: T[K]) => {
    formData.value[field] = value
    if (validateOnChange && rules[field as keyof T]) {
      validateField(field as keyof T)
    }
  }

  const setValues = (values: Partial<T>) => {
    Object.entries(values).forEach(([field, value]) => {
      setFieldValue(field as keyof T, value as T[keyof T])
    })
  }

  const reset = () => {
    formData.value = { ...originalData.value }
    clearErrors()
  }

  const handleSubmit = async (onSubmit: (data: T) => void | Promise<void>) => {
    if (!validate()) return

    isSubmitting.value = true
    try {
      await onSubmit(formData.value)
      if (resetOnSubmit) {
        reset()
      }
    } finally {
      isSubmitting.value = false
    }
  }

  if (ref(initialData)) {
    watch(
      () => ref(initialData).value,
      newData => {
        originalData.value = { ...newData }
        formData.value = { ...newData }
        clearErrors()
      },
      { deep: true }
    )
  }

  return {
    formData,
    errors,
    isValid,
    isDirty,
    isSubmitting,
    validate,
    validateField,
    clearError,
    clearErrors,
    setFieldValue,
    setValues,
    reset,
    handleSubmit,
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

  positive:
    (message = 'Value must be positive') =>
    (value: number) =>
      value > 0 || message,

  numeric:
    (message = 'Must be a number') =>
    (value: any) =>
      !isNaN(Number(value)) || message,

  date:
    (message = 'Invalid date') =>
    (value: string) =>
      !isNaN(Date.parse(value)) || message,

  url:
    (message = 'Invalid URL') =>
    (value: string) => {
      try {
        new URL(value)
        return true
      } catch {
        return message
      }
    },

  compose:
    (...rules: ValidationRule<any>[]) =>
    (value: any) => {
      for (const rule of rules) {
        const result = rule(value)
        if (result !== true) return result
      }
      return true
    },
}
