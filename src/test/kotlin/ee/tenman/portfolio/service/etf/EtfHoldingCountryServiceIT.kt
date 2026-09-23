package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.ninjasquad.springmockk.MockkBean
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.CountrySource
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.VanguardCountryUpdate
import ee.tenman.portfolio.repository.EtfHoldingRepository
import io.mockk.every
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@IntegrationTest
class EtfHoldingCountryServiceIT {
  @Resource
  private lateinit var service: EtfHoldingCountryService

  @Resource
  private lateinit var repository: EtfHoldingRepository

  @MockkBean
  private lateinit var clock: Clock

  private val date = LocalDate.of(2026, 8, 31)

  @BeforeEach
  fun setup() {
    every { clock.instant() } returns Instant.parse("2026-09-23T12:00:00Z")
    every { clock.zone } returns ZoneOffset.UTC
  }

  @Test
  fun `should replace an LLM country with dated Vanguard provenance on the same UUID`() {
    val holding = llmHolding()
    val changed = update(holding, "GB")
    val saved = repository.findById(holding.id).orElseThrow()
    expect(changed).toEqual(1)
    expect(saved.uuid).toEqual(holding.uuid)
    expect(saved.countryCode to saved.countryName).toEqual("GB" to "United Kingdom")
    expect(saved.countrySource to saved.countryEffectiveDate).toEqual(CountrySource.VANGUARD to date)
    expect(saved.countryClassifiedByModel).toEqual(null)
  }

  @Test
  fun `should fill a missing country from Vanguard`() {
    val holding = repository.save(EtfHolding(name = "Tundmatu Ühistu OÜ"))
    update(holding, "EE")
    val saved = repository.findById(holding.id).orElseThrow()
    expect(saved.countryCode to saved.countryName).toEqual("EE" to "Estonia")
    expect(saved.countrySource).toEqual(CountrySource.VANGUARD)
  }

  @ParameterizedTest
  @ValueSource(strings = ["RULE", "UNKNOWN"])
  fun `should replace existing fallback countries with an unambiguous Vanguard country`(source: String) {
    val holding = repository.save(EtfHolding(name = "Haleon PLC", countryCode = "US", countrySource = CountrySource.valueOf(source)))
    update(holding, "GB")
    expect(repository.findById(holding.id).orElseThrow().countryCode).toEqual("GB")
  }

  @Test
  fun `cannot replace any populated country with a delayed LLM answer`() {
    val holding = llmHolding()
    val changed = service.updateCountry(holding.id, "CA", "Canada", AiModel.GPT_6_LUNA)
    expect(changed).toEqual(false)
    expect(repository.findById(holding.id).orElseThrow().countryCode).toEqual("US")
  }

  @Test
  fun `should record LLM provenance when filling a missing country`() {
    val holding = repository.save(EtfHolding(name = "Nestlé SA"))
    val changed = service.updateCountry(holding.id, "CH", "Switzerland", AiModel.GPT_6_LUNA)
    val saved = repository.findById(holding.id).orElseThrow()
    expect(changed).toEqual(true)
    expect(saved.countrySource).toEqual(CountrySource.LLM)
    expect(saved.countryClassifiedByModel).toEqual(AiModel.GPT_6_LUNA)
  }

  @Test
  fun `should record rule provenance when an automatic country assignment has no model`() {
    val holding = repository.save(EtfHolding(name = "Apple Inc"))
    service.updateCountry(holding.id, "US", "United States", null)
    expect(repository.findById(holding.id).orElseThrow().countrySource).toEqual(CountrySource.RULE)
  }

  @ParameterizedTest
  @ValueSource(strings = ["2026-07-22", "2026-09-24"])
  fun `cannot apply stale or future country observations`(value: String) {
    val holding = llmHolding()
    expect(update(holding, "GB", LocalDate.parse(value))).toEqual(0)
    expect(repository.findById(holding.id).orElseThrow().countryCode).toEqual("US")
  }

  @ParameterizedTest
  @ValueSource(strings = ["2026-07-23", "2026-09-23"])
  fun `should accept countries on the inclusive freshness boundaries`(value: String) {
    expect(update(llmHolding(), "GB", LocalDate.parse(value))).toEqual(1)
  }

  @ParameterizedTest
  @ValueSource(strings = ["", "ZZ", "USA"])
  fun `cannot persist invalid source country codes`(code: String) {
    val holding = llmHolding()
    expect(update(holding, code)).toEqual(0)
    expect(repository.findById(holding.id).orElseThrow().countrySource).toEqual(CountrySource.LLM)
  }

  @Test
  fun `should preserve a country when the newest observations conflict`() {
    val holding = llmHolding()
    val updates = listOf(VanguardCountryUpdate(holding.uuid, "CA", date), VanguardCountryUpdate(holding.uuid, "US", date))
    val changed = service.updateVanguardCountries(updates)
    val saved = repository.findById(holding.id).orElseThrow()
    expect(changed).toEqual(0)
    expect(saved.countryCode to saved.countrySource).toEqual("US" to CountrySource.LLM)
  }

  @Test
  fun `should select the newest country rather than an older conflicting observation`() {
    val holding = llmHolding()
    val updates =
      listOf(VanguardCountryUpdate(holding.uuid, "CA", date.minusMonths(1)), VanguardCountryUpdate(holding.uuid, "GB", date))
    service.updateVanguardCountries(updates)
    expect(repository.findById(holding.id).orElseThrow().countryCode).toEqual("GB")
  }

  @Test
  fun `cannot downgrade an existing Vanguard country to an older date`() {
    val holding = vanguardHolding()
    expect(update(holding, "US", date.minusMonths(1))).toEqual(0)
    expect(repository.findById(holding.id).orElseThrow().countryCode).toEqual("GB")
  }

  @Test
  fun `cannot replace an existing Vanguard country with a conflicting value on the same date`() {
    val holding = vanguardHolding()
    expect(update(holding, "US")).toEqual(0)
    expect(repository.findById(holding.id).orElseThrow().countryCode).toEqual("GB")
  }

  @Test
  fun `should avoid writing an identical Vanguard country again`() {
    val holding = vanguardHolding()
    expect(update(holding, "GB")).toEqual(0)
    expect(repository.findById(holding.id).orElseThrow().version).toEqual(holding.version)
  }

  @Test
  fun `cannot create a holding for an unresolved UUID`() {
    val changed = service.updateVanguardCountries(listOf(VanguardCountryUpdate(UUID.randomUUID(), "GB", date)))
    expect(changed).toEqual(0)
    expect(repository.count()).toEqual(0L)
  }

  private fun update(
    holding: EtfHolding,
    country: String,
    effective: LocalDate = date,
  ): Int = service.updateVanguardCountries(listOf(VanguardCountryUpdate(holding.uuid, country, effective)))

  private fun llmHolding(): EtfHolding =
    repository.save(
      EtfHolding(
        name = "Haleon PLC",
        countryCode = "US",
        countryName = "United States",
        countrySource = CountrySource.LLM,
        countryClassifiedByModel = AiModel.GPT_5_6_LUNA,
      ),
    )

  private fun vanguardHolding(): EtfHolding =
    repository.save(
      EtfHolding(
        name = "Haleon PLC",
        countryCode = "GB",
        countryName = "United Kingdom",
        countrySource = CountrySource.VANGUARD,
        countryEffectiveDate = date,
      ),
    )
}
