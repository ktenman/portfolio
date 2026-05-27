# Faster License Plate Detection

## Problem

The current `LicensePlateDetectionService` runs two OpenRouter vision models in parallel (`meta-llama/llama-4-scout` + `amazon/nova-lite-v1`) with first-success-wins. Observed latency on a representative 1920×2560 photo is **1.3–3.4 s** end-to-end, and the "fast" racer (`llama-4-scout`) was empirically wrong on that same image (returned only `HW` instead of `003 HWV`). The race is winning by luck.

For the Telegram bot UX, sub-second response is the target.

## Goal

Reduce plate-detection latency with these targets:

- **Tier 1 happy path (≥ 90% of calls):** p95 < 500 ms end-to-end
- **Tier 2 fallback path:** p95 < 1.2 s
- **Worst case (Tier 1 timeout + Tier 2):** < 1.7 s

Accuracy on Estonian-format plates (`\d{3}[A-Z]{3}`) must not regress from current baseline. Cost must stay below ~$5/month at typical bot usage (low hundreds of images/month).

## Out of Scope

- Restoring Google Cloud Vision (was removed in #1505; PlateRecognizer beats it on both latency and accuracy for plate-specific extraction).
- Cropping the plate region before recognition (would help further but adds complexity; not needed to hit the 500 ms Tier 1 goal).
- Caching plate results (each photo is unique).
- Changes to `CarTelegramBot` reply formatting.

## Solution: Two-Tier Detection

```
Tier 1 (primary):   PlateRecognizer Snapshot API   ────►  return on success (~290 ms)
                              │
                              ▼ (5xx / timeout / no plate field)
Tier 2 (fallback):  Downscale image to 640px,
                    race two OpenRouter models:
                      - meta-llama/llama-3.2-11b-vision-instruct
                      - mistralai/ministral-8b-2512
                    first-success-wins                 ────►  return on success (~800 ms)
                              │
                              ▼ (both failed / no plate)
                    return empty DetectionResult
```

### Tier 1 — PlateRecognizer

- Endpoint: `POST https://api.platerecognizer.com/v1/plate-reader/`
- Auth: `Authorization: Token <PLATE_RECOGNIZER_TOKEN>`
- Body: `multipart/form-data` with `upload=<file>` and `regions=ee`
- Response: JSON with `results[0].plate` (lowercased plate string) and `results[0].score` (0–1 confidence)
- Free tier: 2,500 lookups/month — covers expected bot volume indefinitely
- Latency budget: **500 ms timeout** (Tier 1's reason for existing is speed; if it's slow, fall through immediately)
- On timeout/5xx/empty-plate, log and fall through to Tier 2.
- Considered a success only if `score >= 0.6` AND extracted plate matches the Estonian regex.
- Image is uploaded **at original resolution** (PlateRecognizer's CV model benefits from full pixels; downscaling happens only for Tier 2).

### Tier 2 — Fallback LLM race (downscaled image)

- Image downscaled in-process to 640 px on the longer edge using Java `ImageIO` (avoid shelling out to `sips`).
- Same `first-success-wins` Kotlin coroutine pattern already in `LicensePlateDetectionService`.
- Models:
  - `meta-llama/llama-3.2-11b-vision-instruct` — empirically 707–920 ms, 7/7 correct in benchmark
  - `mistralai/ministral-8b-2512` — empirically 814–1160 ms, 5/5 correct
- Same `OpenRouterVisionService` infrastructure; only `VisionModel` enum values change.

## Component Changes

### New: `PlateRecognizerClient` (Feign)
```
@FeignClient(name = "plate-recognizer", url = "${plate-recognizer.url:https://api.platerecognizer.com/v1}")
interface PlateRecognizerClient {
  @PostMapping(value = "/plate-reader/", consumes = [MULTIPART_FORM_DATA_VALUE])
  fun detect(
    @RequestHeader("Authorization") auth: String,
    @RequestPart upload: MultipartFile,
    @RequestParam regions: String,
  ): PlateRecognizerResponse
}
```

Feign multipart requires `io.github.openfeign.form:feign-form-spring` on the classpath; add to `libs.versions.toml`. Per-client timeout config in `application.yml` under `spring.cloud.openfeign.client.config.plate-recognizer.{connectTimeout,readTimeout}` set to match the 500 ms budget.

### New: `PlateRecognizerResponse` / `PlateRecognizerResult` (data classes, own files per src/CLAUDE.md)

### New: `PlateRecognizerProperties` (`@ConfigurationProperties("plate-recognizer")` — `url`, `apiKey`, `minScore=0.6`, `regions=ee`, `timeoutMs=500`)

### New: `PlateRecognizerService`
- Single `detect(photoFile: File): DetectionResult?` method
- Returns `null` (not empty) when API unavailable so caller can distinguish failure from "no plate"
- Network I/O outside any transaction (no DB writes; trivially satisfied)

### New: `ImageDownscaler`
- Pure function: `downscale(bytes: ByteArray, maxEdgePx: Int): ByteArray`
- Uses `ImageIO` + `AffineTransformOp`; preserves JPEG quality
- Returns original bytes if image is already ≤ maxEdgePx

### Modified: `VisionModel` enum
```
enum class VisionModel(val modelId: String) {
  LLAMA_3_2_11B_VISION("meta-llama/llama-3.2-11b-vision-instruct"),
  MINISTRAL_8B("mistralai/ministral-8b-2512"),
}
```

### Modified: `LicensePlateDetectionService`
- Constructor gains `PlateRecognizerService` + `ImageDownscaler`
- New entry path:
  1. `plateRecognizerService.detect(photoFile)?.let { return it }`
  2. Downscale image bytes → 640 px
  3. Encode to base64
  4. Race `VisionModel.entries` (existing logic)

### Modified: `application.yml`
```
plate-recognizer:
  url: https://api.platerecognizer.com/v1
  api-key: ${PLATE_RECOGNIZER_TOKEN:}
  min-score: 0.6
  regions: ee
  timeout-ms: 500

spring:
  cloud:
    openfeign:
      client:
        config:
          plate-recognizer:
            connectTimeout: 500
            readTimeout: 500
```

### Modified: secrets / deployment
- Add `PLATE_RECOGNIZER_TOKEN` to:
  - `docker-compose.yml`
  - `k8s/secrets.yaml`
  - `.github/workflows/*.yml` (build/deploy)
- Token sourced from PlateRecognizer dashboard (free tier signup)

## Failure Handling

| Failure mode | Behavior |
|---|---|
| `PLATE_RECOGNIZER_TOKEN` blank | Skip Tier 1, go directly to Tier 2 (logged once at startup) |
| Tier 1 HTTP 4xx/5xx | Log + fall through to Tier 2 |
| Tier 1 timeout (500 ms) | Cancel + fall through to Tier 2 |
| Tier 1 returns plate with score < 0.6 | Treat as "uncertain", fall through to Tier 2 |
| Tier 2: both LLMs fail or return non-matching text | Return `DetectionResult()` (empty), bot says "No car detected" |
| Image downscale throws | Use original bytes, log warning |

Rationale for falling through on low-confidence Tier 1: PlateRecognizer occasionally hallucinates plates from background text. LLM second-opinion catches this.

## Testing

### Unit
- `PlateRecognizerServiceTest`: WireMock the API; cover success, 401, 500, timeout, low-score, malformed-plate cases.
- `ImageDownscalerTest`: synthetic image, verify dimensions + that "already small enough" passes through unchanged.
- `LicensePlateDetectionServiceTest` (extend existing): verify Tier 1 success skips Tier 2; Tier 1 failure invokes Tier 2; both fail returns empty.
- `VisionModelTest`: update to new model IDs.

### Integration
- New `PlateRecognizerServiceIT` modelled after `OpenRouterClientIT` — opt-in (skipped without API token), real network call against a known plate image fixture.
- No new database migrations; existing integration test infrastructure unaffected.

### Manual smoke
- Run `LicensePlateDetectionService` against `/Users/tenman/Downloads/2026-05-10 15.28.24.jpg`, verify result = `003HWV` and total elapsed < 500 ms on Tier 1, < 1.2 s on Tier 2 fallback.

## Rollout

Two PRs, shipped independently to de-risk:

### PR 1 — Tier 2 only (LLM swap + downscale)
- Adds `ImageDownscaler` and downscale step
- Replaces `VisionModel` entries with `LLAMA_3_2_11B_VISION` + `MINISTRAL_8B`
- Expected latency drop: 1.3–3.4 s → 0.7–1.2 s. Standalone win.
- Zero new external dependencies; safe to ship today.

### PR 2 — Tier 1 (PlateRecognizer)
- Adds client, service, properties, secret plumbing
- Wires Tier 1 in front of Tier 2 in `LicensePlateDetectionService`
- Requires PlateRecognizer account + token added to secrets before merge
- Expected latency drop: 0.7–1.2 s → ~0.3 s on Tier 1 path

## Empirical Data (benchmark of test image, plate = `003 HWV`)

| Model | Image size | Latency (5–7 runs) | Accuracy |
|---|---|---|---|
| `llama-4-scout` (current) | full 554KB | 3351 ms (1 run) | wrong: "HW" |
| `nova-lite-v1` (current) | full 554KB | 2031–2741 ms | correct |
| **`llama-3.2-11b-vision-instruct`** | **640 px** | **707–920 ms** | **7/7 correct** |
| `llama-3.2-11b-vision-instruct` | 1024 px | 888–2250 ms (1 outlier) | 5/5 correct |
| `ministral-8b-2512` | 1024 px | 814–1160 ms | 5/5 correct |
| `ministral-3b-2512` | 1024 px | 669–1478 ms | 5/5 correct |
| `gemini-2.5-flash-lite` | 1024 px | 1575–1907 ms | correct |
| PlateRecognizer (per docs) | — | ~290 ms | — |

## Cost Estimate

Assuming 200 plate detections/month (high estimate for personal bot):
- Tier 1 (PlateRecognizer): 200 lookups / 2,500 free = **$0/month**
- Tier 2 fallback (~5% of calls): ~10 × $0.0003 = **<$0.01/month**
- Total: **effectively free**
