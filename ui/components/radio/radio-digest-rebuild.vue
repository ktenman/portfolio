<template>
  <button
    type="button"
    class="flex min-h-11 w-full cursor-pointer items-center gap-4 border-t border-hairline px-5 py-3 text-left text-sm font-medium text-brass-deep transition-colors enabled:hover:bg-brass-wash focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brass-deep disabled:cursor-default disabled:opacity-50"
    :disabled="!!build?.step"
    data-testid="radio-digest-rebuild"
    @click="rebuild"
  >
    <span class="flex w-5 shrink-0"><radio-icon name="refresh" /></span>
    {{ label ?? `Rebuild from the last ${COPY[show].span}` }}
  </button>
</template>

<script lang="ts" setup>
import { DigestShow } from '../../models/generated/domain-models'
import { ApiError } from '../../models/api-error'
import { useConfirm } from '../../composables/use-confirm'
import { useRadioDigestBuild } from '../../composables/use-radio-recordings'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ show: DigestShow; label?: string }>()

const COPY: Record<DigestShow, { name: string; span: string }> = {
  [DigestShow.DAY]: { name: 'Day', span: '24 hours' },
  [DigestShow.WEEK]: { name: 'Week', span: '7 days' },
}

const { data: build, refetch } = useRadioDigestBuild()
const { confirm } = useConfirm()
const toast = useToast()

const failure = (error: unknown) =>
  error instanceof ApiError && error.status === 409
    ? 'A digest is already building'
    : 'Could not start the rebuild'

const rebuild = async () => {
  const { name, span } = COPY[props.show]
  const confirmed = await confirm({
    title: `Rebuild ${name} in Radio?`,
    message: `The stories are chosen again from the last ${span}. It takes a few minutes, and a digest made today is replaced.`,
    confirmText: 'Rebuild',
    cancelText: 'Cancel',
    variant: 'primary',
  })
  if (!confirmed) return
  await radioService
    .rebuildDigest(props.show)
    .catch(error => toast.error(failure(error)))
    .finally(() => refetch())
}
</script>
