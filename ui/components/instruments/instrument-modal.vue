<template>
  <div
    class="modal fade"
    :id="modalId"
    tabindex="-1"
    :aria-labelledby="`${modalId}Label`"
    aria-hidden="true"
  >
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" :id="`${modalId}Label`">
            {{ isEditing ? 'Edit Instrument' : 'Add New Instrument' }}
          </h5>
          <button
            type="button"
            class="btn-close"
            data-bs-dismiss="modal"
            aria-label="Close"
          ></button>
        </div>
        <div class="modal-body">
          <instrument-form
            ref="formRef"
            :initial-data="instrument"
            @submit="handleSave"
          />
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">
            Cancel
          </button>
          <button type="button" class="btn btn-primary" @click="triggerSubmit">
            {{ isEditing ? 'Update' : 'Save' }} Instrument
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import InstrumentForm from './instrument-form.vue'
import { Instrument } from '../../models/instrument'

interface Props {
  modalId?: string
  instrument?: Partial<Instrument>
}

const props = withDefaults(defineProps<Props>(), {
  modalId: 'instrumentModal',
  instrument: () => ({})
})

const emit = defineEmits<{
  save: [data: Partial<Instrument>]
}>()

const formRef = ref<InstanceType<typeof InstrumentForm>>()

const isEditing = computed(() => !!props.instrument?.id)

const handleSave = (data: Partial<Instrument>) => {
  emit('save', data)
}

const triggerSubmit = () => {
  formRef.value?.handleSubmit()
}
</script>