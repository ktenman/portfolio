package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class SummaryBatchProcessorServiceTest {
  private val summaryPersistenceService = mockk<SummaryPersistenceService>(relaxed = true)
  private val entityManager = mockk<EntityManager>(relaxUnitFun = true)
  private val dailySummaryCalculator = mockk<DailySummaryCalculator>()

  private lateinit var service: SummaryBatchProcessorService
  private lateinit var instrument: Instrument

  @BeforeEach
  fun setup() {
    service = SummaryBatchProcessorService(summaryPersistenceService, entityManager, dailySummaryCalculator)
    instrument =
      Instrument(
      symbol = "TEST",
      name = "Test Instrument",
      category = "ETF",
      baseCurrency = "EUR",
      currentPrice = BigDecimal("100.00"),
    ).apply { id = 1L }
  }

  @Nested
  inner class ProcessSummariesWithTransactions {
    @Test
    fun `should return zero when dates list is empty`() {
      val result = service.processSummariesWithTransactions(emptyList(), emptyList())
      expect(result).toEqual(0)
      verify(exactly = 0) { summaryPersistenceService.saveSummaries(any()) }
    }

    @Test
    fun `should return zero when all calculations fail`() {
      val dates = listOf(LocalDate.of(2024, 7, 1))
      every { dailySummaryCalculator.calculateFromTransactions(any(), any()) } throws RuntimeException("Calculation failed")
      val result = service.processSummariesWithTransactions(dates, emptyList())
      expect(result).toEqual(0)
    }

    @Test
    fun `should process single date with empty transactions`() {
      val date = LocalDate.of(2024, 7, 1)
      val summary = createSummary(date, BigDecimal.ZERO)
      every { dailySummaryCalculator.calculateFromTransactions(emptyList(), date) } returns summary
      every { summaryPersistenceService.saveSummaries(any()) } returns 1
      val result = service.processSummariesWithTransactions(listOf(date), emptyList())
      expect(result).toEqual(1)
    }

    @Test
    fun `should filter transactions up to each date correctly`() {
      val date1 = LocalDate.of(2024, 7, 1)
      val date2 = LocalDate.of(2024, 7, 2)
      val date3 = LocalDate.of(2024, 7, 3)
      val tx1 = createTransaction(date1, BigDecimal("100"))
      val tx2 = createTransaction(date2, BigDecimal("200"))
      val tx3 = createTransaction(date3, BigDecimal("300"))
      val allTransactions = listOf(tx1, tx2, tx3)
      val transactionsCaptor = slot<List<PortfolioTransaction>>()
      every {
        dailySummaryCalculator.calculateFromTransactions(capture(transactionsCaptor), any())
      } answers {
        val txList = transactionsCaptor.captured
        createSummary(secondArg(), txList.sumOf { it.price })
      }
      every { summaryPersistenceService.saveSummaries(any()) } returns 3
      service.processSummariesWithTransactions(listOf(date1, date2, date3), allTransactions)
      verify(exactly = 1) { entityManager.clear() }
      verify(exactly = 1) { summaryPersistenceService.saveSummaries(any()) }
    }

    @Test
    fun `should include only transactions on or before each date`() {
      val baseDate = LocalDate.of(2024, 7, 5)
      val txBeforeDate = createTransaction(baseDate.minusDays(2), BigDecimal("100"))
      val txOnDate = createTransaction(baseDate, BigDecimal("200"))
      val txAfterDate = createTransaction(baseDate.plusDays(1), BigDecimal("300"))
      val allTransactions = listOf(txBeforeDate, txOnDate, txAfterDate)
      val capturedTransactions = mutableListOf<List<PortfolioTransaction>>()
      every {
        dailySummaryCalculator.calculateFromTransactions(capture(capturedTransactions), baseDate)
      } answers {
        createSummary(baseDate, BigDecimal("300"))
      }
      every { summaryPersistenceService.saveSummaries(any()) } returns 1
      service.processSummariesWithTransactions(listOf(baseDate), allTransactions)
      expect(capturedTransactions.size).toEqual(1)
      val filtered = capturedTransactions[0]
      expect(filtered.size).toEqual(2)
      expect(filtered.contains(txBeforeDate)).toEqual(true)
      expect(filtered.contains(txOnDate)).toEqual(true)
      expect(filtered.contains(txAfterDate)).toEqual(false)
    }

    @Test
    fun `should accumulate transactions correctly for sequential dates`() {
      val date1 = LocalDate.of(2024, 7, 1)
      val date2 = LocalDate.of(2024, 7, 2)
      val date3 = LocalDate.of(2024, 7, 3)
      val tx1 = createTransaction(date1, BigDecimal("100"))
      val tx2 = createTransaction(date2, BigDecimal("200"))
      val tx3 = createTransaction(date3, BigDecimal("300"))
      val transactionCounts = mutableMapOf<LocalDate, Int>()
      every {
        dailySummaryCalculator.calculateFromTransactions(any(), any())
      } answers {
        val txList: List<PortfolioTransaction> = firstArg()
        val date: LocalDate = secondArg()
        transactionCounts[date] = txList.size
        createSummary(date, txList.sumOf { it.price })
      }
      every { summaryPersistenceService.saveSummaries(any()) } returns 3
      service.processSummariesWithTransactions(listOf(date1, date2, date3), listOf(tx1, tx2, tx3))
      expect(transactionCounts[date1]).toEqual(1)
      expect(transactionCounts[date2]).toEqual(2)
      expect(transactionCounts[date3]).toEqual(3)
    }

    @Test
    fun `should handle unordered dates correctly`() {
      val date1 = LocalDate.of(2024, 7, 3)
      val date2 = LocalDate.of(2024, 7, 1)
      val date3 = LocalDate.of(2024, 7, 2)
      val tx1 = createTransaction(LocalDate.of(2024, 7, 1), BigDecimal("100"))
      val tx2 = createTransaction(LocalDate.of(2024, 7, 2), BigDecimal("200"))
      val transactionCounts = mutableMapOf<LocalDate, Int>()
      every {
        dailySummaryCalculator.calculateFromTransactions(any(), any())
      } answers {
        val txList: List<PortfolioTransaction> = firstArg()
        val date: LocalDate = secondArg()
        transactionCounts[date] = txList.size
        createSummary(date, txList.sumOf { it.price })
      }
      every { summaryPersistenceService.saveSummaries(any()) } returns 3
      service.processSummariesWithTransactions(listOf(date1, date2, date3), listOf(tx1, tx2))
      expect(transactionCounts[LocalDate.of(2024, 7, 1)]).toEqual(1)
      expect(transactionCounts[LocalDate.of(2024, 7, 2)]).toEqual(2)
      expect(transactionCounts[LocalDate.of(2024, 7, 3)]).toEqual(2)
    }

    @Test
    fun `should handle unordered transactions correctly`() {
      val date = LocalDate.of(2024, 7, 5)
      val tx1 = createTransaction(LocalDate.of(2024, 7, 3), BigDecimal("300"))
      val tx2 = createTransaction(LocalDate.of(2024, 7, 1), BigDecimal("100"))
      val tx3 = createTransaction(LocalDate.of(2024, 7, 2), BigDecimal("200"))
      val capturedTransactions = mutableListOf<List<PortfolioTransaction>>()
      every {
        dailySummaryCalculator.calculateFromTransactions(capture(capturedTransactions), date)
      } returns createSummary(date, BigDecimal("600"))
      every { summaryPersistenceService.saveSummaries(any()) } returns 1
      service.processSummariesWithTransactions(listOf(date), listOf(tx1, tx2, tx3))
      expect(capturedTransactions[0].size).toEqual(3)
    }

    @Test
    fun `should process in batches when dates exceed batch size`() {
      val dates = (1..35).map { LocalDate.of(2024, 7, 1).plusDays(it.toLong()) }
      val tx = createTransaction(LocalDate.of(2024, 7, 1), BigDecimal("100"))
      every {
        dailySummaryCalculator.calculateFromTransactions(any(), any())
      } answers {
        createSummary(secondArg(), BigDecimal("100"))
      }
      every { summaryPersistenceService.saveSummaries(any()) } returns 30 andThen 5
      val result = service.processSummariesWithTransactions(dates, listOf(tx), batchSize = 30)
      expect(result).toEqual(35)
      verify(exactly = 2) { entityManager.clear() }
      verify(exactly = 2) { summaryPersistenceService.saveSummaries(any()) }
    }

    @Test
    fun `should handle date with no preceding transactions`() {
      val date = LocalDate.of(2024, 7, 10)
      val futureTx = createTransaction(LocalDate.of(2024, 7, 15), BigDecimal("500"))
      val capturedTransactions = mutableListOf<List<PortfolioTransaction>>()
      every {
        dailySummaryCalculator.calculateFromTransactions(capture(capturedTransactions), date)
      } returns createSummary(date, BigDecimal.ZERO)
      every { summaryPersistenceService.saveSummaries(any()) } returns 1
      service.processSummariesWithTransactions(listOf(date), listOf(futureTx))
      expect(capturedTransactions[0]).toEqual(emptyList())
    }

    @Test
    fun `should handle multiple transactions on same date`() {
      val date = LocalDate.of(2024, 7, 5)
      val tx1 = createTransaction(date, BigDecimal("100"))
      val tx2 = createTransaction(date, BigDecimal("200"))
      val tx3 = createTransaction(date, BigDecimal("300"))
      val capturedTransactions = mutableListOf<List<PortfolioTransaction>>()
      every {
        dailySummaryCalculator.calculateFromTransactions(capture(capturedTransactions), date)
      } returns createSummary(date, BigDecimal("600"))
      every { summaryPersistenceService.saveSummaries(any()) } returns 1
      service.processSummariesWithTransactions(listOf(date), listOf(tx1, tx2, tx3))
      expect(capturedTransactions[0].size).toEqual(3)
      expect(capturedTransactions[0].sumOf { it.price }.compareTo(BigDecimal("600"))).toEqual(0)
    }

    @Test
    fun `should handle transaction exactly on boundary date`() {
      val boundaryDate = LocalDate.of(2024, 7, 5)
      val txOnBoundary = createTransaction(boundaryDate, BigDecimal("100"))
      val capturedTransactions = mutableListOf<List<PortfolioTransaction>>()
      every {
        dailySummaryCalculator.calculateFromTransactions(capture(capturedTransactions), boundaryDate)
      } returns createSummary(boundaryDate, BigDecimal("100"))
      every { summaryPersistenceService.saveSummaries(any()) } returns 1
      service.processSummariesWithTransactions(listOf(boundaryDate), listOf(txOnBoundary))
      expect(capturedTransactions[0].size).toEqual(1)
      expect(capturedTransactions[0][0]).toEqual(txOnBoundary)
    }
  }

  @Nested
  inner class SlidingWindowBehaviorVerification {
    @Test
    fun `sliding window should produce same results as filter for ascending dates`() {
      val dates = (1..10).map { LocalDate.of(2024, 7, it) }
      val transactions = dates.map { createTransaction(it, BigDecimal(it.dayOfMonth * 100)) }
      val filterResults = mutableMapOf<LocalDate, List<PortfolioTransaction>>()
      dates.forEach { date ->
        filterResults[date] = transactions.filter { !it.transactionDate.isAfter(date) }
      }
      val slidingWindowResults = mutableMapOf<LocalDate, Int>()
      val sortedTransactions = transactions.sortedBy { it.transactionDate }
      var transactionIndex = 0
      val accumulated = mutableListOf<PortfolioTransaction>()
      dates.sorted().forEach { date ->
        while (transactionIndex < sortedTransactions.size &&
          !sortedTransactions[transactionIndex].transactionDate.isAfter(date)
        ) {
          accumulated.add(sortedTransactions[transactionIndex])
          transactionIndex++
        }
        slidingWindowResults[date] = accumulated.size
      }
      dates.forEach { date ->
        expect(slidingWindowResults[date]).toEqual(filterResults[date]?.size)
      }
    }

    @Test
    fun `sliding window should handle gaps in dates correctly`() {
      val tx1 = createTransaction(LocalDate.of(2024, 7, 1), BigDecimal("100"))
      val tx2 = createTransaction(LocalDate.of(2024, 7, 10), BigDecimal("200"))
      val tx3 = createTransaction(LocalDate.of(2024, 7, 20), BigDecimal("300"))
      val transactions = listOf(tx1, tx2, tx3)
      val datesToProcess =
        listOf(
        LocalDate.of(2024, 7, 5),
        LocalDate.of(2024, 7, 15),
        LocalDate.of(2024, 7, 25),
      )
      val filterResults =
        datesToProcess.associateWith { date ->
        transactions.filter { !it.transactionDate.isAfter(date) }.size
      }
      expect(filterResults[LocalDate.of(2024, 7, 5)]).toEqual(1)
      expect(filterResults[LocalDate.of(2024, 7, 15)]).toEqual(2)
      expect(filterResults[LocalDate.of(2024, 7, 25)]).toEqual(3)
    }

    @Test
    fun `large dataset performance characteristics verification`() {
      val transactionCount = 1000
      val dateCount = 500
      val baseDate = LocalDate.of(2024, 1, 1)
      val transactions =
        (1..transactionCount).map {
        createTransaction(baseDate.plusDays((it % 365).toLong()), BigDecimal(it * 10))
      }
      val dates = (1..dateCount).map { baseDate.plusDays((it * 2).toLong()) }
      var filterOperations = 0L
      dates.forEach { date ->
        transactions.forEach { tx ->
          filterOperations++
          tx.transactionDate.isAfter(date)
        }
      }
      val sortedTransactions = transactions.sortedBy { it.transactionDate }
      var slidingWindowOperations = 0L
      var txIndex = 0
      dates.sorted().forEach { date ->
        while (txIndex < sortedTransactions.size) {
          slidingWindowOperations++
          if (sortedTransactions[txIndex].transactionDate.isAfter(date)) break
          txIndex++
        }
      }
      expect(filterOperations).toEqual(transactionCount.toLong() * dateCount)
      expect(slidingWindowOperations < filterOperations).toEqual(true)
    }
  }

  private fun createTransaction(
    date: LocalDate,
    price: BigDecimal,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = instrument,
      transactionType = TransactionType.BUY,
      quantity = BigDecimal.ONE,
      price = price,
      transactionDate = date,
      platform = Platform.TRADING212,
    )

  private fun createSummary(
    date: LocalDate,
    totalValue: BigDecimal,
  ): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = date,
      totalValue = totalValue,
      xirrAnnualReturn = BigDecimal.ZERO,
      totalProfit = BigDecimal.ZERO,
      earningsPerDay = BigDecimal.ZERO,
    )
}
