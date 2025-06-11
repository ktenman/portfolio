// eslint.config.js
import vueEslintParser from 'vue-eslint-parser'
import vuePlugin from 'eslint-plugin-vue'
import typescriptEslintParser from '@typescript-eslint/parser'
import typescriptEslintPlugin from '@typescript-eslint/eslint-plugin'
import prettierPlugin from 'eslint-plugin-prettier'
import * as espree from 'espree'

export default [
  // Global defaults for all files
  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
    },
    rules: {
      // Place global rules here if needed.
    },
  },

  // Configuration for Vue Single-File Components (*.vue)
  {
    files: ['**/*.vue'],
    languageOptions: {
      // Use vue-eslint-parser to parse Vue SFCs
      parser: vueEslintParser,
      parserOptions: {
        // Delegate the script section to @typescript-eslint/parser
        parser: typescriptEslintParser,
        ecmaVersion: 'latest',
        sourceType: 'module',
      },
    },
    plugins: {
      vue: vuePlugin,
    },
    rules: {
      // Example: disable the multi-word component names rule
      'vue/multi-word-component-names': 'off',
      // Add more Vue rules as needed
    },
  },

  // Configuration for TypeScript files (*.ts, *.tsx)
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      parser: typescriptEslintParser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
      },
    },
    plugins: {
      '@typescript-eslint': typescriptEslintPlugin,
      prettier: prettierPlugin,
    },
    rules: {
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': [
        'warn',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
        },
      ],
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/explicit-module-boundary-types': 'off',
      'prettier/prettier': 'error',
      // Add more TypeScript rules here as needed
    },
  },

  // Configuration for JavaScript files (*.js, *.mjs, *.cjs)
  {
    files: ['**/*.{js,mjs,cjs}'],
    languageOptions: {
      parser: espree,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
      },
    },
  },
]
