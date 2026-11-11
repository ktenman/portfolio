<template>
  <poff-load-error v-if="!data && isError" :error="error" :refetch="refetch" />
  <div v-else-if="!data" aria-busy="true" aria-label="Loading the film">
    <span class="skeleton block h-4 w-36" />
    <span class="skeleton mt-6 block h-9 w-2/3" />
    <span class="skeleton mt-3 block h-4 w-1/2" />
    <span class="skeleton mt-8 block aspect-video max-w-3xl rounded-container" />
  </div>
  <div v-else-if="!film">
    <poff-back-link />
    <p class="mt-4 text-ink-soft">This film is not in the PÖFF programme</p>
  </div>
  <article
    v-else
    class="flex flex-col gap-8 not-md-down:grid not-md-down:grid-cols-[minmax(0,1fr)_25rem] not-md-down:items-start not-md-down:gap-12"
  >
    <div class="flex flex-col gap-8 md-down:contents">
      <header>
        <poff-back-link />
        <poff-film-summary :film="film" class="mt-5" />
      </header>
      <poff-film-media :key="film.id" :film="film" class="md-down:order-3" />
      <section v-if="film.synopsis.length" class="max-w-[65ch] md-down:order-4">
        <h3 :class="HEADING">About the film</h3>
        <p class="text-lg leading-relaxed font-semibold">{{ film.synopsis[0] }}</p>
        <p v-for="(paragraph, i) in film.synopsis.slice(1)" :key="i" class="leading-relaxed">
          {{ paragraph }}
        </p>
      </section>
    </div>
    <div class="flex flex-col gap-8 md-down:contents">
      <poff-film-screenings :film="film" :now="now" class="md-down:order-2" />
      <poff-film-facts :film="film" class="md-down:order-5" />
    </div>
  </article>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useIntervalFn, useNow } from '@vueuse/core'
import { usePoffCatalogue } from '../../composables/use-poff-catalogue'
import { HEADING } from '../../utils/poff'
import PoffBackLink from './poff-back-link.vue'
import PoffFilmFacts from './poff-film-facts.vue'
import PoffFilmMedia from './poff-film-media.vue'
import PoffFilmScreenings from './poff-film-screenings.vue'
import PoffFilmSummary from './poff-film-summary.vue'
import PoffLoadError from './poff-load-error.vue'

const route = useRoute()
const { data, isError, error, refetch } = usePoffCatalogue()
const now = useNow({ scheduler: tick => useIntervalFn(tick, 60_000) })

const film = computed(() => data.value?.films.find(f => f.slug === route.params.slug))
</script>
