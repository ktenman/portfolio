<template>
  <div>
    <h2 class="mb-1 leading-tight font-semibold" :class="compact ? 'text-xl' : 'text-4xl'">
      {{ film.title }}
    </h2>
    <p v-if="titles(film)" class="mb-1 text-ink-soft" :class="{ 'text-lg': !compact }">
      {{ titles(film) }}
    </p>
    <p class="mb-3" :class="{ 'text-sm': compact }">{{ facts(film) }}</p>
    <p class="mb-3 flex flex-wrap items-baseline gap-x-3" data-testid="poff-film-rating">
      <template v-if="film.imdb?.rating != null">
        <poff-rating :imdb="film.imdb" :class="compact ? 'text-2xl' : 'text-3xl'" />
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
        :class="{ 'ml-auto': compact }"
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
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { PoffFilm } from '../../models/generated/domain-models'
import { CHIP } from '../../utils/poff'
import { facts, imdbUrl, titles } from '../../utils/poff-film'
import RadioIcon from '../radio/radio-icon.vue'
import PoffRating from './poff-rating.vue'

const props = defineProps<{ film: PoffFilm; compact?: boolean }>()

const chips = computed(() => [
  ...props.film.genres.map(genre => ({ label: genre, query: { genre } })),
  ...props.film.programmes.map(programme => ({ label: programme, query: { programme } })),
])
</script>
