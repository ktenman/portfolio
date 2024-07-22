<template>
  <div v-if="visible && !!message" class="mt-3">
    <div :class="['alert', alertClass, 'alert-dismissible']" role="alert">
      <strong v-if="message">{{ message }}</strong>
      <p v-if="debugMessage" class="mb-0 mt-2">
        <small>Debug: {{ debugMessage }}</small>
      </p>
      <ul v-if="Object.keys(validationErrors).length > 0" class="mt-2 mb-0">
        <li v-for="(error, field) in validationErrors" :key="field">{{ field }}: {{ error }}</li>
      </ul>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, watch } from 'vue'

export default defineComponent({
  name: 'AlertMessageComponent',
  props: {
    message: String,
    alertClass: {
      type: String,
      default: 'alert-info',
    },
    debugMessage: {
      type: String,
      default: '',
    },
    validationErrors: {
      type: Object,
      default: () => ({}),
    },
  },
  setup(props) {
    const visible = ref(true)

    const resetVisibility = () => {
      visible.value = true
      setTimeout(() => {
        visible.value = false
      }, 3000)
    }

    watch(() => [props.message, props.debugMessage, props.validationErrors], resetVisibility, {
      immediate: true,
    })

    return { visible }
  },
})
</script>

<style scoped>
/* Add any additional styles if necessary */
</style>
