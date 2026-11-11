<template>
  <alert-message :variant="missing ? 'info' : 'danger'">
    {{
      missing
        ? 'The PÖFF programme has not been imported yet.'
        : 'The PÖFF programme could not be loaded.'
    }}
    <app-button ghost size="sm" class="ml-2" data-testid="poff-retry" @click="refetch()">
      Retry
    </app-button>
  </alert-message>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { notImported } from '../../composables/use-poff-catalogue'
import AlertMessage from '../shared/alert-message.vue'
import AppButton from '../shared/app-button.vue'

const props = defineProps<{ error: unknown; refetch: () => unknown }>()
const missing = computed(() => notImported(props.error))
</script>
