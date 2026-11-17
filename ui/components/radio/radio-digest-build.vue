<template>
  <section
    v-if="build"
    class="card relative overflow-hidden bg-surface-sunken"
    data-testid="radio-digest-build"
  >
    <div class="flex items-center gap-4 px-5 py-4">
      <span class="size-13 shrink-0 rounded-full bg-hairline" aria-hidden="true" />
      <div class="grid min-w-0 grow gap-1.5">
        <h3 class="mb-0 text-heading">{{ title }}</h3>
        <p
          v-if="build.failure"
          class="mb-0 flex items-center gap-1.5 text-sm"
          data-testid="radio-digest-build-failure"
        >
          <radio-icon name="alert" />
          Could not make the digest: {{ build.failure.reason }} · {{ next(build.failure) }}
        </p>
        <template v-else-if="meter">
          <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 text-sm">
            <span data-testid="radio-digest-build-step">{{ meter.step }}</span>
            <span class="text-ink-soft tabular-nums">{{ meter.left }}</span>
          </div>
          <div
            role="progressbar"
            aria-label="Week in Radio"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-valuenow="meter.percent"
            :aria-valuetext="`${meter.percent}%, ${meter.step}, ${meter.left}`"
            class="h-1.5 overflow-hidden rounded-full bg-hairline"
          >
            <div
              class="h-full rounded-full bg-brass transition-[width] duration-700 ease-out motion-reduce:transition-none"
              :style="{ width: `${meter.percent}%` }"
            />
          </div>
        </template>
      </div>
    </div>
    <ol v-if="rows.length" class="mb-0 list-none p-0">
      <li
        v-for="(row, index) in rows"
        :key="index"
        class="flex items-baseline gap-4 border-t border-hairline px-5 py-3"
        :class="{ 'text-ink-soft': !row.cut }"
        data-testid="radio-digest-build-story"
      >
        <span class="w-5 shrink-0 text-sm text-ink-soft tabular-nums">{{ index + 1 }}</span>
        <span v-if="row.title" class="min-w-0 grow">{{ row.title }}</span>
        <span
          v-else
          class="h-3 grow self-center rounded-full bg-hairline"
          :style="{ maxWidth: `${BARS[index]}%` }"
        />
      </li>
    </ol>
    <p
      v-if="more"
      class="mb-0 border-t border-hairline px-5 py-3 text-sm text-ink-soft"
      data-testid="radio-digest-build-more"
    >
      {{ more }} more {{ more === 1 ? 'story' : 'stories' }}
    </p>
  </section>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import {
  type DigestBuild,
  type DigestFailure,
  DigestStep,
} from '../../models/generated/domain-models'
import { useElapsed, useRadioDigestBuild } from '../../composables/use-radio-recordings'
import { advance, clock, left } from '../../utils/radio'
import RadioIcon from './radio-icon.vue'

const SHOWN = 4
const BARS = [72, 58, 66, 49]

const { data: build } = useRadioDigestBuild()
const elapsed = useElapsed(build)

const title = computed(() => `Week in Radio ${build.value?.date.split('-').reverse().join('.')}`)

const stepLabel = ({ step, done, titles }: DigestBuild): string => {
  if (step === DigestStep.CLIPS) return `Choosing the clips · ${done} of ${titles.length}`
  if (step === DigestStep.CUTTING) return `Cutting the clips · ${done} of ${titles.length}`
  if (step === DigestStep.MIXING) return `Mixing · ${done}%`
  return 'Choosing the stories'
}

const meter = computed(() => {
  const found = build.value
  if (!found?.step) return null
  const { percent, secondsLeft } = advance(found, elapsed.value)
  return { percent, step: stepLabel(found), left: left(secondsLeft) }
})

const cut = ({ step, done }: DigestBuild, index: number) =>
  step === DigestStep.MIXING || (step === DigestStep.CUTTING && index < done)

const rows = computed(() => {
  const found = build.value
  if (!found) return []
  const titles =
    found.step === DigestStep.STORIES ? BARS.map(() => null) : found.titles.slice(0, SHOWN)
  return titles.map((title, index) => ({ title, cut: cut(found, index) }))
})

const more = computed(() => Math.max(0, (build.value?.titles.length ?? 0) - SHOWN))

const next = ({ tries, attempts, nextTry }: DigestFailure) =>
  nextTry
    ? `Next try at ${clock(Date.parse(nextTry) / 1000, 0)} (${tries + 1} of ${attempts})`
    : `Stopped for today after ${tries} tries`
</script>
