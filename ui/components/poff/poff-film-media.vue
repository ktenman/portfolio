<template>
  <section v-if="film.trailer || film.stills.length">
    <h3 :class="HEADING">{{ film.trailer ? 'Trailer and stills' : 'Stills' }}</h3>
    <div
      v-if="film.trailer"
      class="relative aspect-video overflow-hidden rounded-container bg-surface-sunken"
    >
      <iframe
        v-if="playing"
        :src="`https://www.youtube-nocookie.com/embed/${film.trailer}?autoplay=1`"
        :title="`Trailer of ${film.title}`"
        allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
        allowfullscreen
        referrerpolicy="strict-origin-when-cross-origin"
        class="size-full"
      />
      <button
        v-else
        type="button"
        class="group size-full cursor-pointer"
        :aria-label="`Play the trailer of ${film.title}`"
        @click="playing = true"
      >
        <img
          v-if="film.poster"
          :src="poffService.stillUrl(film.poster, 1280)"
          :srcset="`${poffService.stillUrl(film.poster, 640)} 640w, ${poffService.stillUrl(film.poster, 1280)} 1280w`"
          sizes="(min-width: 768px) 60vw, 100vw"
          alt=""
          class="size-full object-cover"
        />
        <span
          class="absolute top-1/2 left-1/2 flex size-16 -translate-1/2 items-center justify-center rounded-full bg-brass text-white shadow-control transition-colors group-hover:bg-brass-deep"
        >
          <radio-icon name="play" class="size-7" />
        </span>
        <span
          class="absolute bottom-3 left-3 font-semibold text-white [text-shadow:0_1px_3px_#000]"
        >
          Trailer
        </span>
      </button>
    </div>
    <ul
      v-if="film.stills.length"
      class="mt-2 flex gap-2 overflow-x-auto md-down:-mr-3 md-down:pr-3 md-down:[scrollbar-width:none]"
    >
      <li
        v-for="(still, i) in film.stills"
        :key="still"
        class="aspect-[3/2] w-[calc((100%-2rem)/5)] shrink-0 md-down:w-[38%]"
      >
        <button
          type="button"
          class="size-full cursor-pointer overflow-hidden rounded-control bg-surface-sunken"
          :aria-label="`Still ${i + 1} of ${film.stills.length} from ${film.title}`"
          @click="show(still)"
        >
          <img
            :src="poffService.stillUrl(still, 640)"
            alt=""
            loading="lazy"
            class="size-full object-cover"
          />
        </button>
      </li>
    </ul>
    <dialog
      ref="lightbox"
      :aria-label="`Still from ${film.title}`"
      class="m-auto max-h-none max-w-none bg-transparent p-4 backdrop:bg-[rgb(0_0_0/0.85)]"
      @click.self="lightbox?.close()"
      @close="open = null"
    >
      <template v-if="open">
        <img :src="poffService.stillUrl(open, 1280)" alt="" class="max-h-[90vh] max-w-full" />
        <button
          type="button"
          class="fixed top-3 right-3 flex size-11 cursor-pointer items-center justify-center rounded-full bg-black/60 text-white"
          aria-label="Close the still"
          @click="lightbox?.close()"
        >
          <radio-icon name="close" class="size-5" />
        </button>
      </template>
    </dialog>
  </section>
</template>

<script lang="ts" setup>
import { ref, useTemplateRef } from 'vue'
import type { PoffFilm } from '../../models/generated/domain-models'
import { poffService } from '../../services/api'
import { HEADING } from '../../utils/poff'
import RadioIcon from '../radio/radio-icon.vue'

defineProps<{ film: PoffFilm }>()

const playing = ref(false)
const open = ref<string | null>(null)
const lightbox = useTemplateRef<HTMLDialogElement>('lightbox')
const show = (still: string) => {
  open.value = still
  lightbox.value?.showModal()
}
</script>
