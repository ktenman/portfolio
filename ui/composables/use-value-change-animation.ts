import { ref, watch, type Ref } from 'vue'
import type { InstrumentDto } from '../models/generated/domain-models'

type ChangeDirection = 'increase' | 'decrease' | null
type FieldName =
  | 'currentPrice'
  | 'currentValue'
  | 'profit'
  | 'unrealizedProfit'
  | 'priceChangeAmount'
  | 'xirr'
type TotalsFieldName =
  | 'totalValue'
  | 'totalProfit'
  | 'totalUnrealizedProfit'
  | 'totalChangeAmount'
  | 'totalXirr'

interface ChangeState {
  [key: string]: {
    [field in FieldName]?: ChangeDirection
  }
}

type TotalsChangeState = Partial<Record<TotalsFieldName, ChangeDirection>>

interface TotalsValues {
  totalValue: number
  totalProfit: number
  totalUnrealizedProfit: number
  totalChangeAmount: number
  totalXirr: number
}

export function useValueChangeAnimation(instruments: Ref<InstrumentDto[]>) {
  const previousValues = ref<Map<number, Partial<Record<FieldName, number | null>>>>(new Map())
  const changeState = ref<ChangeState>({})
  const previousTotals = ref<Partial<Record<TotalsFieldName, number>>>({})
  const totalsChangeState = ref<TotalsChangeState>({})

  watch(
    instruments,
    newInstruments => {
      if (!newInstruments || newInstruments.length === 0) {
        return
      }

      newInstruments.forEach(instrument => {
        if (!instrument.id) return

        const prev = previousValues.value.get(instrument.id)
        if (!prev) {
          previousValues.value.set(instrument.id, {
            currentPrice: instrument.currentPrice ?? null,
            currentValue: instrument.currentValue ?? null,
            profit: instrument.profit ?? null,
            unrealizedProfit: instrument.unrealizedProfit ?? null,
            priceChangeAmount: instrument.priceChangeAmount ?? null,
            xirr: instrument.xirr ?? null,
          })
          return
        }

        const changes: Partial<Record<FieldName, ChangeDirection>> = {}
        const fields: FieldName[] = [
          'currentPrice',
          'currentValue',
          'profit',
          'unrealizedProfit',
          'priceChangeAmount',
          'xirr',
        ]

        fields.forEach(field => {
          const oldValue = prev[field] ?? 0
          const newValue = instrument[field] ?? 0
          const threshold = field === 'xirr' ? 0.00001 : 0.001

          if (oldValue !== newValue && Math.abs(newValue - oldValue) > threshold) {
            changes[field] = newValue > oldValue ? 'increase' : 'decrease'

            setTimeout(() => {
              const id = instrument.id
              if (id && changeState.value[id]) {
                delete changeState.value[id][field]
                if (Object.keys(changeState.value[id]).length === 0) {
                  delete changeState.value[id]
                }
              }
            }, 3000)
          }
        })

        if (Object.keys(changes).length > 0) {
          changeState.value[instrument.id] = {
            ...changeState.value[instrument.id],
            ...changes,
          }
        }

        previousValues.value.set(instrument.id, {
          currentPrice: instrument.currentPrice ?? null,
          currentValue: instrument.currentValue ?? null,
          profit: instrument.profit ?? null,
          unrealizedProfit: instrument.unrealizedProfit ?? null,
          priceChangeAmount: instrument.priceChangeAmount ?? null,
          xirr: instrument.xirr ?? null,
        })
      })
    },
    { deep: true }
  )

  const getChangeClass = (instrumentId: number | null | undefined, field: FieldName): string => {
    if (!instrumentId || !changeState.value[instrumentId]?.[field]) {
      return ''
    }

    const direction = changeState.value[instrumentId][field]
    return direction === 'increase' ? 'value-increase' : 'value-decrease'
  }

  const trackTotalsChange = (totals: TotalsValues) => {
    const fields: TotalsFieldName[] = [
      'totalValue',
      'totalProfit',
      'totalUnrealizedProfit',
      'totalChangeAmount',
      'totalXirr',
    ]

    const changes: Partial<Record<TotalsFieldName, ChangeDirection>> = {}

    fields.forEach(field => {
      const oldValue = previousTotals.value[field] ?? 0
      const newValue = totals[field] ?? 0
      const threshold = field === 'totalXirr' ? 0.00001 : 0.001

      if (oldValue !== newValue && Math.abs(newValue - oldValue) > threshold) {
        changes[field] = newValue > oldValue ? 'increase' : 'decrease'

        setTimeout(() => {
          delete totalsChangeState.value[field]
        }, 3000)
      }

      previousTotals.value[field] = newValue
    })

    if (Object.keys(changes).length > 0) {
      totalsChangeState.value = {
        ...totalsChangeState.value,
        ...changes,
      }
    }
  }

  const getTotalsChangeClass = (field: TotalsFieldName): string => {
    if (!totalsChangeState.value[field]) {
      return ''
    }

    const direction = totalsChangeState.value[field]
    return direction === 'increase' ? 'value-increase' : 'value-decrease'
  }

  return {
    getChangeClass,
    trackTotalsChange,
    getTotalsChangeClass,
  }
}
