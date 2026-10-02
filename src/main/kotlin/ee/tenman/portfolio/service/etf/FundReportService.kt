package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.FundAllocation
import ee.tenman.portfolio.dto.FundReportDto
import ee.tenman.portfolio.dto.FundWeightDto
import ee.tenman.portfolio.repository.FundAllocationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FundReportService(
  private val fundAllocationRepository: FundAllocationRepository,
) {
  @Transactional(readOnly = true)
  fun reports(isin: String): List<FundReportDto> =
    fundAllocationRepository
      .findByFundIsin(isin)
      .groupBy { it.sourceUrl }
      .values
      .map(::report)
      .sortedByDescending { it.asOfDate }

  private fun report(rows: List<FundAllocation>): FundReportDto =
    FundReportDto(
      asOfDate = rows.first().asOfDate,
      funds = rows.sortedByDescending { it.weight }.map { FundWeightDto(it.underlyingIsin, name(it), it.weight) },
    )

  private fun name(row: FundAllocation): String = NAMES[row.underlyingIsin] ?: row.underlyingName

  companion object {
    private val NAMES =
      mapOf(
        "IE00BFG1TM61" to "BlackRock ISF World",
        "IE0009FT4LX4" to "BlackRock CCF World",
        "IE000I9HGDZ3" to "Xtrackers World Screened",
        "IE000QWCYQT0" to "Amundi World Screened",
        "IE00BFNM3D14" to "iShares Europe Screened",
        "IE00BFNM3G45" to "iShares USA Screened",
        "IE00BFNM3L97" to "iShares Japan Screened",
        "IE00BKPTWY98" to "iShares Emerging Markets",
      )
  }
}
