<template>
  <modal-shell
    :open="modelValue"
    modal-id="logoReplacementModal"
    :title="`Replace Logo: ${holdingName}`"
    size="lg"
    :close-on-esc="false"
    @update:open="close"
  >
    <loading-spinner v-if="isLoading" class="my-6" />
    <div v-else-if="error" class="alert alert-danger">{{ error }}</div>
    <div
      v-else-if="hasFetched && candidates.length === 0"
      class="py-6 text-center text-body-secondary"
    >
      No logo candidates found
    </div>
    <div v-else class="logo-grid">
      <div
        v-for="candidate in candidates"
        :key="candidate.index"
        class="logo-candidate"
        :class="{ selected: selectedIndex === candidate.index }"
        @click="selectCandidate(candidate.index)"
      >
        <img
          :src="candidate.imageDataUrl || candidate.thumbnailUrl"
          :alt="candidate.title"
          class="candidate-image"
          @error="handleImageError"
        />
      </div>
    </div>
    <template #footer>
      <div v-if="!holdingUuid" class="me-auto text-[0.875em] text-body-secondary">
        Logo preview only - no holding record to save to
      </div>
      <AppButton variant="secondary" @click="close" :disabled="isReplacing">
        {{ holdingUuid ? 'Cancel' : 'Close' }}
      </AppButton>
      <AppButton
        v-if="holdingUuid"
        variant="primary"
        :loading="isReplacing"
        @click="confirmReplacement"
        :disabled="selectedIndex === null"
      >
        {{ isReplacing ? 'Replacing...' : 'Use This Logo' }}
      </AppButton>
    </template>
  </modal-shell>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import ModalShell from '../shared/modal-shell.vue'
import LoadingSpinner from '../shared/loading-spinner.vue'
import AppButton from '../shared/app-button.vue'
import { logoService, type LogoCandidateDto } from '../../services/api'

interface Props {
  modelValue: boolean
  holdingUuid: string | null
  holdingName: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  replaced: [holdingUuid: string]
}>()

const candidates = ref<LogoCandidateDto[]>([])
const selectedIndex = ref<number | null>(null)
const isLoading = ref(true)
const isReplacing = ref(false)
const error = ref<string | null>(null)
const hasFetched = ref(false)

watch(
  () => props.modelValue,
  async newValue => {
    if (newValue && (props.holdingUuid || props.holdingName)) {
      await loadCandidates()
      return
    }
    resetState()
  }
)

const loadCandidates = async () => {
  if (!props.holdingUuid && !props.holdingName) return
  isLoading.value = true
  hasFetched.value = false
  error.value = null
  candidates.value = []
  selectedIndex.value = null
  try {
    if (props.holdingUuid) {
      candidates.value = await logoService.getCandidates(props.holdingUuid)
    } else {
      candidates.value = await logoService.searchByName(props.holdingName)
    }
  } catch {
    error.value = 'Failed to load logo candidates'
  } finally {
    isLoading.value = false
    hasFetched.value = true
  }
}

const selectCandidate = (index: number) => {
  selectedIndex.value = index
}

const confirmReplacement = async () => {
  if (selectedIndex.value === null || !props.holdingUuid) return
  isReplacing.value = true
  try {
    const result = await logoService.replaceLogo({
      holdingUuid: props.holdingUuid,
      candidateIndex: selectedIndex.value,
    })
    if (result.success) {
      emit('replaced', props.holdingUuid)
      close()
    } else {
      error.value = result.message || 'Failed to replace logo'
    }
  } catch {
    error.value = 'Failed to replace logo'
  } finally {
    isReplacing.value = false
  }
}

const close = () => {
  emit('update:modelValue', false)
}

const resetState = () => {
  candidates.value = []
  selectedIndex.value = null
  error.value = null
  isLoading.value = true
  hasFetched.value = false
}

const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.style.display = 'none'
}
</script>

<style scoped>
:deep(.modal-body) {
  max-height: 60vh;
  overflow-y: auto;
}

.logo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 1rem;
}

.logo-candidate {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.75rem;
  border: 2px solid var(--color-hairline);
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.logo-candidate:hover {
  border-color: var(--color-hairline-strong);
  background-color: var(--color-surface-hover);
}

.logo-candidate.selected {
  border-color: var(--color-signal-indigo);
  background-color: var(--color-brass-wash);
}

.candidate-image {
  width: 80px;
  height: 80px;
  object-fit: contain;
  border-radius: 0.25rem;
  background-color: var(--color-surface);
}
</style>
