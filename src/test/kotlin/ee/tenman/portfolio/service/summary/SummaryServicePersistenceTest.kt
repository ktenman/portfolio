package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.PortfolioDailySummary
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class SummaryServicePersistenceTest : SummaryServiceTestBase() {
  @Test
  fun `deleteAllDailySummaries should delete all summaries`() {
    summaryService.deleteAllDailySummaries()

    verify(exactly = 1) { portfolioDailySummaryRepository.deleteAll() }
  }

  @Test
  fun `recalculateAllDailySummaries should handle empty transactions`() {
    every { transactionService.getAllTransactions() } returns emptyList()

    val count = summaryService.recalculateAllDailySummaries()

    expect(count).toEqual(0)
    verify(exactly = 0) { portfolioDailySummaryRepository.deleteAll() }
    verify(exactly = 0) { portfolioDailySummaryRepository.saveAll(any<List<PortfolioDailySummary>>()) }
  }

  @Test
  fun `recalculateAllDailySummaries should process all dates between first transaction and yesterday`() {
    val today = LocalDate.of(2024, 7, 5)
    every { clock.instant() } returns today.atStartOfDay(ZoneId.systemDefault()).toInstant()
    every { transactionService.getAllTransactions() } returns
      listOf(createBuyTransaction(BigDecimal("10"), BigDecimal("100"), LocalDate.of(2024, 7, 1)))
    val processedDates = slot<List<LocalDate>>()
    every { summaryBatchProcessor.processSummariesWithTransactions(capture(processedDates), any(), any()) } returns 4

    val count = summaryService.recalculateAllDailySummaries()

    expect(count).toEqual(4)
    verify { summaryDeletionService.deleteHistoricalSummaries(today) }
    verify(exactly = 2) { summaryCacheService.evictAllCaches() }
    expect(processedDates.captured).toContainExactly(
      LocalDate.of(2024, 7, 1),
      LocalDate.of(2024, 7, 2),
      LocalDate.of(2024, 7, 3),
      LocalDate.of(2024, 7, 4),
    )
  }

  @Test
  fun `saveDailySummaries should update existing summaries and add new ones`() {
    val existingDate = LocalDate.of(2024, 7, 1)
    val newDate = LocalDate.of(2024, 7, 2)
    val existing = createSummary(existingDate, "100", "0.05", "10", "0.01")
    val updatedSummary = createSummary(existingDate, "200", "0.06", "20", "0.02")
    val newSummary = createSummary(newDate, "300", "0.07", "30", "0.03")

    every { portfolioDailySummaryRepository.findAllByEntryDateIn(listOf(existingDate, newDate)) } returns listOf(existing)
    every { portfolioDailySummaryRepository.saveAll(any<List<PortfolioDailySummary>>()) } answers { firstArg() }

    summaryService.saveDailySummaries(listOf(updatedSummary, newSummary))

    val summaryListCaptor = slot<List<PortfolioDailySummary>>()
    verify { portfolioDailySummaryRepository.saveAll(capture(summaryListCaptor)) }
    val saved = summaryListCaptor.captured
    expect(saved).toHaveSize(2)

    val firstSaved = saved.first { it.entryDate == existingDate }
    expect(firstSaved.totalValue).toEqualNumerically(BigDecimal("200"))
    expect(firstSaved.xirrAnnualReturn).toEqualNumerically(BigDecimal("0.06"))
    expect(firstSaved.totalProfit).toEqualNumerically(BigDecimal("20"))
    expect(firstSaved.earningsPerDay).toEqualNumerically(BigDecimal("0.02"))

    expect(saved.first { it.entryDate == newDate }).toEqual(newSummary)
  }

  @Test
  fun `getDailySummariesBetween should return summaries between dates`() {
    val start = LocalDate.of(2024, 7, 1)
    val end = LocalDate.of(2024, 7, 5)
    val between =
      listOf(
        createSummary(LocalDate.of(2024, 7, 2), "110", "0.06", "20", "0.02"),
        createSummary(LocalDate.of(2024, 7, 3), "120", "0.07", "30", "0.03"),
      )
    every { portfolioDailySummaryRepository.findAllByEntryDateBetween(start, end) } returns between

    val result = summaryService.getDailySummariesBetween(start, end)

    expect(result).toEqual(between)
  }

  @Test
  fun `saveDailySummary should create new summary when none exists`() {
    val newDate = LocalDate.of(2024, 8, 1)
    val newSummary = createSummary(newDate, "1000.00", "0.05", "50.00", "0.14")

    every { portfolioDailySummaryRepository.findByEntryDate(newDate) } returns null
    every { portfolioDailySummaryRepository.save(any<PortfolioDailySummary>()) } returns newSummary

    val result = summaryService.saveDailySummary(newSummary)

    expect(result).toEqual(newSummary)
    val summaryCaptor = slot<PortfolioDailySummary>()
    verify { portfolioDailySummaryRepository.save(capture(summaryCaptor)) }
    expect(summaryCaptor.captured).toEqual(newSummary)
  }

  @Test
  fun `saveDailySummary should update existing summary when found`() {
    val existingDate = LocalDate.of(2024, 8, 1)
    val existing =
      createSummary(existingDate, "1000.00", "0.05", "50.00", "0.14").apply {
        id = 123L
        version = 1
      }
    val updated = createSummary(existingDate, "1200.00", "0.07", "100.00", "0.23")

    every { portfolioDailySummaryRepository.findByEntryDate(existingDate) } returns existing
    every { portfolioDailySummaryRepository.save(any<PortfolioDailySummary>()) } returns existing

    summaryService.saveDailySummary(updated)

    val summaryCaptor = slot<PortfolioDailySummary>()
    verify { portfolioDailySummaryRepository.save(capture(summaryCaptor)) }
    val saved = summaryCaptor.captured
    expect(saved.id).toEqual(123L)
    expect(saved.version).toEqual(1)
    expect(saved.totalValue).toEqualNumerically(BigDecimal("1200.00"))
    expect(saved.xirrAnnualReturn).toEqualNumerically(BigDecimal("0.07"))
    expect(saved.totalProfit).toEqualNumerically(BigDecimal("100.00"))
    expect(saved.earningsPerDay).toEqualNumerically(BigDecimal("0.23"))
  }
}
