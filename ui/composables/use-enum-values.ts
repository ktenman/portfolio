import { ref, computed } from 'vue'
import { enumService } from '../services/enum-service'

interface SelectOption {
  value: string
  text: string
}

const enumCache = ref<{
  platforms: string[]
  providers: string[]
  transactionTypes: string[]
  categories: string[]
  currencies: string[]
} | null>(null)

const loading = ref(false)
const error = ref<Error | null>(null)

const ACRONYMS = [
  'ETF',
  'FT',
  'API',
  'USD',
  'EUR',
  'GBP',
  'JPY',
  'CHF',
  'CAD',
  'AUD',
  'CEO',
  'CFO',
  'IT',
  'AI',
  'IBKR',
]

const formatEnumText = (value: string): string => {
  if (ACRONYMS.includes(value)) {
    return value
  }

  const withSpaces = value.replace(/_/g, ' ')
  return withSpaces
    .split(' ')
    .map(word => {
      if (ACRONYMS.includes(word)) return word
      return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    })
    .join(' ')
}

const toSelectOptions = (values: string[]): SelectOption[] =>
  values.map(value => ({
    value,
    text: formatEnumText(value),
  }))

export function useEnumValues() {
  const loadAll = async () => {
    if (enumCache.value) return
    loading.value = true
    try {
      enumCache.value = await enumService.getAll()
    } catch (e) {
      error.value = e as Error
    } finally {
      loading.value = false
    }
  }

  const platformOptions = computed(() =>
    enumCache.value ? toSelectOptions(enumCache.value.platforms) : []
  )

  const providerOptions = computed(() =>
    enumCache.value ? toSelectOptions(enumCache.value.providers) : []
  )

  const transactionTypeOptions = computed(() => {
    if (!enumCache.value) return []
    return enumCache.value.transactionTypes.map(value => ({
      value,
      text: value === 'BUY' ? 'Buy' : 'Sell',
    }))
  })

  const categoryOptions = computed(() =>
    enumCache.value ? toSelectOptions(enumCache.value.categories) : []
  )

  const currencyOptions = computed(() => {
    if (enumCache.value && enumCache.value.currencies.length > 0) {
      return toSelectOptions(enumCache.value.currencies)
    }

    return [
      { value: 'EUR', text: 'EUR' },
      { value: 'USD', text: 'USD' },
      { value: 'GBP', text: 'GBP' },
      { value: 'JPY', text: 'JPY' },
      { value: 'CHF', text: 'CHF' },
      { value: 'CAD', text: 'CAD' },
      { value: 'AUD', text: 'AUD' },
    ]
  })

  return {
    loading,
    error,
    platformOptions,
    providerOptions,
    transactionTypeOptions,
    categoryOptions,
    currencyOptions,
    loadAll,
  }
}
