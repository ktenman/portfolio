import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  test: {
    globals: true,
    environment: 'happy-dom',
    setupFiles: ['./ui/tests/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      exclude: [
        'node_modules/**',
        'ui/tests/**',
        '**/*.d.ts',
        '**/*.config.*',
        '**/mockServiceWorker.js',
        '**/*.spec.ts',
        '**/*.test.ts',
        '**/*.vue',
        'ui/main.ts',
        'ui/app.vue',
        'ui/.eslintrc.cjs',
        'ui/public/**',
        'ui/decorators/**',
        'coverage/**',
        'dist/**',
        '**/node_modules/**',
        '**/prettify.js',
        '**/sort.js',
        '**/report.js',
        '**/build-info.js',
        '**/*.mjs',
        '**/*.esm.js',
        '**/index.mjs',
      ],
      thresholds: {
        lines: 70,
        functions: 70,
        branches: 70,
        statements: 70,
      },
    },
    include: ['ui/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts,jsx,tsx}'],
    exclude: ['node_modules', 'dist', '.idea', '.git', '.cache'],
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./ui', import.meta.url)),
    },
  },
})
