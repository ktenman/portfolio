<template>
  <modal-shell
    :open="open"
    modal-id="annualWindowsModal"
    title="Buy-and-hold annualized return"
    centered
    @update:open="emit('update:open', $event)"
  >
    <div v-if="isLoading" class="tw:text-center tw:py-4">
      <div class="spinner-border" role="status" />
    </div>
    <div v-else-if="error" class="alert alert-danger">{{ error }}</div>
    <div v-else>
      <table class="table table-sm tw:mb-0">
        <thead>
          <tr>
            <th>Window</th>
            <th class="tw:text-right">Annualized return</th>
            <th class="tw:hidden tw:sm:table-cell tw:text-right">Since</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in windows" :key="row.period">
            <td class="tw:font-semibold">{{ row.period }}</td>
            <td class="tw:text-right" :class="returnClass(row.annualReturn)">
              {{ formatReturn(row.annualReturn) }}
            </td>
            <td class="tw:hidden tw:sm:table-cell tw:text-right tw:text-body-secondary">
              {{ row.fromDate ?? '—' }}
            </td>
          </tr>
        </tbody>
      </table>
      <p class="tw:mt-4 tw:mb-0 tw:text-[0.875em] tw:italic tw:text-body-secondary">
        Synthetic buy-and-hold using current shares × historical close price at window start vs
        current value. Real transactions during the window are ignored. "Since" clamps to the
        earliest available price when history is shorter than the window.
      </p>
    </div>
    <template #footer>
      <button type="button" class="btn btn-secondary" @click="emit('update:open', false)">
        Close
      </button>
    </template>
  </modal-shell>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ModalShell from '../shared/modal-shell.vue'
import { portfolioSummaryService } from '../../services/portfolio-summary-service'
import type { AnnualWindowDto } from '../../models/generated/domain-models'

interface Props {
  open: boolean
  platforms?: string[]
}

const props = withDefaults(defineProps<Props>(), { platforms: () => [] })

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const windows = ref<AnnualWindowDto[]>([])
const isLoading = ref(false)
const error = ref<string | null>(null)

const platformsKey = computed(() => [...props.platforms].sort().join(','))

const load = async () => {
  isLoading.value = true
  error.value = null
  try {
    const result = await portfolioSummaryService.getAnnualWindows(props.platforms)
    windows.value = result.windows ?? []
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load annual windows'
  } finally {
    isLoading.value = false
  }
}

watch(
  () => [props.open, platformsKey.value],
  ([isOpen]) => {
    if (isOpen) load()
  },
  { immediate: true }
)

const formatReturn = (value: number | null | undefined): string => {
  if (value === null || value === undefined) return '—'
  const sign = value >= 0 ? '+' : ''
  return `${sign}${(value * 100).toFixed(2)}%`
}

const returnClass = (value: number | null | undefined): string => {
  if (value === null || value === undefined) return ''
  if (value > 0) return 'tw:text-gain'
  if (value < 0) return 'tw:text-loss'
  return ''
}
</script>
