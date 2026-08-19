<template>
  <div class="range-change" :class="[changeClass, flashClass]">{{ label }}</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useNumberTransition } from '../../composables/use-number-transition'
import { useFlashOnChange } from '../../composables/use-flash-on-change'
import { formatSignedCurrency, formatSignedPercent, getGainLossClass } from '../../utils/formatters'

const props = defineProps<{
  amount: number
  percent: number
}>()

const amount = computed(() => props.amount)
const percent = computed(() => props.percent)

const animatedAmount = useNumberTransition(amount)
const animatedPercent = useNumberTransition(percent)
const flashClass = useFlashOnChange(amount)

const label = computed(
  () =>
    `${formatSignedCurrency(animatedAmount.value, 'EUR')} (${formatSignedPercent(animatedPercent.value)})`
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
