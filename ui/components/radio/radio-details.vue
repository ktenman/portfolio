<template>
  <radio-card title="Töötlus" fold="shut">
    <template v-if="recording.episodeBytes" #aside>
      <a
        :href="url('episode.m4a')"
        download
        :class="LINK"
        :aria-label="`Laadi alla puhastatud, ${megabytes(recording.episodeBytes)}`"
      >
        <radio-icon name="download" />
        {{ megabytes(recording.episodeBytes) }}
      </a>
    </template>
    <radio-facts :rows="workFacts" />
  </radio-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { RecordingSummary } from '../../models/generated/domain-models'
import { radioService } from '../../services/api'
import { cutState, megabytes, LINK, textState } from '../../utils/radio'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const url = (file: string) => radioService.fileUrl(props.recording.name, file)

const workFacts = computed<[string, string][]>(() => [
  ['Tekst', textState(props.recording)],
  ['Lõiked', cutState(props.recording)],
])
</script>
