<template>
  <span class="aspect-square rounded-control" :class="CELL_CLASSES[variant]" />
</template>

<script lang="ts" setup>
import type { ChangeStep } from '../../services/daily-profit-calendar'

type CellVariant = ChangeStep | 'starting' | 'missing'

defineProps<{ variant: CellVariant }>()

const CELL_CLASSES: Record<CellVariant, string> = {
  'loss-4': 'bg-loss',
  'loss-3': 'bg-loss-step-3',
  'loss-2': 'bg-loss-step-2',
  'loss-1': 'bg-loss-step-1',
  flat: 'bg-change-flat',
  'gain-1': 'bg-gain-step-1',
  'gain-2': 'bg-gain-step-2',
  'gain-3': 'bg-gain-step-3',
  'gain-4': 'bg-gain',
  starting: 'border border-ink-faint bg-surface',
  missing: 'border border-dashed border-ink-faint',
}
</script>
