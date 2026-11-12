<template>
  <span
    class="tabular-nums"
    :class="[
      strong ? 'font-semibold text-ink' : 'text-ink-soft',
      {
        'md-down:flex md-down:min-w-7 md-down:flex-col md-down:items-end md-down:text-sm': stacked,
        'md-down:font-[450]': stacked && !strong,
      },
    ]"
    :title="ratingName(imdb)"
  >
    <span aria-hidden="true" :class="{ 'md-down:hidden': stacked && imdb?.rating == null }">
      {{ ratingLabel(imdb) }}
    </span>
    <span
      v-if="stacked && imdb?.rating != null"
      class="text-[10px] leading-3 font-semibold not-md-down:hidden"
      :class="strong ? 'text-brass-deep' : 'text-ink-soft'"
      aria-hidden="true"
      data-testid="poff-imdb"
    >
      IMDb
    </span>
    <span class="sr-only">{{ ratingName(imdb) }}</span>
  </span>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { ImdbRating } from '../../models/generated/domain-models'
import { ratingLabel, ratingName, strongRating } from '../../utils/poff'

const props = defineProps<{ imdb?: ImdbRating | null; stacked?: boolean }>()
const strong = computed(() => strongRating(props.imdb))
</script>
