import { ref, watch, Ref, computed } from 'vue'

interface UseFormOptions<T> {
  initialData: Ref<Partial<T>>
  validate?: (data: Partial<T>) => boolean | Record<string, string>
  onSubmit: (data: Partial<T>) => void
}

interface UseFormReturn<T> {
  formData: Ref<Partial<T>>
  errors: Ref<Record<string, string>>
  isValid: Ref<boolean>
  handleSubmit: () => void
  resetForm: () => void
  setFieldValue: (field: keyof T, value: any) => void
}

export function useForm<T extends Record<string, any>>({
  initialData,
  validate,
  onSubmit,
}: UseFormOptions<T>): UseFormReturn<T> {
  const formData = ref<Partial<T>>({ ...initialData.value }) as Ref<Partial<T>>
  const errors = ref<Record<string, string>>({})

  watch(
    initialData,
    newData => {
      formData.value = { ...newData }
      errors.value = {}
    },
    { deep: true }
  )

  const isValid = computed(() => {
    if (!validate) return true
    const result = validate(formData.value)
    return typeof result === 'boolean' ? result : Object.keys(result).length === 0
  })

  const handleSubmit = () => {
    if (!validate) {
      onSubmit(formData.value)
      return
    }

    const validationResult = validate(formData.value)

    if (typeof validationResult === 'boolean') {
      if (validationResult) {
        errors.value = {}
        onSubmit(formData.value)
      }
    } else {
      errors.value = validationResult
      if (Object.keys(validationResult).length === 0) {
        onSubmit(formData.value)
      }
    }
  }

  const resetForm = () => {
    formData.value = { ...initialData.value }
    errors.value = {}
  }

  const setFieldValue = (field: keyof T, value: any) => {
    formData.value = {
      ...formData.value,
      [field]: value,
    }
    if (errors.value[field as string]) {
      const newErrors = { ...errors.value }
      delete newErrors[field as string]
      errors.value = newErrors
    }
  }

  return {
    formData,
    errors,
    isValid,
    handleSubmit,
    resetForm,
    setFieldValue,
  }
}
