<template>
  <div>
    <radio-tabs />
    <div class="grid gap-6">
      <nav
        v-if="radioDay"
        class="flex w-fit gap-1 rounded-container bg-surface-sunken p-1 max-md:hidden"
        aria-label="Päev"
      >
        <button
          v-for="d in radioDay.days"
          :key="d"
          type="button"
          class="platform-btn"
          :class="{ active: d === selected }"
          :aria-pressed="d === selected"
          @click="date = d"
        >
          {{ dateDay(d) }}{{ d === radioDay.today ? ' · täna' : '' }}
        </button>
      </nav>
      <p v-if="isError" class="mb-0 text-loss-deep">Saatekava ei õnnestunud laadida.</p>
      <p v-else-if="isLoading" class="mb-0 text-ink-soft">Laadin…</p>
      <template v-if="radioDay">
        <radio-day-bar :radio-day="radioDay" />
        <radio-schedule :day="radioDay" />
        <radio-schedule v-if="phone && next && next.date !== radioDay.date" :day="next" />
        <p class="-mt-3 mb-0 text-sm text-ink-soft">
          Kordust ei salvestata, kui originaal on arhiivis. Kui originaal jäi vahele või salvestus
          on osaline, saab salvestada korduse.
        </p>
      </template>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useRadioDay } from '../../composables/use-radio-recordings'
import { dateDay } from '../../utils/radio'
import RadioDayBar from './radio-day-bar.vue'
import RadioSchedule from './radio-schedule.vue'
import RadioTabs from './radio-tabs.vue'

const date = ref<string | null>(null)
const { data: radioDay, isLoading, isError } = useRadioDay(date)
const phone = useMediaQuery('(max-width: 767px)')

const selected = computed(() => date.value ?? radioDay.value?.date)

const tomorrow = computed(() => radioDay.value?.days.find(d => d > radioDay.value!.today) ?? null)

const { data: next } = useRadioDay(() => (phone.value ? tomorrow.value : null))
</script>
