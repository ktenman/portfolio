package ee.tenman.portfolio.model

import java.time.Instant

object CollectionSchedules {
  const val TIME_ZONE = "Europe/Tallinn"
  const val LIGHTYEAR_PRICE_CRON = "0/30 * 6-23 * * MON-FRI"
  const val FT_HISTORY_CRON = "0 0 5 * * *"
  const val LIGHTYEAR_HISTORY_CRON = "0 30 5 * * *"
  const val LIGHTYEAR_HOLDINGS_CRON = "0 50 23 * * *"
  const val TRADING212_HOLDINGS_CRON = "0 40 23 * * *"
  const val BLACKROCK_HOLDINGS_CRON = "0 50 23 * * *"
  const val VANGUARD_HOLDINGS_CRON = "0 30 2 * * *"
  const val BINANCE_PRICE_INTERVAL = "\${scheduling.jobs.binance-interval:120000}"
  const val TRADING212_PRICE_INTERVAL = "\${scheduling.jobs.trading212-interval:60000}"
  const val LIGHTYEAR_PRICE_STARTUP_SECONDS = 240L
  const val TRADING212_PRICE_STARTUP_SECONDS = 15L
  const val FT_HISTORY_STARTUP_SECONDS = 10L
  const val LIGHTYEAR_HISTORY_STARTUP_SECONDS = 30L
  const val HOLDINGS_STARTUP_SECONDS = 15L
  const val BLACKROCK_HOLDINGS_STARTUP_SECONDS = 20L
  const val VANGUARD_HOLDINGS_STARTUP_SECONDS = 60L
}

data class CollectionExpectation(
  val enabled: Boolean,
  val expectedNow: Boolean,
  val windowStart: Instant,
  val deadline: Instant,
)
