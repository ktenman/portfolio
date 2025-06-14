<template>
  <data-table
    :items="instruments"
    :columns="columns"
    :is-loading="isLoading"
    :is-error="isError"
    :error-message="errorMessage"
    empty-message="No instruments found. Add a new instrument to get started."
  >
    <template #cell-instrument="{ item }">
      <div class="instrument-info">
        <div :class="cn('small', styles.utilities.textTruncate)">{{ item.name }}</div>
        <small :class="styles.text.muted">{{ item.symbol }}</small>
      </div>
    </template>

    <template #cell-type="{ item }">
      {{ item.type || item.category || '-' }}
    </template>

    <template #cell-profit="{ item }">
      <span :class="getProfitClass(item.profit)">
        {{ formatProfitLoss(item.profit) }}
      </span>
    </template>

    <template #actions="{ item }">
      <button :class="cn(styles.buttons.secondary, styles.buttons.sm)" @click="$emit('edit', item)">
        <base-icon name="pencil" :size="14" />
        <span :class="cn('d-none', 'd-lg-inline', styles.spacing.ms(1))">Edit</span>
      </button>
    </template>
  </data-table>
</template>

<script setup lang="ts">
import DataTable from '../shared/data-table.vue'
import BaseIcon from '../shared/base-icon.vue'
import { Instrument } from '../../models/instrument'
import { instrumentColumns } from '../../config'
import { formatProfitLoss, getProfitClass } from '../../utils/formatters'
import { styleClasses as styles, cn } from '../../utils/style-classes'

interface Props {
  instruments: Instrument[]
  isLoading?: boolean
  isError?: boolean
  errorMessage?: string
}

withDefaults(defineProps<Props>(), {
  isLoading: false,
  isError: false,
})

defineEmits<{
  edit: [instrument: Instrument]
}>()

const columns = instrumentColumns
</script>

<style scoped lang="scss">
@import '../../styles/shared-table.scss';

@media (max-width: 992px) {
  .instrument-info > div:first-child {
    max-width: 20ch;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media (max-width: 992px) {
  :deep(.hide-on-mobile) {
    display: none !important;
  }
}
</style>
