<template>
  <svg
    :width="size"
    :height="size"
    :viewBox="viewBox"
    fill="currentColor"
    xmlns="http://www.w3.org/2000/svg"
    :class="className"
  >
    <path
      v-if="name === 'pencil'"
      d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"
    />
    <path
      v-else-if="name === 'trash'"
      d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"
    />
    <path v-else-if="name === 'plus'" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" />
  </svg>
</template>

<script setup lang="ts">
interface Props {
  name: 'pencil' | 'trash' | 'plus'
  size?: number
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  size: 16,
  className: '',
})

const viewBox = props.name === 'pencil' ? '0 0 24 24' : '0 0 24 24'
</script>
