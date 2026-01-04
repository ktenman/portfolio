<template>
  <div
    class="modal fade"
    :id="modalId"
    tabindex="-1"
    :aria-labelledby="`${modalId}Label`"
    aria-hidden="true"
    @click.self="close"
  >
    <div class="modal-dialog modal-lg">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h5 class="modal-title" :id="`${modalId}Label`">Replace Logo: {{ holdingName }}</h5>
          <button type="button" class="btn-close" @click="close" aria-label="Close"></button>
        </div>
        <div class="modal-body modal-body-scroll">
          <loading-spinner v-if="isLoading" class="my-4" />
          <div v-else-if="error" class="alert alert-danger">{{ error }}</div>
          <div
            v-else-if="hasFetched && candidates.length === 0"
            class="text-center text-muted py-4"
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
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="close" :disabled="isReplacing">
            Cancel
          </button>
          <button
            type="button"
            class="btn btn-primary"
            @click="confirmReplacement"
            :disabled="selectedIndex === null || isReplacing"
          >
            <span v-if="isReplacing" class="btn-spinner me-1"></span>
            {{ isReplacing ? 'Replacing...' : 'Use This Logo' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { Modal } from 'bootstrap'
import LoadingSpinner from '../shared/loading-spinner.vue'
import { logoService, type LogoCandidateDto } from '../../services/logo-service'

interface Props {
  modelValue: boolean
  holdingUuid: string | null
  holdingName: string
  modalId?: string
}

const props = withDefaults(defineProps<Props>(), {
  modalId: 'logoReplacementModal',
})

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

let modalInstance: Modal | null = null

onMounted(() => {
  const modalElement = document.getElementById(props.modalId)
  if (modalElement) {
    modalInstance = new Modal(modalElement, { backdrop: 'static', keyboard: false })
    modalElement.addEventListener('hidden.bs.modal', () => {
      emit('update:modelValue', false)
    })
  }
})

onUnmounted(() => {
  modalInstance?.dispose()
})

watch(
  () => props.modelValue,
  async newValue => {
    if (newValue && props.holdingUuid) {
      modalInstance?.show()
      await loadCandidates()
    } else {
      modalInstance?.hide()
      resetState()
    }
  }
)

const loadCandidates = async () => {
  if (!props.holdingUuid) return
  isLoading.value = true
  hasFetched.value = false
  error.value = null
  candidates.value = []
  selectedIndex.value = null
  try {
    candidates.value = await logoService.getCandidates(props.holdingUuid)
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
.modal-body-scroll {
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
  border: 2px solid #e9ecef;
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.logo-candidate:hover {
  border-color: #6c757d;
  background-color: #f8f9fa;
}

.logo-candidate.selected {
  border-color: #0d6efd;
  background-color: #e7f1ff;
}

.candidate-image {
  width: 80px;
  height: 80px;
  object-fit: contain;
  border-radius: 0.25rem;
  background-color: #fff;
}
</style>
