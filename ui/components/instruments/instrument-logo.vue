<template>
  <img
    v-if="logoUuid && !failed"
    :src="logoService.getLogoUrl(logoUuid)"
    alt=""
    loading="lazy"
    decoding="async"
    class="shrink-0 rounded-full bg-surface-sunken object-cover p-0.5"
    :style="box"
    @error="failed = true"
  />
  <span
    v-else
    aria-hidden="true"
    class="inline-flex shrink-0 items-center justify-center rounded-full bg-surface-sunken font-semibold text-ink-soft"
    :style="{ ...box, fontSize: `${Math.round(size * 0.45)}px` }"
  >
    {{ symbol.charAt(0).toUpperCase() }}
  </span>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { logoService } from '../../services/api'

const props = defineProps<{ logoUuid?: string | null; symbol: string; size: number }>()

const failed = ref(false)
const box = computed(() => ({ width: `${props.size}px`, height: `${props.size}px` }))

watch(
  () => props.logoUuid,
  () => (failed.value = false)
)
</script>
