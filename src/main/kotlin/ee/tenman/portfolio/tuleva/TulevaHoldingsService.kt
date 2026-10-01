package ee.tenman.portfolio.tuleva

import ee.tenman.portfolio.blackrock.BlackRockFund
import ee.tenman.portfolio.blackrock.BlackRockHoldingsService
import ee.tenman.portfolio.domain.FundAllocation
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.FundAllocationRepository
import ee.tenman.portfolio.service.etf.HoldingAggregationService
import ee.tenman.portfolio.service.infrastructure.MinioService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.net.URI
import java.time.LocalDate

@Service
class TulevaHoldingsService(
  private val tulevaReportClient: TulevaReportClient,
  private val fundAllocationRepository: FundAllocationRepository,
  private val blackRockHoldingsService: BlackRockHoldingsService,
  private val holdingAggregationService: HoldingAggregationService,
  private val minioService: MinioService,
  @Value("\${tuleva.web-url}") webUrl: String,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val base = URI(webUrl)

  fun importReports() {
    val known = fundAllocationRepository.findByFundIsin(SYMBOL).associate { it.sourceUrl to it.asOfDate }
    val archived = minioService.fundReportDates(SYMBOL)
    val failures =
      reportUrls().filterNot { it in known }.mapNotNull { url -> attempt(url) { import(url) } } +
        known.filterValues { it !in archived }.mapNotNull { (url, date) -> attempt(url) { archive(url, date) } }
    failures.firstOrNull()?.let { throw it }
  }

  fun lookThrough(): List<HoldingData> {
    val latest =
      requireNotNull(
        fundAllocationRepository.findFirstByFundIsinOrderByAsOfDateDescIdDesc(SYMBOL),
      ) { "No Tuleva allocation report imported" }
    return fundAllocationRepository
      .findBySourceUrl(latest.sourceUrl)
      .groupBy(::proxy)
      .flatMap { (fund, allocations) -> scaled(fund, allocations.sumOf { it.weight }) }
      .groupBy { holdingAggregationService.normalizeHoldingName(it.name) }
      .values
      .map { group -> group.first().copy(weight = group.sumOf { it.weight }) }
      .sortedByDescending { it.weight }
      .mapIndexed { index, holding -> holding.copy(rank = index + 1) }
  }

  private fun reportUrls(): List<String> {
    val first = tulevaReportClient.listMedia(1)
    val pages = requireNotNull(first.headers.getFirst(TOTAL_PAGES_HEADER)) { "Tuleva media listing has no $TOTAL_PAGES_HEADER header" }
    return (listOf(first) + (2..pages.toInt()).map { tulevaReportClient.listMedia(it) })
      .flatMap { it.body.orEmpty() }
      .map { it.sourceUrl }
      .filter { it.endsWith(".pdf") }
  }

  private fun attempt(
    url: String,
    task: () -> Unit,
  ): Throwable? = runCatching(task).onFailure { log.error("Tuleva report import failed for $url", it) }.exceptionOrNull()

  private fun import(url: String) {
    val pdf = download(url)
    val report = TulevaAllocationParser.parse(pdf)
    fundAllocationRepository.saveAll(report.rows.map { it.toAllocation(url, report) })
    minioService.uploadFundReport(SYMBOL, report.asOfDate, pdf)
    log.info("Imported Tuleva allocation report of ${report.asOfDate} with ${report.rows.size} funds from $url")
  }

  private fun archive(
    url: String,
    asOfDate: LocalDate,
  ) {
    minioService.uploadFundReport(SYMBOL, asOfDate, download(url))
    log.info("Archived Tuleva allocation report of $asOfDate from $url")
  }

  private fun download(url: String): ByteArray {
    val uri = URI(url)
    require(uri.scheme == base.scheme && uri.host == base.host) { "Tuleva report $url is not served from $base" }
    return tulevaReportClient.download(uri)
  }

  private fun proxy(allocation: FundAllocation): BlackRockFund =
    requireNotNull(TULEVA_PROXIES[allocation.underlyingIsin]) {
      "Tuleva fund ${allocation.underlyingIsin} ${allocation.underlyingName} has no look-through proxy"
    }

  private fun scaled(
    fund: BlackRockFund,
    weight: BigDecimal,
  ): List<HoldingData> {
    val holdings = blackRockHoldingsService.fetchHoldings(fund)
    val total = holdings.sumOf { it.weight }
    require(total in MIN_EQUITY..MAX_EQUITY) { "BlackRock $fund equity weights sum to $total" }
    return holdings.map { it.copy(weight = (it.weight * weight).movePointLeft(2)) }
  }

  private fun TulevaAllocationRow.toAllocation(
    url: String,
    report: TulevaAllocationReport,
  ) = FundAllocation(
    fundIsin = SYMBOL,
    asOfDate = report.asOfDate,
    sourceUrl = url,
    reportedTotal = report.reportedTotal,
    underlyingIsin = isin,
    underlyingName = name,
    weight = weight,
    repairedFrom = repairedFrom,
  )

  companion object {
    const val SYMBOL = "EE3600001707"
    private val MIN_EQUITY = BigDecimal(95)
    private val MAX_EQUITY = BigDecimal(101)
  }
}
