<template>
  <div>
    <h3 class="mt-8 mb-3 text-heading">Saated</h3>
    <data-table
      :items="shown"
      :columns="columns"
      key-field="id"
      :is-loading="isLoading"
      :is-error="isError"
      error-message="Saateid ei õnnestunud laadida."
      empty-message="Kuku kava pole veel laaditud."
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
    <button
      v-if="shown.length < (programmes?.length ?? 0)"
      type="button"
      class="mt-3 cursor-pointer text-sm font-semibold hover:text-brass-deep"
      @click="everything = true"
    >
      Kõik {{ programmes?.length }} saadet saatekavas
    </button>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useRadioProgrammes } from '../../composables/use-radio-recordings'
import { airTime } from '../../utils/radio'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'
import RadioRecordEvery from './radio-record-every.vue'

const FEW = 8

const { data: programmes, isLoading, isError } = useRadioProgrammes()
const everything = ref(false)

const shown = computed(() =>
  (programmes.value ?? []).filter((p, i) => everything.value || p.recordEvery || i < FEW)
)

const columns: ColumnDefinition[] = [
  { key: 'name', label: 'Saade' },
  { key: 'slots', label: 'Eetris' },
  { key: 'recordings', label: 'Arhiivis' },
  { key: 'recordEvery', label: 'Salvestamine' },
]
</script>
