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
          <h5 class="modal-title" :id="`${modalId}Label`">
            {{ mode === 'export' ? 'Export Configuration' : 'Import Configuration' }}
          </h5>
          <button type="button" class="btn-close" @click="close" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <template v-if="mode === 'export'">
            <p class="text-muted small mb-2">
              Download your current ETF allocation configuration as a JSON file.
            </p>
            <div class="editor-container">
              <VueMonacoEditor
                v-model:value="exportContent"
                language="json"
                :options="editorOptions"
                theme="vs"
              />
            </div>
          </template>
          <template v-else>
            <div v-if="!importedData" class="import-area">
              <p class="text-muted small mb-3">
                Select a JSON configuration file to import your ETF allocation.
              </p>
              <div
                class="file-drop-zone"
                @click="triggerFileInput"
                @dragover.prevent
                @drop.prevent="onFileDrop"
              >
                <input
                  ref="fileInput"
                  type="file"
                  accept=".json"
                  class="d-none"
                  @change="onFileSelected"
                />
                <div class="drop-content">
                  <div class="drop-icon">+</div>
                  <div>Click to select or drag a JSON file here</div>
                </div>
              </div>
              <div v-if="importError" class="alert alert-danger mt-3 mb-0">
                {{ importError }}
              </div>
            </div>
            <div v-else class="import-preview">
              <p class="text-muted small mb-2">Preview of configuration to import:</p>
              <div class="editor-container">
                <VueMonacoEditor
                  v-model:value="importContent"
                  language="json"
                  :options="editorOptions"
                  theme="vs"
                />
              </div>
              <div v-if="validationWarning" class="alert alert-warning mt-3 mb-0">
                {{ validationWarning }}
              </div>
            </div>
          </template>
        </div>
        <div class="modal-footer">
          <button type="button" class="dialog-btn" @click="close">Cancel</button>
          <template v-if="mode === 'export'">
            <button type="button" class="dialog-btn primary" @click="downloadConfig">
              Download
            </button>
          </template>
          <template v-else>
            <button v-if="importedData" type="button" class="dialog-btn" @click="resetImport">
              Choose Different File
            </button>
            <button
              type="button"
              class="dialog-btn primary"
              :disabled="!importedData"
              @click="confirmImport"
            >
              Import
            </button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { Modal } from 'bootstrap'
import { VueMonacoEditor } from '@guolao/vue-monaco-editor'

interface AllocationInput {
  instrumentId: number
  value: number
}

interface ConfigData {
  allocations: AllocationInput[]
  inputMode: 'percentage' | 'amount'
}

interface Props {
  modelValue: boolean
  mode: 'export' | 'import'
  config: ConfigData
  validEtfIds: Set<number>
  modalId?: string
}

const props = withDefaults(defineProps<Props>(), {
  modalId: 'configDialog',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  export: []
  import: [data: ConfigData]
}>()

const editorOptions = {
  readOnly: true,
  minimap: { enabled: false },
  scrollBeyondLastLine: false,
  lineNumbers: 'on' as const,
  folding: true,
  automaticLayout: true,
  fontSize: 13,
  fontFamily: "ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace",
  renderLineHighlight: 'none' as const,
  stickyScroll: { enabled: false },
  overviewRulerLanes: 0,
  hideCursorInOverviewRuler: true,
  overviewRulerBorder: false,
  scrollbar: {
    vertical: 'auto' as const,
    horizontal: 'auto' as const,
  },
}

let modalInstance: Modal | null = null
const fileInput = ref<HTMLInputElement | null>(null)
const importedData = ref<ConfigData | null>(null)
const importError = ref('')
const validationWarning = ref('')

const exportContent = computed(() => JSON.stringify(props.config, null, 2))

const importContent = computed(() =>
  importedData.value ? JSON.stringify(importedData.value, null, 2) : ''
)

onMounted(() => {
  const modalElement = document.getElementById(props.modalId)
  if (modalElement) {
    modalInstance = new Modal(modalElement, { backdrop: 'static', keyboard: false })
    modalElement.addEventListener('hidden.bs.modal', () => {
      emit('update:modelValue', false)
      resetImport()
    })
  }
})

onUnmounted(() => {
  modalInstance?.dispose()
})

watch(
  () => props.modelValue,
  newValue => {
    if (modalInstance) {
      if (newValue) {
        modalInstance.show()
      } else {
        modalInstance.hide()
      }
    }
  }
)

const close = () => {
  emit('update:modelValue', false)
}

const downloadConfig = () => {
  const blob = new Blob([JSON.stringify(props.config, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'diversification-config.json'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  emit('export')
  close()
}

const triggerFileInput = () => {
  fileInput.value?.click()
}

const onFileDrop = (event: DragEvent) => {
  const file = event.dataTransfer?.files[0]
  if (file) processFile(file)
}

const onFileSelected = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) processFile(file)
  target.value = ''
}

const processFile = (file: File) => {
  importError.value = ''
  validationWarning.value = ''
  const reader = new FileReader()
  reader.onload = e => {
    try {
      const data = JSON.parse(e.target?.result as string) as ConfigData
      if (!data.allocations || !data.inputMode) {
        importError.value = 'Invalid configuration file format'
        return
      }
      const validAllocations = data.allocations.filter(
        a => a.instrumentId === 0 || props.validEtfIds.has(a.instrumentId)
      )
      if (validAllocations.length === 0) {
        importError.value = 'No valid ETFs found in the configuration'
        return
      }
      if (validAllocations.length < data.allocations.length) {
        validationWarning.value = `${data.allocations.length - validAllocations.length} ETF(s) were removed because they are not available`
      }
      importedData.value = { allocations: validAllocations, inputMode: data.inputMode }
    } catch {
      importError.value = 'Invalid JSON file'
    }
  }
  reader.readAsText(file)
}

const resetImport = () => {
  importedData.value = null
  importError.value = ''
  validationWarning.value = ''
}

const confirmImport = () => {
  if (importedData.value) {
    emit('import', importedData.value)
    close()
  }
}
</script>

<style scoped>
.editor-container {
  height: 400px;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  overflow: hidden;
}

.file-drop-zone {
  border: 2px dashed #dee2e6;
  border-radius: 0.5rem;
  padding: 2rem;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.file-drop-zone:hover {
  border-color: #0d6efd;
  background: #f8f9fa;
}

.drop-content {
  color: #6c757d;
}

.drop-icon {
  font-size: 2rem;
  font-weight: 300;
  color: #adb5bd;
  margin-bottom: 0.5rem;
}
</style>
