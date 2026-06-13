package ee.tenman.portfolio.blackrock

import ee.tenman.portfolio.dto.HoldingData
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

@Service
class CsusHoldingsService(
  private val blackRockHoldingsClient: BlackRockHoldingsClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchHoldings(): List<HoldingData> {
    val csv = blackRockHoldingsClient.getHoldingsCsv(PRODUCT_ID, "${FUND}_holdings", "csv", "fund")
    val holdings = BlackRockCsvParser.parse(csv)
    if (holdings.isEmpty()) {
      log.warn("BlackRock returned 0 equity holdings for $FUND")
      return emptyList()
    }
    return holdings
      .sortedByDescending { it.weight }
      .mapIndexed { index, holding ->
        HoldingData(
          name = holding.name,
          ticker = holding.ticker,
          sector = holding.sector,
          weight = holding.weight,
          rank = index + 1,
        )
      }
  }

  companion object {
    private const val PRODUCT_ID = "253740"
    private const val FUND = "CSUS"
  }
}
