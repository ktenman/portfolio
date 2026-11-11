<template>
  <a
    :href="router.resolve(back.to).href"
    class="-ml-1 inline-flex min-h-11 items-center gap-1 px-1 text-sm font-medium text-ink-soft hover:text-ink"
    @click.exact.prevent="back.history ? router.back() : router.push(back.to)"
  >
    <radio-icon name="left" class="size-4" />
    {{ back.label }}
  </a>
</template>

<script lang="ts" setup>
import { useRouter } from 'vue-router'
import { backTarget } from '../../utils/poff-film'
import RadioIcon from '../radio/radio-icon.vue'

const router = useRouter()
const previous = router.options.history.state.back
const back = backTarget(typeof previous === 'string' ? router.resolve(previous) : null)
</script>
