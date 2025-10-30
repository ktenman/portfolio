import { ref, watch } from 'vue'
import { PriceChangePeriod } from '../models/generated/domain-models'

const STORAGE_KEY = 'portfolio_price_change_period'
const DEFAULT_PERIOD = PriceChangePeriod.P24H

interface PeriodOption {
  readonly value: PriceChangePeriod
  readonly label: string
}

const periodOptions: ReadonlyArray<PeriodOption> = [
  { value: PriceChangePeriod.P24H, label: '24H' },
  { value: PriceChangePeriod.P48H, label: '48H' },
  { value: PriceChangePeriod.P3D, label: '3D' },
  { value: PriceChangePeriod.P7D, label: '7D' },
  { value: PriceChangePeriod.P30D, label: '30D' },
  { value: PriceChangePeriod.P1Y, label: '1Y' },
] as const

export function usePriceChangePeriod() {
  const storedPeriod = localStorage.getItem(STORAGE_KEY)
  const selectedPeriod = ref<PriceChangePeriod>(
    storedPeriod && isPeriodValid(storedPeriod)
      ? (storedPeriod as PriceChangePeriod)
      : DEFAULT_PERIOD
  )

  watch(selectedPeriod, newPeriod => {
    localStorage.setItem(STORAGE_KEY, newPeriod)
  })

  return {
    selectedPeriod,
    periods: periodOptions,
  }
}

function isPeriodValid(period: string): boolean {
  return Object.values(PriceChangePeriod).includes(period as PriceChangePeriod)
}
