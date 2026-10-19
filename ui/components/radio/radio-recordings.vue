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
        >
          <template #cell-programme="{ item }">
            <span class="font-semibold">{{ item.programme }}</span>
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
          <template #cell-listen="{ item }">
            <div class="inline-flex items-center gap-1">
              <button
                v-if="item.status.state !== RecordingState.RECORDING"
                type="button"
                class="inline-flex rounded-container p-1.5 text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep"
                title="Kustuta"
                :aria-label="`Kustuta: ${item.programme} ${day(item.status.startedAt)}`"
                @click="remove(item)"
              >
                <radio-icon name="trash" />
              </button>
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
            </div>
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
import { cutState, day, length, LINK, span, STATES, textState } from '../../utils/radio'
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
  { key: 'listen', label: '', class: 'text-right' },
]
</script>
