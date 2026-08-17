<template>
  <div class="range-change" :class="changeClass">{{ label }}</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatSignedCurrency, formatSignedPercent, getGainLossClass } from '../../utils/formatters'

const props = defineProps<{
  amount: number
  percent: number
}>()

const label = computed(
  () => `${formatSignedCurrency(props.amount, 'EUR')} (${formatSignedPercent(props.percent)})`
)

const changeClass = computed(() => getGainLossClass(props.amount))
</script>

<style scoped>
.range-change {
  font-size: var(--text-control);
  font-weight: 550;
  line-height: 1.2;
}
</style>
