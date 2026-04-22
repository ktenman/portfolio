# Diversification Multi-Platform Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow selecting multiple platforms in the Diversification tab so "Load from Portfolio" loads ETFs across all selected platforms, and strip exchange/currency suffixes from ticker symbols in the Diversification UI.

**Architecture:** Frontend uses a plain `ref<string[]>` in `diversification-calculator.vue` (DB-persisted, inline toggle helpers; does NOT reuse `usePlatformFilter` because its auto-fill and localStorage behavior conflict with diversification's semantics). Button-pill multi-select UI matches other views visually. Backend renames `selectedPlatform: String?` → `selectedPlatforms: List<String>` in the JSON-serialized config blob, with a custom Jackson deserializer that migrates old single-value configs on read.

**Tech Stack:** Vue 3 Composition API, TypeScript, Kotlin, Spring Boot, Jackson, Vitest, Atrium (Kotlin assertions), MockK.

**Related spec:** `docs/superpowers/specs/2026-04-20-diversification-multi-platform-design.md`

---

## File Inventory

**Create:**
- `ui/utils/ticker-symbol.ts` — pure `formatTickerSymbol` utility
- `ui/utils/ticker-symbol.test.ts` — utility tests
- `src/main/kotlin/ee/tenman/portfolio/configuration/DiversificationConfigDataDeserializer.kt` — Jackson deserializer that migrates legacy `selectedPlatform` → `selectedPlatforms`
- `src/test/kotlin/ee/tenman/portfolio/configuration/DiversificationConfigDataDeserializerTest.kt` — deserializer tests

**Modify:**
- `src/main/kotlin/ee/tenman/portfolio/domain/DiversificationConfigData.kt` — rename field, add `@JsonDeserialize` annotation
- `src/main/kotlin/ee/tenman/portfolio/dto/DiversificationConfigDto.kt` — rename field
- `src/main/kotlin/ee/tenman/portfolio/service/diversification/DiversificationConfigService.kt` — update mappers
- `src/test/kotlin/ee/tenman/portfolio/service/diversification/DiversificationConfigServiceTest.kt` — update to new field name
- `ui/components/diversification/types.ts` — `CachedState.selectedPlatform` → `selectedPlatforms`
- `ui/composables/use-allocation-calculations.ts` — prop rename, `showRebalanceColumns` logic
- `ui/components/diversification/allocation-table.vue` — multi-select pills, ticker formatting, props/emits
- `ui/components/diversification/allocation-card.vue` — ticker formatting in dropdown
- `ui/components/diversification/diversification-calculator.vue` — state, wiring, config load/save
- `ui/components/diversification/allocation-table.test.ts` — adjust for new UI
- `ui/components/diversification/diversification-calculator.test.ts` — adjust for multi-platform

---

## Task 1: Ticker symbol utility (pure function, TDD)

**Files:**
- Create: `ui/utils/ticker-symbol.ts`
- Test: `ui/utils/ticker-symbol.test.ts`

- [ ] **Step 1: Write the failing test**

Create `ui/utils/ticker-symbol.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { formatTickerSymbol } from './ticker-symbol'

describe('formatTickerSymbol', () => {
  it('strips exchange and currency suffix from Lightyear-style symbols', () => {
    expect(formatTickerSymbol('AIFS:GER:EUR')).toBe('AIFS')
  })

  it('strips a single colon suffix', () => {
    expect(formatTickerSymbol('VUAA:LN')).toBe('VUAA')
  })

  it('returns the input unchanged when no colon is present', () => {
    expect(formatTickerSymbol('VUAA')).toBe('VUAA')
  })

  it('returns an empty string for empty input', () => {
    expect(formatTickerSymbol('')).toBe('')
  })

  it('returns an empty string when the symbol starts with a colon', () => {
    expect(formatTickerSymbol(':GER:EUR')).toBe('')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run ui/utils/ticker-symbol.test.ts`
Expected: FAIL with "Cannot find module './ticker-symbol'"

- [ ] **Step 3: Write minimal implementation**

Create `ui/utils/ticker-symbol.ts`:

```ts
export const formatTickerSymbol = (symbol: string): string => symbol.split(':')[0]
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run ui/utils/ticker-symbol.test.ts`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add ui/utils/ticker-symbol.ts ui/utils/ticker-symbol.test.ts
git commit -m "Add formatTickerSymbol utility for diversification display"
```

---

## Task 2: Apply ticker formatting in allocation-table dropdown

**Files:**
- Modify: `ui/components/diversification/allocation-table.vue:230`
- Test: `ui/components/diversification/allocation-table.test.ts`

- [ ] **Step 1: Write the failing test**

Open `ui/components/diversification/allocation-table.test.ts` and add this test inside the existing top-level `describe('AllocationTable', () => { ... })` block. Keep all existing tests. Add near the top of the describe body (after the `mount` helper is available — if there's already a `createWrapper` helper, use it with the default `availableEtfs` prop and inject a prop override for `availableEtfs` that includes an ETF with a colon-suffixed symbol):

```ts
  it('renders ETF dropdown options with the ticker stripped of exchange suffix', () => {
    const wrapper = createWrapper({
      availableEtfs: [
        {
          instrumentId: 10,
          symbol: 'AIFS:GER:EUR',
          name: 'Amundi AI Fund',
          ter: 0.25,
          annualReturn: 12,
          currentPrice: 100,
          fundCurrency: 'EUR',
        },
      ],
    })

    const optionTexts = wrapper.findAll('tbody select option').map(o => o.text())
    expect(optionTexts).toContain('AIFS')
    expect(optionTexts).not.toContain('AIFS:GER:EUR')
  })
```

If `createWrapper` does not exist, first read the top of the test file to find the existing mount pattern, and adapt the assertion to match. Do NOT add a new helper; reuse what's there.

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run ui/components/diversification/allocation-table.test.ts -t "stripped of exchange suffix"`
Expected: FAIL — the option text is `AIFS:GER:EUR`, not `AIFS`.

- [ ] **Step 3: Apply formatting in the template**

Open `ui/components/diversification/allocation-table.vue`.

At line 230 (inside the `<tbody>` `<select>`), change:

```vue
                  {{ etf.symbol }}
```

to:

```vue
                  {{ formatTickerSymbol(etf.symbol) }}
```

Add the import in the `<script>` section near the other utility imports (around line 397–401):

```ts
import { formatTickerSymbol } from '../../utils/ticker-symbol'
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run ui/components/diversification/allocation-table.test.ts`
Expected: PASS (new test + all existing tests still green)

- [ ] **Step 5: Commit**

```bash
git add ui/components/diversification/allocation-table.vue ui/components/diversification/allocation-table.test.ts
git commit -m "Strip exchange suffix from ETF ticker in diversification table"
```

---

## Task 3: Apply ticker formatting in allocation-card dropdown

**Files:**
- Modify: `ui/components/diversification/allocation-card.vue:11`

- [ ] **Step 1: Update the template**

Open `ui/components/diversification/allocation-card.vue`.

At line 11 (inside the mobile card's `<select>`), change:

```vue
          {{ etf.symbol }}
```

to:

```vue
          {{ formatTickerSymbol(etf.symbol) }}
```

Add the import in the `<script setup>` section near line 78 (next to `formatTer, formatReturn`):

```ts
import { formatTickerSymbol } from '../../utils/ticker-symbol'
```

- [ ] **Step 2: Verify existing tests still pass**

Run: `npx vitest run ui/components/diversification/`
Expected: all tests still PASS (no test references `allocation-card` symbol text directly; visual change only).

- [ ] **Step 3: Commit**

```bash
git add ui/components/diversification/allocation-card.vue
git commit -m "Strip exchange suffix from ETF ticker in diversification mobile card"
```

---

## Task 4: Backend — Custom Jackson deserializer for legacy config migration

**Files:**
- Create: `src/main/kotlin/ee/tenman/portfolio/configuration/DiversificationConfigDataDeserializer.kt`
- Create: `src/test/kotlin/ee/tenman/portfolio/configuration/DiversificationConfigDataDeserializerTest.kt`
- Modify: `src/main/kotlin/ee/tenman/portfolio/domain/DiversificationConfigData.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/ee/tenman/portfolio/configuration/DiversificationConfigDataDeserializerTest.kt`:

```kotlin
package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import ee.tenman.portfolio.domain.DiversificationConfigData
import org.junit.jupiter.api.Test

class DiversificationConfigDataDeserializerTest {
  private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()

  @Test
  fun `should deserialize legacy single selectedPlatform into selectedPlatforms list`() {
    val json = """
      {
        "allocations": [],
        "inputMode": "PERCENTAGE",
        "selectedPlatform": "LHV",
        "optimizeEnabled": false,
        "totalInvestment": 0.0,
        "actionDisplayMode": "UNITS"
      }
    """.trimIndent()

    val result = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(result.selectedPlatforms).toContainExactly("LHV")
  }

  @Test
  fun `should deserialize legacy null selectedPlatform into empty list`() {
    val json = """
      {
        "allocations": [],
        "inputMode": "PERCENTAGE",
        "selectedPlatform": null,
        "optimizeEnabled": false,
        "totalInvestment": 0.0,
        "actionDisplayMode": "UNITS"
      }
    """.trimIndent()

    val result = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(result.selectedPlatforms).toBeEmpty()
  }

  @Test
  fun `should deserialize new selectedPlatforms array with multiple values`() {
    val json = """
      {
        "allocations": [],
        "inputMode": "PERCENTAGE",
        "selectedPlatforms": ["LHV", "SWEDBANK"],
        "optimizeEnabled": false,
        "totalInvestment": 0.0,
        "actionDisplayMode": "UNITS"
      }
    """.trimIndent()

    val result = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(result.selectedPlatforms).toContainExactly("LHV", "SWEDBANK")
  }

  @Test
  fun `should prefer selectedPlatforms when both fields present`() {
    val json = """
      {
        "allocations": [],
        "inputMode": "PERCENTAGE",
        "selectedPlatform": "OLD",
        "selectedPlatforms": ["NEW"],
        "optimizeEnabled": false,
        "totalInvestment": 0.0,
        "actionDisplayMode": "UNITS"
      }
    """.trimIndent()

    val result = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(result.selectedPlatforms).toContainExactly("NEW")
  }

  @Test
  fun `should default selectedPlatforms to empty list when neither field present`() {
    val json = """
      {
        "allocations": [],
        "inputMode": "PERCENTAGE",
        "optimizeEnabled": false,
        "totalInvestment": 0.0,
        "actionDisplayMode": "UNITS"
      }
    """.trimIndent()

    val result = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(result.selectedPlatforms).toBeEmpty()
  }

  @Test
  fun `should round-trip modern config through serialize and deserialize`() {
    val original =
      DiversificationConfigData(
        allocations = emptyList(),
        inputMode = ee.tenman.portfolio.domain.InputMode.PERCENTAGE,
        selectedPlatforms = listOf("LHV", "SWEDBANK"),
        optimizeEnabled = true,
        totalInvestment = 1000.0,
        actionDisplayMode = ee.tenman.portfolio.domain.ActionDisplayMode.AMOUNT,
      )

    val json = mapper.writeValueAsString(original)
    val restored = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(restored.selectedPlatforms).toContainExactly("LHV", "SWEDBANK")
    expect(restored.optimizeEnabled).toEqual(true)
    expect(restored.totalInvestment).toEqual(1000.0)
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "ee.tenman.portfolio.configuration.DiversificationConfigDataDeserializerTest"`
Expected: FAIL — `DiversificationConfigData` still has `selectedPlatform`, not `selectedPlatforms`; test won't even compile.

- [ ] **Step 3: Rename the field in the data class**

Open `src/main/kotlin/ee/tenman/portfolio/domain/DiversificationConfigData.kt` and replace the entire file with:

```kotlin
package ee.tenman.portfolio.domain

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import ee.tenman.portfolio.configuration.DiversificationConfigDataDeserializer

@JsonDeserialize(using = DiversificationConfigDataDeserializer::class)
data class DiversificationConfigData(
  val allocations: List<DiversificationAllocationData>,
  val inputMode: InputMode,
  val selectedPlatforms: List<String> = emptyList(),
  val optimizeEnabled: Boolean = false,
  val totalInvestment: Double = 0.0,
  val actionDisplayMode: ActionDisplayMode = ActionDisplayMode.UNITS,
)
```

- [ ] **Step 4: Create the deserializer**

Create `src/main/kotlin/ee/tenman/portfolio/configuration/DiversificationConfigDataDeserializer.kt`:

```kotlin
package ee.tenman.portfolio.configuration

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import ee.tenman.portfolio.domain.ActionDisplayMode
import ee.tenman.portfolio.domain.DiversificationAllocationData
import ee.tenman.portfolio.domain.DiversificationConfigData
import ee.tenman.portfolio.domain.InputMode

class DiversificationConfigDataDeserializer : JsonDeserializer<DiversificationConfigData>() {
  override fun deserialize(
    parser: JsonParser,
    ctx: DeserializationContext,
  ): DiversificationConfigData {
    val mapper = parser.codec as ObjectMapper
    val node: JsonNode = parser.readValueAsTree()
    val allocations = readAllocations(mapper, node)
    val inputMode = readInputMode(node)
    val selectedPlatforms = readSelectedPlatforms(node)
    val optimizeEnabled = node.get("optimizeEnabled")?.asBoolean() ?: false
    val totalInvestment = node.get("totalInvestment")?.asDouble() ?: 0.0
    val actionDisplayMode = readActionDisplayMode(node)
    return DiversificationConfigData(
      allocations,
      inputMode,
      selectedPlatforms,
      optimizeEnabled,
      totalInvestment,
      actionDisplayMode,
    )
  }

  private fun readAllocations(
    mapper: ObjectMapper,
    node: JsonNode,
  ): List<DiversificationAllocationData> {
    val allocationsNode = node.get("allocations") ?: return emptyList()
    val type =
      mapper.typeFactory.constructCollectionType(
        List::class.java,
        DiversificationAllocationData::class.java,
      )
    return mapper.convertValue(allocationsNode, type)
  }

  private fun readInputMode(node: JsonNode): InputMode =
    node.get("inputMode")?.asText()?.let { InputMode.fromString(it) } ?: InputMode.PERCENTAGE

  private fun readSelectedPlatforms(node: JsonNode): List<String> {
    val listNode = node.get("selectedPlatforms")
    if (listNode != null && listNode.isArray) {
      return listNode.map { it.asText() }
    }
    val legacy = node.get("selectedPlatform")
    if (legacy != null && !legacy.isNull) {
      val value = legacy.asText()
      if (value.isNotBlank()) return listOf(value)
    }
    return emptyList()
  }

  private fun readActionDisplayMode(node: JsonNode): ActionDisplayMode =
    node.get("actionDisplayMode")?.asText()?.let { ActionDisplayMode.fromString(it) }
      ?: ActionDisplayMode.UNITS
}
```

- [ ] **Step 5: Run the deserializer tests**

Run: `./gradlew test --tests "ee.tenman.portfolio.configuration.DiversificationConfigDataDeserializerTest"`
Expected: PASS (6 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/configuration/DiversificationConfigDataDeserializer.kt src/main/kotlin/ee/tenman/portfolio/domain/DiversificationConfigData.kt src/test/kotlin/ee/tenman/portfolio/configuration/DiversificationConfigDataDeserializerTest.kt
git commit -m "Add multi-platform selectedPlatforms field with legacy deserializer"
```

---

## Task 5: Backend — DTO rename and service mapper update

**Files:**
- Modify: `src/main/kotlin/ee/tenman/portfolio/dto/DiversificationConfigDto.kt`
- Modify: `src/main/kotlin/ee/tenman/portfolio/service/diversification/DiversificationConfigService.kt`
- Modify: `src/test/kotlin/ee/tenman/portfolio/service/diversification/DiversificationConfigServiceTest.kt`

- [ ] **Step 1: Update the DTO**

Open `src/main/kotlin/ee/tenman/portfolio/dto/DiversificationConfigDto.kt` and replace:

```kotlin
  val selectedPlatform: String? = null,
```

with:

```kotlin
  val selectedPlatforms: List<String> = emptyList(),
```

- [ ] **Step 2: Update the service mappers**

Open `src/main/kotlin/ee/tenman/portfolio/service/diversification/DiversificationConfigService.kt`.

On line 39, replace:

```kotlin
      selectedPlatform = configData.selectedPlatform,
```

with:

```kotlin
      selectedPlatforms = configData.selectedPlatforms,
```

On line 55, replace:

```kotlin
      selectedPlatform = selectedPlatform,
```

with:

```kotlin
      selectedPlatforms = selectedPlatforms,
```

- [ ] **Step 3: Write a new test in the existing service test file**

Open `src/test/kotlin/ee/tenman/portfolio/service/diversification/DiversificationConfigServiceTest.kt`. Inside the `class DiversificationConfigServiceTest` block, add a test that verifies the multi-platform round trip. Find the last `@Test` method in the file and add after it:

```kotlin
  @Test
  fun `should round-trip multiple selected platforms through save and get`() {
    val dto =
      DiversificationConfigDto(
        allocations =
          listOf(
            DiversificationConfigAllocationDto(instrumentId = 1L, value = BigDecimal("100")),
          ),
        inputMode = "percentage",
        selectedPlatforms = listOf("LHV", "SWEDBANK"),
      )
    val savedSlot = slot<DiversificationConfig>()
    every { repository.findConfig() } returns null
    every { repository.save(capture(savedSlot)) } answers { savedSlot.captured.apply { id = 42L } }

    val result = service.saveConfig(dto)

    expect(savedSlot.captured.configData.selectedPlatforms).toContainExactly("LHV", "SWEDBANK")
    expect(result.selectedPlatforms).toContainExactly("LHV", "SWEDBANK")
  }
```

Add the corresponding imports at the top of the file (only those not already present):

```kotlin
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
```

- [ ] **Step 4: Fix any existing tests broken by the rename**

Run: `./gradlew test --tests "ee.tenman.portfolio.service.diversification.DiversificationConfigServiceTest"`

If any existing test fails because it references the old `selectedPlatform` field name, update each occurrence in-place (fix the field name; don't add new assertions). Re-run until green.

- [ ] **Step 5: Run full Kotlin test suite for the affected classes**

Run: `./gradlew test --tests "*DiversificationConfig*"`
Expected: PASS

- [ ] **Step 6: Regenerate TypeScript types (safety step, even though DiversificationConfigDto isn't exposed)**

Run: `./gradlew compileKotlin`
Expected: SUCCESS, no TS diff expected (this DTO is not in the generator class list).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/dto/DiversificationConfigDto.kt src/main/kotlin/ee/tenman/portfolio/service/diversification/DiversificationConfigService.kt src/test/kotlin/ee/tenman/portfolio/service/diversification/DiversificationConfigServiceTest.kt
git commit -m "Rename selectedPlatform to selectedPlatforms in diversification DTO and service"
```

---

## Task 6: Frontend — Update CachedState type

**Files:**
- Modify: `ui/components/diversification/types.ts`

- [ ] **Step 1: Update the interface**

Open `ui/components/diversification/types.ts` and change:

```ts
  selectedPlatform?: string | null
```

to:

```ts
  selectedPlatforms?: string[]
```

- [ ] **Step 2: Confirm compile**

Run: `npx vue-tsc --noEmit`
Expected: FAIL — several places in `diversification-calculator.vue`, `allocation-table.vue`, `use-allocation-calculations.ts`, and tests still reference `selectedPlatform`. These are fixed in later tasks. Proceed regardless.

- [ ] **Step 3: Commit**

```bash
git add ui/components/diversification/types.ts
git commit -m "Update CachedState to use selectedPlatforms array"
```

(Intentionally commit in a broken-compile state; the next tasks complete the migration. Acceptable here because the plan's tasks run in order. If executing out of order, stage but don't commit until Task 9 completes.)

---

## Task 7: Frontend — Update useAllocationCalculations composable

**Files:**
- Modify: `ui/composables/use-allocation-calculations.ts`

- [ ] **Step 1: Update the prop type and rebalance-mode check**

Open `ui/composables/use-allocation-calculations.ts`.

At line 30, replace:

```ts
  readonly selectedPlatform: string | null
```

with:

```ts
  readonly selectedPlatforms: string[]
```

At line 47, replace:

```ts
  const showRebalanceColumns = computed(() => !!props.selectedPlatform)
```

with:

```ts
  const showRebalanceColumns = computed(() => props.selectedPlatforms.length > 0)
```

- [ ] **Step 2: Compile check**

Run: `npx vue-tsc --noEmit`
Expected: still FAIL on `allocation-table.vue`/`diversification-calculator.vue` but `use-allocation-calculations.ts` itself is clean. Proceed.

- [ ] **Step 3: Commit**

```bash
git add ui/composables/use-allocation-calculations.ts
git commit -m "Use selectedPlatforms array in allocation calculations composable"
```

---

## Task 8: Frontend — allocation-table.vue multi-select pills and props/emits

**Files:**
- Modify: `ui/components/diversification/allocation-table.vue`

- [ ] **Step 1: Replace the select dropdown with button pills in the template**

Open `ui/components/diversification/allocation-table.vue`.

Replace lines 6–18 (the entire `<div v-if="availablePlatforms.length > 0" class="platform-selector">` block):

```vue
        <div v-if="availablePlatforms.length > 0" class="platform-selector">
          <label class="d-none d-md-inline">Platform</label>
          <select
            class="form-select form-select-sm"
            :value="selectedPlatform ?? ''"
            @change="onPlatformChange"
          >
            <option value="">All platforms</option>
            <option v-for="p in availablePlatforms" :key="p" :value="p">
              {{ formatPlatformName(p) }}
            </option>
          </select>
        </div>
```

with:

```vue
        <div v-if="availablePlatforms.length > 0" class="platform-pill-group">
          <label class="d-none d-md-inline">Platform</label>
          <div class="platform-buttons">
            <button
              v-for="p in availablePlatforms"
              :key="p"
              type="button"
              class="platform-btn"
              :class="{ active: selectedPlatforms.includes(p) }"
              @click="$emit('togglePlatform', p)"
            >
              {{ formatPlatformName(p) }}
            </button>
            <button
              type="button"
              class="platform-btn platform-btn-toggle-all"
              @click="$emit('toggleAllPlatforms')"
            >
              {{
                selectedPlatforms.length === availablePlatforms.length ? 'Clear All' : 'Select All'
              }}
            </button>
          </div>
        </div>
```

- [ ] **Step 2: Update props in the script section**

At line 419, replace:

```ts
  selectedPlatform: string | null
```

with:

```ts
  selectedPlatforms: string[]
```

- [ ] **Step 3: Update emits**

At line 429, replace:

```ts
  'update:selectedPlatform': [value: string | null]
```

with:

```ts
  togglePlatform: [platform: string]
  toggleAllPlatforms: []
```

- [ ] **Step 4: Remove the obsolete change handler**

Delete lines 517–520 (the entire `onPlatformChange` function):

```ts
const onPlatformChange = (event: Event) => {
  const target = event.target as HTMLSelectElement
  emit('update:selectedPlatform', target.value || null)
}
```

- [ ] **Step 5: Add minimal styles for the platform pills**

Open the `<style scoped>` section at the bottom of the file and append the pill styles (find the last style rule inside `<style scoped>` and add after it). Use the same look-and-feel as `portfolio-summary.vue`:

```css
.platform-pill-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.platform-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.platform-btn {
  padding: 0.25rem 0.625rem;
  font-size: 0.8125rem;
  border: 1px solid var(--bs-gray-300);
  background: var(--bs-white);
  color: var(--bs-gray-700);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.12s ease;
}

.platform-btn:hover {
  border-color: var(--bs-primary);
  color: var(--bs-primary);
}

.platform-btn.active {
  background: var(--bs-primary);
  border-color: var(--bs-primary);
  color: var(--bs-white);
}

.platform-btn-toggle-all {
  font-weight: 500;
}
```

- [ ] **Step 6: Compile check**

Run: `npx vue-tsc --noEmit`
Expected: still FAIL on `diversification-calculator.vue` (references `selectedPlatform`). Proceed.

- [ ] **Step 7: Commit**

```bash
git add ui/components/diversification/allocation-table.vue
git commit -m "Replace single platform select with multi-select pill buttons"
```

---

## Task 9: Frontend — diversification-calculator.vue wiring

**Files:**
- Modify: `ui/components/diversification/diversification-calculator.vue`

- [ ] **Step 1: Update the state declaration**

Open `ui/components/diversification/diversification-calculator.vue`.

At line 181, replace:

```ts
const selectedPlatform = ref<string | null>(null)
```

with:

```ts
const selectedPlatforms = ref<string[]>([])
```

- [ ] **Step 2: Update the AllocationTable prop/event bindings in the template**

At lines 38 and 46, replace:

```vue
        :selected-platform="selectedPlatform"
```

with:

```vue
        :selected-platforms="selectedPlatforms"
```

Replace:

```vue
        @update:selected-platform="onPlatformChange"
```

with:

```vue
        @toggle-platform="togglePlatform"
        @toggle-all-platforms="toggleAllPlatforms"
```

- [ ] **Step 3: Update the `handlePriceRefresh` branch**

At lines 167–169, replace:

```ts
    if (selectedPlatform.value) {
      await loadCurrentValues(selectedPlatform.value)
    }
```

with:

```ts
    if (selectedPlatforms.value.length > 0) {
      await loadCurrentValues(selectedPlatforms.value)
    }
```

- [ ] **Step 4: Update `currentConfig`**

At line 236, replace:

```ts
  selectedPlatform: selectedPlatform.value,
```

with:

```ts
  selectedPlatforms: selectedPlatforms.value,
```

- [ ] **Step 5: Update `saveToDatabase`**

At line 331, replace:

```ts
      selectedPlatform: selectedPlatform.value,
```

with:

```ts
      selectedPlatforms: selectedPlatforms.value,
```

- [ ] **Step 6: Update `loadFromPortfolio`**

Replace lines 354–386 (the entire `loadFromPortfolio` function) with:

```ts
const loadFromPortfolio = async () => {
  isLoadingPortfolio.value = true
  error.value = ''
  try {
    const platforms =
      selectedPlatforms.value.length > 0 ? selectedPlatforms.value : undefined
    const response = await instrumentsService.getAll(platforms)
    const etfIds = new Set(etfList.value.map(e => e.instrumentId))
    const portfolioEtfs = response.instruments.filter(
      i => i.id !== null && etfIds.has(i.id) && (i.currentValue ?? 0) > 0
    )
    if (portfolioEtfs.length === 0) {
      error.value =
        selectedPlatforms.value.length > 0
          ? `No ETFs found on ${selectedPlatforms.value.join(', ')}`
          : 'No ETFs found in your portfolio'
      return
    }
    const totalValue = portfolioEtfs.reduce((sum, i) => sum + (i.currentValue ?? 0), 0)
    allocations.value = portfolioEtfs
      .filter((i): i is typeof i & { id: number } => i.id !== null)
      .map(i => ({
        instrumentId: i.id,
        value: Math.round(((i.currentValue ?? 0) / totalValue) * 1000) / 10,
        currentValue:
          selectedPlatforms.value.length > 0 ? (i.currentValue ?? 0) : undefined,
      }))
    hasUnsavedChanges.value = true
    debouncedSave()
    debouncedCalculate()
  } catch (e) {
    error.value = getErrorMessage(e)
  } finally {
    isLoadingPortfolio.value = false
  }
}
```

- [ ] **Step 7: Replace `onPlatformChange` with toggle helpers**

Replace lines 395–404 (the entire `onPlatformChange` function) with:

```ts
const togglePlatform = async (platform: string) => {
  const idx = selectedPlatforms.value.indexOf(platform)
  selectedPlatforms.value =
    idx > -1
      ? selectedPlatforms.value.filter(p => p !== platform)
      : [...selectedPlatforms.value, platform]
  await onPlatformSelectionChange()
}

const toggleAllPlatforms = async () => {
  selectedPlatforms.value =
    selectedPlatforms.value.length === availablePlatforms.value.length
      ? []
      : [...availablePlatforms.value]
  await onPlatformSelectionChange()
}

const onPlatformSelectionChange = async () => {
  hasUnsavedChanges.value = true
  debouncedSave()
  if (selectedPlatforms.value.length === 0) {
    allocations.value = allocations.value.map(a => ({ ...a, currentValue: undefined }))
    return
  }
  await loadCurrentValues(selectedPlatforms.value)
}
```

- [ ] **Step 8: Update `loadCurrentValues` signature**

Replace lines 450–465 (the entire `loadCurrentValues` function) with:

```ts
const loadCurrentValues = async (platforms: string[]) => {
  try {
    const response = await instrumentsService.getAll(platforms)
    const valueMap = new Map(
      response.instruments
        .filter((i): i is typeof i & { id: number } => i.id !== null)
        .map(i => [i.id, i.currentValue ?? 0])
    )
    allocations.value = allocations.value.map(a => ({
      ...a,
      currentValue: valueMap.get(a.instrumentId) ?? 0,
    }))
  } catch {
    allocations.value = allocations.value.map(a => ({ ...a, currentValue: 0 }))
  }
}
```

- [ ] **Step 9: Update `onImportComplete`**

Replace lines 436–448 (the entire `onImportComplete` function) with:

```ts
const onImportComplete = async (data: CachedState) => {
  allocations.value = data.allocations
  selectedPlatforms.value = data.selectedPlatforms ?? []
  optimizeEnabled.value = data.optimizeEnabled ?? false
  totalInvestment.value = data.totalInvestment ?? 0
  actionDisplayMode.value = data.actionDisplayMode ?? 'units'
  hasUnsavedChanges.value = true
  if (selectedPlatforms.value.length > 0) {
    await loadCurrentValues(selectedPlatforms.value)
  }
  debouncedSave()
  debouncedCalculate()
}
```

- [ ] **Step 10: Update the config-load watcher**

Replace lines 467–493 (the `watch(availableEtfs, ...)` block) with:

```ts
watch(
  availableEtfs,
  async newEtfs => {
    if (!newEtfs || newEtfs.length === 0 || isInitialized.value) return
    isInitialized.value = true
    const validIds = new Set(newEtfs.map(e => e.instrumentId))
    const dbConfig = await diversificationService.getConfig()
    if (!dbConfig) {
      await applyFirstTimeDefault()
      return
    }
    const validAllocations = dbConfig.allocations.filter(
      a => a.instrumentId === 0 || validIds.has(a.instrumentId)
    )
    if (validAllocations.length > 0) {
      allocations.value = validAllocations.map(a => ({
        instrumentId: Number(a.instrumentId),
        value: Number(a.value),
      }))
    }
    selectedPlatforms.value = dbConfig.selectedPlatforms ?? []
    optimizeEnabled.value = dbConfig.optimizeEnabled ?? false
    totalInvestment.value = dbConfig.totalInvestment ?? 0
    actionDisplayMode.value = dbConfig.actionDisplayMode ?? 'units'
    if (selectedPlatforms.value.length > 0) {
      await loadCurrentValues(selectedPlatforms.value)
    }
    debouncedCalculate()
  },
  { immediate: true }
)

const applyFirstTimeDefault = async () => {
  if (availablePlatforms.value.length > 0) {
    selectedPlatforms.value = [...availablePlatforms.value]
    await loadCurrentValues(selectedPlatforms.value)
    return
  }
  const stop = watch(availablePlatforms, async newPlatforms => {
    if (newPlatforms.length === 0) return
    selectedPlatforms.value = [...newPlatforms]
    await loadCurrentValues(selectedPlatforms.value)
    stop()
  })
}
```

- [ ] **Step 11: Compile check**

Run: `npx vue-tsc --noEmit`
Expected: PASS. (All references to `selectedPlatform` are removed.)

- [ ] **Step 12: Lint/format**

Run: `npm run lint-format`
Expected: all clean.

- [ ] **Step 13: Commit**

```bash
git add ui/components/diversification/diversification-calculator.vue
git commit -m "Wire multi-platform selection through diversification calculator"
```

---

## Task 10: Frontend — Update diversification-calculator.test.ts

**Files:**
- Modify: `ui/components/diversification/diversification-calculator.test.ts`

- [ ] **Step 1: Read the current test file**

First, read `ui/components/diversification/diversification-calculator.test.ts` in full to see which tests assert on `selectedPlatform`.

- [ ] **Step 2: Update any test that references `selectedPlatform` on a mock config**

For every location where a mock DB config or `saveConfig` call is constructed with `selectedPlatform: 'X'` or `selectedPlatform: null`, change to:
- `selectedPlatform: 'X'` → `selectedPlatforms: ['X']`
- `selectedPlatform: null` → `selectedPlatforms: []` (or remove the field entirely)

For every assertion that checks `selectedPlatform` on the saved payload, rewrite the assertion in terms of `selectedPlatforms`.

- [ ] **Step 3: Add a new test: "Load from Portfolio with multiple platforms"**

Add this test inside the main `describe` block (after existing tests):

```ts
  it('passes all selected platforms to instrumentsService when loading from portfolio', async () => {
    const { instrumentsService } = await import('../../services/instruments-service')
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [
        {
          id: 1,
          symbol: 'VWCE',
          name: 'Vanguard FTSE All-World',
          platforms: ['LHV', 'SWEDBANK'],
          currentValue: 500,
        } as unknown as InstrumentDto,
      ],
    })
    vi.mocked(diversificationService.getConfig).mockResolvedValue({
      allocations: [{ instrumentId: 1, value: 100 }],
      inputMode: 'percentage',
      selectedPlatforms: ['LHV', 'SWEDBANK'],
    })

    const wrapper = mount(DiversificationCalculator)
    await flushPromises()

    const loadBtn = wrapper.find('[aria-label="Load from Portfolio"]')
    await loadBtn.trigger('click')
    await flushPromises()

    expect(instrumentsService.getAll).toHaveBeenCalledWith(['LHV', 'SWEDBANK'])
  })
```

Ensure `flushPromises` and `InstrumentDto` are imported (add imports if not already present):

```ts
import { flushPromises, mount } from '@vue/test-utils'
import type { InstrumentDto } from '../../models/generated/domain-models'
```

Also ensure `diversificationService` is imported via the mock (already present in the file's top-level `vi.mock` — dereference it at runtime via `import('../../services/diversification-service').then(m => m.diversificationService)` if needed, or add a top-level import of `diversificationService` from the same file that also exposes the mock).

Take guidance from how the existing tests in this file import and use these mocks — do not invent a new mocking pattern.

- [ ] **Step 4: Run the updated test file**

Run: `npx vitest run ui/components/diversification/diversification-calculator.test.ts`
Expected: PASS (existing tests + new test).

- [ ] **Step 5: Commit**

```bash
git add ui/components/diversification/diversification-calculator.test.ts
git commit -m "Update diversification calculator tests for multi-platform selection"
```

---

## Task 11: Frontend — Update allocation-table.test.ts

**Files:**
- Modify: `ui/components/diversification/allocation-table.test.ts`

- [ ] **Step 1: Read the current test file**

Read `ui/components/diversification/allocation-table.test.ts` in full (it's ~800 lines). Scan for every reference to the string `selectedPlatform` and every reference to the `<select>` that used to handle platform selection.

- [ ] **Step 2: Update prop defaults and test data**

Wherever test code creates props for `AllocationTable`:
- Replace `selectedPlatform: null` → remove entirely (the new prop `selectedPlatforms: string[]` defaults to `[]`, or pass `selectedPlatforms: []`).
- Replace `selectedPlatform: 'LHV'` → `selectedPlatforms: ['LHV']`.

- [ ] **Step 3: Update platform-selection assertions**

For tests that emit or assert on `update:selectedPlatform`, rewrite in terms of the new events:
- If the old test verified clicking an option emitted `update:selectedPlatform`, rewrite to click a `.platform-btn` and assert on emitted `togglePlatform`.
- If the old test verified the `<select>` rendered specific options, rewrite to assert on `.platform-btn` text.

For the "Select All" / "Clear All" toggle, add (or adapt) a test that clicks the `.platform-btn-toggle-all` button and asserts on emitted `toggleAllPlatforms`.

- [ ] **Step 4: Add a test for the active state**

Add inside the existing describe block:

```ts
  it('marks selected platforms as active and unselected as inactive', () => {
    const wrapper = createWrapper({
      availablePlatforms: ['LHV', 'SWEDBANK'],
      selectedPlatforms: ['LHV'],
    })
    const pills = wrapper.findAll('.platform-btn')
    const lhv = pills.find(p => p.text() === 'LHV')
    const swedbank = pills.find(p => p.text() === 'Swedbank')
    expect(lhv?.classes()).toContain('active')
    expect(swedbank?.classes()).not.toContain('active')
  })
```

Reuse the existing `createWrapper` helper if present; otherwise follow the existing mount pattern.

- [ ] **Step 5: Run the updated test file**

Run: `npx vitest run ui/components/diversification/allocation-table.test.ts`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add ui/components/diversification/allocation-table.test.ts
git commit -m "Update allocation table tests for multi-platform pill UI"
```

---

## Task 12: Full verification

- [ ] **Step 1: Type check + lint + format**

Run: `npm run lint-format`
Expected: exit code 0, no errors.

- [ ] **Step 2: Full frontend test suite**

Run: `npm test -- --run`
Expected: all PASS.

- [ ] **Step 3: Full backend test suite for affected classes**

Run: `./gradlew test --tests "*DiversificationConfig*"`
Expected: all PASS.

- [ ] **Step 4: Kotlin compile (regenerates TS as a side effect)**

Run: `./gradlew compileKotlin`
Expected: SUCCESS, no unexpected TS diff in `ui/models/generated/domain-models.ts`.

- [ ] **Step 5: Manual browser verification**

Start the dev server: `npm run dev`

Open http://localhost:61234/diversification and verify:
1. Platform filter shows button pills (not a dropdown).
2. All available platforms are selected by default (first-time user) — or the previously-saved selection is restored.
3. Clicking a platform pill toggles its active state. The `Select All`/`Clear All` button appears and toggles correctly.
4. With two platforms selected (e.g., LHV + SWEDBANK), clicking "Load from Portfolio" loads ETFs across both and the ETF list shows combined holdings.
5. ETF dropdown options show cleaned ticker symbols (e.g., `AIFS` not `AIFS:GER:EUR`).
6. With all platforms selected (default), rebalance columns are visible with current values.
7. Clicking "Clear All" empties the selection → rebalance columns hide (new-investment mode). Current values clear.
8. Reload the page — the previously-saved selection (from DB) is restored.

- [ ] **Step 6: Final commit (only if fixes were needed)**

If manual testing surfaced small fixes, make them and commit. Otherwise, no action.

```bash
git status
```

Expected: working tree clean after all prior commits.

---

## Self-Review Checklist

- [x] **Spec coverage:** Multi-select (Tasks 8–10), Load from Portfolio with multi (Task 9, Task 10 step 3), ticker stripping (Tasks 1–3), backend rename + migration (Tasks 4–5), frontend type update (Task 6), allocation calc update (Task 7), DB persistence (Task 9 via `saveToDatabase`), first-time default (Task 9 `applyFirstTimeDefault`), rebalance-mode semantic shift (Task 7 `showRebalanceColumns`).
- [x] **Type consistency:** `selectedPlatforms` uniformly typed as `string[]` / `List<String>` across Kotlin + TS. Emits are `togglePlatform` + `toggleAllPlatforms` in both the component, its parent, and the tests. `formatTickerSymbol` signature `(symbol: string) => string` consistent across call sites.
- [x] **Placeholder scan:** No "TBD/TODO". All steps contain the exact code, command, or expected output.
- [x] **Out of order safety:** Task 6 commits a TypeScript-broken state. Acceptable because downstream tasks in this plan fix compilation within 1-2 steps. If the executor runs tasks out of order, they should squash Task 6 with Task 9 before leaving the branch.
