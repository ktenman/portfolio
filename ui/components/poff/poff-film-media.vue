<template>
  <section v-if="film.trailer || film.stills.length">
    <h3 v-if="!compact" :class="HEADING">{{ film.trailer ? 'Trailer and stills' : 'Stills' }}</h3>
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
          :sizes="compact ? '26rem' : '(min-width: 768px) 60vw, 100vw'"
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
      :class="{ 'px-4': compact }"
    >
      <li
        v-for="(still, i) in film.stills"
        :key="still"
        class="aspect-[3/2] w-[calc((100%-2rem)/5)] shrink-0 md-down:w-[38%]"
      >
        <button
          ref="thumbs"
          type="button"
          class="size-full cursor-pointer overflow-hidden rounded-control bg-surface-sunken"
          :aria-label="label(i)"
          @click="show(i)"
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
      :aria-label="open === null ? undefined : label(open)"
      class="m-0 size-full max-h-none max-w-none items-center justify-center bg-[rgb(0_0_0)] p-0 outline-none open:flex"
      @click.self="lightbox?.close()"
      @close="close"
    >
      <template v-if="open !== null">
        <div ref="still" class="relative size-full touch-none">
          <img
            v-for="width in [640, 1280]"
            :key="`${open}-${width}`"
            :src="poffService.stillUrl(film.stills[open]!, width)"
            alt=""
            draggable="false"
            class="absolute inset-0 size-full object-contain"
          />
        </div>
        <p
          v-if="film.stills.length > 1"
          role="status"
          class="absolute inset-x-0 bottom-4 text-center text-sm text-white"
        >
          {{ open + 1 }} / {{ film.stills.length }}
        </p>
        <button
          v-if="open > 0"
          type="button"
          :class="[ROUND, 'top-1/2 left-3 -translate-y-1/2']"
          aria-label="Previous still"
          @click="step(-1)"
        >
          <radio-icon name="left" class="size-5" />
        </button>
        <button
          v-if="open < film.stills.length - 1"
          type="button"
          :class="[ROUND, 'top-1/2 right-3 -translate-y-1/2']"
          aria-label="Next still"
          @click="step(1)"
        >
          <radio-icon name="left" class="size-5 rotate-180" />
        </button>
        <button
          type="button"
          :class="[ROUND, 'top-3 right-3']"
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
import { ref, useTemplateRef, watch } from 'vue'
import { onKeyStroke, usePointerSwipe, useScrollLock } from '@vueuse/core'
import type { PoffFilm } from '../../models/generated/domain-models'
import { poffService } from '../../services/api'
import { HEADING } from '../../utils/poff'
import RadioIcon from '../radio/radio-icon.vue'

const props = defineProps<{ film: PoffFilm; compact?: boolean }>()

const ROUND =
  'fixed flex size-11 cursor-pointer items-center justify-center rounded-full bg-[rgb(0_0_0/0.6)] text-white'

const playing = ref(false)
const open = ref<number | null>(null)
const lightbox = useTemplateRef<HTMLDialogElement>('lightbox')
const thumbs = useTemplateRef<HTMLButtonElement[]>('thumbs')

const label = (i: number) =>
  `Still ${i + 1} of ${props.film.stills.length} from ${props.film.title}`

const locked = useScrollLock(document.body)

const show = (i: number) => {
  open.value = i
  locked.value = true
  lightbox.value?.showModal()
}

const step = (by: number) => {
  if (open.value !== null && props.film.stills[open.value + by]) open.value += by
}

usePointerSwipe(useTemplateRef<HTMLElement>('still'), {
  onSwipeEnd: (_, direction) => {
    if (direction === 'left') step(1)
    if (direction === 'right') step(-1)
  },
})

watch(open, i => {
  if (i === null) return
  for (const still of [props.film.stills[i - 1], props.film.stills[i + 1]])
    if (still) new Image().src = poffService.stillUrl(still, 1280)
})

onKeyStroke('ArrowLeft', () => step(-1))
onKeyStroke('ArrowRight', () => step(1))

const close = () => {
  if (open.value !== null) thumbs.value?.[open.value]?.focus()
  open.value = null
  locked.value = false
}
</script>
