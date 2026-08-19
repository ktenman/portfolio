import { ref, computed } from 'vue'
import { enumService } from '../services/api'
import type { EnumsResponse } from '../models/generated/domain-models'
import { setPlatformDisplayNames } from '../utils/platform-utils'
import { formatAcronym } from '../utils/formatters'

interface SelectOption {
  value: string
  text: string
}

const enumCache = ref<EnumsResponse | null>(null)

const loading = ref(false)
const error = ref<Error | null>(null)

const toSelectOptions = (values: string[]): SelectOption[] =>
  values.map(value => ({
    value,
    text: formatAcronym(value),
  }))

export function useEnumValues() {
  const loadAll = async () => {
    if (enumCache.value) return
    loading.value = true
    try {
      enumCache.value = await enumService.getAll()
      setPlatformDisplayNames(enumCache.value.platforms)
    } catch (e) {
      error.value = e as Error
    } finally {
      loading.value = false
    }
  }

  const providerOptions = computed(() =>
    enumCache.value ? toSelectOptions(enumCache.value.providers) : []
  )

  const categoryOptions = computed(() =>
    enumCache.value ? toSelectOptions(enumCache.value.categories) : []
  )

  return {
    loading,
    error,
    providerOptions,
    categoryOptions,
    loadAll,
  }
}
