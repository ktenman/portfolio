<template>
  <div v-if="!recording.editList" class="grid gap-4">
    <p v-if="!coming" class="mb-0 text-ink-soft">Lõikesoovitusi pole.</p>
    <radio-find-cuts :recording="recording" />
  </div>
  <div v-else class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_20rem]">
    <div class="grid gap-4">
      <radio-card :title="`Salvestus ${span(recording)}`">
        <template #aside>
          <span class="text-sm text-ink-soft">Midagi ei lõigata enne kinnitust</span>
        </template>
        <div class="flex h-8 gap-0.5">
          <div
            v-for="part in strip"
            :key="part.start"
            class="rounded-control border border-hairline"
            :class="part.removed ? HATCH : 'bg-surface'"
            :style="{ flexGrow: part.end - part.start }"
          />
        </div>
        <p class="mt-2 mb-0 flex gap-4 text-sm text-ink-soft">
          <span class="flex items-center gap-1.5">
            <span class="size-3 rounded-control border border-hairline" :class="HATCH" />
            Eemaldatakse
          </span>
          <span class="flex items-center gap-1.5">
            <span class="size-3 rounded-control border border-hairline bg-surface" />
            Jääb alles
          </span>
        </p>
      </radio-card>
      <div class="flex flex-wrap items-baseline justify-between gap-2">
        <h3 class="mb-0 text-heading">Soovitused</h3>
        <span class="text-sm text-ink-soft">Masinteksti ja vaikuste järgi · kuula enne otsust</span>
      </div>
      <radio-cut-card
        v-for="{ cut, i } in ordered"
        :key="cut.start"
        :cut="cut"
        :context="contexts.data.value?.[i]"
        :started-at="recording.status.startedAt"
        :removed="removed.includes(i)"
        @choose="choose(i, $event)"
        @jingle="jingle(i, $event)"
        @drop="drop(i)"
      />
      <radio-manual-cut :recording="recording" />
      <radio-find-cuts :recording="recording" />
    </div>
    <radio-card title="Tulemus">
      <div data-testid="radio-cut-result">
        <radio-facts :rows="facts" />
      </div>
      <div class="mt-3 flex flex-wrap gap-2">
        <app-button variant="primary" :loading="saving" @click="apply">Rakenda lõiked</app-button>
        <app-button :disabled="saving || machine" @click="reset">Lähtesta</app-button>
      </div>
      <p class="mt-3 mb-0 text-sm text-ink-soft">
        Toorsalvestus jääb muutmata. Otsused salvestatakse lõikeloendisse.
      </p>
    </radio-card>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { ProgrammeCutState, type RecordingSummary } from '../../models/generated/domain-models'
import { useConfirm } from '../../composables/use-confirm'
import { useRadioCuts } from '../../composables/use-radio-recordings'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import { position, span } from '../../utils/radio'
import AppButton from '../shared/app-button.vue'
import RadioCard from './radio-card.vue'
import RadioCutCard from './radio-cut-card.vue'
import RadioFacts from './radio-facts.vue'
import RadioFindCuts from './radio-find-cuts.vue'
import RadioManualCut from './radio-manual-cut.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const HATCH =
  'bg-[repeating-linear-gradient(135deg,var(--color-hairline-strong)_0_1px,transparent_1px_6px)]'

const contexts = useRadioCuts(() => props.recording)
const queryClient = useQueryClient()
const { confirm } = useConfirm()
const toast = useToast()

const saved = () => props.recording.cuts.flatMap((cut, i) => (cut.removed ? [i] : []))
const removed = ref(saved())
const saving = ref(false)
watch(
  () => props.recording.cuts,
  (cuts, before) =>
    (removed.value =
      cuts.length === before.length + 1 ? [...removed.value, before.length] : saved())
)

const ordered = computed(() =>
  props.recording.cuts.map((cut, i) => ({ cut, i })).sort((a, b) => a.cut.start - b.cut.start)
)

const coming = computed(() =>
  props.recording.programmeCuts.some(it => it.state !== ProgrammeCutState.NONE)
)
const machine = computed(() => removed.value.length === props.recording.cuts.length)
const cutSeconds = computed(() =>
  props.recording.cuts
    .filter((_, i) => removed.value.includes(i))
    .reduce((sum, cut) => sum + cut.end - cut.start, 0)
)

const strip = computed(() =>
  props.recording.timeline.map(({ start, end, cut }) => ({
    start,
    end,
    removed: cut && removed.value.includes(props.recording.cuts.findIndex(c => c.start === start)),
  }))
)

const facts = computed<[string, string][]>(() => [
  ['Toorsalvestus', position(props.recording.status.seconds)],
  ['Eemaldatakse', `${position(cutSeconds.value)} · ${removed.value.length} lõiget`],
  ['Puhastatud', position(props.recording.status.seconds - cutSeconds.value)],
])

const choose = (index: number, remove: boolean) => {
  const others = removed.value.filter(i => i !== index)
  removed.value = remove ? [...others, index].sort((a, b) => a - b) : others
}

const reset = () => (removed.value = props.recording.cuts.map((_, i) => i))

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
  radioService
    .dropCut(props.recording.name, index)
    .then(() => queryClient.invalidateQueries({ queryKey: ['radio'] }))
    .catch(() => toast.error('Lõiget ei saanud kustutada'))

const apply = async () => {
  const confirmed = await confirm({
    title: 'Rakenda lõiked',
    message: `Puhastatud saade tehakse uuesti ja jääb ${position(props.recording.status.seconds - cutSeconds.value)} pikaks.`,
    confirmText: 'Rakenda',
    cancelText: 'Loobu',
  })
  if (!confirmed) return
  saving.value = true
  await radioService
    .review(props.recording.name, { removed: removed.value })
    .then(() => {
      toast.success('Lõiked rakendatud')
      return queryClient.invalidateQueries({ queryKey: ['radio'] })
    })
    .catch(() => toast.error('Lõikeid ei saanud rakendada'))
    .finally(() => (saving.value = false))
}
</script>
