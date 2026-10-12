<template>
  <div class="period" role="group" aria-label="Period">
    <daily-profit-arrow
      label="Earlier period"
      direction="back"
      :disabled="earlierDisabled"
      :aria-busy="busy || undefined"
      @press="emit('earlier')"
    />
    <span class="period-dates">{{ dates }}</span>
    <daily-profit-arrow
      label="Later period"
      direction="forward"
      :disabled="laterDisabled"
      @press="emit('later')"
    />
  </div>
</template>

<script lang="ts" setup>
import DailyProfitArrow from './daily-profit-arrow.vue'

defineProps<{
  dates: string
  earlierDisabled: boolean
  laterDisabled: boolean
  busy: boolean
}>()

const emit = defineEmits<{ earlier: []; later: [] }>()
</script>

<style scoped>
.period {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-block: -1rem;
}

.period-dates {
  min-width: 12.5rem;
  font-size: var(--text-base);
  font-weight: 500;
  text-align: center;
  white-space: nowrap;
}

@media (max-width: 767.98px) {
  .period {
    justify-content: space-between;
    gap: 0;
    margin-block: 0;
  }

  .period-dates {
    min-width: auto;
  }

  .period button:focus-visible {
    outline-offset: -2px;
  }
}
</style>
