<template>
  <span
    class="tabular-nums"
    :class="strongRating(imdb) ? 'font-semibold text-ink' : 'text-ink-soft'"
    :title="ratingName(imdb)"
  >
    <span aria-hidden="true">{{ ratingLabel(imdb) }}</span>
    <span class="sr-only">{{ ratingName(imdb) }}</span>
  </span>
</template>

<script lang="ts" setup>
import type { ImdbRating } from '../../models/generated/domain-models'
import { ratingLabel, ratingName, strongRating } from '../../utils/poff'

defineProps<{ imdb?: ImdbRating | null }>()
</script>
