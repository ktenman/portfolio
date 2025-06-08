import { ref, reactive, toRaw, nextTick } from 'vue'

export interface FormState {
  [key: string]: any
}

export const useLocalStorage = <T extends FormState>(storageKey: string, defaultForm: T) => {
  const form = reactive({ ...defaultForm })
  const isUpdatingForm = ref(false)
  const formChanges = ref<Record<string, boolean>>({})

  const loadFromLocalStorage = (): void => {
    try {
      const savedForm = localStorage.getItem(storageKey)
      if (savedForm) {
        const parsedForm = JSON.parse(savedForm)

        Object.assign(form, parsedForm)
        Object.keys(parsedForm).forEach(key => {
          formChanges.value[key] = true
        })
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

  const handleInput = (field: string): void => {
    formChanges.value[field] = true
    saveToLocalStorage()
  }

  const resetForm = (): void => {
    Object.keys(form).forEach(key => {
      ;(form as any)[key] = (defaultForm as any)[key]
    })
    formChanges.value = {}
    localStorage.removeItem(storageKey)
  }

  const updateFormField = async (field: keyof T, value: any): Promise<void> => {
    if (formChanges.value[field as string]) return

    try {
      isUpdatingForm.value = true
      ;(form as any)[field] = value
      saveToLocalStorage()
    } finally {
      await nextTick(() => {
        isUpdatingForm.value = false
      })
    }
  }

  return {
    form,
    isUpdatingForm,
    formChanges,
    loadFromLocalStorage,
    saveToLocalStorage,
    handleInput,
    resetForm,
    updateFormField,
  }
}
