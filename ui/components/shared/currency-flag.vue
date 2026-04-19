<template>
  <img
    v-if="flagUrl"
    :src="flagUrl"
    :title="normalizedCurrency"
    :alt="normalizedCurrency"
    :width="size"
    :height="size"
    class="currency-flag"
    loading="lazy"
    decoding="async"
    @error="handleError"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { currencyFlagUrl } from '../../utils/currency-flag'

const props = withDefaults(
  defineProps<{
    currency: string | null | undefined
    size?: number
  }>(),
  { size: 16 }
)

const normalizedCurrency = computed(() => props.currency?.toUpperCase() ?? '')
const flagUrl = computed(() => currencyFlagUrl(props.currency))

const handleError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.style.display = 'none'
}
</script>

<style scoped>
.currency-flag {
  display: inline-block;
  border-radius: 50%;
  vertical-align: middle;
  object-fit: cover;
}
</style>
