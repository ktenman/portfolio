<template>
  <div class="grid gap-8 lg:grid-cols-[minmax(0,1fr)_20rem]">
    <div class="min-w-0">
      <data-table
        :items="recordings ?? []"
        :columns="columns"
        key-field="name"
        :is-loading="isLoading"
        :is-error="isError"
        error-message="Could not load the recordings."
        empty-message="No recordings yet."
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
                :class="ROW_LINK"
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
              :aria-label="`Delete: ${item.programme} ${day(item.status.startedAt)}`"
              @click="remove(item)"
            >
              <radio-icon name="trash" />
            </button>
          </div>
        </template>
        <template #cell-programme="{ item }">
          <router-link
            :to="`/radio/${item.name}`"
            :class="[ROW_LINK, 'hover:underline']"
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
          <span class="sr-only">Actions</span>
        </template>
        <template #cell-actions="{ item }">
          <span>
            <button
              v-if="item.status.state !== RecordingState.RECORDING"
              type="button"
              class="relative z-10 inline-flex items-center justify-center rounded-container p-1.5 text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep max-md:min-h-11 max-md:min-w-11"
              title="Delete"
              :aria-label="`Delete: ${item.programme} ${day(item.status.startedAt)}`"
              @click="remove(item)"
            >
              <radio-icon name="trash" />
            </button>
          </span>
        </template>
      </data-table>
      <radio-on-air class="lg:hidden" />
    </div>
    <radio-station />
  </div>
</template>

<script lang="ts" setup>
import { useQueryClient } from '@tanstack/vue-query'
import { RecordingState, type RecordingSummary } from '../../models/generated/domain-models'
import { useConfirm } from '../../composables/use-confirm'
import { useRadioRecordings } from '../../composables/use-radio-recordings'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import { clock, day, length, ROW_LINK, span, STATES, textState } from '../../utils/radio'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'
import RadioBadge, { type BadgeTone } from './radio-badge.vue'
import RadioIcon from './radio-icon.vue'
import RadioOnAir from './radio-on-air.vue'
import RadioStation from './radio-station.vue'

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
  item.editList ? length(Math.round(item.editList.durationSeconds / 60) * 60) : 'not cleaned'

const remove = async (item: RecordingSummary) => {
  const confirmed = await confirm({
    title: 'Delete recording?',
    message: `${item.programme}, ${day(item.status.startedAt)} · ${span(item)} will be deleted permanently, along with its audio and text.`,
    confirmText: 'Delete',
    cancelText: 'Cancel',
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
    .catch(() => toast.error('Could not delete the recording'))
}

const columns: ColumnDefinition[] = [
  { key: 'programme', label: 'Show' },
  {
    key: 'recorded',
    label: 'Recorded',
    class: 'whitespace-nowrap',
    formatter: (_, item: RecordingSummary) => `${day(item.status.startedAt)} · ${span(item)}`,
  },
  {
    key: 'length',
    label: 'Length',
    class: 'whitespace-nowrap',
    formatter: (_, item: RecordingSummary) => length(item.status.seconds),
  },
  { key: 'text', label: 'Transcript' },
  { key: 'state', label: 'Status' },
  { key: 'actions', label: 'Actions', class: 'text-right' },
]
</script>
