# Faster License Plate Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce Telegram bot license-plate detection latency from ~1.3–3.4 s to < 500 ms via PlateRecognizer Snapshot API as Tier 1, with downscaled-image LLM race as Tier 2 fallback.

**Architecture:** Two-tier detection in `LicensePlateDetectionService`. Tier 1 calls PlateRecognizer Cloud API (`POST /v1/plate-reader/`, multipart, ~290 ms). On 4xx/5xx/timeout/low-confidence, fall through to Tier 2 — the existing parallel LLM race, but with smaller/faster models (`meta-llama/llama-3.2-11b-vision-instruct` + `mistralai/ministral-8b-2512`) and the image downscaled to 640 px max edge before encoding.

**Tech Stack:** Kotlin 2.2, Spring Boot 4.0, JDK 21 `ImageIO` for downscaling, Spring `RestClient` for PlateRecognizer (multipart support without extra Feign deps), Atrium 1.3 + MockK + WireMock for tests.

**Spec:** `docs/superpowers/specs/2026-05-12-faster-license-plate-detection-design.md`

---

## Pre-flight

- [ ] **Create feature branch**

```bash
git checkout -b feature/faster-plate-detection main
git status
```

Expected: `On branch feature/faster-plate-detection`

---

## File Structure

### Phase 1 (PR 1 — Tier 2: LLM swap + downscale)

| File                                                                              | Action | Responsibility                                                   |
| --------------------------------------------------------------------------------- | ------ | ---------------------------------------------------------------- |
| `src/main/kotlin/ee/tenman/portfolio/service/ImageDownscaler.kt`                  | Create | Pure JPEG downscaler component                                   |
| `src/test/kotlin/ee/tenman/portfolio/service/ImageDownscalerTest.kt`              | Create | Unit tests with synthetic JPEGs                                  |
| `src/main/kotlin/ee/tenman/portfolio/domain/VisionModel.kt`                       | Modify | New model IDs                                                    |
| `src/test/kotlin/ee/tenman/portfolio/openrouter/VisionModelTest.kt`               | Modify | Updated assertions                                               |
| `src/main/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionService.kt`     | Modify | Inject `ImageDownscaler`, downscale in `detectPlateNumber(File)` |
| `src/test/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionServiceTest.kt` | Modify | Add downscale assertion                                          |

### Phase 2 (PR 2 — Tier 1: PlateRecognizer)

| File                                                                                | Action | Responsibility                                 |
| ----------------------------------------------------------------------------------- | ------ | ---------------------------------------------- |
| `src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerProperties.kt`  | Create | `@ConfigurationProperties("plate-recognizer")` |
| `src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerResponse.kt`    | Create | DTO matching API response                      |
| `src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerResult.kt`      | Create | Inner DTO (separate file per src/CLAUDE.md)    |
| `src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerConfig.kt`      | Create | `RestClient` bean with timeout                 |
| `src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerService.kt`     | Create | Tier 1 detection                               |
| `src/test/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerServiceTest.kt` | Create | WireMock-based tests                           |
| `src/main/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionService.kt`       | Modify | Wire Tier 1 before Tier 2                      |
| `src/test/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionServiceTest.kt`   | Modify | Tier 1 success / fallthrough tests             |
| `src/main/resources/application.yml`                                                | Modify | `plate-recognizer:` section                    |
| `src/test/resources/application.yml`                                                | Modify | Test config                                    |
| `docker-compose.yml`                                                                | Modify | `PLATE_RECOGNIZER_TOKEN` env                   |
| `k8s/secrets.yaml`                                                                  | Modify | Secret entry                                   |
| `.github/workflows/ci.yml`, `deploy-k3s.yml`, `deploy-pipeline.yml`                 | Modify | Workflow secret plumbing                       |

---

# Phase 1 — Tier 2 (LLM swap + downscale)

## Task 1: ImageDownscaler

**Files:**

- Create: `src/main/kotlin/ee/tenman/portfolio/service/ImageDownscaler.kt`
- Test: `src/test/kotlin/ee/tenman/portfolio/service/ImageDownscalerTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/ee/tenman/portfolio/service/ImageDownscalerTest.kt`:

```kotlin
package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageDownscalerTest {
  private val downscaler = ImageDownscaler()

  @Test
  fun `should downscale image whose longer edge exceeds limit`() {
    val source = jpegBytes(width = 1920, height = 2560)

    val result = downscaler.downscale(source, maxEdgePx = 640)

    val resized = ImageIO.read(ByteArrayInputStream(result))
    expect(maxOf(resized.width, resized.height)).toEqual(640)
  }

  @Test
  fun `should preserve aspect ratio when downscaling`() {
    val source = jpegBytes(width = 1920, height = 2560)

    val result = downscaler.downscale(source, maxEdgePx = 640)

    val resized = ImageIO.read(ByteArrayInputStream(result))
    expect(resized.width).toEqual(480)
    expect(resized.height).toEqual(640)
  }

  @Test
  fun `should return bytes unchanged when image is already within limit`() {
    val source = jpegBytes(width = 400, height = 300)

    val result = downscaler.downscale(source, maxEdgePx = 640)

    expect(result.contentEquals(source)).toEqual(true)
  }

  @Test
  fun `should return bytes unchanged when input cannot be decoded`() {
    val source = "not an image".toByteArray()

    val result = downscaler.downscale(source, maxEdgePx = 640)

    expect(result.contentEquals(source)).toEqual(true)
  }

  @Test
  fun `should produce smaller byte output than source`() {
    val source = jpegBytes(width = 1920, height = 2560)

    val result = downscaler.downscale(source, maxEdgePx = 640)

    expect(result.size).toBeLessThan(source.size)
  }

  private fun jpegBytes(width: Int, height: Int): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val output = ByteArrayOutputStream()
    ImageIO.write(image, "jpg", output)
    return output.toByteArray()
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "ee.tenman.portfolio.service.ImageDownscalerTest" -i
```

Expected: compilation error (`ImageDownscaler` not found).

- [ ] **Step 3: Implement ImageDownscaler**

Create `src/main/kotlin/ee/tenman/portfolio/service/ImageDownscaler.kt`:

```kotlin
package ee.tenman.portfolio.service

import org.springframework.stereotype.Component
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Component
class ImageDownscaler {
  fun downscale(
    bytes: ByteArray,
    maxEdgePx: Int,
  ): ByteArray {
    val original = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return bytes
    val longerEdge = maxOf(original.width, original.height)
    if (longerEdge <= maxEdgePx) return bytes
    val scale = maxEdgePx.toDouble() / longerEdge
    val newWidth = (original.width * scale).toInt()
    val newHeight = (original.height * scale).toInt()
    val resized = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
    val graphics = resized.createGraphics()
    graphics.drawImage(original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null)
    graphics.dispose()
    val output = ByteArrayOutputStream()
    ImageIO.write(resized, "jpg", output)
    return output.toByteArray()
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "ee.tenman.portfolio.service.ImageDownscalerTest" -i
```

Expected: 5 tests, all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/service/ImageDownscaler.kt \
        src/test/kotlin/ee/tenman/portfolio/service/ImageDownscalerTest.kt
git commit -m "Add ImageDownscaler for in-process JPEG resizing"
```

---

## Task 2: Update VisionModel enum to faster models

**Files:**

- Modify: `src/main/kotlin/ee/tenman/portfolio/domain/VisionModel.kt`
- Modify: `src/test/kotlin/ee/tenman/portfolio/openrouter/VisionModelTest.kt`
- Modify: `src/test/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionServiceTest.kt:98-100`

- [ ] **Step 1: Update the failing test**

Replace contents of `src/test/kotlin/ee/tenman/portfolio/openrouter/VisionModelTest.kt`:

```kotlin
package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.VisionModel
import org.junit.jupiter.api.Test

class VisionModelTest {
  @Test
  fun `should have correct model ids`() {
    expect(VisionModel.LLAMA_3_2_11B_VISION.modelId).toEqual("meta-llama/llama-3.2-11b-vision-instruct")
    expect(VisionModel.MINISTRAL_8B.modelId).toEqual("mistralai/ministral-8b-2512")
  }
}
```

- [ ] **Step 2: Update the parallel-providers test reference**

In `src/test/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionServiceTest.kt` replace lines that reference `LLAMA_4_SCOUT` and `NOVA_LITE`:

```kotlin
  @Test
  fun `should continue with other providers when one throws exception`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterVisionService.extractText(match { it.model == VisionModel.LLAMA_3_2_11B_VISION.modelId }) } throws
      RuntimeException("API error")
    every { openRouterVisionService.extractText(match { it.model == VisionModel.MINISTRAL_8B.modelId }) } returns "333CCC"

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("333CCC")
  }
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
./gradlew compileKotlin compileTestKotlin
```

Expected: compilation error — `VisionModel.LLAMA_3_2_11B_VISION` and `VisionModel.MINISTRAL_8B` don't exist yet.

- [ ] **Step 4: Update VisionModel enum**

Replace contents of `src/main/kotlin/ee/tenman/portfolio/domain/VisionModel.kt`:

```kotlin
package ee.tenman.portfolio.domain

enum class VisionModel(
  val modelId: String,
) {
  LLAMA_3_2_11B_VISION("meta-llama/llama-3.2-11b-vision-instruct"),
  MINISTRAL_8B("mistralai/ministral-8b-2512"),
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew test --tests "ee.tenman.portfolio.openrouter.VisionModelTest" \
              --tests "ee.tenman.portfolio.service.LicensePlateDetectionServiceTest"
```

Expected: 1 + 8 tests, all PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/domain/VisionModel.kt \
        src/test/kotlin/ee/tenman/portfolio/openrouter/VisionModelTest.kt \
        src/test/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionServiceTest.kt
git commit -m "Swap LLM vision models to faster Llama 3.2 11B and Ministral 8B"
```

---

## Task 3: Wire ImageDownscaler into LicensePlateDetectionService

**Files:**

- Modify: `src/main/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionService.kt:19-30,101`
- Modify: `src/test/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionServiceTest.kt`

- [ ] **Step 1: Add the failing test**

Append to `src/test/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionServiceTest.kt` (before the closing `}` of the class) and add the necessary imports at top:

Imports to add at top of test file:

```kotlin
import io.mockk.slot
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
```

Update `setUp()` to construct the service with a real `ImageDownscaler`:

```kotlin
  private val imageDownscaler = ImageDownscaler()

  @BeforeEach
  fun setUp() {
    every { openRouterProperties.apiKey } returns "test-api-key"
    service =
      LicensePlateDetectionService(
        openRouterVisionService,
        openRouterProperties,
        dispatcher,
        imageDownscaler,
      )
  }
```

Add the new test method:

```kotlin
  @Test
  fun `should downscale image to 640px before encoding for LLM`() {
    val tempFile = File.createTempFile("plate-", ".jpg")
    val source = BufferedImage(1920, 2560, BufferedImage.TYPE_INT_RGB)
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(source, "jpg", outputStream)
    tempFile.writeBytes(outputStream.toByteArray())
    val sourceSize = tempFile.length()
    val requestSlot = slot<ee.tenman.portfolio.openrouter.OpenRouterVisionRequest>()
    every { openRouterVisionService.extractText(capture(requestSlot)) } returns "111AAA"

    try {
      service.detectPlateNumber(tempFile)

      val capturedImageUrl =
        (
          requestSlot.captured.messages
            .first()
            .content
            .last() as ee.tenman.portfolio.openrouter.OpenRouterVisionRequest.ImageContent
        ).imageUrl.url
      val base64 = capturedImageUrl.removePrefix("data:image/jpeg;base64,")
      val decodedSize = java.util.Base64.getDecoder().decode(base64).size.toLong()
      expect(decodedSize).toBeLessThan(sourceSize)
    } finally {
      tempFile.delete()
    }
  }
```

- [ ] **Step 2: Run tests to verify failure**

```bash
./gradlew test --tests "ee.tenman.portfolio.service.LicensePlateDetectionServiceTest"
```

Expected: compilation error — `LicensePlateDetectionService` constructor does not accept 4 args.

- [ ] **Step 3: Modify LicensePlateDetectionService**

Replace contents of `src/main/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionService.kt`:

```kotlin
package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.VisionModel
import ee.tenman.portfolio.dto.DetectionResult
import ee.tenman.portfolio.openrouter.OpenRouterProperties
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest
import ee.tenman.portfolio.openrouter.OpenRouterVisionService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.util.Base64
import java.util.UUID

@Service
class LicensePlateDetectionService(
  private val openRouterVisionService: OpenRouterVisionService,
  private val openRouterProperties: OpenRouterProperties,
  private val calculationDispatcher: CoroutineDispatcher,
  private val imageDownscaler: ImageDownscaler,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun detectPlateNumber(photoFile: File): DetectionResult {
    val downscaled = imageDownscaler.downscale(photoFile.readBytes(), MAX_EDGE_PX_FOR_LLM)
    val base64Image = Base64.getEncoder().encodeToString(downscaled)
    return detectPlateNumber(base64Image, UUID.randomUUID())
  }

  fun detectPlateNumber(
    base64Image: String,
    uuid: UUID,
  ): DetectionResult =
    runBlocking(calculationDispatcher) {
      log.info("Starting parallel license plate detection for $uuid")
      if (openRouterProperties.apiKey.isBlank()) {
        log.warn("OpenRouter API key is blank, no detection providers available")
        return@runBlocking DetectionResult()
      }
      val jobs =
        VisionModel.entries.map { model ->
          async { tryVisionModel(model, base64Image) }
        }
      selectFirstSuccessful(jobs)
    }

  private suspend fun selectFirstSuccessful(jobs: List<Deferred<DetectionResult?>>): DetectionResult {
    val remainingJobs = jobs.toMutableList()
    while (remainingJobs.isNotEmpty()) {
      val completedDeferred =
        select<Deferred<DetectionResult?>> {
          remainingJobs.forEach { deferred ->
            deferred.onAwait { deferred }
          }
        }
      val result = completedDeferred.await()
      remainingJobs.remove(completedDeferred)
      if (result?.plateNumber != null) {
        log.info("Plate detected by ${result.provider}, cancelling remaining jobs")
        remainingJobs.forEach { it.cancel() }
        return result
      }
    }
    log.warn("All providers exhausted, no plate detected")
    return DetectionResult()
  }

  private fun tryVisionModel(
    model: VisionModel,
    base64Image: String,
  ): DetectionResult? =
    runCatching {
      log.info("Attempting detection with ${model.modelId}")
      val startTime = System.currentTimeMillis()
      val request = OpenRouterVisionRequest.forLicensePlateExtraction(model.modelId, base64Image)
      val response = openRouterVisionService.extractText(request)
      val elapsedMs = System.currentTimeMillis() - startTime
      if (response.isNullOrBlank()) {
        log.info("${model.modelId}: empty response in ${elapsedMs}ms")
        return@runCatching DetectionResult(provider = model)
      }
      val plateNumber = extractPlateNumber(response)
      if (plateNumber != null) {
        log.info("${model.modelId} detected plate: $plateNumber in ${elapsedMs}ms")
        return@runCatching DetectionResult(plateNumber = plateNumber, provider = model)
      }
      log.info("${model.modelId}: response '$response' did not match pattern in ${elapsedMs}ms")
      DetectionResult(provider = model)
    }.getOrElse { e ->
      log.error("${model.modelId} failed", e)
      null
    }

  private fun extractPlateNumber(response: String): String? {
    val cleaned = response.replace(WHITESPACE, "").uppercase()
    return PLATE_NUMBER_PATTERN.find(cleaned)?.value
  }

  companion object {
    private const val MAX_EDGE_PX_FOR_LLM = 640
    private val PLATE_NUMBER_PATTERN = Regex("\\b\\d{3}[A-Z]{3}\\b", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s")
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "ee.tenman.portfolio.service.LicensePlateDetectionServiceTest"
```

Expected: 9 tests (8 existing + 1 new), all PASS.

- [ ] **Step 5: Run full backend test suite**

```bash
./gradlew test
```

Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionService.kt \
        src/test/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionServiceTest.kt
git commit -m "Downscale photo to 640px before LLM plate detection"
```

---

## Task 4: Manual smoke test for Phase 1

- [ ] **Step 1: Start backend with test API key**

```bash
OPENROUTER_API_KEY="$OPENROUTER_API_KEY" ./gradlew bootRun
```

Wait for `Started PortfolioApplication`.

- [ ] **Step 2: Exercise via existing test image**

Open another terminal and exercise the service through whatever existing endpoint or test harness already calls it. Or run the focused integration test if you wrote one. As a final fallback, write a one-off Kotlin script invoking `service.detectPlateNumber(File("/Users/tenman/Downloads/2026-05-10 15.28.24.jpg"))` from a test, log elapsed.

Expected: plate `003HWV` returned in 700–1200 ms.

- [ ] **Step 3: Push branch and open PR for Phase 1**

```bash
git push -u origin feature/faster-plate-detection
gh pr create --title "Faster LLM plate detection (Phase 1)" --body "$(cat <<'EOF'
## Summary
- Replaces vision models with `meta-llama/llama-3.2-11b-vision-instruct` and `mistralai/ministral-8b-2512` (both empirically faster and more accurate than current `llama-4-scout` + `nova-lite` for Estonian plates)
- Downscales photos to 640px max edge before LLM call, halving payload size and inference latency
- Expected p95 latency: 1.3–3.4 s → 0.7–1.2 s

## Test plan
- [ ] `./gradlew test` passes
- [ ] Manual smoke against `2026-05-10 15.28.24.jpg`: returns `003HWV` in < 1.2 s
EOF
)"
```

**Stop here if you only want Phase 1 shipped. Merge PR, then continue with Phase 2 on a new branch.**

---

# Phase 2 — Tier 1 (PlateRecognizer)

> Assumes Phase 1 is merged. If iterating on the same branch, skip the new-branch step.

## Task 5: New branch + obtain PlateRecognizer token

- [ ] **Step 1: New branch from updated main**

```bash
git checkout main && git pull
git checkout -b feature/plate-recognizer-tier1
```

- [ ] **Step 2: Sign up and retrieve API token**

1. Sign up at https://platerecognizer.com/ (free tier, no card required for 2,500 lookups/mo).
2. Copy API token from dashboard.
3. Export locally for testing:

```bash
export PLATE_RECOGNIZER_TOKEN="<your-token-here>"
```

(Add the same secret to GitHub repository secrets in the next-to-last task.)

- [ ] **Step 3: Sanity check the API with curl**

```bash
curl -s -X POST "https://api.platerecognizer.com/v1/plate-reader/" \
  -H "Authorization: Token $PLATE_RECOGNIZER_TOKEN" \
  -F "upload=@/Users/tenman/Downloads/2026-05-10 15.28.24.jpg" \
  -F "regions=ee" | jq .
```

Expected: JSON with `results[0].plate = "003hwv"` (lowercase) and `results[0].score > 0.6`.

---

## Task 6: PlateRecognizerProperties

**Files:**

- Create: `src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerProperties.kt`

- [ ] **Step 1: Implement**

```kotlin
package ee.tenman.portfolio.platerecognizer

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "plate-recognizer")
data class PlateRecognizerProperties(
  val apiKey: String = "",
  val url: String = "https://api.platerecognizer.com/v1",
  val minScore: Double = 0.6,
  val regions: String = "ee",
  val timeoutMs: Long = 500,
)
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerProperties.kt
git commit -m "Add PlateRecognizerProperties"
```

---

## Task 7: PlateRecognizerResult and PlateRecognizerResponse DTOs

**Files:**

- Create: `src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerResult.kt`
- Create: `src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerResponse.kt`

- [ ] **Step 1: Create PlateRecognizerResult**

```kotlin
package ee.tenman.portfolio.platerecognizer

data class PlateRecognizerResult(
  val plate: String = "",
  val score: Double = 0.0,
)
```

- [ ] **Step 2: Create PlateRecognizerResponse**

```kotlin
package ee.tenman.portfolio.platerecognizer

data class PlateRecognizerResponse(
  val results: List<PlateRecognizerResult> = emptyList(),
)
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerResult.kt \
        src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerResponse.kt
git commit -m "Add PlateRecognizer response DTOs"
```

---

## Task 8: PlateRecognizerConfig (RestClient bean)

**Files:**

- Create: `src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerConfig.kt`

- [ ] **Step 1: Implement**

```kotlin
package ee.tenman.portfolio.platerecognizer

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(PlateRecognizerProperties::class)
class PlateRecognizerConfig {
  @Bean("plateRecognizerRestClient")
  fun plateRecognizerRestClient(properties: PlateRecognizerProperties): RestClient =
    RestClient
      .builder()
      .baseUrl(properties.url)
      .requestFactory(requestFactory(properties.timeoutMs))
      .build()

  private fun requestFactory(timeoutMs: Long): SimpleClientHttpRequestFactory =
    SimpleClientHttpRequestFactory().apply {
      setConnectTimeout(Duration.ofMillis(timeoutMs))
      setReadTimeout(Duration.ofMillis(timeoutMs))
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerConfig.kt
git commit -m "Add PlateRecognizer RestClient configuration"
```

---

## Task 9: PlateRecognizerService

**Files:**

- Create: `src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerService.kt`
- Test: `src/test/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerServiceTest.kt`

- [ ] **Step 1: Write failing tests with WireMock**

Create `src/test/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerServiceTest.kt`:

```kotlin
package ee.tenman.portfolio.platerecognizer

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import javax.imageio.ImageIO

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlateRecognizerServiceTest {
  private val wireMock = WireMockServer(0)
  private lateinit var properties: PlateRecognizerProperties
  private lateinit var service: PlateRecognizerService

  @BeforeAll
  fun startWireMock() {
    wireMock.start()
    properties =
      PlateRecognizerProperties(
        apiKey = "test-token",
        url = "http://localhost:${wireMock.port()}",
        minScore = 0.6,
        regions = "ee",
        timeoutMs = 2000,
      )
    val restClient =
      RestClient
        .builder()
        .baseUrl(properties.url)
        .requestFactory(
          SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(properties.timeoutMs))
            setReadTimeout(Duration.ofMillis(properties.timeoutMs))
          },
        ).build()
    service = PlateRecognizerService(restClient, properties)
  }

  @AfterAll
  fun stopWireMock() {
    wireMock.stop()
  }

  @BeforeEach
  fun resetStubs() {
    wireMock.resetAll()
  }

  @Test
  fun `should return uppercase plate when score meets threshold`() {
    stubSuccess(plate = "003hwv", score = 0.92)

    val result = service.detect(jpegFile())

    expect(result).notToEqualNull()
    expect(result?.plateNumber).toEqual("003HWV")
  }

  @Test
  fun `should return null when score below threshold`() {
    stubSuccess(plate = "003hwv", score = 0.4)

    val result = service.detect(jpegFile())

    expect(result).toEqual(null)
  }

  @Test
  fun `should return null when no results returned`() {
    wireMock.stubFor(
      post(urlPathEqualTo("/plate-reader/"))
        .willReturn(
          aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withBody("""{"results":[]}"""),
        ),
    )

    val result = service.detect(jpegFile())

    expect(result).toEqual(null)
  }

  @Test
  fun `should return null when plate does not match Estonian pattern`() {
    stubSuccess(plate = "abc123", score = 0.9)

    val result = service.detect(jpegFile())

    expect(result).toEqual(null)
  }

  @Test
  fun `should return null on server error`() {
    wireMock.stubFor(
      post(urlPathEqualTo("/plate-reader/"))
        .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())),
    )

    val result = service.detect(jpegFile())

    expect(result).toEqual(null)
  }

  @Test
  fun `should return null when API key is blank`() {
    val blankProps = properties.copy(apiKey = "")
    val restClient = RestClient.builder().baseUrl(properties.url).build()
    val blankService = PlateRecognizerService(restClient, blankProps)

    val result = blankService.detect(jpegFile())

    expect(result).toEqual(null)
  }

  private fun stubSuccess(plate: String, score: Double) {
    wireMock.stubFor(
      post(urlPathEqualTo("/plate-reader/"))
        .willReturn(
          aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withBody("""{"results":[{"plate":"$plate","score":$score}]}"""),
        ),
    )
  }

  private fun jpegFile(): File {
    val file = File.createTempFile("plate-test-", ".jpg")
    file.deleteOnExit()
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
    val out = ByteArrayOutputStream()
    ImageIO.write(image, "jpg", out)
    file.writeBytes(out.toByteArray())
    return file
  }
}
```

- [ ] **Step 2: Run tests to verify failure**

```bash
./gradlew test --tests "ee.tenman.portfolio.platerecognizer.PlateRecognizerServiceTest" -i
```

Expected: compilation error — `PlateRecognizerService` does not exist.

- [ ] **Step 3: Implement PlateRecognizerService**

Create `src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerService.kt`:

```kotlin
package ee.tenman.portfolio.platerecognizer

import ee.tenman.portfolio.domain.VisionModel
import ee.tenman.portfolio.dto.DetectionResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.io.File

@Service
class PlateRecognizerService(
  @Qualifier("plateRecognizerRestClient") private val restClient: RestClient,
  private val properties: PlateRecognizerProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun detect(photoFile: File): DetectionResult? {
    if (properties.apiKey.isBlank()) {
      log.info("PlateRecognizer API key not configured, skipping Tier 1")
      return null
    }
    return runCatching { callApi(photoFile) }
      .onFailure { log.warn("PlateRecognizer call failed: ${it.message}") }
      .getOrNull()
      ?.let(::toDetectionResult)
  }

  private fun callApi(photoFile: File): PlateRecognizerResponse? {
    val body =
      LinkedMultiValueMap<String, Any>().apply {
        add("upload", FileSystemResource(photoFile))
        add("regions", properties.regions)
      }
    return restClient
      .post()
      .uri("/plate-reader/")
      .header(HttpHeaders.AUTHORIZATION, "Token ${properties.apiKey}")
      .contentType(MediaType.MULTIPART_FORM_DATA)
      .body(body)
      .retrieve()
      .body(PlateRecognizerResponse::class.java)
  }

  private fun toDetectionResult(response: PlateRecognizerResponse): DetectionResult? {
    val top = response.results.firstOrNull() ?: return null
    if (top.score < properties.minScore) {
      log.info("PlateRecognizer score ${top.score} below threshold ${properties.minScore}")
      return null
    }
    val matched = PLATE_NUMBER_PATTERN.find(top.plate.replace(WHITESPACE, "").uppercase())?.value ?: return null
    log.info("PlateRecognizer detected plate: $matched (score ${top.score})")
    return DetectionResult(plateNumber = matched, provider = null)
  }

  companion object {
    private val PLATE_NUMBER_PATTERN = Regex("\\b\\d{3}[A-Z]{3}\\b", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s")
  }
}
```

Note: `DetectionResult.provider` is left `null` because `VisionModel` enum represents LLM providers only. The Tier 1 result is identified by the non-null `plateNumber` alone. (This is preserved by the existing `DetectionResult` API contract; the bot reply only checks `plateNumber`.) Leaving the `import ee.tenman.portfolio.domain.VisionModel` line out is fine — the actual code above does NOT use it; remove that import if added by autocomplete.

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "ee.tenman.portfolio.platerecognizer.PlateRecognizerServiceTest" -i
```

Expected: 6 tests, all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerService.kt \
        src/test/kotlin/ee/tenman/portfolio/platerecognizer/PlateRecognizerServiceTest.kt
git commit -m "Add PlateRecognizerService for Tier 1 detection"
```

---

## Task 10: Wire Tier 1 into LicensePlateDetectionService

**Files:**

- Modify: `src/main/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionService.kt`
- Modify: `src/test/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionServiceTest.kt`

- [ ] **Step 1: Add the failing tests**

Add to `LicensePlateDetectionServiceTest`:

Imports to add at top:

```kotlin
import ee.tenman.portfolio.platerecognizer.PlateRecognizerService
```

Modify the `private val` block + `setUp()`:

```kotlin
  private val openRouterVisionService = mockk<OpenRouterVisionService>()
  private val openRouterProperties = mockk<OpenRouterProperties>()
  private val plateRecognizerService = mockk<PlateRecognizerService>()
  private val imageDownscaler = ImageDownscaler()
  private val dispatcher = Dispatchers.Default

  private lateinit var service: LicensePlateDetectionService

  @BeforeEach
  fun setUp() {
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { plateRecognizerService.detect(any()) } returns null
    service =
      LicensePlateDetectionService(
        openRouterVisionService,
        openRouterProperties,
        dispatcher,
        imageDownscaler,
        plateRecognizerService,
      )
  }
```

Add new tests:

```kotlin
  @Test
  fun `should return Tier 1 result without invoking LLMs`() {
    val tempFile = File.createTempFile("plate-", ".jpg")
    val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
    ByteArrayOutputStream().use { out ->
      ImageIO.write(image, "jpg", out)
      tempFile.writeBytes(out.toByteArray())
    }
    every { plateRecognizerService.detect(tempFile) } returns DetectionResult(plateNumber = "003HWV", provider = null)

    try {
      val result = service.detectPlateNumber(tempFile)

      expect(result.plateNumber).toEqual("003HWV")
      verify(exactly = 0) { openRouterVisionService.extractText(any()) }
    } finally {
      tempFile.delete()
    }
  }

  @Test
  fun `should fall through to Tier 2 when Tier 1 returns null`() {
    val tempFile = File.createTempFile("plate-", ".jpg")
    val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
    ByteArrayOutputStream().use { out ->
      ImageIO.write(image, "jpg", out)
      tempFile.writeBytes(out.toByteArray())
    }
    every { plateRecognizerService.detect(tempFile) } returns null
    every { openRouterVisionService.extractText(any()) } returns "003 HWV"

    try {
      val result = service.detectPlateNumber(tempFile)

      expect(result.plateNumber).toEqual("003HWV")
      verify(atLeast = 1) { openRouterVisionService.extractText(any()) }
    } finally {
      tempFile.delete()
    }
  }
```

Note: The `DetectionResult` import already exists in the test. The `ee.tenman.portfolio.dto.DetectionResult` package is needed if missing — add `import ee.tenman.portfolio.dto.DetectionResult`.

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "ee.tenman.portfolio.service.LicensePlateDetectionServiceTest"
```

Expected: compilation error — `LicensePlateDetectionService` constructor needs 5 args.

- [ ] **Step 3: Modify LicensePlateDetectionService to call Tier 1 first**

In `src/main/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionService.kt`:

Replace the constructor to add `plateRecognizerService`:

```kotlin
@Service
class LicensePlateDetectionService(
  private val openRouterVisionService: OpenRouterVisionService,
  private val openRouterProperties: OpenRouterProperties,
  private val calculationDispatcher: CoroutineDispatcher,
  private val imageDownscaler: ImageDownscaler,
  private val plateRecognizerService: PlateRecognizerService,
) {
```

Add `import ee.tenman.portfolio.platerecognizer.PlateRecognizerService` at the top of the file.

Replace `detectPlateNumber(photoFile: File)`:

```kotlin
  fun detectPlateNumber(photoFile: File): DetectionResult {
    plateRecognizerService.detect(photoFile)?.let { return it }
    val downscaled = imageDownscaler.downscale(photoFile.readBytes(), MAX_EDGE_PX_FOR_LLM)
    val base64Image = Base64.getEncoder().encodeToString(downscaled)
    return detectPlateNumber(base64Image, UUID.randomUUID())
  }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "ee.tenman.portfolio.service.LicensePlateDetectionServiceTest" \
              --tests "ee.tenman.portfolio.platerecognizer.PlateRecognizerServiceTest"
```

Expected: all PASS.

- [ ] **Step 5: Run full backend suite**

```bash
./gradlew test
```

Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionService.kt \
        src/test/kotlin/ee/tenman/portfolio/service/LicensePlateDetectionServiceTest.kt
git commit -m "Wire PlateRecognizer Tier 1 in front of LLM Tier 2"
```

---

## Task 11: Configuration plumbing

**Files:**

- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application.yml`
- Modify: `docker-compose.yml`
- Modify: `k8s/secrets.yaml`

- [ ] **Step 1: application.yml (main)**

Append to `src/main/resources/application.yml`:

```yaml
plate-recognizer:
  url: https://api.platerecognizer.com/v1
  api-key: ${PLATE_RECOGNIZER_TOKEN:}
  min-score: 0.6
  regions: ee
  timeout-ms: 500
```

- [ ] **Step 2: application.yml (test)**

Append to `src/test/resources/application.yml`:

```yaml
plate-recognizer:
  url: http://localhost:0
  api-key: ''
  min-score: 0.6
  regions: ee
  timeout-ms: 500
```

(API key blank in test → service short-circuits to Tier 2, keeping integration tests deterministic.)

- [ ] **Step 3: docker-compose.yml**

Locate the `app:` (or `portfolio:`) service `environment:` block (where `OPENROUTER_API_KEY` is set, around line 50–60). Add:

```yaml
PLATE_RECOGNIZER_TOKEN: ${PLATE_RECOGNIZER_TOKEN}
```

- [ ] **Step 4: k8s/secrets.yaml**

In the `stringData:` block, after `TELEGRAM_BOT_TOKEN`, add:

```yaml
PLATE_RECOGNIZER_TOKEN: '${PLATE_RECOGNIZER_TOKEN}'
```

- [ ] **Step 5: Verify config loads**

```bash
./gradlew test
```

Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/application.yml src/test/resources/application.yml \
        docker-compose.yml k8s/secrets.yaml
git commit -m "Wire PLATE_RECOGNIZER_TOKEN through config and deployment"
```

---

## Task 12: GitHub Actions workflow secrets

**Files:**

- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/deploy-k3s.yml`
- Modify: `.github/workflows/deploy-pipeline.yml`

- [ ] **Step 1: Add repo secret in GitHub UI**

Settings → Secrets and variables → Actions → New repository secret:

- Name: `PLATE_RECOGNIZER_TOKEN`
- Value: token from PlateRecognizer dashboard

- [ ] **Step 2: Reference secret in workflows**

For each of the three workflow files, find the `env:` block of the backend test/build job (where `OPENROUTER_API_KEY` is set). Add:

```yaml
PLATE_RECOGNIZER_TOKEN: ${{ secrets.PLATE_RECOGNIZER_TOKEN }}
```

(Same indentation as the existing `OPENROUTER_API_KEY` line.)

For deploy workflows, also ensure the secret is passed to the k8s deploy step where other secrets are substituted.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml .github/workflows/deploy-k3s.yml .github/workflows/deploy-pipeline.yml
git commit -m "Pass PLATE_RECOGNIZER_TOKEN through GitHub Actions"
```

---

## Task 13: Manual smoke test + PR

- [ ] **Step 1: Run backend locally with both keys**

```bash
OPENROUTER_API_KEY="$OPENROUTER_API_KEY" \
PLATE_RECOGNIZER_TOKEN="$PLATE_RECOGNIZER_TOKEN" \
./gradlew bootRun
```

- [ ] **Step 2: Exercise plate detection**

Trigger plate detection through the Telegram bot (or whatever entry point exists) using `/Users/tenman/Downloads/2026-05-10 15.28.24.jpg`.

Expected:

- Result plate: `003HWV`
- Total latency: < 500 ms (Tier 1 success path)

Check backend log for: `PlateRecognizer detected plate: 003HWV (score …)`

- [ ] **Step 3: Verify Tier 2 fallback by temporarily breaking Tier 1**

Set `PLATE_RECOGNIZER_TOKEN=invalid` and re-test. Expected: Tier 1 logs failure, Tier 2 returns `003HWV` in < 1.2 s.

- [ ] **Step 4: Push branch and open PR**

```bash
git push -u origin feature/plate-recognizer-tier1
gh pr create --title "Add PlateRecognizer as Tier 1 plate detection" --body "$(cat <<'EOF'
## Summary
- Adds PlateRecognizer Snapshot API as Tier 1 detection (~290 ms typical)
- Falls back to existing LLM race (Tier 2 from Phase 1) on Tier 1 failure
- Free tier (2,500/mo) covers expected bot volume

## Test plan
- [ ] `./gradlew test` passes
- [ ] Manual smoke: plate detected via Tier 1 in < 500 ms
- [ ] Manual smoke: invalid token causes Tier 1 to fail, Tier 2 still returns plate
- [ ] `PLATE_RECOGNIZER_TOKEN` added to GitHub repository secrets
EOF
)"
```

---

## Final Verification Checklist (run before merging each PR)

- [ ] `./gradlew test` passes
- [ ] `./gradlew compileKotlin` produces no warnings about deprecated APIs introduced by changes
- [ ] CI passes on the branch (lint, tests, deploy preview if applicable)
- [ ] Bot smoke test returns expected plate in expected latency band
- [ ] No new dependencies surprise the build (PR 2 adds no new Gradle deps — `RestClient` is in Spring core)
