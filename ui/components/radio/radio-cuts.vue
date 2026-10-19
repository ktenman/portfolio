<template>
  <div class="grid gap-4">
    <p v-if="!recording.editList && !coming" class="mb-0 text-ink-soft">Lõikesoovitusi pole.</p>
    <details v-if="recording.editList">
      <summary :class="LINK" class="w-fit cursor-pointer list-none">+ Lisa lõige käsitsi</summary>
      <radio-manual-cut class="mt-3" :recording="recording" />
    </details>
    <radio-find-cuts :recording="recording" />
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { ProgrammeCutState, type RecordingSummary } from '../../models/generated/domain-models'
import { LINK } from '../../utils/radio'
import RadioFindCuts from './radio-find-cuts.vue'
import RadioManualCut from './radio-manual-cut.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const coming = computed(() =>
  props.recording.programmeCuts.some(it => it.state !== ProgrammeCutState.NONE)
)
</script>
