<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3 pb-20 md:pb-0">
    <div class="mb-6 flex flex-wrap items-baseline justify-between gap-2">
      <h2 class="mb-0">Monitoring</h2>
      <p v-if="collections" class="mb-0 text-body-secondary" data-testid="monitoring-verdict">
        {{ verdict }}
      </p>
    </div>
    <data-table
      :items="collections ?? []"
      :columns="columns"
      :is-loading="isLoading"
      :is-error="isError"
      error-message="Could not load collection status. It retries every 15 seconds."
      empty-message="No collections are configured."
      data-testid="monitoring-table"
    >
      <template #cell-status="{ item }">
        <span
          class="inline-block rounded-control px-2 py-0.5 text-label font-semibold tracking-wide uppercase"
          :class="STATUS_STYLES[item.status as CollectionStatus].class"
        >
          {{ STATUS_STYLES[item.status as CollectionStatus].label }}
        </span>
      </template>
      <template #cell-items="{ item }">
        <span :class="{ 'text-loss-deep': item.failed > 0 }">{{ items(item) }}</span>
        <span
          v-if="item.failedItems.length"
          class="block text-label text-body-secondary"
          data-testid="monitoring-failed-items"
        >
          {{ item.failedItems.join(', ') }}
        </span>
      </template>
      <template #cell-lastFullSuccess="{ value }">
        <time :datetime="value" :title="value ?? ''">{{ ago(value) }}</time>
      </template>
      <template #cell-deadline="{ value }">
        <time :datetime="value" :title="value ?? ''">{{ formatDateTime(value ?? '') || '—' }}</time>
      </template>
      <template #cell-actions="{ item }">
        <button
          type="button"
          class="inline-flex size-8 cursor-pointer items-center justify-center rounded-control text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep disabled:cursor-default disabled:text-brass-deep disabled:hover:bg-transparent md-down:size-11"
          :disabled="running.has(item.key)"
          :title="running.has(item.key) ? 'Running…' : 'Run now'"
          :aria-label="running.has(item.key) ? `${label(item)} running` : `Run ${label(item)} now`"
          data-testid="monitoring-rerun"
          @click="rerun(item)"
        >
          <svg
            class="size-4"
            :class="{ 'motion-safe:animate-spin': running.has(item.key) }"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8" />
            <path d="M21 3v5h-5" />
          </svg>
        </button>
      </template>
    </data-table>
    <p v-if="rerunError" class="mt-3 mb-0 text-loss-deep" role="alert">
      Could not start {{ rerunError }}. Check the backend log and try again.
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'
import { monitoringService } from '../../services/api'
import { REFETCH_INTERVALS } from '../../constants'
import { formatDateTime } from '../../utils/formatters'
import { CollectionStatus, type CollectionStatusDto } from '../../models/generated/domain-models'

const STATUS_STYLES: Record<CollectionStatus, { label: string; class: string }> = {
  [CollectionStatus.OK]: { label: 'OK', class: 'bg-gain-wash text-gain-deep' },
  [CollectionStatus.PARTIAL_FAILURE]: { label: 'Partial', class: 'bg-brass-wash text-brass-deep' },
  [CollectionStatus.OVERDUE]: { label: 'Overdue', class: 'bg-loss-wash text-loss-deep' },
  [CollectionStatus.BREAKER_OPEN]: { label: 'Breaker open', class: 'bg-loss-wash text-loss-deep' },
  [CollectionStatus.RUNNING]: { label: 'Running', class: 'bg-surface-sunken text-ink-soft' },
  [CollectionStatus.DISABLED]: { label: 'Disabled', class: 'bg-surface-sunken text-ink-soft' },
}

const RERUN_TIMEOUT = 5 * 60 * 1000

const PROVIDER_NAMES: Record<string, string> = { ft: 'FT', blackrock: 'BlackRock' }

const capitalize = (value: string) => value.charAt(0).toUpperCase() + value.slice(1)

const columns: ColumnDefinition[] = [
  {
    key: 'provider',
    label: 'Provider',
    formatter: (value: string) => PROVIDER_NAMES[value] ?? capitalize(value),
  },
  { key: 'operation', label: 'Operation', formatter: (value: string) => capitalize(value) },
  { key: 'status', label: 'Status' },
  { key: 'items', label: 'Persisted / expected', class: 'text-right' },
  { key: 'lastFullSuccess', label: 'Last full success' },
  { key: 'deadline', label: 'Next deadline', hideOnMobile: true },
  {
    key: 'durationSeconds',
    label: 'Duration',
    class: 'text-right',
    hideOnMobile: true,
    formatter: (value: number) => `${value.toFixed(1)} s`,
  },
  { key: 'consecutiveEmptyRuns', label: 'Empty runs', class: 'text-right', hideOnMobile: true },
  { key: 'actions', label: '', class: 'text-right' },
]

const requested = ref(new Map<string, number>())
const running = computed(
  () =>
    new Set([
      ...requested.value.keys(),
      ...(collections.value ?? [])
        .filter(c => c.status === CollectionStatus.RUNNING)
        .map(c => c.key),
    ])
)
const rerunError = ref<string | null>(null)

const {
  data: collections,
  isLoading,
  isError,
} = useQuery({
  queryKey: ['monitoring-collections'],
  queryFn: monitoringService.getCollections,
  refetchInterval: () => (requested.value.size ? 3000 : REFETCH_INTERVALS.MONITORING),
})

const label = (item: CollectionStatusDto) =>
  `${PROVIDER_NAMES[item.provider] ?? capitalize(item.provider)} ${item.operation}`

const rerun = async (item: CollectionStatusDto) => {
  rerunError.value = null
  requested.value = new Map(requested.value).set(item.key, Date.now())
  await monitoringService.rerun(item.key).catch(() => {
    rerunError.value = label(item)
    finish(item.key)
  })
}

const finish = (key: string) => {
  const next = new Map(requested.value)
  next.delete(key)
  requested.value = next
}

watch(collections, current =>
  current?.forEach(item => {
    const since = requested.value.get(item.key)
    if (!since) return
    const completed = item.lastCompletion && new Date(item.lastCompletion).getTime() >= since
    if (completed || Date.now() - since > RERUN_TIMEOUT) finish(item.key)
  })
)

const verdict = computed(() => {
  const failing = (collections.value ?? []).filter(c =>
    [
      CollectionStatus.PARTIAL_FAILURE,
      CollectionStatus.OVERDUE,
      CollectionStatus.BREAKER_OPEN,
    ].includes(c.status)
  ).length
  if (failing === 0) return `All ${collections.value?.length ?? 0} collections healthy`
  return `${failing} of ${collections.value?.length ?? 0} need attention`
})

const items = (item: CollectionStatusDto) =>
  item.failed > 0
    ? `${item.persisted} / ${item.expected} · ${item.failed} failed`
    : `${item.persisted} / ${item.expected}`

const ago = (value: string | null): string => {
  if (!value) return 'Never'
  const minutes = Math.round((Date.now() - new Date(value).getTime()) / 60000)
  if (minutes < 1) return 'Just now'
  if (minutes < 60) return `${minutes} min ago`
  if (minutes < 48 * 60) return `${Math.round(minutes / 60)} h ago`
  return `${Math.round(minutes / 1440)} d ago`
}
</script>
