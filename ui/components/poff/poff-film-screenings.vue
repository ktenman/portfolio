<template>
  <section>
    <h3 :class="HEADING">Screenings</h3>
    <p v-if="!rows.length" class="text-ink-soft">No screenings left</p>
    <ul v-else class="rounded-container border border-hairline bg-surface">
      <li
        v-for="row in rows"
        :key="row.screening.id"
        class="flex items-center gap-4 border-hairline p-3 not-first:border-t"
        data-testid="poff-film-screening"
      >
        <span
          class="flex w-12 shrink-0 flex-col items-center rounded-control bg-surface-sunken py-1.5 leading-tight"
        >
          <span class="text-xs text-ink-soft">{{ row.weekday }}</span>
          <span class="text-xl font-semibold tabular-nums">{{ row.day }}</span>
        </span>
        <span class="min-w-0 flex-1">
          <span class="flex flex-wrap items-center gap-x-2">
            <span class="font-semibold tabular-nums">{{ row.range }}</span>
            <radio-badge v-if="row.tonight" tone="live">Tonight</radio-badge>
          </span>
          <span class="block text-sm text-ink-soft">
            {{ row.place }}
            <template v-if="row.screening.qa">· Q&amp;A</template>
          </span>
        </span>
        <radio-badge v-if="row.screening.soldOut" tone="error">Sold out</radio-badge>
        <a
          v-else
          :href="ticketUrl(film, row.screening)"
          target="_blank"
          rel="noopener"
          class="inline-flex min-h-11 shrink-0 items-center gap-1 rounded-control border px-3.5 font-medium whitespace-nowrap shadow-control transition-colors focus-visible:outline-2 focus-visible:outline-brass-deep"
          :class="row.next ? 'border-brass bg-brass text-white hover:bg-brass-deep' : OUTLINED"
          :aria-label="`Tickets for ${film.title} on ${row.weekday} ${row.day} at ${clock(row.screening.start)}`"
        >
          Tickets
          <radio-icon name="open" class="size-3.5" />
        </a>
      </li>
    </ul>
  </section>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { PoffFilm } from '../../models/generated/domain-models'
import { HEADING, OUTLINED, clock, ticketUrl } from '../../utils/poff'
import { screeningRows } from '../../utils/poff-film'
import RadioBadge from '../radio/radio-badge.vue'
import RadioIcon from '../radio/radio-icon.vue'

const props = defineProps<{ film: PoffFilm; now: Date }>()

const rows = computed(() => screeningRows(props.film, props.now))
</script>
