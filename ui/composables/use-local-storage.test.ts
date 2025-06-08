import { describe, it, expect, beforeEach, vi } from 'vitest'
import { nextTick } from 'vue'
import { useLocalStorage } from './use-local-storage'

// Mock localStorage
const localStorageMock = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
}

Object.defineProperty(window, 'localStorage', {
  value: localStorageMock,
})

// Mock console.error to avoid noise in tests
const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

describe('useLocalStorage', () => {
  const defaultForm = {
    name: '',
    email: '',
    age: 0,
  }
  const storageKey = 'test-form'

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('initialization', () => {
    it('initializes with default form values', () => {
      const { form, formChanges, isUpdatingForm } = useLocalStorage(storageKey, defaultForm)

      expect(form.name).toBe('')
      expect(form.email).toBe('')
      expect(form.age).toBe(0)
      expect(formChanges.value).toEqual({})
      expect(isUpdatingForm.value).toBe(false)
    })
  })

  describe('loadFromLocalStorage', () => {
    it('loads saved form data from localStorage', () => {
      const savedData = { name: 'John', email: 'john@example.com', age: 30 }
      localStorageMock.getItem.mockReturnValue(JSON.stringify(savedData))

      const { form, formChanges, loadFromLocalStorage } = useLocalStorage(storageKey, defaultForm)
      loadFromLocalStorage()

      expect(localStorageMock.getItem).toHaveBeenCalledWith(storageKey)
      expect(form.name).toBe('John')
      expect(form.email).toBe('john@example.com')
      expect(form.age).toBe(30)
      expect(formChanges.value).toEqual({ name: true, email: true, age: true })
    })

    it('handles missing localStorage data', () => {
      localStorageMock.getItem.mockReturnValue(null)

      const { form, formChanges, loadFromLocalStorage } = useLocalStorage(storageKey, defaultForm)
      loadFromLocalStorage()

      expect(form.name).toBe('')
      expect(form.email).toBe('')
      expect(form.age).toBe(0)
      expect(formChanges.value).toEqual({})
    })

    it('handles invalid JSON in localStorage', () => {
      localStorageMock.getItem.mockReturnValue('invalid json')

      const { form, loadFromLocalStorage } = useLocalStorage(storageKey, defaultForm)
      loadFromLocalStorage()

      expect(consoleSpy).toHaveBeenCalledWith(
        'Error loading form data from localStorage:',
        expect.any(SyntaxError)
      )
      expect(form.name).toBe('')
      expect(form.email).toBe('')
      expect(form.age).toBe(0)
    })

    it('handles localStorage access error', () => {
      localStorageMock.getItem.mockImplementation(() => {
        throw new Error('Storage access denied')
      })

      const { form, loadFromLocalStorage } = useLocalStorage(storageKey, defaultForm)
      loadFromLocalStorage()

      expect(consoleSpy).toHaveBeenCalledWith(
        'Error loading form data from localStorage:',
        expect.any(Error)
      )
      expect(form.name).toBe('')
    })
  })

  describe('saveToLocalStorage', () => {
    it('saves form data to localStorage', () => {
      const { form, saveToLocalStorage } = useLocalStorage(storageKey, defaultForm)

      form.name = 'John'
      form.email = 'john@example.com'
      saveToLocalStorage()

      expect(localStorageMock.setItem).toHaveBeenCalledWith(
        storageKey,
        JSON.stringify({ name: 'John', email: 'john@example.com', age: 0 })
      )
    })

    it('handles localStorage save error', () => {
      localStorageMock.setItem.mockImplementation(() => {
        throw new Error('Storage quota exceeded')
      })

      const { saveToLocalStorage } = useLocalStorage(storageKey, defaultForm)
      saveToLocalStorage()

      expect(consoleSpy).toHaveBeenCalledWith(
        'Error saving form data to localStorage:',
        expect.any(Error)
      )
    })
  })

  describe('handleInput', () => {
    it('marks field as changed and saves to localStorage', () => {
      const { form, formChanges, handleInput } = useLocalStorage(storageKey, defaultForm)

      form.name = 'John'
      handleInput('name')

      expect(formChanges.value.name).toBe(true)
      expect(localStorageMock.setItem).toHaveBeenCalledWith(
        storageKey,
        JSON.stringify({ name: 'John', email: '', age: 0 })
      )
    })

    it('handles multiple field inputs', () => {
      const { form, formChanges, handleInput } = useLocalStorage(storageKey, defaultForm)

      form.name = 'John'
      handleInput('name')

      form.email = 'john@example.com'
      handleInput('email')

      expect(formChanges.value.name).toBe(true)
      expect(formChanges.value.email).toBe(true)
      expect(localStorageMock.setItem).toHaveBeenCalledTimes(2)
    })
  })

  describe('resetForm', () => {
    it('resets form to default values and clears storage', () => {
      const { form, formChanges, handleInput, resetForm } = useLocalStorage(storageKey, defaultForm)

      // Modify form first
      form.name = 'John'
      form.email = 'john@example.com'
      handleInput('name')
      handleInput('email')

      // Reset
      resetForm()

      expect(form.name).toBe('')
      expect(form.email).toBe('')
      expect(form.age).toBe(0)
      expect(formChanges.value).toEqual({})
      expect(localStorageMock.removeItem).toHaveBeenCalledWith(storageKey)
    })
  })

  describe('updateFormField', () => {
    it('updates field value when not changed by user', async () => {
      const { form, updateFormField, isUpdatingForm } = useLocalStorage(storageKey, defaultForm)

      await updateFormField('name', 'Auto-filled Name')

      expect(form.name).toBe('Auto-filled Name')
      expect(localStorageMock.setItem).toHaveBeenCalledWith(
        storageKey,
        JSON.stringify({ name: 'Auto-filled Name', email: '', age: 0 })
      )
      expect(isUpdatingForm.value).toBe(false)
    })

    it('does not update field when already changed by user', async () => {
      const { form, handleInput, updateFormField } = useLocalStorage(storageKey, defaultForm)

      // User changes the field first
      form.name = 'User Input'
      handleInput('name')

      // Clear mock to track only updateFormField call
      vi.clearAllMocks()

      // Try to update field programmatically
      await updateFormField('name', 'Auto-filled Name')

      expect(form.name).toBe('User Input') // Should not change
      expect(localStorageMock.setItem).not.toHaveBeenCalled()
    })

    it('sets and clears isUpdatingForm flag', async () => {
      const { updateFormField, isUpdatingForm } = useLocalStorage(storageKey, defaultForm)

      const updatePromise = updateFormField('name', 'Test')

      expect(isUpdatingForm.value).toBe(true)

      await updatePromise
      await nextTick()

      expect(isUpdatingForm.value).toBe(false)
    })

    it('clears isUpdatingForm flag even if error occurs', async () => {
      localStorageMock.setItem.mockImplementation(() => {
        throw new Error('Storage error')
      })

      const { updateFormField, isUpdatingForm } = useLocalStorage(storageKey, defaultForm)

      await updateFormField('name', 'Test')
      await nextTick()

      expect(isUpdatingForm.value).toBe(false)
    })

    it('updates numeric field correctly', async () => {
      const { form, updateFormField } = useLocalStorage(storageKey, defaultForm)

      await updateFormField('age', 25)

      expect(form.age).toBe(25)
      expect(localStorageMock.setItem).toHaveBeenCalledWith(
        storageKey,
        JSON.stringify({ name: '', email: '', age: 25 })
      )
    })
  })

  describe('complex scenarios', () => {
    it('handles load, modify, and reset flow', () => {
      // Setup localStorage with existing data
      const savedData = { name: 'Saved Name', email: 'saved@example.com', age: 25 }
      localStorageMock.getItem.mockReturnValue(JSON.stringify(savedData))

      const { form, formChanges, loadFromLocalStorage, handleInput, resetForm } = useLocalStorage(
        storageKey,
        defaultForm
      )

      // Load existing data
      loadFromLocalStorage()
      expect(form.name).toBe('Saved Name')
      expect(formChanges.value.name).toBe(true)

      // User modifies field
      form.email = 'new@example.com'
      handleInput('email')
      expect(formChanges.value.email).toBe(true)

      // Reset everything
      resetForm()
      expect(form.name).toBe('')
      expect(form.email).toBe('')
      expect(formChanges.value).toEqual({})
    })

    it('handles mixed user input and programmatic updates', async () => {
      const { form, formChanges, handleInput, updateFormField } = useLocalStorage(
        storageKey,
        defaultForm
      )

      // User input
      form.name = 'User Name'
      handleInput('name')

      // Programmatic update of different field
      await updateFormField('email', 'auto@example.com')

      // Try programmatic update of user-modified field (should be ignored)
      await updateFormField('name', 'Auto Name')

      expect(form.name).toBe('User Name') // User input preserved
      expect(form.email).toBe('auto@example.com') // Programmatic update applied
      expect(formChanges.value.name).toBe(true)
      expect(formChanges.value.email).toBe(undefined) // Not marked as user-changed
    })
  })
})
