<template>
  <aside
    class="sticky top-[calc(60.5px+1rem)] max-h-[calc(100dvh-60.5px-2rem)] overflow-y-auto rounded-container border border-hairline bg-surface"
    :aria-label="film.title"
    data-testid="poff-panel"
  >
    <div class="relative">
      <poff-film-media :key="film.id" :film="film" compact />
      <button
        ref="close"
        type="button"
        class="absolute top-3 right-3 z-1 flex size-9 cursor-pointer items-center justify-center rounded-full bg-surface text-ink shadow-control"
        aria-label="Close the film"
        @click="emit('close')"
      >
        <radio-icon name="close" class="size-4" />
      </button>
    </div>
    <div class="flex flex-col gap-4 p-4">
      <poff-film-summary :film="film" compact />
      <p v-if="film.synopsis.length" class="mb-0 leading-relaxed">{{ film.synopsis[0] }}</p>
      <poff-film-screenings :film="film" :now="now" />
      <router-link
        :to="{ name: 'PoffFilm', params: { slug: film.slug } }"
        :class="[OUTLINED, 'flex min-h-11 items-center justify-center gap-1']"
      >
        Open film page
        <radio-icon name="left" class="size-4 rotate-180" />
      </router-link>
    </div>
  </aside>
</template>

<script lang="ts" setup>
import { useTemplateRef } from 'vue'
import type { PoffFilm } from '../../models/generated/domain-models'
import { OUTLINED } from '../../utils/poff'
import RadioIcon from '../radio/radio-icon.vue'
import PoffFilmMedia from './poff-film-media.vue'
import PoffFilmScreenings from './poff-film-screenings.vue'
import PoffFilmSummary from './poff-film-summary.vue'

defineProps<{ film: PoffFilm; now: Date }>()
const emit = defineEmits<{ close: [] }>()

const close = useTemplateRef<HTMLButtonElement>('close')
defineExpose({ focus: () => close.value?.focus() })
</script>
