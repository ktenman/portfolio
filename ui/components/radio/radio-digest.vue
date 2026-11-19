<template>
  <section v-if="latest" class="card relative overflow-hidden" data-testid="radio-digest">
    <div class="flex items-center gap-3.5 px-5 py-4">
      <button
        type="button"
        :class="[loaded ? LOADED : LISTEN, 'relative z-10 size-11 [&>svg]:size-5']"
        :aria-label="`${on ? 'Pause' : 'Play'}: ${latest.recording.programme}`"
        @click="play(latest.recording)"
      >
        <radio-ring v-if="loaded" />
        <radio-icon :name="on ? 'pause' : 'play'" />
      </button>
      <div class="grid min-w-0 grow gap-0.5">
        <h3 class="mb-0 text-heading">
          <router-link :to="`/radio/${latest.recording.name}`" :class="STRETCHED">
            {{ latest.recording.programme }}
          </router-link>
        </h3>
        <p class="mb-0 text-sm text-ink-soft" data-testid="radio-digest-meta">
          <template v-for="(part, index) in meta(latest)" :key="part">
            <template v-if="index">{{ '\u00a0· ' }}</template>
            <span class="whitespace-nowrap">{{ part }}</span>
          </template>
        </p>
      </div>
      <button
        type="button"
        :class="[ROUND, 'relative z-10 size-11']"
        :aria-expanded="open"
        :aria-label="`Stories: ${latest.recording.programme}`"
        @click="open = !open"
      >
        <radio-icon name="down" :class="{ 'rotate-180': open }" />
      </button>
    </div>
    <ol v-if="open" class="mb-0 list-none p-0">
      <li
        v-for="(story, index) in latest.stories"
        :key="story.start"
        class="flex items-baseline gap-4 border-t border-hairline px-5 py-3"
        data-testid="radio-digest-story"
      >
        <span class="w-5 shrink-0 text-sm text-ink-soft tabular-nums">{{ index + 1 }}</span>
        <span class="flex min-w-0 grow items-baseline gap-x-4 max-md:flex-col">
          <span class="min-w-0 grow">{{ story.title }}</span>
          <span class="shrink-0 text-sm text-ink-soft">{{ told(story) }}</span>
        </span>
      </li>
    </ol>
    <radio-digest-rebuild v-if="open" :show="show" class="relative z-10" />
  </section>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { STORAGE_KEYS } from '../../constants'
import type {
  DigestShow,
  DigestStory,
  RadioDigest,
  RecordingSummary,
} from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioDigests } from '../../composables/use-radio-recordings'
import { clock, day, LISTEN, LOADED, minutes, ROUND } from '../../utils/radio'
import RadioDigestRebuild from './radio-digest-rebuild.vue'
import RadioIcon from './radio-icon.vue'
import RadioRing from './radio-ring.vue'

const STRETCHED =
  'after:absolute after:inset-0 after:rounded-container underline-offset-2 hover:underline'

const props = defineProps<{ show: DigestShow }>()

const { data } = useRadioDigests()
const player = useRadioPlayer()
const open = useLocalStorage(`${STORAGE_KEYS.RADIO_DIGEST_OPEN}_${props.show.toLowerCase()}`, false)

const latest = computed(() => data.value?.find(found => found.show === props.show) ?? null)
const loaded = computed(() => player.recording.value?.name === latest.value?.recording.name)
const on = computed(() => loaded.value && player.playing.value)

const noun = (n: number) => (n === 1 ? 'story' : 'stories')

const meta = ({ recording, stories: told }: RadioDigest) => [
  day(recording.status.startedAt),
  clock(recording.status.startedAt, 0),
  `${told.length} ${noun(told.length)}`,
  minutes(recording.status.seconds),
]

const told = ({ source }: DigestStory) =>
  source.also.length ? `${source.programme} +${source.also.length}` : source.programme

const play = (target: RecordingSummary) => {
  player.load(target, 'episode')
  player.toggle()
}
</script>
