import { computed } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { PriceChangePeriod } from '../models/generated/domain-models'

const STORAGE_KEY = 'portfolio_price_change_period'
const DEFAULT_PERIOD = PriceChangePeriod.P24H

interface PeriodOption {
  readonly value: PriceChangePeriod
  readonly label: string
}

const periodOptions: ReadonlyArray<PeriodOption> = Object.values(PriceChangePeriod).map(value => ({
  value: value as PriceChangePeriod,
  label: value.substring(1),
}))

function isPeriodValid(period: string): boolean {
  return Object.values(PriceChangePeriod).includes(period as PriceChangePeriod)
}

export function usePriceChangePeriod() {
  const storedPeriod = useLocalStorage<string>(STORAGE_KEY, DEFAULT_PERIOD)

  const selectedPeriod = computed({
    get: () =>
      isPeriodValid(storedPeriod.value)
        ? (storedPeriod.value as PriceChangePeriod)
        : DEFAULT_PERIOD,
    set: (value: PriceChangePeriod) => {
      storedPeriod.value = value
    },
  })

  return {
    selectedPeriod,
    periods: periodOptions,
  }
}
