<template>
  <dl class="mb-0">
    <div
      v-for="[label, value] in rows"
      :key="label"
      class="flex items-baseline justify-between gap-4 border-b border-hairline py-2.5 last:border-b-0"
    >
      <dt class="text-label font-semibold tracking-wide text-ink-soft uppercase">{{ label }}</dt>
      <dd class="mb-0 text-right font-semibold">{{ value }}</dd>
    </div>
  </dl>
</template>

<script lang="ts" setup>
defineProps<{ rows: [string, string][] }>()
</script>
