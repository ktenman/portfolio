<template>
  <div v-if="open" class="modal-backdrop" @click.self="cancel">
    <div class="modal-dialog" role="dialog" aria-labelledby="thresholds-modal-title">
      <div class="modal-header">
        <h5 id="thresholds-modal-title" class="modal-title">Rebalance thresholds</h5>
        <button type="button" class="close-btn" aria-label="Close" @click="cancel">&times;</button>
      </div>
      <div class="modal-body">
        <p class="threshold-intro">
          A holding's drift is how far its current allocation is from its target. These thresholds
          decide when a holding is flagged as drifting or due for rebalance.
        </p>
        <div class="threshold-row">
          <div class="threshold-label-group">
            <label for="drifting-rel">Drifting (relative %)</label>
            <small class="threshold-hint">
              Flag as drifting once drift reaches this share of the target (e.g. 10% of a 5% target
              = 0.5pp).
            </small>
          </div>
          <input
            id="drifting-rel"
            v-model.number="local.driftingThresholdRel"
            type="number"
            min="0"
            step="0.5"
          />
        </div>
        <div class="threshold-row">
          <div class="threshold-label-group">
            <label for="rebalance-rel">Rebalance (relative %)</label>
            <small class="threshold-hint">
              Flag for rebalance once drift reaches this share of the target (the "25" in the 5/25
              rule).
            </small>
          </div>
          <input
            id="rebalance-rel"
            v-model.number="local.rebalanceThresholdRel"
            type="number"
            min="0"
            step="0.5"
          />
        </div>
        <div class="threshold-row">
          <div class="threshold-label-group">
            <label for="rebalance-abs">Rebalance (absolute pp)</label>
            <small class="threshold-hint">
              Flag for rebalance once drift reaches this many percentage points, regardless of
              target size (the "5" in the 5/25 rule).
            </small>
          </div>
          <input
            id="rebalance-abs"
            v-model.number="local.rebalanceThresholdAbs"
            type="number"
            min="0"
            step="0.5"
          />
        </div>
        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
      </div>
      <div class="modal-footer">
        <button type="button" class="reset-btn" @click="reset">Reset to defaults</button>
        <div class="footer-actions">
          <button type="button" class="cancel-btn" @click="cancel">Cancel</button>
          <button type="button" class="save-btn" :disabled="!isValid" @click="save">Save</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, reactive, watch } from 'vue'
import {
  DEFAULT_REBALANCE_THRESHOLDS,
  type RebalanceThresholds,
} from '../../composables/use-allocation-calculations'

const props = defineProps<{
  modelValue: RebalanceThresholds
  open: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: RebalanceThresholds]
  'update:open': [value: boolean]
  save: [value: RebalanceThresholds]
}>()

const local = reactive<RebalanceThresholds>({ ...props.modelValue })

watch(
  () => props.modelValue,
  v => Object.assign(local, v),
  { deep: true }
)

watch(
  () => props.open,
  isOpen => {
    if (isOpen) Object.assign(local, props.modelValue)
  }
)

const errorMessage = computed(() => {
  if (
    !Number.isFinite(local.driftingThresholdRel) ||
    !Number.isFinite(local.rebalanceThresholdRel) ||
    !Number.isFinite(local.rebalanceThresholdAbs)
  ) {
    return 'Thresholds must be numeric'
  }
  if (
    local.driftingThresholdRel < 0 ||
    local.rebalanceThresholdRel < 0 ||
    local.rebalanceThresholdAbs < 0
  ) {
    return 'Thresholds must be zero or greater'
  }
  if (local.driftingThresholdRel > local.rebalanceThresholdRel) {
    return 'Drifting threshold must be less than or equal to the rebalance threshold'
  }
  return ''
})

const isValid = computed(() => errorMessage.value === '')

const save = () => {
  if (!isValid.value) return
  emit('update:modelValue', { ...local })
  emit('save', { ...local })
  emit('update:open', false)
}

const cancel = () => {
  emit('update:open', false)
}

const reset = () => {
  Object.assign(local, DEFAULT_REBALANCE_THRESHOLDS)
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1050;
}

.modal-dialog {
  background: var(--bs-white);
  border-radius: 0.5rem;
  width: min(420px, 92vw);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25);
  pointer-events: auto;
}

.modal-header,
.modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--bs-gray-200);
}

.modal-footer {
  border-bottom: none;
  border-top: 1px solid var(--bs-gray-200);
}

.modal-title {
  margin: 0;
  font-size: 1rem;
}

.close-btn {
  background: transparent;
  border: none;
  font-size: 1.25rem;
  cursor: pointer;
}

.modal-body {
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.threshold-intro {
  margin: 0;
  font-size: 0.8125rem;
  color: var(--bs-gray-700);
}

.threshold-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.threshold-label-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  flex: 1;
}

.threshold-row label {
  font-size: 0.875rem;
  color: var(--bs-gray-700);
}

.threshold-hint {
  font-size: 0.75rem;
  color: var(--bs-gray-600);
  line-height: 1.3;
}

.threshold-row input {
  width: 100px;
  padding: 0.25rem 0.5rem;
  border: 1px solid var(--bs-gray-300);
  border-radius: 0.25rem;
  text-align: right;
  flex-shrink: 0;
}

.error {
  color: var(--bs-danger);
  font-size: 0.8125rem;
  margin: 0;
}

.footer-actions {
  display: flex;
  gap: 0.5rem;
}

.reset-btn,
.cancel-btn,
.save-btn {
  padding: 0.375rem 0.75rem;
  border-radius: 0.375rem;
  border: 1px solid var(--bs-gray-300);
  background: var(--bs-white);
  cursor: pointer;
  font-size: 0.8125rem;
}

.save-btn {
  background: var(--bs-primary);
  color: var(--bs-white);
  border-color: var(--bs-primary);
}

.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
