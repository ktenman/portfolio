<template>
  <modal-shell
    :open="modelValue"
    :modal-id="modalId"
    :title="mode === 'export' ? 'Export Configuration' : 'Import Configuration'"
    size="lg"
    :close-on-esc="false"
    @update:open="onDialogClosed"
  >
    <template v-if="mode === 'export'">
      <p class="mb-2 text-[0.875em] text-body-secondary">
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
        <p class="mb-4 text-[0.875em] text-body-secondary">
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
            class="hidden"
            @change="onFileSelected"
          />
          <div class="drop-content">
            <div class="drop-icon">+</div>
            <div>Click to select or drag a JSON file here</div>
          </div>
        </div>
        <AlertMessage v-if="importError" variant="danger" class="mt-4 mb-0">
          {{ importError }}
        </AlertMessage>
      </div>
      <div v-else class="import-preview">
        <p class="mb-2 text-[0.875em] text-body-secondary">Preview of configuration to import:</p>
        <div class="editor-container">
          <VueMonacoEditor
            v-model:value="importContent"
            language="json"
            :options="editorOptions"
            theme="vs"
          />
        </div>
        <AlertMessage v-if="validationWarning" variant="warning" class="mt-4 mb-0">
          {{ validationWarning }}
        </AlertMessage>
      </div>
    </template>
    <template #footer>
      <button type="button" class="dialog-btn" @click="close">Cancel</button>
      <template v-if="mode === 'export'">
        <button type="button" class="dialog-btn primary" @click="downloadConfig">Download</button>
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
    </template>
  </modal-shell>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { VueMonacoEditor } from '@guolao/vue-monaco-editor'
import ModalShell from '../shared/modal-shell.vue'
import AlertMessage from '../shared/alert-message.vue'
import type { CachedState } from './types'

interface Props {
  modelValue: boolean
  mode: 'export' | 'import'
  config: CachedState
  validEtfIds: Set<number>
  modalId?: string
}

const props = withDefaults(defineProps<Props>(), {
  modalId: 'configDialog',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  export: []
  import: [data: CachedState]
}>()

const editorOptions = {
  readOnly: true,
  minimap: { enabled: false },
  scrollBeyondLastLine: false,
  lineNumbers: 'on' as const,
  folding: true,
  automaticLayout: true,
  fontSize: 13,
  fontFamily: 'var(--font-mono)',
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

const fileInput = ref<HTMLInputElement | null>(null)
const importedData = ref<CachedState | null>(null)
const importError = ref('')
const validationWarning = ref('')

const exportContent = computed(() => JSON.stringify(props.config, null, 2))

const importContent = computed(() =>
  importedData.value ? JSON.stringify(importedData.value, null, 2) : ''
)

const onDialogClosed = () => {
  emit('update:modelValue', false)
  resetImport()
}

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
      const data = JSON.parse(e.target?.result as string) as CachedState
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
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  overflow: hidden;
}

.file-drop-zone {
  border: 2px dashed var(--color-hairline-strong);
  border-radius: 0.5rem;
  padding: 2rem;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.file-drop-zone:hover {
  border-color: var(--color-signal-indigo);
  background: var(--color-surface-sunken);
}

.drop-content {
  color: var(--color-ink-soft);
}

.drop-icon {
  font-size: 2rem;
  font-weight: 300;
  color: var(--color-ink-faint);
  margin-bottom: 0.5rem;
}
</style>
