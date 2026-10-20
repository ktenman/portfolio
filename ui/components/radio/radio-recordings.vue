<template>
  <div>
    <radio-tabs />
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
          :row-class="() => 'relative cursor-pointer'"
        >
          <template #cell-programme="{ item }">
            <router-link
              :to="`/radio/${item.name}`"
              class="font-semibold after:absolute after:inset-0 after:rounded-container hover:underline focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-brass-deep"
              :aria-label="`${item.programme} ${day(item.status.startedAt)} · ${span(item)}`"
            >
              {{ item.programme }}
            </router-link>
          </template>
          <template #cell-cuts="{ item }">
            <span :class="{ 'font-semibold': !item.editList?.reviewed }">{{ cutState(item) }}</span>
          </template>
          <template #cell-text="{ item }">
            <span data-testid="radio-transcript">{{ textState(item) }}</span>
          </template>
          <template #cell-state="{ item }">
            <radio-badge :tone="STATES[item.status.state].tone">
              {{ STATES[item.status.state].label }}
            </radio-badge>
          </template>
          <template #header-actions>
            <span class="sr-only">Toimingud</span>
          </template>
          <template #cell-actions="{ item }">
            <span>
              <button
                v-if="item.status.state !== RecordingState.RECORDING"
                type="button"
                class="relative z-10 inline-flex items-center justify-center rounded-container p-1.5 text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep max-md:min-h-11 max-md:min-w-11"
                title="Kustuta"
                :aria-label="`Kustuta: ${item.programme} ${day(item.status.startedAt)}`"
                @click="remove(item)"
              >
                <radio-icon name="trash" />
              </button>
            </span>
          </template>
        </data-table>
        <radio-programmes />
      </div>
      <radio-station />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { useQueryClient } from '@tanstack/vue-query'
import { RecordingState, type RecordingSummary } from '../../models/generated/domain-models'
import { useConfirm } from '../../composables/use-confirm'
import { useRadioRecordings } from '../../composables/use-radio-recordings'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import { cutState, day, length, span, STATES, textState } from '../../utils/radio'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'
import RadioBadge from './radio-badge.vue'
import RadioIcon from './radio-icon.vue'
import RadioProgrammes from './radio-programmes.vue'
import RadioStation from './radio-station.vue'
import RadioTabs from './radio-tabs.vue'

const { data: recordings, isLoading, isError } = useRadioRecordings()
const { confirm } = useConfirm()
const toast = useToast()
const queryClient = useQueryClient()

const remove = async (item: RecordingSummary) => {
  const confirmed = await confirm({
    title: 'Kustuta salvestus?',
    message: `${item.programme}, ${day(item.status.startedAt)} · ${span(item)}, kustutatakse jäädavalt koos heli ja tekstiga.`,
    confirmText: 'Kustuta',
    cancelText: 'Loobu',
    variant: 'danger',
  })
  if (!confirmed) return
  await radioService
    .deleteRecording(item.name)
    .then(() =>
      Promise.all(
        ['radio', 'radio-programmes', 'radio-station'].map(key =>
          queryClient.invalidateQueries({ queryKey: [key] })
        )
      )
    )
    .catch(() => toast.error('Salvestust ei saanud kustutada'))
}

const columns: ColumnDefinition[] = [
  { key: 'programme', label: 'Saade' },
  {
    key: 'recorded',
    label: 'Salvestus',
    class: 'whitespace-nowrap',
    formatter: (_, item: RecordingSummary) => `${day(item.status.startedAt)} · ${span(item)}`,
  },
  {
    key: 'length',
    label: 'Kestus',
    class: 'whitespace-nowrap',
    formatter: (_, item: RecordingSummary) => length(item.status.seconds),
  },
  { key: 'text', label: 'Tekst' },
  { key: 'cuts', label: 'Lõiked' },
  { key: 'state', label: 'Olek' },
  { key: 'actions', label: 'Toimingud', class: 'text-right' },
]
</script>
