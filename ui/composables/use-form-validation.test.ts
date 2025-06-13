import { describe, it, expect } from 'vitest'
import { z } from 'zod'
import { useFormValidation } from './use-form-validation'

describe('useFormValidation', () => {
  const testSchema = z.object({
    name: z.string().min(1, 'Name is required').max(50, 'Name too long'),
    email: z.string().email('Invalid email format'),
    age: z.number().min(18, 'Must be 18 or older').max(100, 'Must be 100 or younger'),
  })

  describe('initialization', () => {
    it('should initialize with empty form data when no initial data provided', () => {
      const { formData } = useFormValidation(testSchema)

      expect(formData).toEqual({})
    })

    it('should initialize with provided initial data', () => {
      const initialData = { name: 'John Doe', email: 'john@example.com' }
      const { formData } = useFormValidation(testSchema, initialData)

      expect(formData.name).toBe('John Doe')
      expect(formData.email).toBe('john@example.com')
    })

    it('should have no errors initially', () => {
      const { errors } = useFormValidation(testSchema)

      expect(errors.value).toEqual({})
    })

    it('should be invalid initially when required fields are missing', () => {
      const { isValid } = useFormValidation(testSchema)

      expect(isValid.value).toBe(false)
    })
  })

  describe('field updates', () => {
    it('should update field value', () => {
      const { formData, updateField } = useFormValidation(testSchema)

      updateField('name', 'Jane Doe')

      expect(formData.name).toBe('Jane Doe')
    })

    it('should validate field on update', () => {
      const { errors, updateField } = useFormValidation(testSchema)

      updateField('email', 'invalid-email')

      expect(errors.value.email).toBe('Invalid email format')
    })

    it('should clear error when field becomes valid', () => {
      const { errors, updateField } = useFormValidation(testSchema)

      updateField('email', 'invalid-email')
      expect(errors.value.email).toBe('Invalid email format')

      updateField('email', 'valid@email.com')
      expect(errors.value.email).toBeUndefined()
    })

    it('should mark field as touched on update', () => {
      const { getFieldError, updateField } = useFormValidation(testSchema)

      expect(getFieldError('name')).toBeUndefined()

      updateField('name', '')

      expect(getFieldError('name')).toBe('Name is required')
    })
  })

  describe('touch field', () => {
    it('should mark field as touched without changing value', () => {
      const { formData, getFieldError, touchField } = useFormValidation(testSchema)

      expect(getFieldError('name')).toBeUndefined()

      touchField('name')

      expect(formData.name).toBeUndefined()
      expect(getFieldError('name')).toBe('Name is required')
    })

    it('should validate existing value when touched', () => {
      const { getFieldError, touchField } = useFormValidation(testSchema, {
        email: 'invalid-email',
      })

      expect(getFieldError('email')).toBeUndefined()

      touchField('email')

      expect(getFieldError('email')).toBe('Invalid email format')
    })
  })

  describe('form validation', () => {
    it('should validate entire form and return false when invalid', () => {
      const { validateForm, errors } = useFormValidation(testSchema, {
        name: '',
        email: 'invalid-email',
      })

      const isValid = validateForm()

      expect(isValid).toBe(false)
      expect(errors.value.name).toBe('Name is required')
      expect(errors.value.email).toBe('Invalid email format')
      expect(errors.value.age).toBe('Required')
    })

    it('should validate entire form and return true when valid', () => {
      const { validateForm, errors } = useFormValidation(testSchema, {
        name: 'John Doe',
        email: 'john@example.com',
        age: 25,
      })

      const isValid = validateForm()

      expect(isValid).toBe(true)
      expect(errors.value).toEqual({})
    })

    it('should update isValid computed property', () => {
      const { isValid, updateField } = useFormValidation(testSchema)

      expect(isValid.value).toBe(false)

      updateField('name', 'John Doe')
      updateField('email', 'john@example.com')
      updateField('age', 25)

      expect(isValid.value).toBe(true)
    })
  })

  describe('error display', () => {
    it('should not show errors for untouched fields', () => {
      const { getFieldError } = useFormValidation(testSchema)

      expect(getFieldError('name')).toBeUndefined()
      expect(getFieldError('email')).toBeUndefined()
    })

    it('should show errors only for touched fields', () => {
      const { getFieldError, touchField, updateField } = useFormValidation(testSchema)

      touchField('name')
      updateField('email', 'invalid')

      expect(getFieldError('name')).toBe('Name is required')
      expect(getFieldError('email')).toBe('Invalid email format')
      expect(getFieldError('age')).toBeUndefined()
    })
  })

  describe('reset form', () => {
    it('should clear all data and errors', () => {
      const { formData, errors, resetForm, updateField } = useFormValidation(testSchema)

      updateField('name', 'John')
      updateField('email', 'invalid')

      expect(formData.name).toBe('John')
      expect(errors.value.email).toBe('Invalid email format')

      resetForm()

      expect(formData).toEqual({})
      expect(errors.value).toEqual({})
    })

    it('should reset with new data', () => {
      const { formData, errors, resetForm, updateField } = useFormValidation(testSchema)

      updateField('name', 'John')
      updateField('email', 'invalid')

      resetForm({ name: 'Jane', email: 'jane@example.com', age: 30 })

      expect(formData.name).toBe('Jane')
      expect(formData.email).toBe('jane@example.com')
      expect(formData.age).toBe(30)
      expect(errors.value).toEqual({})
    })

    it('should clear touched fields on reset', () => {
      const { getFieldError, touchField, resetForm } = useFormValidation(testSchema)

      touchField('name')
      expect(getFieldError('name')).toBe('Name is required')

      resetForm()

      expect(getFieldError('name')).toBeUndefined()
    })
  })

  describe('reactive updates', () => {
    it('should update form data when initial data changes', async () => {
      const { formData } = useFormValidation(testSchema, { name: 'Initial' })

      expect(formData.name).toBe('Initial')
    })
  })

  describe('complex validation scenarios', () => {
    it('should handle nested validation errors', () => {
      const complexSchema = z.object({
        user: z.object({
          name: z.string().min(1, 'Name required'),
          email: z.string().email('Invalid email'),
        }),
      })

      const { validateForm, errors } = useFormValidation(complexSchema, {
        user: { name: '', email: 'bad' },
      })

      validateForm()

      expect(errors.value['user.name']).toBe('Name required')
      expect(errors.value['user.email']).toBe('Invalid email')
    })

    it('should handle array validation', () => {
      const arraySchema = z.object({
        tags: z.array(z.string().min(1, 'Tag cannot be empty')).min(1, 'At least one tag required'),
      })

      const { validateForm, errors } = useFormValidation(arraySchema, {
        tags: [],
      })

      validateForm()

      expect(errors.value.tags).toBe('At least one tag required')
    })
  })

  describe('edge cases', () => {
    it('should handle validation exceptions gracefully', () => {
      const throwingSchema = z.object({
        test: z.custom(() => {
          throw new Error('Validation error')
        }),
      })

      const { validateField, errors } = useFormValidation(throwingSchema)

      validateField('test', 'any value')

      expect(errors.value.test).toBe('Invalid value')
    })

    it('should handle nested field validation with dots', () => {
      const nestedSchema = z.object({
        user: z.object({
          profile: z.object({
            name: z.string().min(1, 'Name required'),
          }),
        }),
      })

      const { validateField, errors } = useFormValidation(nestedSchema, {
        user: { profile: { name: '' } },
      })

      validateField('user.profile.name', '')

      expect(errors.value['user.profile.name']).toBe('Name required')
    })

    it('should handle deep nested field validation', () => {
      const nestedSchema = z.object({
        a: z.object({
          b: z.object({
            c: z.string().min(1, 'Required'),
          }),
        }),
      })

      const { validateField, errors } = useFormValidation(nestedSchema)

      validateField('a.b.c', '')
      expect(errors.value['a.b.c']).toBe('Required')

      validateField('a.b.c', 'test')
      expect(errors.value['a.b.c']).toBeUndefined()
    })

    it('should handle validation when nested path is partially missing', () => {
      const nestedSchema = z.object({
        user: z.object({
          settings: z.object({
            theme: z.string(),
          }),
        }),
      })

      const { validateField, errors } = useFormValidation(nestedSchema, {
        user: { settings: { theme: '' } },
      })

      validateField('user.settings.theme', 'dark')

      expect(errors.value['user.settings.theme']).toBeUndefined()
    })

    it('should watch and update formData when initialData changes', async () => {
      const { nextTick } = await import('vue')
      const { reactive } = await import('vue')

      const initialData = reactive({ name: 'Initial' })
      const { formData } = useFormValidation(testSchema, initialData)

      expect(formData.name).toBe('Initial')

      initialData.name = 'Updated'
      await nextTick()

      expect(formData.name).toBe('Updated')
    })

    it('should handle validateForm when schema throws non-Zod error', () => {
      const customSchema = {
        parse: () => {
          throw new Error('Custom error')
        },
        safeParse: () => ({ success: false, error: new Error('Not a ZodError') }),
      } as any

      const { validateForm, errors } = useFormValidation(customSchema)

      const result = validateForm()

      expect(result).toBe(false)
      expect(errors.value).toEqual({})
    })

    it('should delete field error when validation passes but field had error', () => {
      const { updateField, validateField, errors } = useFormValidation(testSchema)

      updateField('email', 'invalid')
      expect(errors.value.email).toBe('Invalid email format')

      validateField('email', 'valid@email.com')
      expect(errors.value.email).toBeUndefined()
    })

    it('should handle field validation when no specific field error found', () => {
      const schema = z.object({
        field1: z.string(),
        field2: z.string().refine(val => val === 'specific', { message: 'Must be specific' }),
      })

      const { validateField, errors } = useFormValidation(schema)

      validateField('field1', 'test')

      expect(errors.value.field1).toBeUndefined()
    })
  })
})
