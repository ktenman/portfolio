<template>
  <radio-card title="Töötlus">
    <radio-facts :rows="workFacts" />
    <div class="mt-3 flex flex-wrap gap-2">
      <a v-if="recording.episodeBytes" :href="url('episode.m4a')" download :class="LINK">
        <radio-icon name="download" />
        Puhastatud · {{ megabytes(recording.episodeBytes) }}
      </a>
    </div>
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
