<template>
  <div>
    <data-table
      :items="programmes ?? []"
      :columns="columns"
      key-field="id"
      :is-loading="isLoading"
      :is-error="isError"
      error-message="Could not load the shows."
      empty-message="Kuku's schedule has not been loaded yet."
      mobile-list
    >
      <template #mobile-card="{ item }">
        <div data-testid="radio-programme-row" class="flex items-center justify-between gap-3">
          <div class="min-w-0">
            <span class="block font-semibold">{{ item.name }}</span>
            <span class="block text-sm text-body-secondary">
              {{ item.slots.map(airTime).join(' · ') }}
            </span>
          </div>
          <radio-record-every :programme="item" />
        </div>
      </template>
      <template #cell-name="{ item }">
        <span class="font-semibold">{{ item.name }}</span>
        <span v-if="item.description" class="block text-sm text-body-secondary">
          {{ item.description }}
        </span>
      </template>
      <template #cell-slots="{ item }">
        <span v-for="slot in item.slots" :key="airTime(slot)" class="block whitespace-nowrap">
          {{ airTime(slot) }}
        </span>
      </template>
      <template #cell-recordings="{ item }">
        <span :class="{ 'text-body-secondary': !item.recordings }">
          {{ item.recordings || '—' }}
        </span>
      </template>
      <template #cell-recordEvery="{ item }">
        <radio-record-every :programme="item" />
      </template>
    </data-table>
  </div>
</template>

<script lang="ts" setup>
import { useRadioProgrammes } from '../../composables/use-radio-recordings'
import { airTime } from '../../utils/radio'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'
import RadioRecordEvery from './radio-record-every.vue'

const { data: programmes, isLoading, isError } = useRadioProgrammes()

const columns: ColumnDefinition[] = [
  { key: 'name', label: 'Show' },
  { key: 'slots', label: 'On air' },
  { key: 'recordings', label: 'Archived' },
  { key: 'recordEvery', label: 'Recording' },
]
</script>
