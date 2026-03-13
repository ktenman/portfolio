# Frontend Rules (Vue.js / TypeScript)

## Standards

- Use TypeScript strict mode
- Prefer composition API over options API
- Use `const` for all variables unless reassignment is required
- Extract reusable logic into composables (`use-*.ts`)
- Keep components focused and under 200 lines
- Use proper TypeScript types, avoid `any`
- **Use `useLocalStorage` from `@vueuse/core`** instead of direct `localStorage` access for reactive persistence

## After Making Changes

**ALWAYS run both after UI changes:**

```bash
npm run lint-format         # Type check + lint + format
npm test                    # All UI tests
```

## Knip - Unused Code Detection

The project uses [Knip](https://knip.dev/) to detect unused exports, dependencies, and files. Configuration is in `knip.json`.

```bash
npm run check-unused       # Check for unused code
npm run check-unused:fix   # Auto-fix some issues
```

- Configured for Vue 3 + TypeScript with `vue-tsc` compiler
- Entry points: `ui/main.ts` and `ui/index.html`
- Ignores backend code (`src/**`), build outputs, and infrastructure files
- May not detect unused object properties in service exports
- For comprehensive unused code detection, consider manual review of service methods

## Configuration

- Frontend env: Development uses proxy config in `vite.config.ts`

## Testing

- Frontend API calls go through `/api` proxy in development (see `vite.config.ts`)
- Frontend tests focus on business logic with comprehensive coverage
- Redis cache keys are defined in `ui/constants/cache-keys.ts`
- Test files excluded from coverage: `.eslintrc.cjs` and `app.vue`
