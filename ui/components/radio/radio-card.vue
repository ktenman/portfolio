<template>
  <section class="card">
    <div class="card-body">
      <div class="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h3 class="mb-0 text-heading">{{ title }}</h3>
        <slot name="aside" />
      </div>
      <slot />
    </div>
  </section>
</template>

<script lang="ts" setup>
defineProps<{ title: string }>()
</script>
