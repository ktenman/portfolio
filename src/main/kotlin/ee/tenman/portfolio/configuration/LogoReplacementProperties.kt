package ee.tenman.portfolio.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "logo-replacement")
data class LogoReplacementProperties(
  val maxSearchResults: Int = 50,
  val maxDisplayCandidates: Int = 15,
  val parallelValidationThreads: Int = 15,
  val parallelPrefetchThreads: Int = 3,
  val prefetchBatchSize: Int = 10,
  val prefetchBatchDelayMs: Long = 2000L,
  val downloadTimeoutMs: Long = 5000L,
)
