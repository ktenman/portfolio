package ee.tenman.portfolio.trading212

import ee.tenman.portfolio.dto.HoldingData
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class Trading212HoldingsService(
  private val etfClient: Trading212EtfClient,
  private val enricher: Trading212HoldingEnricher,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchHoldings(ticker: String): List<HoldingData> {
    val raw = etfClient.getHoldings(ticker)
    if (raw.isEmpty()) {
      log.warn("Trading212 returned 0 holdings for ticker $ticker")
      return emptyList()
    }
    val sorted = raw.sortedByDescending { it.percentage }
    return sorted.mapIndexed { index, holding -> enricher.enrich(holding, rank = index + 1) }
  }

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchTer(ticker: String): BigDecimal? = etfClient.getSummary(ticker).expenseRatio
}
