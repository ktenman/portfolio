<template>
  <div>
    <radio-tabs />
    <div class="grid gap-6">
      <nav
        v-if="radioDay"
        class="flex w-fit gap-1 rounded-container bg-surface-sunken p-1"
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
        <section>
          <div class="mb-3 flex flex-wrap items-baseline justify-between gap-2">
            <h3 class="mb-0 text-heading">Saatekava</h3>
            <span class="text-sm text-ink-soft">{{ fullDay(radioDay.from) }}</span>
          </div>
          <p v-if="!radioDay.day.length && !radioDay.night.length" class="text-ink-soft">
            Selle päeva kava pole veel laaditud.
          </p>
          <div
            v-else
            class="divide-y divide-hairline overflow-hidden rounded-container border border-hairline bg-surface"
          >
            <template v-for="section in sections" :key="section.key">
              <p
                v-if="section.divider"
                class="mb-0 bg-surface-sunken px-3 py-2 text-sm text-ink-soft md:px-4"
              >
                {{ section.divider }}
              </p>
              <template v-for="block in section.blocks" :key="block.airings[0].id">
                <button
                  v-if="block.missed"
                  type="button"
                  class="flex w-full cursor-pointer items-center gap-2 bg-surface-sunken px-3 py-2.5 text-left text-sm text-ink-soft hover:text-ink md:px-4"
                  :aria-expanded="open.has(block.airings[0].id)"
                  @click="toggle(block.airings[0].id)"
                >
                  <radio-icon
                    name="down"
                    class="transition-transform"
                    :class="{ 'rotate-180': open.has(block.airings[0].id) }"
                  />
                  {{ missedRun(block.airings) }}
                </button>
                <template v-if="!block.missed || open.has(block.airings[0].id)">
                  <radio-airing-row v-for="a in block.airings" :key="a.id" :airing="a" />
                </template>
              </template>
            </template>
          </div>
          <p class="mt-3 mb-0 text-sm text-ink-soft">
            Kordust ei salvestata, kui originaal on arhiivis. Kui originaal jäi vahele või salvestus
            on osaline, saab salvestada korduse.
          </p>
        </section>
      </template>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useRadioDay } from '../../composables/use-radio-recordings'
import { dateDay, fullDay, missedRun, nightOf } from '../../utils/radio'
import RadioAiringRow from './radio-airing-row.vue'
import RadioDayBar from './radio-day-bar.vue'
import RadioIcon from './radio-icon.vue'
import RadioTabs from './radio-tabs.vue'

const date = ref<string | null>(null)
const open = ref(new Set<number>())
const { data: radioDay, isLoading, isError } = useRadioDay(date)

const selected = computed(() => date.value ?? radioDay.value?.date)

const sections = computed(() => {
  const d = radioDay.value
  if (!d) return []
  return [
    { key: 'day', divider: null, blocks: d.day },
    { key: 'night', divider: d.night.length ? nightOf(d.until) : null, blocks: d.night },
  ]
})

const toggle = (id: number) => {
  if (!open.value.delete(id)) open.value.add(id)
}
</script>
