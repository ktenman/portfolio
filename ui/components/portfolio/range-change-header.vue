<template>
  <div class="range-change" :class="changeClass">{{ label }}</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatCurrencyWithSign, getGainLossClass } from '../../utils/formatters'

const props = defineProps<{
  amount: number
  percent: number
}>()

const label = computed(() => {
  const sign = props.amount < 0 ? '−' : '+'
  const percent = Math.abs(props.percent).toFixed(2)
  return `${sign}${formatCurrencyWithSign(props.amount, 'EUR')} (${sign}${percent}%)`
})

const changeClass = computed(() => getGainLossClass(props.amount))
</script>

<style scoped>
.range-change {
  font-size: var(--text-control);
  font-weight: 550;
  line-height: 1.2;
}
</style>
