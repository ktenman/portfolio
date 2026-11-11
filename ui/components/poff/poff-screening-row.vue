<template>
  <tr
    class="border-t border-hairline align-top md-down:grid md-down:grid-cols-[3.5rem_minmax(0,1fr)_auto] md-down:gap-x-3 md-down:px-4 md-down:py-3"
    :class="{
      'cursor-pointer hover:bg-surface-hover': panel,
      'bg-brass-wash outline -outline-offset-1 outline-brass hover:bg-brass-wash': selected,
    }"
    :aria-current="selected || undefined"
    :data-film="film.slug"
    data-testid="poff-row"
  >
    <td class="py-3 pl-4 whitespace-nowrap tabular-nums md-down:p-0">
      <span class="font-semibold">{{ clock(screening.start) }}</span>
      <span class="text-sm text-ink-soft md-down:block">–{{ clock(screening.end) }}</span>
    </td>
    <td class="py-3 pr-4 md-down:p-0">
      <span class="flex flex-wrap items-center gap-x-2 gap-y-1">
        <router-link
          v-slot="{ href, navigate }"
          :to="{ name: 'PoffFilm', params: { slug: film.slug } }"
          custom
        >
          <a
            :href="href"
            class="relative font-semibold hover:underline focus-visible:underline md-down:before:absolute md-down:before:top-1/2 md-down:before:left-0 md-down:before:h-11 md-down:before:w-full md-down:before:min-w-11 md-down:before:-translate-y-1/2"
            data-testid="poff-title"
            @click="follow($event, navigate)"
          >
            {{ film.title }}
          </a>
        </router-link>
        <radio-badge v-if="badge" class="md-down:hidden">{{ badge }}</radio-badge>
      </span>
      <span v-if="filmLine(film)" class="block text-sm text-ink-soft">{{ filmLine(film) }}</span>
      <span class="block text-sm text-ink-soft not-md-down:hidden">
        {{ place }}
        <template v-if="screening.qa">· Q&amp;A</template>
      </span>
      <span
        v-if="screening.soldOut || badge"
        class="mt-1 flex flex-wrap gap-1.5 not-md-down:hidden"
      >
        <radio-badge v-if="screening.soldOut" tone="error">Sold out</radio-badge>
        <radio-badge v-if="badge">{{ badge }}</radio-badge>
      </span>
    </td>
    <td class="py-3 pr-4 md-down:hidden">
      {{ place }}
      <span v-if="screening.qa" class="block text-sm text-ink-soft">
        Q&amp;A
        <template v-if="screening.qaMinutes">· {{ screening.qaMinutes }} min</template>
      </span>
    </td>
    <td class="py-3 pr-4 text-right md-down:p-0">
      <poff-rating :imdb="film.imdb" />
    </td>
    <td class="py-2.5 pr-4 text-right md-down:hidden">
      <radio-badge v-if="screening.soldOut" tone="error">Sold out</radio-badge>
      <a
        v-else
        :href="ticketUrl(film, screening)"
        target="_blank"
        rel="noopener"
        :class="[
          OUTLINED,
          'inline-flex items-center gap-1 px-2.5 py-1 text-sm whitespace-nowrap focus-visible:outline-2 focus-visible:outline-brass-deep',
        ]"
        :aria-label="`Tickets for ${film.title} at ${clock(screening.start)}`"
      >
        Tickets
        <radio-icon name="open" class="size-3.5" />
      </a>
    </td>
  </tr>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { PoffFilm, PoffScreening } from '../../models/generated/domain-models'
import { OUTLINED, clock, filmLine, premiere, shortCinema, ticketUrl } from '../../utils/poff'
import RadioBadge from '../radio/radio-badge.vue'
import RadioIcon from '../radio/radio-icon.vue'
import PoffRating from './poff-rating.vue'

const props = defineProps<{
  film: PoffFilm
  screening: PoffScreening
  panel?: boolean
  selected?: boolean
}>()

const follow = (event: MouseEvent, navigate: (event: MouseEvent) => unknown) => {
  if (!props.panel || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey)
    return navigate(event)
  event.preventDefault()
}

const badge = computed(() => premiere(props.film))
const place = computed(() =>
  [shortCinema(props.screening.cinema), props.screening.hall].filter(Boolean).join(' · ')
)
</script>
