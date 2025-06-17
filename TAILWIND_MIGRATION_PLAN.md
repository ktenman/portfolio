# Tailwind CSS v4.1 Migration Plan

## Overview

This document outlines the comprehensive plan for migrating the Portfolio Management System from Bootstrap 5.3.5 to Tailwind CSS v4.1.

**Migration Branch**: `feature/tailwind-v4.1-migration`  
**Rollback Tag**: `pre-tailwind-v4.1-migration`  
**Estimated Timeline**: 10-15 days  
**Strategy**: Hybrid transition with feature flags

## Current State Analysis

### Bootstrap Usage

- **Version**: Bootstrap 5.3.5 with custom SCSS architecture
- **JavaScript Dependencies**: Bootstrap Modal API (`bootstrap/js/dist/modal`)
- **Custom Components**:
  - `useBootstrapModal` composable
  - Custom SCSS with Bootstrap overrides
  - Custom breakpoints: 666px and 794px
- **Key Components**: Tables, Forms, Modals, Grid system, Buttons, Alerts, Spinners

### Component Inventory

Created tracking system in `ui/config/tailwind-migration.ts` with 10 components categorized by complexity:

- **High Complexity** (3): DataTable, TransactionModal, ConfirmDialog
- **Medium Complexity** (5): FormInput, NavBar, TransactionForm, CrudLayout, Calculator
- **Low Complexity** (2): LoadingSpinner, PortfolioChart

## Migration Strategy: Hybrid Transition

### Why Hybrid Approach?

- Allows gradual migration with reduced risk
- Enables A/B testing with feature flags
- Provides immediate rollback capability
- Maintains production stability during migration

### Key Principles

1. Keep Bootstrap and Tailwind running in parallel initially
2. Use feature flags for component-by-component rollout
3. Create adapter patterns for seamless switching
4. Comprehensive testing before removing Bootstrap

## Detailed Migration Phases

### Phase 1: Setup & Configuration (Days 1-2)

#### 1.1 Install Tailwind CSS v4.1

```bash
npm install -D tailwindcss@latest @tailwindcss/vite@latest
```

#### 1.2 Create Tailwind Configuration

```css
/* ui/styles/tailwind.css */
@import 'tailwindcss';

@theme {
  /* Custom breakpoints matching Bootstrap */
  --breakpoint-xs: 576px;
  --breakpoint-sm: 666px; /* Custom */
  --breakpoint-md: 768px;
  --breakpoint-md-lg: 794px; /* Custom */
  --breakpoint-lg: 992px;
  --breakpoint-xl: 1200px;

  /* Colors from Bootstrap overrides */
  --color-primary: #007bff;
  --color-secondary: #6c757d;
  --color-success: #28a745;
  --color-danger: #dc3545;
  --color-warning: #ffc107;
  --color-info: #17a2b8;
}
```

#### 1.3 Update Vite Configuration

```typescript
// vite.config.ts
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
})
```

#### 1.4 Create Feature Flags System

```typescript
// ui/config/features.ts
export const FEATURE_FLAGS = {
  tailwindModals: import.meta.env.VITE_FEATURE_TW_MODALS === 'true',
  tailwindForms: import.meta.env.VITE_FEATURE_TW_FORMS === 'true',
  tailwindTables: import.meta.env.VITE_FEATURE_TW_TABLES === 'true',
  tailwindButtons: import.meta.env.VITE_FEATURE_TW_BUTTONS === 'true',
}
```

### Phase 2: Component Migration (Days 3-10)

#### 2.1 Modal System (High Priority - Days 3-4)

**Challenge**: Direct Bootstrap JavaScript dependency

**Solution**: Create Tailwind modal with adapter pattern

```typescript
// ui/composables/use-tailwind-modal.ts
export function useTailwindModal(modalId: string) {
  const isOpen = ref(false)
  const modalRef = ref<HTMLElement>()

  const show = () => {
    isOpen.value = true
    document.body.style.overflow = 'hidden'
  }

  const hide = () => {
    isOpen.value = false
    document.body.style.overflow = ''
  }

  // Handle escape key and backdrop clicks
  useEventListener('keydown', e => {
    if (e.key === 'Escape' && isOpen.value) hide()
  })

  return { isOpen, show, hide, modalRef }
}

// Adapter pattern
export function useModal(modalId: string) {
  if (FEATURE_FLAGS.tailwindModals) {
    return useTailwindModal(modalId)
  }
  return useBootstrapModal(modalId)
}
```

#### 2.2 Data Tables (High Complexity - Days 5-6)

**Challenge**: Complex responsive behavior with mobile card view at 666px

**Solution**: Use Tailwind v4.1 container queries

```css
/* Mobile card transformation */
@media (max-width: 666px) {
  [data-mobile='card'] {
    @apply block w-full;
  }
  [data-mobile='card'] thead {
    @apply hidden;
  }
  [data-mobile='card'] tr {
    @apply block mb-4 border-b;
  }
  [data-mobile='card'] td {
    @apply flex justify-between items-center;
  }
  [data-mobile='card'] td:before {
    content: attr(data-label);
    @apply font-bold text-gray-600 mr-2;
  }
}
```

#### 2.3 Form Components (Medium Complexity - Days 7-8)

**Mapping Bootstrap to Tailwind**:

- `form-control` → `block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500`
- `is-invalid` → `border-red-500 focus:border-red-500`
- `invalid-feedback` → `text-red-600 text-sm mt-1`

#### 2.4 Buttons & Utilities (Medium Priority - Days 9-10)

**Button Mapping**:

- `btn btn-primary` → `bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500`
- `btn btn-secondary` → `bg-gray-500 text-white px-4 py-2 rounded hover:bg-gray-600`
- `btn btn-danger` → `bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700`

### Phase 3: Testing & Optimization (Days 11-13)

#### 3.1 Visual Regression Testing

```typescript
// tests/visual-regression.spec.ts
import { test, expect } from '@playwright/test'

const components = [
  { name: 'DataTable', route: '/instruments' },
  { name: 'Modal', route: '/instruments', action: 'click-add' },
  { name: 'Forms', route: '/calculator' },
]

for (const component of components) {
  test(`${component.name} visual consistency`, async ({ page }) => {
    await page.goto(component.route)
    if (component.action) {
      await page.click(`[data-test="${component.action}"]`)
    }
    await expect(page).toHaveScreenshot(`${component.name}-baseline.png`)
  })
}
```

#### 3.2 Performance Testing

- Measure bundle size before/after
- Test build times with Tailwind v4.1's 5x faster engine
- Monitor runtime performance

### Phase 4: Cleanup (Days 14-15)

#### 4.1 Remove Bootstrap (Only after full verification)

```bash
npm uninstall bootstrap
rm -rf ui/styles/framework/bootstrap.scss
rm -rf ui/styles/bootstrap-overrides.scss
```

#### 4.2 Update Documentation

- Update CLAUDE.md with new Tailwind setup
- Document utility class mappings
- Add Tailwind-specific guidelines

## Component-Specific Migration Details

### DataTable Component

**Current Bootstrap Classes**:

- `table`, `table-striped`, `table-hover`, `table-responsive`
- `spinner-border`, `alert`, `alert-danger`, `alert-info`

**Tailwind Replacement**:

```vue
<div class="overflow-x-auto">
  <table class="min-w-full divide-y divide-gray-200">
    <thead class="bg-gray-50">
      <tr>
        <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
          <!-- Header content -->
        </th>
      </tr>
    </thead>
    <tbody class="bg-white divide-y divide-gray-200">
      <tr class="hover:bg-gray-50">
        <td class="px-6 py-4 whitespace-nowrap">
          <!-- Cell content -->
        </td>
      </tr>
    </tbody>
  </table>
</div>
```

### Modal Component

**Current Bootstrap Structure**:

```vue
<div class="modal fade" tabindex="-1"></div>
```

**Tailwind Replacement**:

```vue
<Teleport to="body">
  <Transition
    enter-active-class="transition ease-out duration-300"
    enter-from-class="opacity-0"
    enter-to-class="opacity-100"
    leave-active-class="transition ease-in duration-200"
    leave-from-class="opacity-100"
    leave-to-class="opacity-0"
  >
    <div v-if="isOpen" class="fixed inset-0 z-50 overflow-y-auto">
      <!-- Backdrop -->
      <div class="fixed inset-0 bg-black/50" @click="hide"></div>
      
      <!-- Modal -->
      <div class="relative min-h-screen flex items-center justify-center p-4">
        <div class="relative bg-white rounded-lg shadow-xl max-w-md w-full">
          <!-- Header -->
          <div class="border-b px-6 py-4">
            <h3 class="text-lg font-semibold">{{ title }}</h3>
          </div>
          <!-- Body -->
          <div class="px-6 py-4">
            <slot />
          </div>
          <!-- Footer -->
          <div class="border-t px-6 py-4 flex justify-end space-x-2">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </div>
  </Transition>
</Teleport>
```

## Tailwind v4.1 Specific Features to Leverage

### 1. Text Shadows (New in v4.1)

```css
/* For financial headers */
.portfolio-title {
  @apply text-4xl font-bold text-shadow-sm;
}

/* For improved readability on charts */
.chart-label {
  @apply text-sm text-shadow-xs;
}
```

### 2. Mask Utilities (New in v4.1)

```css
/* Loading skeleton */
.skeleton {
  @apply bg-gray-200 mask-gradient-to-r from-transparent via-black to-transparent animate-pulse;
}
```

### 3. Container Queries (Native in v4.0+)

```css
@container (min-width: 400px) {
  .responsive-grid {
    @apply grid-cols-2;
  }
}

@container (min-width: 768px) {
  .responsive-grid {
    @apply grid-cols-3;
  }
}
```

### 4. Improved Browser Compatibility (v4.1)

- Colors in older Safari versions
- Shadows and transforms in older browsers
- Graceful degradation for unsupported features

## Risk Mitigation

### 1. Feature Flag Rollout

```typescript
// .env.development
VITE_FEATURE_TW_MODALS = false
VITE_FEATURE_TW_FORMS = false
VITE_FEATURE_TW_TABLES = false

// Gradual enablement in production
// Week 1: Enable modals (10% of users)
// Week 2: Enable forms (50% of users)
// Week 3: Enable tables (100% of users)
```

### 2. Rollback Strategy

```bash
# Quick rollback if needed
git checkout pre-tailwind-v4.1-migration
npm install
npm run build
```

### 3. Monitoring

- Track bundle size changes
- Monitor error rates during rollout
- Collect user feedback on UI changes

## Success Metrics

1. **Performance**

   - 60-70% reduction in CSS bundle size
   - 5x faster build times
   - No increase in runtime performance metrics

2. **Quality**

   - Zero visual regressions
   - All E2E tests passing
   - No increase in bug reports

3. **Developer Experience**
   - Faster development iteration
   - Easier style modifications
   - Better responsive design tools

## Next Steps

1. ✅ Created migration branch and rollback tag
2. ✅ Created component inventory and tracking system
3. ⏳ Install Tailwind CSS v4.1 and configure
4. ⏳ Implement feature flags
5. ⏳ Begin component migration starting with low-complexity items

## Commands Reference

```bash
# Development
npm run dev                    # Start dev server with both Bootstrap and Tailwind
npm run lint-format           # Ensure code quality

# Testing
npm run test                  # Run component tests
./test-runner.sh --e2e       # Run E2E tests

# Feature flag testing
VITE_FEATURE_TW_MODALS=true npm run dev

# Build for production
npm run build
```

## Migration Progress Tracking

Use the migration inventory system:

```typescript
import { getMigrationProgress, updateComponentStatus } from './ui/config/tailwind-migration'

// Check progress
const progress = getMigrationProgress()
console.log(`Migration ${progress.percentage}% complete`)

// Update component status
updateComponentStatus('LoadingSpinner', 'completed')
```

---

Last Updated: 2025-06-15  
Branch: `feature/tailwind-v4.1-migration`  
Author: Portfolio Dev Team
