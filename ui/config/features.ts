export const FEATURE_FLAGS = {
  tailwindModals: import.meta.env.VITE_FEATURE_TW_MODALS === 'true',
  tailwindForms: import.meta.env.VITE_FEATURE_TW_FORMS === 'true',
  tailwindTables: import.meta.env.VITE_FEATURE_TW_TABLES === 'true',
  tailwindButtons: import.meta.env.VITE_FEATURE_TW_BUTTONS === 'true',
  tailwindAlerts: import.meta.env.VITE_FEATURE_TW_ALERTS === 'true',
  tailwindSpinners: import.meta.env.VITE_FEATURE_TW_SPINNERS === 'true',
  tailwindNavigation: import.meta.env.VITE_FEATURE_TW_NAVIGATION === 'true',
  tailwindCharts: import.meta.env.VITE_FEATURE_TW_CHARTS === 'true',
}

export function isFeatureEnabled(feature: keyof typeof FEATURE_FLAGS): boolean {
  return FEATURE_FLAGS[feature]
}

export function getEnabledFeatures(): string[] {
  return Object.entries(FEATURE_FLAGS)
    .filter(([_, enabled]) => enabled)
    .map(([feature]) => feature)
}

export function logFeatureFlags(): void {
  const enabledFeatures = getEnabledFeatures()
  if (enabledFeatures.length > 0) {
    console.log('Tailwind features enabled:', enabledFeatures.join(', '))
  } else {
    console.log('All components using Bootstrap (no Tailwind features enabled)')
  }
}
