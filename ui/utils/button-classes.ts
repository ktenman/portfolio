import { FEATURE_FLAGS } from '../config/features'

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'success' | 'warning' | 'info' | 'light' | 'dark' | 
  'outline-primary' | 'outline-secondary' | 'outline-danger' | 'outline-success' | 'outline-warning' | 'outline-info'
export type ButtonSize = 'sm' | 'md' | 'lg'

export function getButtonClass(variant: ButtonVariant = 'primary', size: ButtonSize = 'md'): string {
  if (!FEATURE_FLAGS.tailwindButtons) {
    // Bootstrap classes
    const sizeClass = size === 'sm' ? 'btn-sm' : size === 'lg' ? 'btn-lg' : ''
    return `btn btn-${variant} ${sizeClass}`.trim()
  }
  
  // Tailwind classes
  return getTailwindButtonClass(variant, size)
}

function getTailwindButtonClass(variant: ButtonVariant, size: ButtonSize): string {
  // Use CSS classes instead of inline Tailwind utilities for better hover support
  const sizeClass = size === 'sm' ? 'tw-btn-sm' : size === 'lg' ? 'tw-btn-lg' : ''
  const variantClass = `tw-btn-${variant}`
  
  return `${variantClass} ${sizeClass}`.trim()
}

// Helper function to add button classes to existing elements
export function applyButtonClasses(element: HTMLElement, variant: ButtonVariant = 'primary', size: ButtonSize = 'md'): void {
  const classes = getButtonClass(variant, size).split(' ')
  element.classList.add(...classes)
}