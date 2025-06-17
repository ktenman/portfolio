<template>
  <div class="container mt-4">
    <h2 class="mb-4">Button Styles Migration Test</h2>
    
    <div class="alert" :class="FEATURE_FLAGS.tailwindButtons ? 'alert-success' : 'alert-info'">
      <p class="mb-0">
        <strong>Feature Flag Status:</strong> 
        {{ FEATURE_FLAGS.tailwindButtons ? 'Tailwind Enabled ✓' : 'Bootstrap Version (Current)' }}
      </p>
    </div>

    <div class="row mt-4">
      <div class="col-12">
        <h3>Button Variants</h3>
        
        <!-- Primary Buttons -->
        <div class="mb-3">
          <h5>Primary</h5>
          <button :class="getButtonClass('primary')">Primary</button>
          <button :class="getButtonClass('primary', 'sm')" class="ms-2">Small</button>
          <button :class="getButtonClass('primary', 'lg')" class="ms-2">Large</button>
          <button :class="getButtonClass('primary')" class="ms-2" disabled>Disabled</button>
        </div>
        
        <!-- Debug Test -->
        <div class="mb-3">
          <h5>Debug Hover Test</h5>
          <button class="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 hover:text-white">
            Direct Tailwind Test
          </button>
          <button class="px-4 py-2 bg-blue-600 text-white rounded ms-2" 
                  style="transition: all 0.2s"
                  @mouseenter="$event.target.style.backgroundColor = '#1d4ed8'; $event.target.style.color = '#ffffff'"
                  @mouseleave="$event.target.style.backgroundColor = '#2563eb'; $event.target.style.color = '#ffffff'">
            Inline Style Test
          </button>
          <div class="mt-2">
            <code>Classes: {{ getButtonClass('primary') }}</code>
          </div>
        </div>
        
        <!-- Secondary Buttons -->
        <div class="mb-3">
          <h5>Secondary</h5>
          <button :class="getButtonClass('secondary')">Secondary</button>
          <button :class="getButtonClass('secondary', 'sm')" class="ms-2">Small</button>
          <button :class="getButtonClass('secondary')" class="ms-2" disabled>Disabled</button>
        </div>
        
        <!-- Danger Buttons -->
        <div class="mb-3">
          <h5>Danger</h5>
          <button :class="getButtonClass('danger')">Danger</button>
          <button :class="getButtonClass('danger', 'sm')" class="ms-2">Small</button>
          <button :class="getButtonClass('danger')" class="ms-2" disabled>Disabled</button>
        </div>
        
        <!-- Outline Buttons -->
        <div class="mb-3">
          <h5>Outline Variants</h5>
          <button :class="getButtonClass('outline-primary')">Outline Primary</button>
          <button :class="getButtonClass('outline-secondary')" class="ms-2">Outline Secondary</button>
          <button :class="getButtonClass('outline-danger')" class="ms-2">Outline Danger</button>
        </div>
        
        <!-- Icon Buttons -->
        <div class="mb-3">
          <h5>With Icons</h5>
          <button :class="getButtonClass('primary')" class="d-inline-flex align-items-center">
            <base-icon name="pencil" :size="16" />
            <span class="ms-1">Edit</span>
          </button>
          <button :class="getButtonClass('danger', 'sm')" class="ms-2 d-inline-flex align-items-center">
            <base-icon name="trash" :size="14" />
            <span class="ms-1">Delete</span>
          </button>
          <button :class="getButtonClass('secondary', 'sm')" class="ms-2 d-inline-flex align-items-center">
            <base-icon name="plus" :size="14" />
            <span class="ms-1">Add</span>
          </button>
        </div>
      </div>
    </div>

    <div class="row mt-4">
      <div class="col-12">
        <h3>Bootstrap → Tailwind Class Mapping</h3>
        <div class="table-responsive">
          <table class="table table-sm">
            <thead>
              <tr>
                <th>Bootstrap</th>
                <th>Tailwind</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>btn btn-primary</code></td>
                <td><code>px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500</code></td>
              </tr>
              <tr>
                <td><code>btn btn-secondary</code></td>
                <td><code>px-4 py-2 bg-gray-500 text-white rounded hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-gray-400</code></td>
              </tr>
              <tr>
                <td><code>btn btn-danger</code></td>
                <td><code>px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500</code></td>
              </tr>
              <tr>
                <td><code>btn btn-sm</code></td>
                <td><code>px-3 py-1 text-sm</code></td>
              </tr>
              <tr>
                <td><code>btn btn-lg</code></td>
                <td><code>px-6 py-3 text-lg</code></td>
              </tr>
              <tr>
                <td><code>btn-outline-primary</code></td>
                <td><code>px-4 py-2 border-2 border-blue-600 text-blue-600 rounded hover:bg-blue-600 hover:text-white</code></td>
              </tr>
              <tr>
                <td><code>disabled</code></td>
                <td><code>opacity-50 cursor-not-allowed</code></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="mt-4">
      <h3>Usage Examples</h3>
      <pre class="bg-light p-3 rounded"><code>// Current usage in components:
&lt;button class="btn btn-primary"&gt;Save&lt;/button&gt;
&lt;button class="btn btn-sm btn-secondary"&gt;Cancel&lt;/button&gt;

// After migration (with feature flag):
// The same code works, but classes are replaced internally</code></pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { FEATURE_FLAGS } from '../../config/features'
import BaseIcon from '../shared/base-icon.vue'

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'outline-primary' | 'outline-secondary' | 'outline-danger'
type ButtonSize = 'sm' | 'md' | 'lg'

const getButtonClass = (variant: ButtonVariant, size: ButtonSize = 'md'): string => {
  if (!FEATURE_FLAGS.tailwindButtons) {
    // Bootstrap classes
    const sizeClass = size === 'sm' ? 'btn-sm' : size === 'lg' ? 'btn-lg' : ''
    return `btn btn-${variant} ${sizeClass}`.trim()
  }
  
  // Tailwind classes
  const sizeClasses = {
    sm: 'px-3 py-1 text-sm',
    md: 'px-4 py-2',
    lg: 'px-6 py-3 text-lg'
  }
  
  const variantClasses = {
    primary: 'bg-blue-600 text-white hover:bg-blue-700 focus:ring-blue-500',
    secondary: 'bg-gray-500 text-white hover:bg-gray-600 focus:ring-gray-400',
    danger: 'bg-red-600 text-white hover:bg-red-700 focus:ring-red-500',
    'outline-primary': 'border-2 border-blue-600 text-blue-600 hover:bg-blue-600 hover:text-white focus:ring-blue-500',
    'outline-secondary': 'border-2 border-gray-500 text-gray-600 hover:bg-gray-500 hover:text-white focus:ring-gray-400',
    'outline-danger': 'border-2 border-red-600 text-red-600 hover:bg-red-600 hover:text-white focus:ring-red-500'
  }
  
  const baseClasses = 'inline-flex items-center justify-center font-medium rounded transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed'
  
  return `${baseClasses} ${sizeClasses[size]} ${variantClasses[variant]}`
}
</script>