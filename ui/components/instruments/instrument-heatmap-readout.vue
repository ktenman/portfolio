<template>
  <div data-readout class="flex size-full items-center px-3 max-[374px]:px-2.5">
    <p v-if="!instrument" class="m-0 text-label text-ink-soft max-md:hidden">
      Select a box to see its figures.
    </p>
    <div
      class="flex min-w-0 flex-1 items-center gap-4 max-md:flex-col max-md:items-stretch max-md:gap-2"
      :class="{ 'md:hidden': !instrument }"
    >
      <div class="flex min-w-0 flex-1 items-center gap-3">
        <button
          v-if="instrument"
          type="button"
          class="group relative flex shrink-0 cursor-pointer rounded-full"
          title="Click to replace logo"
          @click="emit('replaceLogo', instrument)"
        >
          <instrument-logo
            :logo-uuid="instrument.logoUuid"
            :version="instrument.logoVersion"
            :symbol="tickerOf(instrument.symbol)"
            :size="40"
          />
          <logo-edit-hint />
        </button>
        <div class="min-w-0">
          <p class="m-0 truncate text-ink">
            <template v-if="instrument">
              <span class="font-semibold">{{ tickerOf(instrument.symbol) }}</span>
              {{ instrument.name }}
            </template>
            <span v-else class="font-semibold">All positions</span>
          </p>
          <p class="m-0 truncate text-label text-ink-soft">
            {{ instrument ? platforms(instrument) : 'Tap a box to see its figures' }}
          </p>
        </div>
      </div>
      <dl class="m-0 flex shrink-0 justify-between gap-x-2.5 md:gap-x-8">
        <div v-for="figure in figures" :key="figure.label" class="min-w-0">
          <dt class="text-2xs font-medium tracking-wider text-ink-soft uppercase">
            {{ figure.label }}
          </dt>
          <dd
            class="m-0 truncate font-semibold text-ink tabular-nums max-md:text-2xs max-[374px]:text-label"
          >
            {{ figure.value }}
          </dd>
        </div>
      </dl>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { InstrumentDto } from '../../models/generated/domain-models'
import { tickerOf } from '../../services/instrument-heatmap'
import { formatCurrencyWithSymbol, formatPercentageFromDecimal } from '../../utils/formatters'
import { calculatePortfolioWeight } from '../../utils/instrument-formatters'
import { formatPlatformName } from '../../utils/platform-utils'
import InstrumentLogo from './instrument-logo.vue'
import LogoEditHint from '../shared/logo-edit-hint.vue'

type Figure = { label: string; value: string }

const props = defineProps<{
  instrument: InstrumentDto | null
  total: number
  totals: Figure[]
}>()

const emit = defineEmits<{
  replaceLogo: [instrument: InstrumentDto]
}>()

const platforms = (instrument: InstrumentDto): string =>
  (instrument.platforms ?? []).map(formatPlatformName).join(', ')

const figures = computed<Figure[]>(() => {
  const instrument = props.instrument
  if (!instrument) return props.totals
  return [
    { label: 'Value', value: formatCurrencyWithSymbol(instrument.currentValue) },
    { label: 'Weight', value: calculatePortfolioWeight(instrument.currentValue ?? 0, props.total) },
    {
      label: 'XIRR',
      value: instrument.xirr == null ? '—' : formatPercentageFromDecimal(instrument.xirr),
    },
  ]
})
</script>
