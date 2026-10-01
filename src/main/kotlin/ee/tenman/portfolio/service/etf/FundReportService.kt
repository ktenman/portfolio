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
      funds = rows.sortedByDescending { it.weight }.map { FundWeightDto(it.underlyingIsin, it.underlyingName, it.weight) },
    )
}
