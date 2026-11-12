<template>
  <div class="grid gap-8 lg:grid-cols-[minmax(0,1fr)_20rem]">
    <div class="grid min-w-0 gap-6">
      <data-table
        v-if="!groups.length"
        :items="[]"
        :columns="columns"
        :is-loading="isLoading"
        :is-error="isError"
        error-message="Could not load the shows."
        empty-message="The schedule has not been loaded yet."
      />
      <section v-for="group in groups" :key="group.title" class="min-w-0">
        <div
          data-testid="radio-programme-group"
          class="mb-2 flex items-baseline justify-between gap-3"
        >
          <h3 class="mb-0 text-heading">{{ group.title }}</h3>
          <span class="text-sm text-ink-soft">{{ group.note }}</span>
        </div>
        <data-table
          :items="group.items"
          :columns="columns"
          key-field="id"
          :row-class="() => 'relative'"
          mobile-list
        >
          <template #mobile-card="{ item }">
            <radio-record-every
              :programme="item"
              data-testid="radio-programme-row"
              class="min-h-11 justify-between after:absolute after:inset-0"
            >
              <span class="min-w-0">
                <span class="block font-semibold">{{ item.name }}</span>
                <span class="block text-sm text-body-secondary">
                  {{ [STATIONS[item.station], ...item.slots.map(airTime)].join(' · ') }}
                </span>
              </span>
            </radio-record-every>
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
      </section>
      <radio-on-air class="lg:hidden" />
    </div>
    <radio-station />
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRadioProgrammes, useStationFilter } from '../../composables/use-radio-recordings'
import { airTime, STATIONS } from '../../utils/radio'
import type { Station } from '../../models/generated/domain-models'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'
import RadioOnAir from './radio-on-air.vue'
import RadioRecordEvery from './radio-record-every.vue'
import RadioStation from './radio-station.vue'

const { data: programmes, isLoading, isError } = useRadioProgrammes()
const station = useStationFilter()
const shown = computed(() =>
  (programmes.value ?? []).filter(item => !station.value || item.station === station.value)
)
const groups = computed(() => {
  const ticked = shown.value.filter(item => item.recordEvery)
  const other = shown.value.filter(item => !item.recordEvery)
  return [
    { title: 'Recorded every time', note: `${ticked.length}`, items: ticked },
    { title: 'Other shows', note: `${other.length} · by next airing`, items: other },
  ].filter(group => group.items.length)
})

const columns: ColumnDefinition[] = [
  { key: 'name', label: 'Show' },
  {
    key: 'station',
    label: 'Station',
    class: 'w-28',
    formatter: (value: Station) => STATIONS[value],
  },
  { key: 'slots', label: 'Airs', class: 'w-48' },
  { key: 'recordings', label: 'Archived', class: 'w-28' },
  { key: 'recordEvery', label: 'Record', class: 'w-24' },
]
</script>
