<template>
  <alert-message :variant="missing ? 'info' : 'danger'">
    {{
      missing
        ? 'The PÖFF programme has not been imported yet.'
        : 'The PÖFF programme could not be loaded.'
    }}
    <button
      type="button"
      class="ml-2 cursor-pointer font-semibold underline"
      data-testid="poff-retry"
      @click="refetch()"
    >
      Retry
    </button>
  </alert-message>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { notImported } from '../../composables/use-poff-catalogue'
import AlertMessage from '../shared/alert-message.vue'

const props = defineProps<{ error: unknown; refetch: () => unknown }>()
const missing = computed(() => notImported(props.error))
</script>
