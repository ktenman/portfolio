<template>
  <modal-shell
    :open="open"
    modal-id="xirrWindowsModal"
    title="Annualized return over time"
    centered
    @update:open="emit('update:open', $event)"
  >
    <div v-if="isLoading" class="text-center py-3">
      <div class="spinner-border" role="status" />
    </div>
    <div v-else-if="error" class="alert alert-danger">{{ error }}</div>
    <div v-else>
      <table class="table table-sm mb-0">
        <thead>
          <tr>
            <th>Window</th>
            <th class="text-end">Annualized XIRR</th>
            <th class="d-none d-sm-table-cell text-end">Since</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in windows" :key="row.period">
            <td class="fw-semibold">{{ row.period }}</td>
            <td class="text-end" :class="returnClass(row.xirr)">
              {{ formatXirr(row.xirr) }}
            </td>
            <td class="d-none d-sm-table-cell text-end text-muted">
              {{ row.fromDate ?? '—' }}
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted small fst-italic mt-3 mb-0">
        Synthetic open at window start (portfolio value), real cash flows during the window,
        synthetic close today. Rows show "—" when the window predates your earliest portfolio
        snapshot.
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
import type { XirrWindowDto } from '../../models/generated/domain-models'

interface Props {
  open: boolean
  platforms?: string[]
}

const props = withDefaults(defineProps<Props>(), { platforms: () => [] })

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const windows = ref<XirrWindowDto[]>([])
const isLoading = ref(false)
const error = ref<string | null>(null)

const platformsKey = computed(() => [...props.platforms].sort().join(','))

const load = async () => {
  isLoading.value = true
  error.value = null
  try {
    const result = await portfolioSummaryService.getXirrWindows(props.platforms)
    windows.value = result.windows ?? []
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load XIRR windows'
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

const formatXirr = (value: number | null | undefined): string => {
  if (value === null || value === undefined) return '—'
  const sign = value >= 0 ? '+' : ''
  return `${sign}${(value * 100).toFixed(2)}%`
}

const returnClass = (value: number | null | undefined): string => {
  if (value === null || value === undefined) return ''
  if (value > 0) return 'text-success'
  if (value < 0) return 'text-danger'
  return ''
}
</script>
