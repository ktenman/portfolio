<template>
  <section v-if="latest" class="card relative overflow-hidden" data-testid="radio-digest">
    <div class="flex items-center gap-3.5 px-5 py-4">
      <button
        type="button"
        :class="[LISTEN, 'relative z-10 size-11 [&>svg]:size-5']"
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
    <div
      v-if="loaded"
      class="relative z-10 flex items-center gap-2 px-3 pb-3 text-label text-ink-soft tabular-nums md:px-5"
      data-testid="radio-digest-transport"
    >
      <button
        type="button"
        :class="[ROUND, 'size-11 flex-col text-label']"
        aria-label="Back 15 s"
        @click="player.skip(-15)"
      >
        <radio-icon name="back" />
        15
      </button>
      <span>{{ position(player.seconds.value) }}</span>
      <radio-scrubber
        class="min-w-0 grow"
        bare
        :value="player.seconds.value"
        :total="player.total.value"
        :parts="parts"
        label="Position in the digest"
        @seek="player.seek"
      />
      <span>&minus;{{ position(player.total.value - player.seconds.value) }}</span>
      <button
        type="button"
        :class="[ROUND, 'size-11 flex-col-reverse text-label']"
        aria-label="Forward 30 s"
        @click="player.skip(30)"
      >
        30
        <radio-icon name="forward" />
      </button>
      <button
        type="button"
        data-testid="radio-speed"
        :class="[ROUND, 'size-11 text-sm font-medium']"
        :aria-label="`Speed ${player.rate.value}×, change`"
        @click="player.step()"
      >
        {{ player.rate.value }}×
      </button>
    </div>
    <ol v-if="open" class="mb-0 list-none p-0">
      <li
        v-for="(story, index) in latest.stories"
        :key="story.start"
        class="border-t border-hairline"
        data-testid="radio-digest-story"
      >
        <button
          type="button"
          class="relative z-10 flex w-full cursor-pointer items-baseline gap-4 px-5 py-3 text-left hover:bg-surface-hover focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brass-deep"
          :class="{ 'bg-brass-wash hover:bg-brass-wash': now(story) }"
          :aria-current="now(story) ? 'true' : undefined"
          @click="jump(story)"
        >
          <span
            class="w-5 shrink-0 text-sm tabular-nums"
            :class="now(story) ? 'font-semibold text-brass-deep' : 'text-ink-soft'"
          >
            {{ index + 1 }}
          </span>
          <span class="flex min-w-0 grow items-baseline gap-x-4 max-md:flex-col">
            <span class="min-w-0 grow" :class="{ 'font-semibold text-brass-deep': now(story) }">
              {{ story.title }}
            </span>
            <span class="shrink-0 text-sm text-ink-soft">{{ told(story) }}</span>
          </span>
        </button>
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
import { clock, day, episodeParts, LISTEN, minutes, position, ROUND } from '../../utils/radio'
import RadioDigestRebuild from './radio-digest-rebuild.vue'
import RadioIcon from './radio-icon.vue'
import RadioRing from './radio-ring.vue'
import RadioScrubber from './radio-scrubber.vue'

const STRETCHED =
  'after:absolute after:inset-0 after:rounded-container underline-offset-2 hover:underline'

const props = defineProps<{ show: DigestShow }>()

const { data } = useRadioDigests()
const player = useRadioPlayer()
const open = useLocalStorage(`${STORAGE_KEYS.RADIO_DIGEST_OPEN}_${props.show.toLowerCase()}`, false)

const latest = computed(() => data.value?.find(found => found.show === props.show) ?? null)
const loaded = computed(() => player.recording.value?.name === latest.value?.recording.name)
const on = computed(() => loaded.value && player.playing.value)
const parts = computed(() => (latest.value ? episodeParts(latest.value.recording) : []))

const noun = (n: number) => (n === 1 ? 'story' : 'stories')

const meta = ({ recording, stories: told }: RadioDigest) => [
  day(recording.status.startedAt),
  clock(recording.status.startedAt, 0),
  `${told.length} ${noun(told.length)}`,
  minutes(recording.status.seconds),
]

const told = ({ source }: DigestStory) =>
  source.also.length ? `${source.programme} +${source.also.length}` : source.programme

const now = ({ start, end }: DigestStory) =>
  loaded.value && start <= player.seconds.value && player.seconds.value < end

const play = (target: RecordingSummary) => {
  if (!on.value) open.value = true
  player.load(target, 'episode')
  player.toggle()
}

const jump = ({ start }: DigestStory) => {
  if (!loaded.value) player.load(latest.value!.recording, 'episode')
  player.playFrom(start, 'episode')
}
</script>
