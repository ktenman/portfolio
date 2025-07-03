# CI/CD Pipeline Optimization Guide

## Overview

This guide documents the optimizations implemented to significantly reduce CI/CD pipeline execution time by leveraging caching strategies and parallel execution.

## Key Optimizations

### 1. Gradle Dependency Caching

**Before**: Each job re-downloaded all Gradle dependencies
**After**: Dependencies are cached and reused across runs

**Implementation**:

```yaml
- name: Setup Gradle
  uses: gradle/actions/setup-gradle@v4.4.1
  with:
    gradle-home-cache-cleanup: true
```

**Impact**: ~2-3 minutes saved per Gradle-using job

### 2. Node.js (npm) Dependency Caching

**Before**: `npm ci` re-installed all packages on every run
**After**: `node_modules` are cached based on `package-lock.json`

**Implementation**:

```yaml
- name: Use Node.js
  uses: actions/setup-node@v4.4.0
  with:
    node-version: 'lts/iron'
    cache: 'npm'
```

**Impact**: ~1-2 minutes saved per npm-using job

### 3. Docker Layer Caching

**Before**: Docker images rebuilt from scratch every time
**After**: Build layers are cached using GitHub Actions cache

**Implementation**:

```yaml
- name: Build and push image
  uses: docker/build-push-action@v6.18.0
  with:
    cache-from: type=gha,scope=${{ github.workflow }}-${{ matrix.name }}
    cache-to: type=gha,scope=${{ github.workflow }}-${{ matrix.name }},mode=max
```

**Impact**: ~3-5 minutes saved per Docker image build

### 4. Parallel Linting Jobs

**Before**: Sequential linting created a bottleneck
**After**: Backend and frontend linting run in parallel

**Structure Change**:

- Split `lint-and-format` into `lint-backend` and `lint-frontend`
- Both run simultaneously, reducing critical path time

**Impact**: ~2-3 minutes reduction in total pipeline time

### 5. Additional Optimizations

#### a. Shallow Git Clone

Changed `fetch-depth: 0` to `fetch-depth: 1` where full history isn't needed

#### b. Docker Image Caching for E2E Tests

Added caching for Docker images used in E2E tests:

```yaml
- name: Cache Docker images
  uses: ScribeMD/docker-cache@0.5.0
  with:
    key: docker-${{ runner.os }}-${{ hashFiles('docker-compose.*.yml') }}
```

#### c. Gradle Build Cache

Added `--build-cache` flag to Gradle commands

## Expected Time Savings

Based on typical project patterns:

| Stage          | Before        | After         | Savings       |
| -------------- | ------------- | ------------- | ------------- |
| Linting        | 5-6 min       | 2-3 min       | 3 min         |
| Backend Tests  | 4-5 min       | 2-3 min       | 2 min         |
| Frontend Tests | 3-4 min       | 1-2 min       | 2 min         |
| E2E Tests      | 6-8 min       | 4-5 min       | 2-3 min       |
| Docker Builds  | 10-12 min     | 3-5 min       | 7 min         |
| **Total**      | **28-35 min** | **12-18 min** | **16-17 min** |

## Migration Guide

1. **Review the optimized workflow**: Compare `ci.yml` with `ci-optimized.yml`
2. **Test in a branch first**: Create a PR with the optimized workflow
3. **Monitor cache usage**: Check Actions > Caches in your GitHub repo
4. **Gradual rollout**: Consider testing individual optimizations first

## Cache Management

### Cache Invalidation

- Gradle: Automatically invalidated when `build.gradle.kts` changes
- npm: Automatically invalidated when `package-lock.json` changes
- Docker: Manually invalidate by changing the cache scope if needed

### Cache Limits

- GitHub Actions provides 10GB total cache per repository
- Gradle cleanup option helps manage cache size
- Docker caches are automatically pruned when space is needed

## Monitoring and Maintenance

1. **Check cache hit rates**: Look for "Cache restored successfully" in logs
2. **Monitor build times**: Use GitHub's workflow run history
3. **Update actions regularly**: Keep setup actions up to date
4. **Review cache usage**: Periodically check cache storage in repo settings

## Troubleshooting

### Cache Misses

- Verify cache keys match between save and restore
- Check if dependencies have changed
- Ensure workflow has proper permissions

### Stale Caches

- Manually delete caches via GitHub UI if needed
- Update cache keys to force refresh
- Use cache versioning in keys (e.g., `v1-cache-...`)

## Future Optimizations

1. **Self-hosted runners**: For even faster builds with persistent caches
2. **Remote caching**: Consider Gradle remote build cache for team sharing
3. **Test parallelization**: Split large test suites across multiple jobs
4. **Conditional builds**: Skip unchanged modules using path filters

## References

- [Gradle Build Cache](https://docs.gradle.org/current/userguide/build_cache.html)
- [GitHub Actions Cache](https://docs.github.com/en/actions/using-workflows/caching-dependencies-to-speed-up-workflows)
- [Docker BuildKit Cache](https://docs.docker.com/build/cache/)
- [npm ci Performance](https://docs.npmjs.com/cli/v9/commands/npm-ci)
