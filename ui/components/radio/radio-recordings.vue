<template>
  <div>
    <h2 class="mb-4">Raadio</h2>
    <div class="grid gap-8 lg:grid-cols-[minmax(0,1fr)_20rem]">
      <div class="min-w-0">
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
        <radio-programmes />
      </div>
      <radio-station />
    </div>
  </div>
</template>

<script lang="ts" setup>
import type { RecordingSummary } from '../../models/generated/domain-models'
import { useRadioRecordings } from '../../composables/use-radio-recordings'
import { cutState, day, length, LINK, span, STATES, textState } from '../../utils/radio'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'
import RadioBadge from './radio-badge.vue'
import RadioProgrammes from './radio-programmes.vue'
import RadioStation from './radio-station.vue'

const { data: recordings, isLoading, isError } = useRadioRecordings()

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
