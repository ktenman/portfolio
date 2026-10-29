<template>
  <div class="range-line">
    <span class="range-change" :class="[changeClass, flashClass]">
      <span>{{ parts[0] }}</span>
      <span class="range-cents">{{ parts[1] }}</span>
      <span>{{ rate }}</span>
    </span>
    <span class="range-period">{{ period }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed, toRefs } from 'vue'
import { useNumberTransition } from '../../composables/use-number-transition'
import { useFlashOnChange } from '../../composables/use-flash-on-change'
import { describeRange } from '../../composables/use-time-range'
import type { TimeRange } from '../../models/generated/domain-models'
import {
  formatSignedCurrency,
  formatSignedPercent,
  getGainLossClass,
  splitCents,
} from '../../utils/formatters'

const props = defineProps<{
  amount: number
  percent: number
  range: TimeRange
}>()

const { amount, percent } = toRefs(props)

const animatedAmount = useNumberTransition(amount)
const animatedPercent = useNumberTransition(percent)
const flashClass = useFlashOnChange(amount)

const parts = computed(() => splitCents(formatSignedCurrency(animatedAmount.value, 'EUR')))

const rate = computed(() => ` (${formatSignedPercent(animatedPercent.value)})`)

const period = computed(() => ` ${describeRange(props.range)}`)

const changeClass = computed(() => getGainLossClass(amount.value))
</script>

<style scoped>
.range-line {
  font-size: var(--text-control);
  line-height: 1.2;
}

.range-change {
  font-weight: 550;
}

.range-period {
  color: var(--color-ink-soft);
}

.range-cents {
  font-size: var(--text-label);
  font-weight: 500;
}
</style>
