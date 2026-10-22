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
          mobile-list
        >
          <template #mobile-card="{ item }">
            <div data-testid="radio-recording-row" class="flex items-center gap-3.5">
              <span
                class="grid size-13 shrink-0 content-center justify-items-center gap-0.5 rounded-container"
                :class="live(item) ? 'bg-notice-wash text-notice' : 'bg-surface-sunken'"
              >
                <span
                  class="text-label leading-none font-medium"
                  :class="{ 'text-ink-soft': !live(item) }"
                >
                  {{ weekday(item) }}
                </span>
                <span class="text-xl leading-none font-semibold tabular-nums">
                  {{ date(item) }}
                </span>
              </span>
              <span class="grid min-w-0 grow justify-items-start gap-0.5">
                <router-link
                  :to="`/radio/${item.name}`"
                  class="font-semibold after:absolute after:inset-0 after:rounded-container focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-brass-deep"
                  :aria-label="`${item.programme} ${day(item.status.startedAt)} · ${span(item)}`"
                >
                  {{ item.programme }}
                </router-link>
                <span class="text-sm text-ink-soft tabular-nums">
                  {{ clock(item.span.start, 0) }} ·
                  <span :class="TONE_TEXT[STATES[item.status.state].tone]">
                    {{
                      item.status.state === RecordingState.CAPTURED
                        ? cleaned(item)
                        : STATES[item.status.state].label.toLowerCase()
                    }}
                  </span>
                </span>
              </span>
              <button
                v-if="!live(item)"
                type="button"
                class="relative z-10 inline-flex size-11 shrink-0 items-center justify-center rounded-full text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep"
                :aria-label="`Kustuta: ${item.programme} ${day(item.status.startedAt)}`"
                @click="remove(item)"
              >
                <radio-icon name="trash" />
              </button>
            </div>
          </template>
          <template #cell-programme="{ item }">
            <router-link
              :to="`/radio/${item.name}`"
              class="font-semibold after:absolute after:inset-0 after:rounded-container hover:underline focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-brass-deep"
              :aria-label="`${item.programme} ${day(item.status.startedAt)} · ${span(item)}`"
            >
              {{ item.programme }}
            </router-link>
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
import { clock, day, length, span, STATES, textState } from '../../utils/radio'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'
import RadioBadge, { type BadgeTone } from './radio-badge.vue'
import RadioIcon from './radio-icon.vue'
import RadioProgrammes from './radio-programmes.vue'
import RadioStation from './radio-station.vue'
import RadioTabs from './radio-tabs.vue'

const { data: recordings, isLoading, isError } = useRadioRecordings()
const { confirm } = useConfirm()
const toast = useToast()
const queryClient = useQueryClient()

const TONE_TEXT: Record<BadgeTone, string> = {
  plain: 'text-ink-soft',
  live: 'text-notice',
  warn: 'text-brass-deep',
  error: 'text-loss-deep',
}

const weekday = (item: RecordingSummary) => day(item.status.startedAt).split(' ')[0]

const date = (item: RecordingSummary) => Number(day(item.status.startedAt).slice(-5, -3))

const live = (item: RecordingSummary) => item.status.state === RecordingState.RECORDING

const cleaned = (item: RecordingSummary) =>
  item.editList ? length(Math.round(item.editList.durationSeconds / 60) * 60) : 'Puhastamata'

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
  { key: 'state', label: 'Olek' },
  { key: 'actions', label: 'Toimingud', class: 'text-right' },
]
</script>
