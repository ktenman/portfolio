<template>
  <section v-if="latest" class="card relative overflow-hidden" data-testid="radio-digest">
    <div class="flex items-center gap-4 px-5 py-4">
      <button
        type="button"
        :class="[PLAY, 'relative z-10 size-13 [&>svg]:size-5']"
        :aria-label="`${on ? 'Pause' : 'Play'}: ${latest.recording.programme}`"
        @click="play(latest.recording)"
      >
        <radio-icon :name="on ? 'pause' : 'play'" />
      </button>
      <div class="grid min-w-0 gap-0.5">
        <h3 class="mb-0 text-heading">
          <router-link :to="`/radio/${latest.recording.name}`" :class="STRETCHED">
            {{ latest.recording.programme }}
          </router-link>
        </h3>
        <p class="mb-0 text-sm text-ink-soft" data-testid="radio-digest-meta">
          <template v-for="(part, index) in meta(latest)" :key="part">
            <template v-if="index">{{ ' · ' }}</template>
            <span class="whitespace-nowrap">{{ part }}</span>
          </template>
        </p>
      </div>
    </div>
    <ol class="mb-0 list-none p-0">
      <li
        v-for="(story, index) in latest.stories.slice(0, SHOWN)"
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
    <p
      v-if="latest.stories.length > SHOWN"
      class="mb-0 border-t border-hairline px-5 py-3 text-sm text-ink-soft"
      data-testid="radio-digest-more"
    >
      {{ latest.stories.length - SHOWN }} more {{ noun(latest.stories.length - SHOWN) }}
    </p>
  </section>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type {
  DigestShow,
  DigestStory,
  RadioDigest,
  RecordingSummary,
} from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioDigests } from '../../composables/use-radio-recordings'
import { clock, day, minutes, PLAY } from '../../utils/radio'
import RadioIcon from './radio-icon.vue'

const SHOWN = 4
const STRETCHED =
  'after:absolute after:inset-0 after:rounded-container underline-offset-2 hover:underline'

const props = defineProps<{ show: DigestShow }>()

const { data } = useRadioDigests()
const player = useRadioPlayer()

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
  if (!loaded.value) player.load(target, 'episode')
  player.toggle()
}
</script>
