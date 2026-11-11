<template>
  <li class="relative min-w-0" data-testid="poff-film">
    <div class="relative aspect-[3/2] overflow-hidden rounded-container bg-surface-sunken">
      <img
        v-if="still"
        :src="poffService.stillUrl(still, WIDTHS[0])"
        :srcset="WIDTHS.map(width => `${poffService.stillUrl(still, width)} ${width}w`).join(', ')"
        sizes="(min-width: 768px) 25vw, 50vw"
        alt=""
        loading="lazy"
        class="size-full object-cover"
      />
      <span
        v-if="film.imdb?.rating != null"
        class="absolute top-2 left-2 rounded-control bg-surface px-2 py-0.5 text-sm tabular-nums shadow-control"
        :class="strongRating(film.imdb) ? 'font-semibold text-ink' : 'text-ink-soft'"
        :title="ratingName(film.imdb)"
        data-testid="poff-pill"
      >
        <span aria-hidden="true">IMDb {{ ratingLabel(film.imdb) }}</span>
        <span class="sr-only">{{ ratingName(film.imdb) }}</span>
      </span>
    </div>
    <router-link
      :to="{ name: 'PoffFilm', params: { slug: film.slug } }"
      class="mt-2.5 block leading-snug font-semibold after:absolute after:inset-0 hover:underline focus-visible:underline"
      data-testid="poff-film-title"
    >
      {{ film.title }}
    </router-link>
    <p v-if="filmLine(film)" class="mb-0 text-sm text-ink-soft">{{ filmLine(film) }}</p>
    <radio-badge v-if="soldOut(film, now)" tone="error" class="mt-1">Sold out</radio-badge>
    <p v-else class="mt-1 mb-0 text-sm">{{ nextLine(film, now) }}</p>
  </li>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { PoffFilm } from '../../models/generated/domain-models'
import { poffService } from '../../services/api'
import { filmLine, ratingLabel, ratingName, strongRating } from '../../utils/poff'
import { nextLine, soldOut } from '../../utils/poff-films'
import RadioBadge from '../radio/radio-badge.vue'

const props = defineProps<{ film: PoffFilm; now: Date }>()

const WIDTHS = [640, 1280] as const
const still = computed(() => props.film.stills[0])
</script>
