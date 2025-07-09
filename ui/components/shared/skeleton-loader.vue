<template>
  <div class="skeleton-wrapper">
    <div v-if="type === 'card'" class="skeleton skeleton-card"></div>

    <div v-else-if="type === 'table'" class="skeleton-table">
      <div class="skeleton skeleton-header"></div>
      <div v-for="i in rows" :key="i" class="skeleton-row">
        <div v-for="j in columns" :key="j" class="skeleton skeleton-cell"></div>
      </div>
    </div>

    <div v-else-if="type === 'list'" class="skeleton-list">
      <div v-for="i in rows" :key="i" class="skeleton-list-item">
        <div class="skeleton skeleton-icon"></div>
        <div class="skeleton-content">
          <div class="skeleton skeleton-title"></div>
          <div class="skeleton skeleton-text"></div>
        </div>
      </div>
    </div>

    <div v-else-if="type === 'form'" class="skeleton-form">
      <div v-for="i in fields" :key="i" class="skeleton-form-group">
        <div class="skeleton skeleton-label"></div>
        <div class="skeleton skeleton-input"></div>
      </div>
    </div>

    <div v-else-if="type === 'text'" class="skeleton-text-block">
      <div
        v-for="i in lines"
        :key="i"
        class="skeleton skeleton-text"
        :style="{ width: i === lines ? '60%' : '100%' }"
      ></div>
    </div>

    <div v-else class="skeleton skeleton-custom" :style="customStyle"></div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  type?: 'card' | 'table' | 'list' | 'form' | 'text' | 'custom'
  rows?: number
  columns?: number
  fields?: number
  lines?: number
  customStyle?: Record<string, string>
}

withDefaults(defineProps<Props>(), {
  type: 'card',
  rows: 5,
  columns: 4,
  fields: 3,
  lines: 3,
})
</script>

<style scoped lang="scss">
.skeleton-wrapper {
  width: 100%;
}

.skeleton-table {
  .skeleton-header {
    height: 40px;
    margin-bottom: 1rem;
    border-radius: var(--radius-base);
  }

  .skeleton-row {
    display: flex;
    gap: 1rem;
    margin-bottom: 0.75rem;

    .skeleton-cell {
      flex: 1;
      height: 20px;
      border-radius: var(--radius-sm);
    }
  }
}

.skeleton-list {
  .skeleton-list-item {
    display: flex;
    gap: 1rem;
    margin-bottom: 1rem;

    .skeleton-icon {
      width: 48px;
      height: 48px;
      border-radius: var(--radius-base);
      flex-shrink: 0;
    }

    .skeleton-content {
      flex: 1;

      .skeleton-title {
        height: 20px;
        width: 60%;
        margin-bottom: 0.5rem;
      }

      .skeleton-text {
        height: 16px;
        width: 90%;
      }
    }
  }
}

.skeleton-form {
  .skeleton-form-group {
    margin-bottom: 1.5rem;

    .skeleton-label {
      height: 16px;
      width: 30%;
      margin-bottom: 0.5rem;
      border-radius: var(--radius-sm);
    }

    .skeleton-input {
      height: 38px;
      border-radius: var(--radius-base);
    }
  }
}

.skeleton-text-block {
  .skeleton-text {
    height: 16px;
    margin-bottom: 0.5rem;

    &:last-child {
      margin-bottom: 0;
    }
  }
}

.skeleton-custom {
  width: 100%;
  height: 200px;
}
</style>
