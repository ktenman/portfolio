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
          <instrument-form :initial-data="instrument" @submit="handleSave" />
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
          <button type="submit" class="btn btn-primary" form="instrumentForm">
            {{ isEditing ? 'Update' : 'Save' }} Instrument
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import InstrumentForm from './instrument-form.vue'
import { Instrument } from '../../models/instrument'

interface Props {
  modalId?: string
  instrument?: Partial<Instrument>
}

const props = withDefaults(defineProps<Props>(), {
  modalId: 'instrumentModal',
  instrument: () => ({}),
})

const emit = defineEmits<{
  save: [data: Partial<Instrument>]
}>()

const isEditing = computed(() => !!props.instrument?.id)

const handleSave = (data: Partial<Instrument>) => {
  emit('save', data)
}
</script>
