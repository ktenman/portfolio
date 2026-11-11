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
        <h2 class="mt-5 mb-1 text-4xl leading-tight font-semibold">{{ film.title }}</h2>
        <p v-if="titles(film)" class="mb-1 text-lg text-ink-soft">{{ titles(film) }}</p>
        <p class="mb-3">{{ facts(film) }}</p>
        <p class="mb-3 flex flex-wrap items-baseline gap-x-3" data-testid="poff-film-rating">
          <template v-if="film.imdb?.rating != null">
            <poff-rating :imdb="film.imdb" class="text-3xl" />
            <span class="text-sm text-ink-soft" aria-hidden="true">
              IMDb
              <template v-if="film.imdb.votes != null">
                · {{ film.imdb.votes.toLocaleString('en-US') }} votes
              </template>
            </span>
          </template>
          <span v-else class="text-ink-soft">No IMDb rating yet</span>
          <a
            v-if="film.imdb"
            :href="imdbUrl(film.imdb)"
            target="_blank"
            rel="noopener"
            class="inline-flex min-h-11 items-center gap-1 text-sm underline md-down:ml-auto"
          >
            IMDb page
            <radio-icon name="open" class="size-3.5" />
          </a>
        </p>
        <div class="flex flex-wrap gap-1.5">
          <router-link
            v-for="chip in chips"
            :key="chip.label"
            :to="{ path: '/poff/films', query: chip.query }"
            :class="CHIP"
          >
            {{ chip.label }}
          </router-link>
        </div>
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
import { CHIP, HEADING } from '../../utils/poff'
import { facts, imdbUrl, titles } from '../../utils/poff-film'
import RadioIcon from '../radio/radio-icon.vue'
import PoffBackLink from './poff-back-link.vue'
import PoffFilmFacts from './poff-film-facts.vue'
import PoffFilmMedia from './poff-film-media.vue'
import PoffFilmScreenings from './poff-film-screenings.vue'
import PoffLoadError from './poff-load-error.vue'
import PoffRating from './poff-rating.vue'

const route = useRoute()
const { data, isError, error, refetch } = usePoffCatalogue()
const now = useNow({ scheduler: tick => useIntervalFn(tick, 60_000) })

const film = computed(() => data.value?.films.find(f => f.slug === route.params.slug))
const chips = computed(() => [
  ...(film.value?.genres ?? []).map(genre => ({ label: genre, query: { genre } })),
  ...(film.value?.programmes ?? []).map(programme => ({ label: programme, query: { programme } })),
])
</script>
