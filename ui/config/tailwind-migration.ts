export interface MigrationItem {
  component: string
  filePath: string
  bootstrapClasses: string[]
  tailwindEquivalents: string[]
  customCSS: boolean
  jsDepedencies: string[]
  complexity: 'low' | 'medium' | 'high'
  status: 'pending' | 'in-progress' | 'completed' | 'tested'
}

export const migrationInventory: MigrationItem[] = [
  {
    component: 'DataTable',
    filePath: 'ui/components/shared/data-table.vue',
    bootstrapClasses: [
      'table',
      'table-striped',
      'table-hover',
      'table-responsive',
      'spinner-border',
      'alert',
      'alert-danger',
      'alert-info',
      'visually-hidden',
      'text-end',
    ],
    tailwindEquivalents: [
      'min-w-full',
      'divide-y',
      'divide-gray-200',
      '[&>tbody>tr:nth-child(odd)]:bg-gray-50',
      'hover:bg-gray-100',
      'overflow-x-auto',
      'animate-spin',
      'bg-red-50',
      'border-red-400',
      'text-red-700',
      'bg-blue-50',
      'sr-only',
      'text-right',
    ],
    customCSS: true,
    jsDepedencies: [],
    complexity: 'high',
    status: 'pending',
  },
  {
    component: 'TransactionModal',
    filePath: 'ui/components/transactions/transaction-modal.vue',
    bootstrapClasses: [
      'modal',
      'fade',
      'modal-dialog',
      'modal-content',
      'modal-header',
      'modal-title',
      'modal-body',
      'modal-footer',
      'btn-close',
      'btn',
      'btn-secondary',
      'btn-primary',
    ],
    tailwindEquivalents: [
      'fixed',
      'inset-0',
      'z-50',
      'overflow-y-auto',
      'flex',
      'items-center',
      'justify-center',
      'bg-white',
      'rounded-lg',
      'shadow-xl',
      'p-6',
    ],
    customCSS: false,
    jsDepedencies: ['bootstrap/js/dist/modal', 'useBootstrapModal'],
    complexity: 'high',
    status: 'pending',
  },
  {
    component: 'FormInput',
    filePath: 'ui/components/shared/form-input.vue',
    bootstrapClasses: [
      'mb-3',
      'form-label',
      'form-select',
      'form-control',
      'is-invalid',
      'invalid-feedback',
    ],
    tailwindEquivalents: [
      'mb-3',
      'block',
      'text-sm',
      'font-medium',
      'text-gray-700',
      'block',
      'w-full',
      'rounded-md',
      'border-gray-300',
      'shadow-sm',
      'border-red-500',
      'text-red-600',
      'text-sm',
      'mt-1',
    ],
    customCSS: false,
    jsDepedencies: [],
    complexity: 'medium',
    status: 'completed',
  },
  {
    component: 'NavBar',
    filePath: 'ui/components/nav-bar.vue',
    bootstrapClasses: [
      'navbar',
      'navbar-expand-lg',
      'navbar-light',
      'bg-light',
      'container-fluid',
      'navbar-brand',
      'navbar-toggler',
      'navbar-nav',
      'nav-link',
    ],
    tailwindEquivalents: [
      'bg-white',
      'shadow',
      'px-4',
      'py-2',
      'flex',
      'justify-between',
      'items-center',
      'text-lg',
      'font-semibold',
      'hover:text-blue-600',
    ],
    customCSS: false,
    jsDepedencies: [],
    complexity: 'medium',
    status: 'pending',
  },
  {
    component: 'LoadingSpinner',
    filePath: 'ui/components/shared/loading-spinner.vue',
    bootstrapClasses: ['spinner-border', 'text-primary', 'visually-hidden'],
    tailwindEquivalents: [
      'animate-spin',
      'rounded-full',
      'h-8',
      'w-8',
      'border-b-2',
      'border-blue-600',
      'sr-only',
    ],
    customCSS: false,
    jsDepedencies: [],
    complexity: 'low',
    status: 'completed',
  },
  {
    component: 'ConfirmDialog',
    filePath: 'ui/components/shared/confirm-dialog.vue',
    bootstrapClasses: [
      'modal',
      'fade',
      'modal-dialog',
      'modal-content',
      'modal-header',
      'modal-body',
      'modal-footer',
      'btn',
      'btn-secondary',
      'btn-danger',
    ],
    tailwindEquivalents: [
      'fixed',
      'inset-0',
      'z-50',
      'bg-white',
      'rounded-lg',
      'shadow-xl',
      'p-6',
      'bg-gray-500',
      'bg-red-600',
      'text-white',
      'hover:bg-red-700',
    ],
    customCSS: false,
    jsDepedencies: ['bootstrap/js/dist/modal', 'useBootstrapModal'],
    complexity: 'high',
    status: 'pending',
  },
  {
    component: 'TransactionForm',
    filePath: 'ui/components/transactions/transaction-form.vue',
    bootstrapClasses: ['form-control', 'form-select', 'is-invalid', 'invalid-feedback', 'mb-3'],
    tailwindEquivalents: [
      'block',
      'w-full',
      'rounded-md',
      'border-gray-300',
      'shadow-sm',
      'border-red-500',
      'text-red-600',
      'text-sm',
      'mb-3',
    ],
    customCSS: false,
    jsDepedencies: [],
    complexity: 'medium',
    status: 'pending',
  },
  {
    component: 'CrudLayout',
    filePath: 'ui/components/shared/crud-layout.vue',
    bootstrapClasses: [
      'container-fluid',
      'row',
      'col',
      'btn',
      'btn-primary',
      'btn-sm',
      'btn-outline-secondary',
      'btn-outline-danger',
    ],
    tailwindEquivalents: [
      'w-full',
      'px-4',
      'flex',
      'flex-wrap',
      'w-full',
      'bg-blue-600',
      'text-white',
      'px-3',
      'py-1',
      'text-sm',
      'border',
      'border-gray-400',
      'border-red-600',
    ],
    customCSS: false,
    jsDepedencies: [],
    complexity: 'medium',
    status: 'pending',
  },
  {
    component: 'PortfolioChart',
    filePath: 'ui/components/portfolio/portfolio-chart.vue',
    bootstrapClasses: ['mb-3'],
    tailwindEquivalents: ['mb-3', 'lg:h-[25rem]'],
    customCSS: true,
    jsDepedencies: [],
    complexity: 'low',
    status: 'completed',
  },
  {
    component: 'Calculator',
    filePath: 'ui/components/calculator.vue',
    bootstrapClasses: [
      'container',
      'row',
      'col-md-6',
      'form-control',
      'btn',
      'btn-primary',
      'btn-secondary',
      'mb-3',
    ],
    tailwindEquivalents: [
      'max-w-7xl',
      'mx-auto',
      'px-4',
      'grid',
      'grid-cols-1',
      'md:grid-cols-2',
      'gap-4',
      'block',
      'w-full',
      'bg-blue-600',
      'bg-gray-500',
      'mb-3',
    ],
    customCSS: false,
    jsDepedencies: [],
    complexity: 'medium',
    status: 'pending',
  },
]

export function getComponentsByComplexity(complexity: 'low' | 'medium' | 'high'): MigrationItem[] {
  return migrationInventory.filter(item => item.complexity === complexity)
}

export function getComponentsByStatus(status: MigrationItem['status']): MigrationItem[] {
  return migrationInventory.filter(item => item.status === status)
}

export function updateComponentStatus(component: string, status: MigrationItem['status']): void {
  const item = migrationInventory.find(i => i.component === component)
  if (item) {
    item.status = status
  }
}

export function getMigrationProgress(): { total: number; completed: number; percentage: number } {
  const total = migrationInventory.length
  const completed = migrationInventory.filter(
    i => i.status === 'completed' || i.status === 'tested'
  ).length
  return {
    total,
    completed,
    percentage: Math.round((completed / total) * 100),
  }
}
