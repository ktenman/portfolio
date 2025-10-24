<template>
  <div class="page-header mb-4">
    <div>
      <p class="page-subtitle text-muted">
        {{ getDescription() }}
      </p>
    </div>
    <div v-if="totalValue > 0" class="header-right">
      <div class="stat-card">
        <div class="stat-label">Total Value</div>
        <div class="stat-value">{{ formatCurrency(totalValue) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Unique Holdings</div>
        <div class="stat-value">{{ uniqueHoldings }}</div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
const props = defineProps<{
  totalValue: number
  uniqueHoldings: number
  selectedEtfs: string[]
  availableEtfs: string[]
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

const formatCurrency = (value: number) => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 2rem;
  flex-wrap: wrap;
}

.page-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
}

.page-subtitle {
  font-size: 0.95rem;
  margin: 0;
}

.header-right {
  display: flex;
  flex-direction: row;
  gap: 0.75rem;
}

.stat-card {
  background: white;
  border: 1px solid #e0e0e0;
  padding: 1rem 1.5rem;
  border-radius: 0.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.stat-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #6c757d;
  font-weight: 500;
  margin-bottom: 0.25rem;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  line-height: 1.2;
  color: #1a1a1a;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 1rem;
  }

  .header-right {
    width: 100%;
  }

  .stat-card {
    padding: 0.75rem 1rem;
  }

  .stat-value {
    font-size: 1.25rem;
  }

  .page-title {
    font-size: 1.5rem;
  }
}

@media (max-width: 926px) and (orientation: landscape) {
  .stat-card {
    padding: 1.25rem 2rem;
    min-width: 200px;
  }

  .stat-label {
    font-size: 0.8rem;
  }

  .stat-value {
    font-size: 1.5rem;
  }

  .page-title {
    font-size: 1.6rem;
  }
}
</style>
