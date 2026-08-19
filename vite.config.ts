import { defineConfig } from 'vite'
import { configDefaults } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  root: 'ui',
  plugins: [vue(), tailwindcss()],
  server: {
    port: 61234,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../dist', // This will output the build artifacts to /app/dist when the root is /app/ui
    sourcemap: true,
    target: 'esnext',
  },
  test: {
    environment: 'happy-dom',
    css: { include: [/theme\.css/] },
    globals: true,
    setupFiles: './tests/setup.ts',
    exclude: [...configDefaults.exclude, 'tests/visual/**'],
    disableConsoleIntercept: true,
    reporters: ['default', 'html', 'junit'],
    outputFile: {
      junit: '../test-results/junit.xml',
      html: '../test-results/html/index.html',
    },
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      reportsDirectory: '../coverage',
      exclude: [
        'node_modules/',
        'tests/',
        '**/*.d.ts',
        '**/*.config.*',
        '**/main.ts',
        '**/env.d.ts',
        'coverage/',
        'app.vue',
      ],
    },
  },
})
