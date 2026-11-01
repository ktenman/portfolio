<template>
  <section
    class="grid gap-4 transition-opacity"
    :class="{ 'opacity-60': isPlaceholderData }"
    :aria-busy="isPlaceholderData"
    aria-label="Search results"
  >
    <p class="mb-0 font-semibold" role="status">{{ status }}</p>
    <app-button
      v-if="isLoadingError"
      ghost
      size="sm"
      class="justify-self-start"
      @click="refetch({ cancelRefetch: false })"
    >
      Retry
    </app-button>
    <template v-if="found">
      <div
        v-if="isRefetchError && !isRefetching"
        class="flex items-center gap-3 text-sm text-ink-soft"
      >
        <span role="alert">Couldn’t refresh the results</span>
        <app-button ghost size="sm" @click="refetch({ cancelRefetch: false })">Retry</app-button>
      </div>
      <p v-if="found.partial" class="mb-0 text-ink-soft">
        No line has every word, so these have most of them.
      </p>
      <p v-if="found.suggestions.length" class="mb-0 text-ink-soft">
        Close words that were said:
        <span v-for="(word, index) in found.suggestions" :key="word">
          {{ index ? ',' : '' }}
          <router-link
            :to="{ query: { ...route.query, q: word } }"
            class="font-medium text-brass-deep underline-offset-2 hover:underline"
            data-testid="radio-search-close"
          >
            {{ word }}
          </router-link>
        </span>
      </p>
      <radio-card v-if="found.topics.length" title="Topics">
        <ol>
          <li
            v-for="topic in found.topics"
            :key="`${topic.name} ${topic.start}`"
            data-testid="radio-search-topic"
          >
            <router-link :to="at(topic.name, topic.start)" :class="[HIT, 'grid gap-0.5']">
              <span class="font-semibold"><radio-marked :parts="topic.title" /></span>
              <span v-if="topic.summary.length"><radio-marked :parts="topic.summary" /></span>
              <span class="text-sm text-ink-soft">
                {{ topic.programme }} · {{ day(topic.startedAt) }} ·
                {{ clock(topic.startedAt, topic.start) }}
              </span>
            </router-link>
          </li>
        </ol>
        <p v-if="found.moreTopics" class="mt-2 mb-0 text-sm text-ink-soft">
          {{ count(found.moreTopics, 'more topic') }}
        </p>
      </radio-card>
      <radio-card v-if="groups.length" title="Said on air">
        <div class="divide-y divide-hairline">
          <div v-for="group in groups" :key="group.name" class="py-3 first:pt-0 last:pb-0">
            <p class="mb-1 text-sm text-ink-soft">
              <b class="font-semibold text-ink">{{ group.programme }}</b>
              ·
              {{ day(group.startedAt) }}
            </p>
            <ol>
              <li v-for="line in group.lines" :key="line.start" data-testid="radio-search-line">
                <router-link
                  :to="at(group.name, line.start, line.kept)"
                  :class="[HIT, 'grid grid-cols-[3.25rem_minmax(0,1fr)] gap-x-2.5 gap-y-0.5']"
                >
                  <span class="text-sm text-ink-soft tabular-nums" data-testid="radio-search-time">
                    {{ clock(group.startedAt, line.start) }}
                  </span>
                  <span><radio-marked :parts="line.text" /></span>
                  <span v-if="!line.kept" class="col-start-2 text-sm text-ink-soft">
                    Cut from the episode as news or ads. Plays the original recording.
                  </span>
                </router-link>
              </li>
            </ol>
            <p v-if="group.more" class="mt-1 mb-0 text-sm text-ink-soft">
              {{ group.more }} more in this recording
            </p>
          </div>
        </div>
        <div
          v-if="hasNextPage && !isRefetchError"
          :key="`${data?.pages.length} ${isRefetching}`"
          ref="more"
          class="mt-3 flex items-center gap-3 text-sm text-ink-soft"
        >
          <template v-if="isFetchNextPageError">
            <span role="alert">Couldn’t load more lines</span>
            <app-button ghost size="sm" @click="loadMore">
              {{ isFetchingNextPage ? 'Retrying…' : 'Retry' }}
            </app-button>
          </template>
          <template v-else>Loading more…</template>
        </div>
      </radio-card>
    </template>
  </section>
</template>

<script lang="ts" setup>
import { computed, type FunctionalComponent, h, useTemplateRef } from 'vue'
import { useIntersectionObserver } from '@vueuse/core'
import { useRoute } from 'vue-router'
import type { TextPart } from '../../models/generated/domain-models'
import { useRadioSearch, useStationFilter } from '../../composables/use-radio-recordings'
import { clock, day } from '../../utils/radio'
import AppButton from '../shared/app-button.vue'
import RadioCard from './radio-card.vue'

const props = defineProps<{ query: string }>()

const HIT =
  '-mx-5 px-5 py-2 transition-colors hover:bg-surface-hover focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brass-deep'

const RadioMarked: FunctionalComponent<{ parts: TextPart[] }> = ({ parts }) =>
  parts.map(part =>
    part.mark
      ? h('mark', { class: 'rounded-sm bg-brass/20 px-0.5 text-current' }, part.text)
      : part.text
  )

const route = useRoute()
const station = useStationFilter()
const {
  data,
  isError,
  isLoadingError,
  isPlaceholderData,
  isRefetchError,
  isRefetching,
  refetch,
  hasNextPage,
  isFetchNextPageError,
  isFetchingNextPage,
  loadMore,
} = useRadioSearch(() => props.query, station)

useIntersectionObserver(
  useTemplateRef<HTMLElement>('more'),
  entries => {
    if (entries.some(entry => entry.isIntersecting)) loadMore()
  },
  { rootMargin: '0px 0px 100% 0px' }
)

const count = (n: number, word: string) => `${n} ${n === 1 ? word : `${word}s`}`

const at = (name: string, start: number, kept = true) => ({
  path: `/radio/${name}`,
  query: { t: start, raw: kept ? undefined : 1 },
})

const found = computed(() => data.value?.pages[0])

const groups = computed(() => {
  const all = data.value?.pages.flatMap(page => page.groups) ?? []
  return all.filter((group, index) => all.findIndex(other => other.name === group.name) === index)
})

const status = computed(() => {
  if (!found.value || isPlaceholderData.value)
    return isError.value ? 'Couldn’t search the recordings' : 'Searching…'
  const { searchable, lines, recordings } = found.value
  if (!searchable) return 'These words are too short or too common to search'
  return lines
    ? `${count(lines, 'line')} in ${count(recordings, 'recording')}`
    : `Nobody said “${props.query}”`
})
</script>
