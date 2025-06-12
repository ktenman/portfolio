// eslint.config.js
import vueEslintParser from 'vue-eslint-parser'
import vuePlugin from 'eslint-plugin-vue'
import typescriptEslintParser from '@typescript-eslint/parser'
import typescriptEslintPlugin from '@typescript-eslint/eslint-plugin'
import prettierPlugin from 'eslint-plugin-prettier'
import unusedImportsPlugin from 'eslint-plugin-unused-imports'
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
      'unused-imports': unusedImportsPlugin,
    },
    rules: {
      // Example: disable the multi-word component names rule
      'vue/multi-word-component-names': 'off',
      // Unused imports detection
      'unused-imports/no-unused-imports': 'error',
      'unused-imports/no-unused-vars': [
        'warn',
        {
          vars: 'all',
          varsIgnorePattern: '^_',
          args: 'after-used',
          argsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
          ignoreRestSiblings: true,
        },
      ],
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
        project: './tsconfig.json',
      },
    },
    plugins: {
      '@typescript-eslint': typescriptEslintPlugin,
      prettier: prettierPlugin,
      'unused-imports': unusedImportsPlugin,
    },
    rules: {
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': 'off', // Using unused-imports plugin instead
      'unused-imports/no-unused-imports': 'error',
      'unused-imports/no-unused-vars': [
        'warn',
        {
          vars: 'all',
          varsIgnorePattern: '^_',
          args: 'after-used',
          argsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
          ignoreRestSiblings: true,
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
