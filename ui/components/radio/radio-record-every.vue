<template>
  <label class="mb-0 flex cursor-pointer items-center gap-3">
    <slot />
    <input
      type="checkbox"
      class="form-check-input"
      :checked="programme[choice]"
      :aria-label="`${LABELS[choice]}: ${programme.name}`"
      @change="choose($event.target as HTMLInputElement)"
    />
  </label>
</template>

<script lang="ts" setup>
import { useQueryClient } from '@tanstack/vue-query'
import type { RadioProgrammeDto } from '../../models/generated/domain-models'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'

type Choice = 'recordEvery' | 'uncut'

const LABELS: Record<Choice, string> = {
  recordEvery: 'Record every time',
  uncut: 'Keep music and ads',
}

const props = withDefaults(defineProps<{ programme: RadioProgrammeDto; choice?: Choice }>(), {
  choice: 'recordEvery',
})
const queryClient = useQueryClient()
const toast = useToast()

const choose = (box: HTMLInputElement) =>
  radioService
    .choose(props.programme.id, {
      recordEvery: props.programme.recordEvery,
      uncut: props.programme.uncut,
      [props.choice]: box.checked,
    })
    .then(() =>
      Promise.all(
        ['radio-programmes', 'radio-station'].map(key =>
          queryClient.invalidateQueries({ queryKey: [key] })
        )
      )
    )
    .catch(() => {
      box.checked = !box.checked
      toast.error('Could not save the choice')
    })
</script>
