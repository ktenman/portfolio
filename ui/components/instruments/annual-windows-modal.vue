<template>
  <div
    class="modal fade"
    id="annualWindowsModal"
    tabindex="-1"
    aria-labelledby="annualWindowsModalLabel"
    aria-hidden="true"
  >
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="annualWindowsModalLabel">Buy-and-hold annualized return</h5>
          <button
            type="button"
            class="btn-close"
            data-bs-dismiss="modal"
            aria-label="Close"
          ></button>
        </div>
        <div class="modal-body">
          <div v-if="isLoading" class="text-center py-3">
            <div class="spinner-border" role="status" />
          </div>
          <div v-else-if="error" class="alert alert-danger">{{ error }}</div>
          <div v-else>
            <table class="table table-sm mb-0">
              <thead>
                <tr>
                  <th>Window</th>
                  <th class="text-end">Annualized return</th>
                  <th class="d-none d-sm-table-cell text-end">Since</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in windows" :key="row.period">
                  <td class="fw-semibold">{{ row.period }}</td>
                  <td class="text-end" :class="returnClass(row.annualReturn)">
                    {{ formatReturn(row.annualReturn) }}
                  </td>
                  <td class="d-none d-sm-table-cell text-end text-muted">
                    {{ row.fromDate ?? '—' }}
                  </td>
                </tr>
              </tbody>
            </table>
            <p class="text-muted small fst-italic mt-3 mb-0">
              Synthetic buy-and-hold using current shares × historical close price at window start
              vs current value. Real transactions during the window are ignored. "Since" clamps to
              the earliest available price when history is shorter than the window.
            </p>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { portfolioSummaryService } from '../../services/portfolio-summary-service'
import type { AnnualWindowDto } from '../../models/generated/domain-models'

interface Props {
  open: boolean
  platforms?: string[]
}

const props = withDefaults(defineProps<Props>(), { platforms: () => [] })

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
  if (value > 0) return 'text-success'
  if (value < 0) return 'text-danger'
  return ''
}
</script>
