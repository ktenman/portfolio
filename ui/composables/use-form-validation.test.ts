import { describe, it, expect } from 'vitest'
import { useFormValidation } from './use-form-validation'

describe('useFormValidation', () => {
  describe('required validation', () => {
    it('validates required fields', () => {
      const rules = {
        name: { required: true },
        email: { required: true },
      }
      const { validate, errors } = useFormValidation(rules)

      expect(validate({ name: '', email: '' })).toBe(false)
      expect(errors.value).toEqual({
        name: 'name is required',
        email: 'email is required',
      })

      expect(validate({ name: 'John', email: 'john@example.com' })).toBe(true)
      expect(errors.value).toEqual({})
    })

    it('handles null and undefined as empty', () => {
      const rules = { field: { required: true } }
      const { validate, errors } = useFormValidation(rules)

      expect(validate({ field: null })).toBe(false)
      expect(errors.value.field).toBe('field is required')

      expect(validate({ field: undefined })).toBe(false)
      expect(errors.value.field).toBe('field is required')
    })
  })

  describe('min/max validation', () => {
    it('validates minimum values', () => {
      const rules = { age: { min: 18 } }
      const { validate, errors } = useFormValidation(rules)

      expect(validate({ age: 17 })).toBe(false)
      expect(errors.value.age).toBe('age must be at least 18')

      expect(validate({ age: 18 })).toBe(true)
      expect(errors.value).toEqual({})
    })

    it('validates maximum values', () => {
      const rules = { score: { max: 100 } }
      const { validate, errors } = useFormValidation(rules)

      expect(validate({ score: 101 })).toBe(false)
      expect(errors.value.score).toBe('score must be at most 100')

      expect(validate({ score: 100 })).toBe(true)
      expect(errors.value).toEqual({})
    })

    it('validates min and max together', () => {
      const rules = { value: { min: 0, max: 10 } }
      const { validate } = useFormValidation(rules)

      expect(validate({ value: -1 })).toBe(false)
      expect(validate({ value: 11 })).toBe(false)
      expect(validate({ value: 5 })).toBe(true)
    })
  })

  describe('pattern validation', () => {
    it('validates string patterns', () => {
      const rules = {
        email: { pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/ },
      }
      const { validate, errors } = useFormValidation(rules)

      expect(validate({ email: 'invalid-email' })).toBe(false)
      expect(errors.value.email).toBe('email format is invalid')

      expect(validate({ email: 'valid@email.com' })).toBe(true)
      expect(errors.value).toEqual({})
    })
  })

  describe('custom validation', () => {
    it('validates with custom function returning boolean', () => {
      const rules = {
        password: {
          custom: (value: string) => value && value.length >= 8,
        },
      }
      const { validate, errors } = useFormValidation(rules)

      expect(validate({ password: 'short' })).toBe(false)
      expect(errors.value.password).toBe('password is invalid')

      expect(validate({ password: 'longenough' })).toBe(true)
      expect(errors.value).toEqual({})
    })

    it('validates with custom function returning error message', () => {
      const rules = {
        confirmPassword: {
          custom: (value: string) => value === 'password123' || 'Passwords do not match',
        },
      }
      const { validate, errors } = useFormValidation(rules)

      expect(validate({ confirmPassword: 'different' })).toBe(false)
      expect(errors.value.confirmPassword).toBe('Passwords do not match')

      expect(validate({ confirmPassword: 'password123' })).toBe(true)
      expect(errors.value).toEqual({})
    })
  })

  describe('single field validation', () => {
    it('validates individual fields', () => {
      const rules = {
        name: { required: true },
        age: { min: 18 },
      }
      const { validateSingleField, errors } = useFormValidation(rules)

      validateSingleField('name', '')
      expect(errors.value).toEqual({ name: 'name is required' })

      validateSingleField('name', 'John')
      expect(errors.value).toEqual({})

      validateSingleField('age', 17)
      expect(errors.value).toEqual({ age: 'age must be at least 18' })
    })
  })

  describe('touch and error display', () => {
    it('tracks touched fields', () => {
      const rules = { name: { required: true } }
      const { touch, touched, getError } = useFormValidation(rules)

      expect(touched.value.name).toBeUndefined()
      expect(getError('name')).toBeUndefined()

      touch('name')
      expect(touched.value.name).toBe(true)
    })

    it('only shows errors for touched fields', () => {
      const rules = { name: { required: true } }
      const { validateSingleField, touch, getError } = useFormValidation(rules)

      validateSingleField('name', '')
      expect(getError('name')).toBeUndefined()

      touch('name')
      expect(getError('name')).toBe('name is required')
    })
  })

  describe('reset functionality', () => {
    it('clears errors and touched state', () => {
      const rules = { name: { required: true } }
      const { validate, touch, reset, errors, touched } = useFormValidation(rules)

      validate({ name: '' })
      touch('name')
      expect(errors.value).toEqual({ name: 'name is required' })
      expect(touched.value).toEqual({ name: true })

      reset()
      expect(errors.value).toEqual({})
      expect(touched.value).toEqual({})
    })
  })

  describe('isValid computed', () => {
    it('reflects validation state', () => {
      const rules = { name: { required: true } }
      const { validate, isValid } = useFormValidation(rules)

      validate({ name: '' })
      expect(isValid.value).toBe(false)

      validate({ name: 'John' })
      expect(isValid.value).toBe(true)
    })
  })

  describe('validateField edge cases', () => {
    it('returns null for fields without rules', () => {
      const rules = { name: { required: true } }
      const { validateSingleField, errors } = useFormValidation(rules)

      // Try to validate a field that doesn't have rules defined
      validateSingleField('undefinedField', 'some value')
      expect(errors.value).toEqual({})
    })
  })
})
