<template>
  <div class="grid gap-6 px-1 py-2 text-sm md:grid-cols-[22rem_minmax(0,32rem)]">
    <dl class="m-0 grid grid-cols-[auto_1fr] content-start gap-x-4 gap-y-1.5">
      <dt class="text-label text-body-secondary">Job</dt>
      <dd class="m-0 font-mono wrap-anywhere" data-testid="monitoring-job">{{ collection.job }}</dd>
      <template v-for="row in timing" :key="row.label">
        <dt class="text-label text-body-secondary">{{ row.label }}</dt>
        <dd class="m-0 tabular-nums">
          <time v-if="row.value" :datetime="row.value" :title="row.value">
            {{ formatDateTime(row.value) }}
          </time>
          <span v-else>—</span>
        </dd>
      </template>
    </dl>
    <ul class="m-0 grid list-none content-start gap-1 p-0" data-testid="monitoring-items">
      <li
        v-for="item in ordered"
        :key="item.symbol"
        class="grid grid-cols-[minmax(0,1fr)_auto] gap-x-4"
      >
        <span class="font-medium wrap-anywhere" :class="{ 'text-loss-deep': item.failed }">
          {{ item.symbol }}
        </span>
        <time
          class="text-body-secondary tabular-nums"
          :datetime="item.lastSuccess ?? undefined"
          :title="item.lastSuccess ?? ''"
        >
          {{ ago(item.lastSuccess) }}
        </time>
        <span
          v-if="item.failed"
          class="col-span-2 text-label text-loss-deep wrap-anywhere"
          data-testid="monitoring-item-error"
        >
          {{ item.error ?? 'Not persisted' }}
        </span>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ago, formatDateTime } from '../../utils/formatters'
import type { CollectionStatusDto } from '../../models/generated/domain-models'

const props = defineProps<{ collection: CollectionStatusDto }>()

const timing = computed(() => [
  { label: 'Last attempt', value: props.collection.lastAttempt },
  { label: 'Last completion', value: props.collection.lastCompletion },
  { label: 'Last full success', value: props.collection.lastFullSuccess },
  { label: 'Next deadline', value: props.collection.deadline },
])

const ordered = computed(() =>
  [...props.collection.items].sort((a, b) => Number(b.failed) - Number(a.failed))
)
</script>
