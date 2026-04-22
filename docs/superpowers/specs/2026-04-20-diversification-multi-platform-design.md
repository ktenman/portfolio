# Diversification Tab: Multi-Platform Filter and Ticker Symbol Display

Date: 2026-04-20
Status: Approved for implementation

## Problem

The Diversification tab has two UX friction points:

1. **Single-platform filter.** The platform filter is a `<select>` dropdown that allows one choice at a time. Users who hold the same ETFs across multiple platforms (e.g., LHV and Swedbank) cannot run "Load from Portfolio" against a combined set — they must pick one, load, then mentally merge. Every other platform-filtered view in the app (Portfolio Summary, ETF Breakdown, Transactions, Instruments) already supports multi-select via a shared `usePlatformFilter` composable.

2. **Noisy ticker symbols.** ETFs sourced from Lightyear are stored with the full exchange-and-currency suffix in `instrument.symbol` (e.g., `AIFS:GER:EUR`, `QDVF:GER:EUR`, `EUDF:GER:EUR`). The dropdown renders the raw value, which is visually noisy and redundant when the user is just identifying the ETF by ticker.

## Goals

- Allow selecting multiple platforms (or none) in the Diversification tab.
- "Load from Portfolio" uses the selected platforms as a combined filter, loading ETF holdings from all of them.
- ETF symbols display as the ticker only (`AIFS`, `EUDF`), not the raw exchange-suffixed form.
- Behavior stays consistent with the rest of the app: same filter UI, same default (all platforms selected).
- Existing persisted user config with a single `selectedPlatform` continues to work without user intervention.

## Non-Goals

- Changing ticker display in other views (Portfolio Summary, ETF Breakdown, etc.). Those views may rely on exchange/currency disambiguation and are out of scope.
- Database schema changes. The diversification config is JSON-serialized into a single column; read-time deserialization handles the migration from old field to new field.
- New backend API endpoints. `GET /api/instruments?platforms=...` already accepts a list; only the data class that serializes to the config column changes.

## Design

### Rebalance-Mode Semantics (Behavioral Shift)

Today: "rebalance mode" (shows current holdings, Buy/Sell actions) activates only when a specific platform is chosen. Empty selection = new-investment-planning mode.

New: The platform filter **is** the mode selector.

| Selection state                   | Mode                        | `currentValue` captured? |
| --------------------------------- | --------------------------- | ------------------------ |
| All available platforms (default) | Rebalance (whole portfolio) | Yes                      |
| Subset (e.g., LHV + Swedbank)     | Rebalance (scoped)          | Yes                      |
| Empty (user clicked "Clear All")  | New investment planning     | No                       |

First-time users land in rebalance-whole-portfolio mode. To return to the old "new investment" default, they click "Clear All".

Rationale: Matches the convention of every other platform-filtered view in the app. The filter default (all selected) means "show my whole portfolio." Users who want pure new-investment planning can opt out explicitly.

### Frontend — Multi-Select Platform Filter

**Do not reuse `usePlatformFilter`.** It uses `useLocalStorage` and auto-fills to all platforms whenever `selectedPlatforms` is empty and `availablePlatforms` changes. Diversification has different semantics (DB-persisted config, empty-selection is an intentional "new-investment mode" state), so we'd have to fight the composable.

**Approach:** a plain `ref<string[]>` in `diversification-calculator.vue` with small inline toggle helpers (`togglePlatform`, `toggleAllPlatforms`, `isPlatformSelected`). The UI/visual pattern (button pills + Select All / Clear All) matches the other views, but the state is DB-backed, not localStorage-backed.

**First-time default**: if there's no saved DB config, select all available platforms once they load. If the saved config has `selectedPlatforms: []`, respect it (new-investment mode).

**Changes to `ui/components/diversification/allocation-table.vue`:**

- Remove the `<select>` dropdown (lines 6–18).
- Add the platform-button-pill UI used in `ui/components/portfolio-summary.vue:9–26`:
  - One button per available platform, toggling its membership in `selectedPlatforms`.
  - Trailing "Select All" / "Clear All" button (label switches based on state).
  - Class `.active` applied when the platform is in `selectedPlatforms`.
- Replace props:
  - Remove `selectedPlatform: string | null`.
  - Add `selectedPlatforms: string[]`.
  - Keep `availablePlatforms: string[]`.
- Replace emits:
  - Remove `update:selectedPlatform`.
  - Add `togglePlatform: [platform: string]` and `toggleAllPlatforms: []`.

**Changes to `ui/components/diversification/diversification-calculator.vue`:**

- Replace `const selectedPlatform = ref<string | null>(null)` with `const selectedPlatforms = ref<string[]>([])`.
- Add inline helpers: `togglePlatform(platform)`, `toggleAllPlatforms()`, `isPlatformSelected(platform)` — each small, pure, and paired with the existing `onPlatformSelectionChange()` side-effect orchestrator.
- Wire the ref and helpers into `<AllocationTable>` via the new props/emits.
- Update `loadFromPortfolio()` (currently lines 354–386):
  - Compute `platforms = selectedPlatforms.value.length > 0 ? selectedPlatforms.value : undefined`.
  - Call `instrumentsService.getAll(platforms)` — API already accepts `string[]`.
  - Capture `currentValue` on each allocation when `selectedPlatforms.value.length > 0` (rebalance mode), `undefined` otherwise. The "filtered subset" flag becomes `selectedPlatforms.value.length > 0` instead of `!!selectedPlatform.value`.
  - Error message when the filter yields no ETFs: adapt the existing message to list the selected platforms (e.g., `"No ETFs found on LHV, SWEDBANK"`) rather than a single name.
- Update `currentConfig` (lines 233–240) and `saveToDatabase` (lines 325–344) to send `selectedPlatforms` instead of `selectedPlatform`.
- Update the loader that applies saved config (around line 438): read `data.selectedPlatforms` and assign it via the composable's setter. No frontend-side migration needed — the backend deserializer handles old persisted configs before they reach the DTO.
- Update `loadCurrentValues` trigger (lines 443–444) to run when `selectedPlatforms.value.length > 0`.

### Frontend — Ticker Symbol Display

**New utility `ui/utils/ticker-symbol.ts`:**

```ts
export const formatTickerSymbol = (symbol: string): string => symbol.split(':')[0]
```

Pure function: `AIFS:GER:EUR → AIFS`, `VUAA → VUAA`, `'' → ''`.

**Application points (Diversification tab only):**

- `allocation-table.vue:230` — dropdown `<option>` label.
- `allocation-card.vue` — any place it prints the symbol in the mobile card.
- Any Buy/Sell action label that includes the symbol (search the diversification folder for `etf.symbol` / ETF-name display sites and apply).

The underlying data (`instrumentId`, full `symbol` on the DTO, API calls) is untouched. This is a display-only transformation.

### Backend — Config Data Class

**Changes to `src/main/kotlin/ee/tenman/portfolio/domain/DiversificationConfigData.kt`:**

Replace `val selectedPlatform: String? = null` with `val selectedPlatforms: List<String> = emptyList()`.

**Read-time migration for old persisted configs.**

Old configs serialized as `{"selectedPlatform": "LHV", ...}`. We need to deserialize them into the new shape without a DB migration. Options:

1. **Jackson `@JsonAlias` + `@JsonIgnoreProperties(ignoreUnknown = true)`** — won't work directly because the old field is a `String`, the new is `List<String>`.
2. **Custom Jackson deserializer** on `DiversificationConfigData` that reads either field. Recommended.

Implementation sketch (custom deserializer):

```kotlin
class DiversificationConfigDataDeserializer : JsonDeserializer<DiversificationConfigData>() {
  override fun deserialize(parser: JsonParser, ctx: DeserializationContext): DiversificationConfigData {
    val node = parser.readValueAsTree<JsonNode>()
    val selectedPlatforms = when {
      node.has("selectedPlatforms") -> node.get("selectedPlatforms").map { it.asText() }
      node.hasNonNull("selectedPlatform") -> listOf(node.get("selectedPlatform").asText())
      else -> emptyList()
    }
    // ... parse remaining fields normally ...
  }
}
```

Annotate `DiversificationConfigData` with `@JsonDeserialize(using = DiversificationConfigDataDeserializer::class)`. Serialization writes only `selectedPlatforms` (no custom serializer needed).

**Also update:**

- `src/main/kotlin/ee/tenman/portfolio/dto/DiversificationConfigDto.kt` — mirror the field rename.
- `src/main/kotlin/ee/tenman/portfolio/service/diversification/DiversificationConfigService.kt` — rename in the mapping logic (lines 39, 55).
- Generated TypeScript types regenerate automatically on `./gradlew compileKotlin`.

### Testing

**Frontend:**

- New `ui/utils/ticker-symbol.test.ts`:
  - `AIFS:GER:EUR → AIFS`
  - `VUAA → VUAA`
  - Empty string → empty string
  - String with no colon, single colon, multiple colons.
- Update `ui/components/diversification/` tests:
  - `allocation-table` rendering test to reflect button pills instead of `<select>`.
  - `diversification-calculator` `loadFromPortfolio` test for multi-platform case (asserts `instrumentsService.getAll` called with `['LHV', 'SWEDBANK']`).
  - Test for ticker formatting applied in the dropdown.
- Existing `use-platform-filter.test.ts` — no changes; composable is unmodified.

**Backend:**

- New test on the Jackson deserializer (or on `DiversificationConfigDataConverter`) asserting:
  - Old JSON `{"selectedPlatform": "LHV", ...}` → `selectedPlatforms = ["LHV"]`.
  - Old JSON `{"selectedPlatform": null, ...}` → `selectedPlatforms = []`.
  - New JSON `{"selectedPlatforms": ["LHV", "SWEDBANK"], ...}` → round-trips correctly.
  - JSON with both fields → `selectedPlatforms` wins.
- Update `DiversificationConfigService` unit tests to use the new field.

### Rollout

- Merge in one PR. The read-time deserializer handles existing user configs seamlessly on first load.
- No Flyway migration required.
- After deploy, the first time a user saves their config, the stored JSON is rewritten into the new shape. Old configs that are never re-saved continue to deserialize correctly indefinitely.

## Open Questions

None. All decisions confirmed with user on 2026-04-20.
