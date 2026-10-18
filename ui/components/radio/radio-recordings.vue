<template>
  <div>
    <div class="mb-4 flex flex-wrap items-baseline justify-between gap-2">
      <h2 class="mb-0">Raadio</h2>
      <p v-if="recordings" class="mb-0 text-body-secondary">{{ summary }}</p>
    </div>
    <h3 class="mb-3 text-heading">Salvestused</h3>
    <data-table
      :items="recordings ?? []"
      :columns="columns"
      key-field="name"
      :is-loading="isLoading"
      :is-error="isError"
      error-message="Salvestusi ei õnnestunud laadida."
      empty-message="Salvestusi veel pole."
    >
      <template #cell-programme="{ item }">
        <span class="font-semibold">{{ item.programme }}</span>
      </template>
      <template #cell-cuts="{ item }">
        <span :class="{ 'font-semibold': !item.editList?.reviewed }">{{ cutState(item) }}</span>
      </template>
      <template #cell-text="{ item }">
        <span :class="{ 'font-semibold': item.transcribed && !item.transcriptCheckedAt }">
          {{ textState(item) }}
        </span>
      </template>
      <template #cell-state="{ item }">
        <radio-badge :tone="STATES[item.status.state].tone">
          {{ STATES[item.status.state].label }}
        </radio-badge>
      </template>
      <template #cell-listen="{ item }">
        <router-link
          :to="`/radio/${item.name}`"
          :class="LINK"
          :aria-label="`Kuula: ${item.programme} ${day(item.status.startedAt)}`"
        >
          <svg class="size-3" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M7 4v16l13-8z" />
          </svg>
          Kuula
        </router-link>
      </template>
    </data-table>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { RecordingSummary } from '../../models/generated/domain-models'
import { useRadioRecordings } from '../../composables/use-radio-recordings'
import { cutState, day, length, LINK, megabytes, span, STATES, textState } from '../../utils/radio'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'
import RadioBadge from './radio-badge.vue'

const { data: recordings, isLoading, isError } = useRadioRecordings()

const summary = computed(() => {
  const list = recordings.value ?? []
  const bytes = list.reduce((sum, r) => sum + r.sizeBytes, 0)
  const count = list.length === 1 ? '1 salvestus' : `${list.length} salvestust`
  return `Kuku · ${count} · ${megabytes(bytes)}`
})

const columns: ColumnDefinition[] = [
  { key: 'programme', label: 'Saade' },
  {
    key: 'recorded',
    label: 'Salvestus',
    formatter: (_, item: RecordingSummary) => `${day(item.status.startedAt)} · ${span(item)}`,
  },
  {
    key: 'length',
    label: 'Kestus',
    formatter: (_, item: RecordingSummary) => length(item.status.seconds),
  },
  { key: 'text', label: 'Tekst' },
  { key: 'cuts', label: 'Lõiked' },
  { key: 'state', label: 'Olek' },
  { key: 'listen', label: '', class: 'text-right' },
]
</script>
