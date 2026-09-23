package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.ninjasquad.springmockk.MockkBean
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.domain.IndustrySource
import ee.tenman.portfolio.domain.SectorSource
import ee.tenman.portfolio.domain.VanguardIndustryUpdate
import ee.tenman.portfolio.repository.EtfHoldingRepository
import io.mockk.every
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@IntegrationTest
class EtfHoldingIndustryServiceIT {
  @Resource
  private lateinit var service: EtfHoldingIndustryService

  @Resource
  private lateinit var repository: EtfHoldingRepository

  @Resource
  private lateinit var jdbc: JdbcTemplate

  @MockkBean
  private lateinit var clock: Clock

  @BeforeEach
  fun setup() {
    every { clock.instant() } returns Instant.parse("2026-09-23T00:15:00Z")
    every { clock.zone } returns ZoneOffset.UTC
  }

  @Test
  fun `cannot overwrite an existing industry with a delayed LLM classification`() {
    val holding = repository.save(EtfHolding(name = "Haleon", industry = GicsIndustry.PHARMACEUTICALS))
    service.updateIndustry(holding.id, GicsIndustry.PERSONAL_CARE_PRODUCTS, AiModel.GPT_5_6_LUNA)
    expect(repository.findById(holding.id).orElseThrow().industry).toEqual(GicsIndustry.PHARMACEUTICALS)
  }

  @Test
  fun `should save LLM provenance only when an industry is missing`() {
    val holding = repository.save(EtfHolding(name = "Haleon"))
    val changed = service.updateIndustry(holding.id, GicsIndustry.PERSONAL_CARE_PRODUCTS, AiModel.GPT_5_6_LUNA)
    val saved = repository.findById(holding.id).orElseThrow()
    expect(changed).toEqual(true)
    expect(saved.industrySource).toEqual(IndustrySource.LLM)
    expect(saved.industryClassifiedByModel).toEqual(AiModel.GPT_5_6_LUNA)
    expect(saved.industryEffectiveDate).toEqual(null)
  }

  @Test
  fun `should accept only one of two concurrent LLM classifications`() {
    val holding = repository.save(EtfHolding(name = "Haleon"))
    val start = CountDownLatch(1)
    val results =
      Executors.newVirtualThreadPerTaskExecutor().use { executor ->
        val tasks =
          listOf(GicsIndustry.PHARMACEUTICALS, GicsIndustry.PERSONAL_CARE_PRODUCTS).map { industry ->
            executor.submit<Boolean> {
              check(start.await(10, TimeUnit.SECONDS))
              service.updateIndustry(holding.id, industry, AiModel.GPT_5_6_LUNA)
            }
          }
        start.countDown()
        tasks.map { it.get(15, TimeUnit.SECONDS) }
      }
    expect(results.count { it }).toEqual(1)
  }

  @Test
  fun `should replace an LLM industry with dated Vanguard provenance`() {
    val holding = llmHolding()
    val changed = update(holding)
    val saved = repository.findById(holding.id).orElseThrow()
    expect(changed).toEqual(1)
    expect(saved.industry).toEqual(GicsIndustry.PHARMACEUTICALS)
    expect(saved.industrySource).toEqual(IndustrySource.VANGUARD)
    expect(saved.industryEffectiveDate).toEqual(LocalDate.of(2026, 8, 31))
    expect(saved.industryClassifiedByModel).toEqual(null)
  }

  @Test
  fun `should fill a missing industry from Vanguard`() {
    val holding = repository.save(EtfHolding(name = "Haleon"))
    val changed = update(holding)
    val saved = repository.findById(holding.id).orElseThrow()
    expect(changed).toEqual(1)
    expect(saved.industry).toEqual(GicsIndustry.PHARMACEUTICALS)
    expect(saved.industrySource).toEqual(IndustrySource.VANGUARD)
    expect(saved.sector).toEqual(IndustrySector.HEALTH)
  }

  @Test
  fun `should refresh only sectors derived from a replaced industry`() {
    val holding = llmHolding()
    update(holding)
    val saved = repository.findById(holding.id).orElseThrow()
    expect(saved.sector).toEqual(IndustrySector.HEALTH)
    expect(saved.sectorSource).toEqual(SectorSource.INDUSTRY)
    expect(saved.classifiedByModel).toEqual(null)
  }

  @Test
  fun `should rebuild an independent sector while preserving identity and country fields`() {
    val holding =
      repository.save(
        EtfHolding(
          name = "Haleon Ü",
          ticker = "HLN",
          countryCode = "GB",
          countryName = "United Kingdom",
          sector = IndustrySector.CONSUMER_ESSENTIALS,
          sectorSource = SectorSource.LIGHTYEAR,
          industry = GicsIndustry.PERSONAL_CARE_PRODUCTS,
          industrySource = IndustrySource.LLM,
          industryClassifiedByModel = AiModel.GPT_5_6_LUNA,
        ),
      )
    update(holding)
    val saved = repository.findById(holding.id).orElseThrow()
    expect(listOf(saved.uuid, saved.name, saved.ticker, saved.countryCode, saved.countryName, saved.sector, saved.sectorSource))
      .toEqual(listOf(holding.uuid, "Haleon Ü", "HLN", "GB", "United Kingdom", IndustrySector.HEALTH, SectorSource.INDUSTRY))
  }

  @Test
  fun `should repair an existing sector even when the Vanguard industry observation is unchanged`() {
    val holding = vanguardHolding()
    jdbc.update("UPDATE etf_holding SET sector = 'Consumer Essentials', sector_source = 'LLM' WHERE id = ?", holding.id)
    val changed = update(holding)
    val saved = repository.findById(holding.id).orElseThrow()
    expect(changed).toEqual(1)
    expect(saved.sector to saved.sectorSource).toEqual(IndustrySector.HEALTH to SectorSource.INDUSTRY)
  }

  @Test
  fun `should backfill existing Vanguard sectors without another holdings import`() {
    val holding = vanguardHolding()
    jdbc.update("UPDATE etf_holding SET sector = 'Consumer Essentials', sector_source = 'LIGHTYEAR' WHERE id = ?", holding.id)
    val changed = service.deriveSectorsFromIndustries()
    val repeated = service.deriveSectorsFromIndustries()
    val saved = repository.findById(holding.id).orElseThrow()
    expect(changed).toEqual(1)
    expect(repeated).toEqual(0)
    expect(saved.sector to saved.sectorSource).toEqual(IndustrySector.HEALTH to SectorSource.INDUSTRY)
  }

  @ParameterizedTest
  @ValueSource(strings = ["UNKNOWN", "null"])
  fun `cannot replace an industry whose existing source is unknown`(source: String) {
    val holding =
      repository.save(
        EtfHolding(name = "Haleon", industry = GicsIndustry.PERSONAL_CARE_PRODUCTS, industrySource = source(source)),
      )
    val changed = update(holding)
    expect(changed).toEqual(0)
    expect(repository.findById(holding.id).orElseThrow().industry).toEqual(GicsIndustry.PERSONAL_CARE_PRODUCTS)
  }

  @ParameterizedTest
  @ValueSource(strings = ["UNKNOWN", "null"])
  fun `should adopt provenance when an unknown industry agrees with Vanguard`(source: String) {
    val holding = repository.save(EtfHolding(name = "Haleon", industry = GicsIndustry.PHARMACEUTICALS, industrySource = source(source)))
    val changed = update(holding)
    expect(changed).toEqual(1)
    expect(repository.findById(holding.id).orElseThrow().industrySource).toEqual(IndustrySource.VANGUARD)
  }

  @ParameterizedTest
  @ValueSource(strings = ["2026-07-22", "2026-09-24"])
  fun `cannot apply an expired or future Vanguard observation`(date: String) {
    val holding = llmHolding()
    val changed = update(holding, date = LocalDate.parse(date))
    expect(changed).toEqual(0)
    expect(repository.findById(holding.id).orElseThrow().industrySource).toEqual(IndustrySource.LLM)
  }

  @ParameterizedTest
  @ValueSource(strings = ["2026-07-23", "2026-09-23"])
  fun `should accept the inclusive calendar month boundaries`(date: String) {
    expect(update(llmHolding(), date = LocalDate.parse(date))).toEqual(1)
  }

  @Test
  fun `cannot replace a newer Vanguard classification with an older observation`() {
    val holding = vanguardHolding()
    val changed = update(holding, GicsIndustry.PERSONAL_CARE_PRODUCTS, LocalDate.of(2026, 7, 31))
    expect(changed).toEqual(0)
    expect(repository.findById(holding.id).orElseThrow().industryEffectiveDate).toEqual(LocalDate.of(2026, 8, 31))
  }

  @Test
  fun `cannot change an existing Vanguard industry for the same effective date`() {
    val holding = vanguardHolding()
    val changed = update(holding, GicsIndustry.PERSONAL_CARE_PRODUCTS)
    expect(changed).toEqual(0)
    expect(repository.findById(holding.id).orElseThrow().industry).toEqual(GicsIndustry.PHARMACEUTICALS)
  }

  @Test
  fun `should advance the Vanguard effective date even when the industry is unchanged`() {
    val holding = vanguardHolding()
    val changed = update(holding, date = LocalDate.of(2026, 9, 1))
    expect(changed).toEqual(1)
    expect(repository.findById(holding.id).orElseThrow().industryEffectiveDate).toEqual(LocalDate.of(2026, 9, 1))
  }

  @Test
  fun `cannot count an identical Vanguard observation as a change`() {
    val holding = vanguardHolding()
    val version = holding.version
    val changed = update(holding)
    expect(changed).toEqual(0)
    expect(repository.findById(holding.id).orElseThrow().version).toEqual(version)
  }

  @Test
  fun `should select the newest date regardless of the order of observations`() {
    val holding = llmHolding()
    val updates =
      listOf(
        VanguardIndustryUpdate(holding.uuid, GicsIndustry.PHARMACEUTICALS, LocalDate.of(2026, 8, 31)),
        VanguardIndustryUpdate(holding.uuid, GicsIndustry.PERSONAL_CARE_PRODUCTS, LocalDate.of(2026, 7, 31)),
      )
    val changed = service.updateVanguardIndustries(updates)
    expect(changed).toEqual(1)
    expect(repository.findById(holding.id).orElseThrow().industry).toEqual(GicsIndustry.PHARMACEUTICALS)
  }

  @Test
  fun `should quarantine conflicting industries for the same date while applying other holdings`() {
    val conflict = llmHolding()
    val healthy = repository.save(EtfHolding(name = "Garmin"))
    val date = LocalDate.of(2026, 8, 31)
    val changed =
      service.updateVanguardIndustries(
        listOf(
          VanguardIndustryUpdate(conflict.uuid, GicsIndustry.PHARMACEUTICALS, date),
          VanguardIndustryUpdate(healthy.uuid, GicsIndustry.HOUSEHOLD_DURABLES, date),
          VanguardIndustryUpdate(conflict.uuid, GicsIndustry.PERSONAL_CARE_PRODUCTS, date),
        ),
      )
    expect(changed).toEqual(1)
    expect(repository.findById(conflict.id).orElseThrow().industrySource).toEqual(IndustrySource.LLM)
    expect(repository.findById(healthy.id).orElseThrow().industry).toEqual(GicsIndustry.HOUSEHOLD_DURABLES)
  }

  @Test
  fun `should count duplicate agreeing observations as one changed holding`() {
    val holding = llmHolding()
    val observation = VanguardIndustryUpdate(holding.uuid, GicsIndustry.PHARMACEUTICALS, LocalDate.of(2026, 8, 31))
    expect(service.updateVanguardIndustries(listOf(observation, observation))).toEqual(1)
  }

  @Test
  fun `cannot replace Vanguard with a previously requested LLM result`() {
    val holding = repository.save(EtfHolding(name = "Haleon"))
    update(holding)
    val changed = service.updateIndustry(holding.id, GicsIndustry.PERSONAL_CARE_PRODUCTS, AiModel.GPT_5_6_LUNA)
    expect(changed).toEqual(false)
    expect(repository.findById(holding.id).orElseThrow().industrySource).toEqual(IndustrySource.VANGUARD)
  }

  @Test
  fun `cannot create a holding for an unresolved UUID`() {
    val update = VanguardIndustryUpdate(UUID.randomUUID(), GicsIndustry.PHARMACEUTICALS, LocalDate.of(2026, 8, 31))
    expect(service.updateVanguardIndustries(listOf(update))).toEqual(0)
    expect(repository.count()).toEqual(0L)
  }

  private fun llmHolding(): EtfHolding =
    repository.save(
      EtfHolding(
        name = "Haleon",
        industry = GicsIndustry.PERSONAL_CARE_PRODUCTS,
        industrySource = IndustrySource.LLM,
        industryClassifiedByModel = AiModel.GPT_5_6_LUNA,
      ),
    )

  private fun vanguardHolding(): EtfHolding =
    repository.save(
      EtfHolding(
        name = "Haleon",
        industry = GicsIndustry.PHARMACEUTICALS,
        industrySource = IndustrySource.VANGUARD,
        industryEffectiveDate = LocalDate.of(2026, 8, 31),
      ),
    )

  private fun source(value: String): IndustrySource? = value.takeUnless { it == "null" }?.let(IndustrySource::valueOf)

  private fun update(
    holding: EtfHolding,
    industry: GicsIndustry = GicsIndustry.PHARMACEUTICALS,
    date: LocalDate = LocalDate.of(2026, 8, 31),
  ): Int = service.updateVanguardIndustries(listOf(VanguardIndustryUpdate(holding.uuid, industry, date)))
}
