# CLAUDE.md

## Project Overview

Portfolio Management System - full-stack app for tracking investment portfolios with automated price updates and XIRR calculations.

**Tech Stack:**

- Backend: Kotlin 2.2, Spring Boot 4.0, Java 21
- Frontend: Vue.js 3.5, TypeScript 5.9, Vite 7.3, Bootstrap 5.3
- Database: PostgreSQL 17 with Flyway migrations (V1-V126+)
- Cache: Redis 8 (multi-level caching strategy)
- Testing: Atrium 1.3 (Kotlin assertions), JUnit 5, MockK, Selenide, Vitest
- Build: Gradle 8.8 with Version Catalogs (libs.versions.toml)
- Authentication: Custom Spring Boot auth service (https://github.com/ktenman/auth)
- Infrastructure: Docker, Kubernetes, Caddy reverse proxy
- Additional Services: Google Cloud Vision API

## Context Map

Backend-specific rules (Kotlin, Spring, testing, architecture) are in **src/CLAUDE.md** - loaded automatically when working on backend code.

Frontend-specific rules (Vue, TypeScript, Knip) are in **ui/CLAUDE.md** - loaded automatically when working on frontend code.

Proxy-specific rules are in **cloudflare-bypass-proxy/CLAUDE.md** - loaded automatically when working on proxy code.

## Git Branching Strategy

When working on features or bug fixes:

1. **Always create a branch from the related GitHub issue** when possible
   - Use format: `feature/<issue-number>-<short-description>` (e.g., `feature/1035-circuit-breaker-openrouter`)
   - For bug fixes: `fix/<issue-number>-<short-description>`
2. **Never commit directly to main** for non-trivial changes
3. **Create PRs that reference the issue** with "Closes #XXX" or "Fixes #XXX"
4. **If CI fails on main**, reset main to the last good commit and move failing changes to a feature branch
5. **Squash related commits** when moving work to a feature branch to keep history clean and then squash and then close pr and create new pr

## Essential Commands

```bash
# Dev
npm run dev                 # Start both backend (8081) and frontend (61234)
npm run dev:ui              # Frontend only (Vite)
npm run dev:backend         # Backend only (Gradle bootRun)

# Backend - prefer targeted commands over full builds
./gradlew test --tests "ClassName.methodName"   # Single test
./gradlew test              # All backend tests
./gradlew compileKotlin     # Compile only (also regenerates TS types)
./gradlew bootRun           # Run Spring Boot application (port 8081)
# Use ./gradlew clean build ONLY for specific reasons (dependency changes, build cache issues)

# Frontend - ALWAYS run both after UI changes
npm run lint-format         # Type check + lint + format (RECOMMENDED)
npm run lint                # Run ESLint only
npm run format              # Format with Prettier only
npm run format:check        # Check formatting only
npm test                    # All UI tests
npm test -- --run           # Run tests once (no watch mode)
npm test -- --coverage      # Run tests with coverage report
npm run build               # Production build

# Testing
npm run test:all            # ALL tests: backend + frontend + E2E
npm run test:unit           # Backend unit + frontend UI + proxy tests
npm run test:e2e            # Full E2E setup + tests (starts all services)
npm run test:proxy          # Cloudflare-bypass-proxy tests only

# Docker
docker compose -f compose.yaml up -d                    # Start PostgreSQL & Redis
docker compose -f docker-compose.local.yml build        # Build all services
docker compose -f docker-compose.local.yml up -d        # Run full stack
npm run docker:up                                        # Start Docker services (PostgreSQL & Redis)
npm run docker:down                                      # Stop Docker services
npm run test:cleanup                                     # Stop all services and cleanup

# E2E manual setup
npm run test:setup && E2E=true ./gradlew test --info -Pheadless=true

# Unused code detection
npm run check-unused
```

### TypeScript Type Generation

Auto-generates TypeScript types from Kotlin DTOs to `ui/models/generated/domain-models.ts`.

- **NEVER manually edit** generated file - it's auto-generated
- **Add new types** to the `classes` list in `build.gradle.kts:179-213`
- Types auto-regenerate on `./gradlew compileKotlin` or `./gradlew build`
- Post-processing removes timestamps and unexports DateAsString (for knip compatibility)

## Behavioral Principles

Four principles to reduce common coding mistakes (adapted from [Karpathy's observations](https://x.com/karpathy/status/2015883857489522876)). Bias toward caution over speed — use judgment on trivial tasks.

### 1. Think Before Coding
Don't assume. Don't hide confusion. Don't be sycophantic.
- State assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted, even against the user.
- If something is unclear, stop. Name what's confusing. Ask.
- Surface tradeoffs and inconsistencies instead of agreeing reflexively.

### 2. Simplicity First
Minimum code that solves the problem. Nothing speculative.
- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If 200 lines could be 50, rewrite it. If asked "couldn't you just do X?", do it.
- For optimization: write the naive-but-correct version first, then optimize while preserving correctness.

Test: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes
Touch only what you must. Clean up only your own mess.
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- Don't change or remove code/comments you don't understand, even if they look orthogonal.
- If you notice unrelated dead code, mention it — don't delete it.
- Remove imports/variables YOUR changes made unused; leave pre-existing dead code alone.

Test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution (Declarative > Imperative)
Define success criteria. Loop until verified. Use stamina as leverage.
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"
- "Optimize Y" → "Naive correct version → benchmark → optimize → verify no regression"

For multi-step tasks, state a brief plan:

```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
```

Strong success criteria let the agent loop independently via tools (tests, type checkers, browser MCP, lint). Weak criteria ("make it work") require constant clarification.

**Anti-pattern examples:** See `~/.claude/docs/karpathy-examples.md` for concrete before/after code diffs.

## Core Development Philosophy

Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (\*.md) or README files. Only create documentation files if explicitly requested by the User.

## Clean Code Standards

### No Comments - Write Self-Documenting Code

NEVER add comments to code. Code should be self-documenting through:

- Clear, descriptive naming that reveals intent
- Small, focused functions with single responsibilities
- Well-organized structure that tells a story
- Meaningful abstractions and type definitions

AVOID all forms of code comments including:

- Single-line comments (//)
- Multi-line comments (/\* \*/)
- Documentation comments (/\*\* \*/)
- Inline comments

The only exception is TypeScript triple-slash directives (///) which are required for type definitions.

### Method Design Principles

ALWAYS write methods that:

- Use guard clauses to exit early and reduce nesting
- Have a single, clear responsibility
- Are small enough to understand at a glance (typically < 20 lines)
- Return early when conditions aren't met
- Use descriptive names that explain what they do, not how

### Code Quality Standards

APPLY these senior-level practices:

- **Extract complex logic** into well-named private methods
- **Avoid deep nesting** - if you have more than 2 levels of indentation, refactor
- **Make invalid states unrepresentable** through proper type design
- **Prefer immutability** - use `val` in Kotlin, `const` in TypeScript
- **Fail fast** - validate inputs early and throw meaningful exceptions
- **Use domain-specific types** instead of primitives (e.g., `EmailAddress` instead of `string`)
- Write code that is easy to test by avoiding side effects
- Prefer pure functions that always return the same output for the same input
- Keep business logic separate from framework code
- Design for failure - handle edge cases explicitly

### File Size Guidelines

Keep files small and focused:

- **Ideal**: 100-200 lines per file
- **Acceptable**: Up to 300 lines
- **Too large**: Over 400 lines - refactor into separate services

When a file exceeds 300 lines:

1. Extract related functionality into separate services
2. Create specialized services for specific domains
3. Use composition - inject smaller services into larger ones
4. Follow Single Responsibility Principle

## File Naming Conventions

ALWAYS follow the existing file naming patterns in the codebase:

- Use kebab-case for all file names (e.g., `transaction-form.vue`, `use-crud-alerts.ts`)
- NEVER add suffixes like `-improved`, `-new`, `-simple`, `-refactored` to file names
- When updating a file, modify it in place rather than creating a new version
- Component files: `[feature-name].vue` (e.g., `instrument-table.vue`)
- Composables: `use-[feature].ts` (e.g., `use-form-validation.ts`)
- Services: `[domain]-service.ts` (e.g., `instruments-service.ts`)
- Models/Types: `[entity].ts` (e.g., `instrument.ts`)
- Keep file names consistent with the existing patterns in each directory

## CI/CD and Code Review Rules

- All CI workflows must pass before code changes may be reviewed
- The existing code structure must not be changed without a strong reason
- Every bug must be reproduced by a unit test before being fixed
- Every new feature must be covered by a unit test before it is implemented
- Minor inconsistencies and typos in the existing code may be fixed

## Git Commit Conventions

When creating commits:

- Use the global git user configured in the system (`git config --global user.name` and `git config --global user.email`)
- **NEVER** add "Generated with Claude Code" or similar attribution lines to commit messages
- **NEVER** add "Co-Authored-By: Claude" or any AI co-author attribution
- Start with uppercase imperative verb (e.g., "Add", "Fix", "Update", "Remove")
- **NO PREFIXES** - never use `feat:`, `fix:`, `chore:`, `docs:`, etc.
- Subject line max 50 characters
- Good: `Add user authentication`
- Bad: `feat: add user authentication`

## Pull Request Conventions

When creating pull requests:

- **NEVER** add "Generated with Claude Code" or similar attribution lines to PR descriptions
- **NEVER** add any AI attribution or emoji indicators
- Use clear, descriptive titles that summarize the change
- Include a Summary section with bullet points
- Include a Test plan section with checkboxes
- Reference related issues with "Closes #XXX" or "Fixes #XXX"
