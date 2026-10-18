<template>
  <div>
    <div class="mt-8 mb-3 flex flex-wrap items-baseline justify-between gap-2">
      <h3 class="mb-0 text-heading">Saated</h3>
      <p class="mb-0 text-sm text-body-secondary">Kordusi ei salvestata topelt</p>
    </div>
    <data-table
      :items="programmes ?? []"
      :columns="columns"
      key-field="id"
      :is-loading="isLoading"
      :is-error="isError"
      error-message="Saateid ei õnnestunud laadida."
      empty-message="Kuku kava pole veel laaditud."
    >
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
      <template #cell-repeats="{ item }">
        <span class="text-body-secondary">{{ item.repeats.map(airStart).join(' · ') || '—' }}</span>
      </template>
      <template #cell-recordings="{ item }">
        <span :class="{ 'text-body-secondary': !item.recordings }">
          {{ item.recordings || '—' }}
        </span>
      </template>
      <template #cell-recordEvery="{ item }">
        <label class="mb-0 inline-flex cursor-pointer items-center gap-2 whitespace-nowrap">
          <input
            type="checkbox"
            class="form-check-input"
            :checked="item.recordEvery"
            :aria-label="`Salvesta iga kord: ${item.name}`"
            @change="choose(item, $event.target as HTMLInputElement)"
          />
          Iga kord
        </label>
      </template>
    </data-table>
  </div>
</template>

<script lang="ts" setup>
import { useQueryClient } from '@tanstack/vue-query'
import type { RadioProgrammeDto } from '../../models/generated/domain-models'
import { useRadioProgrammes } from '../../composables/use-radio-recordings'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import { airStart, airTime } from '../../utils/radio'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'

const { data: programmes, isLoading, isError } = useRadioProgrammes()
const queryClient = useQueryClient()
const toast = useToast()

const columns: ColumnDefinition[] = [
  { key: 'name', label: 'Saade' },
  { key: 'slots', label: 'Eetris' },
  { key: 'repeats', label: 'Kordused' },
  { key: 'recordings', label: 'Arhiivis' },
  { key: 'recordEvery', label: 'Salvestamine' },
]

const choose = (programme: RadioProgrammeDto, box: HTMLInputElement) =>
  radioService
    .choose(programme.id, { recordEvery: box.checked })
    .then(() =>
      Promise.all(
        ['radio-programmes', 'radio-station'].map(key =>
          queryClient.invalidateQueries({ queryKey: [key] })
        )
      )
    )
    .catch(() => {
      box.checked = !box.checked
      toast.error('Valikut ei saanud salvestada')
    })
</script>
