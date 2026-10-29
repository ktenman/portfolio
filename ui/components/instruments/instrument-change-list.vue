<template>
  <section>
    <h2 class="m-0 text-[1.125rem] font-semibold text-ink">{{ period }} change in euros</h2>
    <p class="mt-0.5 mb-2 text-label text-ink-soft">Largest gain first</p>
    <ol class="m-0 list-none p-0">
      <li
        v-for="row in rows"
        :key="row.instrument.symbol"
        class="grid grid-cols-[auto_minmax(0,1fr)_6rem] items-center gap-x-2 rounded-control px-1 py-1 md:h-7.5 md:grid-cols-[auto_5.5rem_minmax(0,1fr)_6rem] md:py-0"
        :class="{ 'cursor-pointer hover:bg-surface-hover': interactive }"
        @pointerenter="preview(row.instrument.id)"
        @pointerleave="preview(null)"
        @click="select(row.instrument.id)"
      >
        <instrument-logo
          class="max-md:row-span-2"
          :logo-uuid="row.instrument.logoUuid"
          :symbol="row.ticker"
          :size="wide ? 20 : 24"
        />
        <span class="truncate text-label font-semibold text-ink">{{ row.ticker }}</span>
        <span aria-hidden="true" class="relative h-2 max-md:col-start-2 max-md:row-start-2">
          <span class="absolute inset-y-[-3px] left-1/2 w-px bg-hairline-strong" />
          <span
            data-bar
            class="absolute inset-y-0"
            :class="
              row.gain
                ? 'left-1/2 rounded-r-control bg-gain'
                : 'right-1/2 rounded-l-control bg-loss'
            "
            :style="{ width: `${row.share * 50}%` }"
          />
        </span>
        <span
          class="text-right text-label text-ink tabular-nums max-md:col-start-3 max-md:row-span-2 max-md:row-start-1"
        >
          {{ row.amount }}
        </span>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import type { InstrumentDto, TimeRange } from '../../models/generated/domain-models'
import { barShare, changeOrder, tickerOf } from '../../services/instrument-heatmap'
import { formatCurrencyChange } from '../../utils/formatters'
import InstrumentLogo from './instrument-logo.vue'

const props = defineProps<{
  positions: InstrumentDto[]
  period: TimeRange
  interactive: boolean
}>()

const emit = defineEmits<{
  preview: [id: number | null]
  select: [id: number]
}>()

const wide = useMediaQuery('(min-width: 768px)')

const rows = computed(() => {
  const largest = Math.max(
    0,
    ...props.positions.map(({ priceChangeAmount }) => Math.abs(priceChangeAmount ?? 0))
  )
  return changeOrder(props.positions).map(instrument => {
    const amount = instrument.priceChangeAmount
    return {
      instrument,
      ticker: tickerOf(instrument.symbol),
      gain: (amount ?? 0) >= 0,
      share: barShare(amount, largest),
      amount: amount == null ? '—' : formatCurrencyChange(amount),
    }
  })
})

const preview = (id: number | null) => {
  if (props.interactive) emit('preview', id)
}

const select = (id: number | null) => {
  if (props.interactive && id != null) emit('select', id)
}
</script>
