package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class SummaryPersistenceServiceTest {
  private val portfolioDailySummaryRepository = mockk<PortfolioDailySummaryRepository>()
  private lateinit var summaryPersistenceService: SummaryPersistenceService

  @BeforeEach
  fun setUp() {
    summaryPersistenceService = SummaryPersistenceService(portfolioDailySummaryRepository)
  }

  @Test
  fun `should return zero when summaries list is empty`() {
    val result = summaryPersistenceService.saveSummaries(emptyList())

    expect(result).toEqual(0)
    verify(exactly = 0) { portfolioDailySummaryRepository.saveAll(any<List<PortfolioDailySummary>>()) }
  }

  @Test
  fun `should save summaries and return count`() {
    val summaries = listOf(createSummary(LocalDate.of(2024, 1, 15)))
    every { portfolioDailySummaryRepository.saveAll(summaries) } returns summaries

    val result = summaryPersistenceService.saveSummaries(summaries)

    expect(result).toEqual(1)
    verify(exactly = 1) { portfolioDailySummaryRepository.saveAll(summaries) }
  }

  @Test
  fun `should save multiple summaries and return correct count`() {
    val summaries =
      listOf(
        createSummary(LocalDate.of(2024, 1, 15)),
        createSummary(LocalDate.of(2024, 1, 16)),
        createSummary(LocalDate.of(2024, 1, 17)),
      )
    every { portfolioDailySummaryRepository.saveAll(summaries) } returns summaries

    val result = summaryPersistenceService.saveSummaries(summaries)

    expect(result).toEqual(3)
    verify(exactly = 1) { portfolioDailySummaryRepository.saveAll(summaries) }
  }

  private fun createSummary(date: LocalDate): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = date,
      totalValue = BigDecimal("10000"),
      totalProfit = BigDecimal("500"),
      xirrAnnualReturn = BigDecimal("0.05"),
      earningsPerDay = BigDecimal("10"),
    )
}
