<template>
  <modal-shell
    :open="open"
    modal-id="instrumentModal"
    :title="isEditing ? 'Edit Instrument' : 'Add New Instrument'"
    @update:open="emit('update:open', $event)"
  >
    <instrument-form :initial-data="instrument" @submit="handleSave" />
    <template #footer>
      <AppButton variant="secondary" @click="emit('update:open', false)">Cancel</AppButton>
      <AppButton variant="primary" type="submit" form="instrumentForm">
        {{ isEditing ? 'Update' : 'Save' }} Instrument
      </AppButton>
    </template>
  </modal-shell>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ModalShell from '../shared/modal-shell.vue'
import AppButton from '../shared/app-button.vue'
import InstrumentForm from './instrument-form.vue'
import { InstrumentDto } from '../../models/generated/domain-models'

interface Props {
  open: boolean
  instrument?: Partial<InstrumentDto>
}

const props = withDefaults(defineProps<Props>(), {
  instrument: () => ({}),
})

const emit = defineEmits<{
  save: [data: Partial<InstrumentDto>]
  'update:open': [value: boolean]
}>()

const isEditing = computed(() => !!props.instrument?.id)

const handleSave = (data: Partial<InstrumentDto>) => {
  emit('save', data)
}
</script>
