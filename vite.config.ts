import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  root: 'ui',
  plugins: [vue()],
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
  },
  test: {
    environment: 'happy-dom',
    globals: true,
    setupFiles: './tests/setup.ts',
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
        '.eslintrc.cjs',
        'app.vue',
      ],
    },
  },
})
