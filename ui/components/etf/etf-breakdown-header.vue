<template>
  <p class="page-subtitle text-body-secondary">
    {{ getDescription() }}
    <a
      v-if="reportDate"
      class="report-link"
      :href="fundReportService.getReportUrl(TULEVA_SYMBOL, reportDate)"
      target="_blank"
      rel="noopener"
    >
      Tuleva report {{ formatReportDate(reportDate) }}
    </a>
  </p>
</template>

<script lang="ts" setup>
import { fundReportService } from '../../services/api'
import { formatReportDate, TULEVA_SYMBOL } from '../../services/fund-allocation'

const props = defineProps<{
  selectedEtfs: string[]
  availableEtfs: string[]
  reportDate?: string
}>()

const getSymbolOnly = (fullSymbol: string): string => {
  return fullSymbol.split(':')[0]
}

const numberToWord = (num: number): string => {
  const words = [
    'zero',
    'one',
    'two',
    'three',
    'four',
    'five',
    'six',
    'seven',
    'eight',
    'nine',
    'ten',
  ]
  return num <= 10 ? words[num] : num.toString()
}

const getDescription = (): string => {
  if (props.selectedEtfs.length === 0 || props.selectedEtfs.length === props.availableEtfs.length) {
    const count = props.availableEtfs.length
    const countWord = numberToWord(count)
    return `Aggregated view of underlying holdings across all ${countWord} ${count === 1 ? 'ETF position' : 'ETF positions'}`
  }

  const symbols = props.selectedEtfs.map(getSymbolOnly).sort()
  const listFormatter = new Intl.ListFormat('en', { style: 'long', type: 'conjunction' })

  if (symbols.length <= 3) {
    const etfList = listFormatter.format(symbols)
    return `Aggregated view of underlying holdings from ${etfList}`
  }

  const countWord = numberToWord(symbols.length)
  return `Aggregated view of underlying holdings from ${countWord} selected ETF positions`
}
</script>

<style scoped>
.page-subtitle {
  font-size: 0.95rem;
  margin: 0;
}

.report-link {
  margin-left: 0.5rem;
  white-space: nowrap;
  color: var(--color-brass-deep);
}
</style>
