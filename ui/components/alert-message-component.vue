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

<script>
export default {
  name: 'AlertMessageComponent',
  props: {
    message: {
      type: String,
      required: false,
    },
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
  data() {
    return {
      visible: true,
    }
  },
  watch: {
    message: {
      immediate: true,
      handler() {
        this.resetVisibility()
      },
    },
    debugMessage: {
      immediate: true,
      handler() {
        this.resetVisibility()
      },
    },
    validationErrors: {
      immediate: true,
      handler() {
        this.resetVisibility()
      },
    },
  },
  methods: {
    resetVisibility() {
      this.visible = true
      if (this.message || this.debugMessage || Object.keys(this.validationErrors).length > 0) {
        setTimeout(this.hideAlert, 3000)
      }
    },
    hideAlert() {
      this.visible = false
    },
  },
}
</script>

<style scoped>
/* Add any additional styles if necessary */
</style>
