import { ref, reactive, toRaw } from 'vue'

interface FormState {
  [key: string]: any
}

export const useLocalStorage = <T extends FormState>(storageKey: string, defaultForm: T) => {
  const form = reactive({ ...defaultForm })
  const isUpdatingForm = ref(false)

  const loadFromLocalStorage = (): void => {
    try {
      const savedForm = localStorage.getItem(storageKey)
      if (savedForm) {
        Object.assign(form, JSON.parse(savedForm))
      }
    } catch (error) {
      console.error('Error loading form data from localStorage:', error)
    }
  }

  const saveToLocalStorage = (): void => {
    try {
      localStorage.setItem(storageKey, JSON.stringify(toRaw(form)))
    } catch (error) {
      console.error('Error saving form data to localStorage:', error)
    }
  }

  const handleInput = (_field: string): void => {
    saveToLocalStorage()
  }

  const resetForm = (): void => {
    Object.keys(form).forEach(key => {
      ;(form as any)[key] = (defaultForm as any)[key]
    })
    localStorage.removeItem(storageKey)
  }

  const updateFormField = async (field: keyof T, value: any): Promise<void> => {
    ;(form as any)[field] = value
    saveToLocalStorage()
  }

  return {
    form,
    isUpdatingForm,
    loadFromLocalStorage,
    saveToLocalStorage,
    handleInput,
    resetForm,
    updateFormField,
  }
}
