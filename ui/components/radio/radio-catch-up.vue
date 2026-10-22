<template>
  <div>
    <radio-tabs />
    <div class="grid gap-6">
      <nav v-if="radioDay" class="grid w-fit grid-cols-7 gap-1.5 max-md:hidden" aria-label="Day">
        <button
          v-for="d in radioDay.days"
          :key="d"
          type="button"
          class="relative grid w-13 cursor-pointer justify-items-center rounded-container border pt-1.5 pb-2.5 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brass-deep"
          :class="[
            d === selected
              ? 'border-brass bg-brass-wash text-brass-deep inset-ring inset-ring-brass'
              : 'border-transparent bg-surface-sunken hover:border-hairline-strong hover:bg-surface-hover',
            { [TODAY]: d === radioDay.today },
          ]"
          :aria-pressed="d === selected"
          :aria-current="d === radioDay.today ? 'date' : undefined"
          :aria-label="dateDay(d)"
          @click="date = d"
        >
          <span class="text-sm" :class="{ 'text-ink-soft': d !== selected }">
            {{ dateDay(d).slice(0, 2) }}
          </span>
          <span class="text-xl leading-tight font-bold tabular-nums">{{ Number(d.slice(8)) }}</span>
        </button>
      </nav>
      <p v-if="isError" class="mb-0 text-loss-deep">Could not load the schedule.</p>
      <p v-else-if="isLoading" class="mb-0 text-ink-soft">Loading…</p>
      <template v-if="radioDay">
        <radio-day-bar :radio-day="radioDay" />
        <radio-schedule :day="radioDay" />
        <radio-schedule v-if="phone && next && next.date !== radioDay.date" :day="next" />
        <p class="-mt-3 mb-0 text-sm text-ink-soft">
          Repeats are not recorded when the original is archived. If the original was missed or
          recorded only in part, you can record the repeat.
        </p>
      </template>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { useRadioDay } from '../../composables/use-radio-recordings'
import { dateDay } from '../../utils/radio'
import RadioDayBar from './radio-day-bar.vue'
import RadioSchedule from './radio-schedule.vue'
import RadioTabs from './radio-tabs.vue'

const TODAY =
  "after:absolute after:bottom-1 after:size-1 after:rounded-full after:bg-brass after:content-['']"

const route = useRoute()
const router = useRouter()
const date = computed({
  get: () => (typeof route.query.day === 'string' ? route.query.day : null),
  set: (day: string) => router.replace({ query: { day } }),
})
const { data: radioDay, isLoading, isError } = useRadioDay(date)
const phone = useMediaQuery('(max-width: 767px)')

const selected = computed(() => date.value ?? radioDay.value?.date)

const tomorrow = computed(() =>
  radioDay.value
    ? new Date(Date.parse(`${radioDay.value.today}T12:00:00Z`) + 86_400_000)
        .toISOString()
        .slice(0, 10)
    : null
)

const { data: next } = useRadioDay(() => (phone.value ? tomorrow.value : null))
</script>
