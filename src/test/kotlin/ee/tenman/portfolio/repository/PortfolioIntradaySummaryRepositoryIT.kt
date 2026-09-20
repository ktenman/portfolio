package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.job.TransactionRunner
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

@IntegrationTest
class PortfolioIntradaySummaryRepositoryIT {
  @Resource
  private lateinit var portfolioIntradaySummaryRepository: PortfolioIntradaySummaryRepository

  @Resource
  private lateinit var transactionRunner: TransactionRunner

  private fun upsert(
    capturedAt: String,
    totalValue: String,
    platformKey: String = "",
  ) = transactionRunner.runInTransaction {
    portfolioIntradaySummaryRepository.upsert(
      Instant.parse(capturedAt),
      platformKey,
      BigDecimal(totalValue),
      BigDecimal("0.1857"),
      BigDecimal("42.5"),
      BigDecimal("1.25"),
    )
  }

  @Test
  fun `should overwrite the existing row when the same captured at is upserted twice`() {
    upsert("2026-09-20T10:15:00Z", "100.00")
    upsert("2026-09-20T10:15:00Z", "222.22")

    val rows = portfolioIntradaySummaryRepository.findAll()

    expect(rows).toHaveSize(1)
  }

  @Test
  fun `should keep the latest value when the same captured at is upserted twice`() {
    upsert("2026-09-20T10:15:00Z", "100.00")
    upsert("2026-09-20T10:15:00Z", "222.22")

    val saved = portfolioIntradaySummaryRepository.findAll().single()

    expect(saved.totalValue).toEqualNumerically(BigDecimal("222.22"))
  }

  @Test
  fun `should return the last point of every bucket when bucketing by epoch seconds`() {
    upsert("2026-09-20T10:00:00Z", "1")
    upsert("2026-09-20T10:04:00Z", "2")
    upsert("2026-09-20T10:06:00Z", "3")

    val bucketed = portfolioIntradaySummaryRepository.findBucketed(Instant.parse("2026-09-20T09:00:00Z"), 300, "")

    expect(bucketed.map { it.totalValue.toInt() }).toEqual(listOf(2, 3))
  }

  @Test
  fun `should ignore points captured before the requested start when bucketing`() {
    upsert("2026-09-20T08:00:00Z", "1")
    upsert("2026-09-20T10:00:00Z", "2")

    val bucketed = portfolioIntradaySummaryRepository.findBucketed(Instant.parse("2026-09-20T09:00:00Z"), 300, "")

    expect(bucketed).toHaveSize(1)
  }

  @Test
  fun `should keep the rows of different platforms captured at the same minute`() {
    upsert("2026-09-20T10:15:00Z", "100.00")
    upsert("2026-09-20T10:15:00Z", "40.00", "LIGHTYEAR_BUSINESS")

    expect(portfolioIntradaySummaryRepository.findAll()).toHaveSize(2)
  }

  @Test
  fun `should return only the rows of the requested platform when bucketing`() {
    upsert("2026-09-20T10:00:00Z", "100.00")
    upsert("2026-09-20T10:00:00Z", "40.00", "LIGHTYEAR_BUSINESS")

    val bucketed =
      portfolioIntradaySummaryRepository.findBucketed(
        Instant.parse("2026-09-20T09:00:00Z"),
        300,
        "LIGHTYEAR_BUSINESS",
      )

    expect(bucketed.single().totalValue).toEqualNumerically(BigDecimal("40.00"))
  }

  @Test
  fun `should delete only the rows captured before the cutoff`() {
    upsert("2026-09-01T10:00:00Z", "1")
    upsert("2026-09-19T10:00:00Z", "2")

    transactionRunner.runInTransaction {
      portfolioIntradaySummaryRepository.deleteOlderThan(Instant.parse("2026-09-10T00:00:00Z"))
    }

    expect(portfolioIntradaySummaryRepository.findAll()).toHaveSize(1)
  }
}
