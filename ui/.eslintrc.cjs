module.exports = {
  root: true,
  ignorePatterns: ['models/generated/**'],
  env: {
    browser: true,
    node: true,
  },
  parserOptions: {
    parser: '@typescript-eslint/parser',
    ecmaVersion: 2021,
    sourceType: 'module',
  },
  plugins: ['no-comments'],
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:vue/vue3-recommended',
    'plugin:prettier/recommended',
  ],
  rules: {
    'no-unused-vars': 'warn',
    'no-console': 'warn',
    '@typescript-eslint/no-explicit-any': 'off',
    '@typescript-eslint/explicit-module-boundary-types': 'off',
    'prettier/prettier': 'error',
    // Allow strategic comments for complex logic and business rules
    'no-comments/disallowComments': ['warn', {
      allow: [
        '///', // TypeScript triple-slash directives
        '// TODO:', // Future improvements
        '// FIXME:', // Known issues
        '// HACK:', // Temporary workarounds
        '// NOTE:', // Important explanations
        '// WARNING:', // Critical information
        '// @', // JSDoc annotations
      ]
    }],
  },
}
