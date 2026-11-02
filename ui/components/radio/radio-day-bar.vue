<template>
  <radio-card title="Recorded time" class="max-md:hidden">
    <template #aside>
      <span class="text-sm text-ink-soft" data-testid="radio-day-summary">
        {{ recordedSummary(radioDay) ?? plannedSummary(radioDay) }}
      </span>
    </template>
    <div
      v-for="track in tracks"
      :key="track.station"
      class="grid grid-cols-[72px_minmax(0,1fr)] items-end gap-x-3"
      data-testid="radio-day-track"
    >
      <span class="h-7 text-sm leading-7 font-medium" data-testid="radio-day-station">
        {{ STATIONS[track.station] }}
      </span>
      <div class="@container relative pt-10">
        <router-link
          v-for="span in track.recorded"
          :key="span.start"
          :to="`/radio/${span.name}`"
          class="group absolute inset-y-0 z-10 rounded-control hover:bg-ink/5"
          data-testid="radio-day-link"
          :style="place(span)"
          :title="`${span.programme} · ${times(span)}`"
          :aria-label="`${span.programme}, ${times(span)} · ${STATIONS[track.station]}`"
        >
          <span
            class="@container pointer-events-none block px-1 pt-px"
            :style="{ width: room(span, track.starts) }"
          >
            <span
              class="pointer-events-auto block max-w-fit truncate text-label text-ink-soft tabular-nums @max-[2rem]:hidden"
            >
              {{ clock(span.start, 0) }}
            </span>
            <span
              class="pointer-events-auto block max-w-fit truncate text-sm font-medium group-hover:underline group-focus-visible:underline @max-[3.5rem]:hidden"
            >
              {{ span.programme }}
            </span>
          </span>
        </router-link>
        <div
          v-for="span in track.planned"
          :key="span.start"
          class="absolute inset-y-0 z-10"
          aria-hidden="true"
          data-testid="radio-day-planned"
          :style="place(span)"
          :title="`${span.programme} · ${range(span.start, 0, span.end - span.start)} · planned`"
        >
          <span
            class="@container pointer-events-none block px-1 pt-px text-ink-soft"
            :style="{ width: room(span, track.starts) }"
          >
            <span
              class="pointer-events-auto block max-w-fit truncate text-label tabular-nums @max-[2rem]:hidden"
            >
              {{ clock(span.start, 0) }}
            </span>
            <span
              class="pointer-events-auto block max-w-fit truncate text-sm font-medium @max-[3.5rem]:hidden"
            >
              {{ span.programme }}
            </span>
          </span>
        </div>
        <div class="relative h-7" aria-hidden="true">
          <div class="absolute inset-x-0 top-2.5 h-2 rounded-control bg-hairline" />
          <div
            v-for="tick in radioDay.ticks"
            :key="tick.at"
            class="absolute"
            :class="
              tick.midnight
                ? 'top-5 h-2 w-0.5 -ml-px bg-ink-faint'
                : 'top-5.5 h-1.5 w-px bg-hairline-strong'
            "
            data-testid="radio-day-tick"
            :style="{ left: at(tick.at) }"
          />
          <div
            v-for="span in track.recorded"
            :key="span.start"
            class="absolute top-2.5 h-2 min-w-0.5"
            :class="span.recording ? `rounded-l-control ${LIVE}` : 'rounded-control bg-ink'"
            data-testid="radio-day-span"
            :style="place(span)"
          />
          <div
            v-for="span in track.planned"
            :key="span.start"
            class="absolute top-2.5 h-2 min-w-0.5 rounded-control border border-ink-soft"
            data-testid="radio-day-planned-span"
            :style="place(span)"
          />
          <div
            v-if="current"
            class="absolute top-0.5 bottom-0 -ml-px w-0.5 rounded-full bg-ink-soft"
            data-testid="radio-day-now"
            :style="{ left: at(radioDay.now) }"
          />
        </div>
      </div>
    </div>
    <div
      class="mt-1.5 grid grid-cols-[72px_minmax(0,1fr)] gap-x-3"
      aria-hidden="true"
      data-testid="radio-day-hours"
    >
      <div class="relative col-start-2 h-4.5 text-label text-ink-soft tabular-nums">
        <span
          v-for="tick in hours"
          :key="tick.at"
          class="absolute top-0 -translate-x-1/2 whitespace-nowrap"
          :class="{ 'text-ink': tick.midnight }"
          data-testid="radio-day-hour"
          :style="{ left: at(tick.at) }"
        >
          {{ clock(tick.at, 0).slice(0, 2) }}
        </span>
        <span
          v-if="current"
          class="absolute top-0 font-semibold whitespace-nowrap text-ink"
          :class="align"
          data-testid="radio-day-clock"
          :style="{ left: at(radioDay.now) }"
        >
          Now {{ clock(radioDay.now, 0) }}
        </span>
      </div>
    </div>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import {
  type PlannedSpan,
  type RadioDay,
  type RecordedSpan,
  Station,
} from '../../models/generated/domain-models'
import { clock, plannedSummary, range, recordedSummary, STATIONS } from '../../utils/radio'
import RadioCard from './radio-card.vue'

const LIVE =
  'bg-[repeating-linear-gradient(135deg,var(--color-notice)_0_3px,var(--color-notice-wash)_3px_6px)]'
const CROWDED = 5400

type Span = Pick<PlannedSpan, 'start' | 'end'>

const props = defineProps<{ radioDay: RadioDay; station: Station | null }>()

const fraction = (seconds: number) =>
  (seconds - props.radioDay.from) / (props.radioDay.until - props.radioDay.from)

const share = (seconds: number, unit = '%') =>
  `${(seconds / (props.radioDay.until - props.radioDay.from)) * 100}${unit}`

const at = (seconds: number) => `${fraction(seconds) * 100}%`

const place = ({ start, end }: Span) => ({ left: at(start), width: share(end - start) })

const tracks = computed(() =>
  (props.station ? [props.station] : Object.values(Station)).map(station => {
    const recorded = props.radioDay.recorded.filter(span => span.station === station)
    const planned = props.radioDay.planned.filter(span => span.station === station)
    return { station, recorded, planned, starts: [...recorded, ...planned].map(s => s.start) }
  })
)

const room = ({ start, end }: Span, starts: number[]) => {
  const next = Math.min(...starts.filter(other => other > start), props.radioDay.until)
  return `min(${share(next - start, 'cqw')}, max(${share(end - start, 'cqw')}, 9rem))`
}

const times = (span: RecordedSpan) =>
  span.recording
    ? `${clock(span.start, 0)}–, recording now`
    : range(span.start, 0, span.end - span.start)

const current = computed(
  () => props.radioDay.now >= props.radioDay.from && props.radioDay.now < props.radioDay.until
)

const hours = computed(() =>
  props.radioDay.ticks.filter(
    tick => !current.value || Math.abs(tick.at - props.radioDay.now) >= CROWDED
  )
)

const align = computed(() => {
  const now = fraction(props.radioDay.now)
  if (now < 0.05) return 'translate-x-0'
  return now > 0.95 ? '-translate-x-full' : '-translate-x-1/2'
})
</script>
