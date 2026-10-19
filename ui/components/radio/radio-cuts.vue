<template>
  <div v-if="!recording.editList" class="grid gap-4">
    <p v-if="!coming" class="mb-0 text-ink-soft">Lõikesoovitusi pole.</p>
    <radio-find-cuts :recording="recording" />
  </div>
  <div v-else class="grid gap-4">
    <radio-card title="Lõiked">
      <template #aside>
        <span class="text-sm text-ink-soft">
          Midagi ei lõigata enne kinnitust · kuula enne otsust
        </span>
      </template>
      <radio-cut-card
        v-for="{ cut, i } in ordered"
        :key="cut.start"
        :cut="cut"
        :context="contexts.data.value?.[i]"
        :started-at="recording.status.startedAt"
        :removed="decisions.removed.value.includes(i)"
        @choose="decisions.choose(i, $event)"
        @jingle="jingle(i, $event)"
        @drop="drop(i)"
      />
    </radio-card>
    <details>
      <summary :class="LINK" class="w-fit cursor-pointer list-none">+ Lisa lõige käsitsi</summary>
      <radio-manual-cut class="mt-3" :recording="recording" />
    </details>
    <radio-find-cuts :recording="recording" />
  </div>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { ProgrammeCutState, type RecordingSummary } from '../../models/generated/domain-models'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useRadioCuts } from '../../composables/use-radio-recordings'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import { LINK } from '../../utils/radio'
import RadioCard from './radio-card.vue'
import RadioCutCard from './radio-cut-card.vue'
import RadioFindCuts from './radio-find-cuts.vue'
import RadioManualCut from './radio-manual-cut.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const contexts = useRadioCuts(() => props.recording)
const decisions = useRadioDecisions(() => props.recording)
const queryClient = useQueryClient()
const toast = useToast()

onBeforeUnmount(decisions.flush)

const ordered = computed(() =>
  props.recording.cuts.map((cut, i) => ({ cut, i })).sort((a, b) => a.cut.start - b.cut.start)
)

const coming = computed(() =>
  props.recording.programmeCuts.some(it => it.state !== ProgrammeCutState.NONE)
)

const jingle = (index: number, saved: boolean) =>
  radioService
    .jingle(props.recording.name, index, { saved })
    .then(() => {
      toast.success(saved ? 'Džingel salvestatud' : 'Džingel unustatud')
      return queryClient.invalidateQueries({ queryKey: ['radio'], exact: true })
    })
    .catch(() =>
      toast.error(saved ? 'Džinglit ei saanud salvestada' : 'Džinglit ei saanud unustada')
    )

const drop = (index: number) =>
  decisions
    .flush()
    .then(() => radioService.dropCut(props.recording.name, index))
    .then(() => queryClient.invalidateQueries({ queryKey: ['radio'] }))
    .catch(() => toast.error('Lõiget ei saanud kustutada'))
</script>
