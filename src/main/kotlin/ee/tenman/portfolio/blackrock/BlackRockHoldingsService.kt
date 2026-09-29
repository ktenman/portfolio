package ee.tenman.portfolio.blackrock

import ee.tenman.portfolio.dto.HoldingData
import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service

@Service
class BlackRockHoldingsService(
  private val blackRockHoldingsClient: BlackRockHoldingsClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Retryable(maxRetries = 2, multiplier = 2.0, excludes = [FeignException.FeignClientException::class])
  fun fetchHoldings(fund: BlackRockFund): List<HoldingData> {
    val csv = blackRockHoldingsClient.getHoldingsCsv(fund.productId, "${fund}_holdings", "csv", "fund")
    val holdings = BlackRockCsvParser.parse(csv)
    if (holdings.isEmpty()) {
      log.warn("BlackRock returned 0 equity holdings for $fund")
      return emptyList()
    }
    return holdings
      .sortedByDescending { it.weight }
      .mapIndexed { index, holding ->
        HoldingData(
          name = holding.name,
          ticker = holding.ticker,
          sector = null,
          weight = holding.weight,
          rank = index + 1,
        )
      }
  }
}
