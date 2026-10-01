package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.FundAllocation
import ee.tenman.portfolio.repository.FundAllocationRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class FundReportServiceTest {
  @Test
  fun `should list reports newest first`() {
    val repository = mockk<FundAllocationRepository>()
    every { repository.findByFundIsin(FUND) } returns
      listOf(allocation(JULY, "IE00BFG1TM61", "29.13"), allocation(AUGUST, "IE00BFG1TM61", "29.07"))
    expect(FundReportService(repository).reports(FUND).map { it.asOfDate }).toContainExactly(AUGUST, JULY)
  }

  @Test
  fun `should list the funds of a report by descending weight`() {
    val repository = mockk<FundAllocationRepository>()
    every { repository.findByFundIsin(FUND) } returns
      listOf(allocation(AUGUST, "IE00BKPTWY98", "12.55"), allocation(AUGUST, "IE000I9HGDZ3", "29.36"))
    expect(
      FundReportService(repository)
      .reports(FUND)
      .single()
      .funds
      .map { it.isin },
        ).toContainExactly("IE000I9HGDZ3", "IE00BKPTWY98")
  }

  @Test
  fun `should keep two reports of the same date apart when they come from different sources`() {
    val repository = mockk<FundAllocationRepository>()
    every { repository.findByFundIsin(FUND) } returns
      listOf(allocation(AUGUST, "IE00BKPTWY98", "12.55", "a.pdf"), allocation(AUGUST, "IE00BKPTWY98", "12.56", "b.pdf"))
    expect(FundReportService(repository).reports(FUND).map { it.funds.size }).toContainExactly(1, 1)
  }

  private fun allocation(
    date: LocalDate,
    isin: String,
    weight: String,
    url: String = "https://tuleva.ee/aruanne-$date.pdf",
  ) = FundAllocation(
    fundIsin = FUND,
    asOfDate = date,
    sourceUrl = url,
    reportedTotal = BigDecimal(100),
    underlyingIsin = isin,
    underlyingName = "Fond $isin õ",
    weight = BigDecimal(weight),
  )

  companion object {
    private const val FUND = "EE3600001707"
    private val JULY = LocalDate.of(2026, 7, 31)
    private val AUGUST = LocalDate.of(2026, 8, 31)
  }
}
