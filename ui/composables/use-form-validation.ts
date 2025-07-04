import { ref, computed, reactive, watch } from 'vue'
import type { ZodSchema, ZodError } from 'zod'

export function useFormValidation<T extends Record<string, any>>(
  schema: ZodSchema<T>,
  initialData: Partial<T> = {}
) {
  const formData = reactive<Record<string, any>>({ ...initialData })
  const errors = ref<Record<string, string>>({})
  const touchedFields = ref<Set<string>>(new Set())

  const flattenErrors = (error: ZodError): Record<string, string> => {
    const flattened: Record<string, string> = {}

    error.errors.forEach(err => {
      const path = err.path.join('.')
      if (path) {
        flattened[path] = err.message
      }
    })

    return flattened
  }

  const setNestedValue = (obj: any, path: string, value: any): void => {
    const keys = path.split('.')
    const lastKey = keys.pop()

    if (!lastKey || keys.some(k => k === '__proto__' || k === 'constructor' || k === 'prototype')) {
      return
    }

    const target = keys.reduce((current, key) => {
      if (!current[key]) current[key] = {}
      return current[key]
    }, obj)

    target[lastKey] = value
  }

  const validateField = (field: string, value: any) => {
    try {
      const tempData = { ...formData }

      if (field.includes('.')) {
        setNestedValue(tempData, field, value)
      } else {
        tempData[field] = value
      }

      const result = schema.safeParse(tempData)

      if (!result.success) {
        const fieldError = result.error.errors.find(
          err => err.path.join('.') === field || err.path[0] === field
        )
        if (fieldError) {
          errors.value[field] = fieldError.message
        } else {
          delete errors.value[field]
        }
      } else {
        delete errors.value[field]
      }
    } catch (_error) {
      errors.value[field] = 'Invalid value'
    }
  }

  const validateForm = (): boolean => {
    try {
      schema.parse(formData)
      errors.value = {}
      return true
    } catch (error) {
      if (error instanceof Error && 'errors' in error) {
        const zodError = error as ZodError
        errors.value = flattenErrors(zodError)
      }
      return false
    }
  }

  const isValid = computed(() => {
    const result = schema.safeParse(formData)
    return result.success
  })

  const getFieldError = (field: string): string | undefined => {
    return touchedFields.value.has(field) ? errors.value[field] : undefined
  }

  const updateField = (field: string, value: any) => {
    ;(formData as any)[field] = value
    touchedFields.value.add(field)
    validateField(field, value)
  }

  const touchField = (field: string) => {
    touchedFields.value.add(field)
    const value = (formData as any)[field]
    validateField(field, value === undefined ? '' : value)
  }

  const resetForm = (newData: Record<string, any> = {}) => {
    Object.keys(formData).forEach(key => {
      delete (formData as any)[key]
    })
    Object.assign(formData, newData)
    errors.value = {}
    touchedFields.value.clear()
  }

  watch(
    () => initialData,
    newData => {
      Object.assign(formData, newData)
    },
    { deep: true }
  )

  return {
    formData,
    errors,
    isValid,
    validateForm,
    validateField,
    updateField,
    touchField,
    getFieldError,
    resetForm,
  }
}
