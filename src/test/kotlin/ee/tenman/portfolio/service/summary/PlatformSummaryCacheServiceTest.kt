package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import org.junit.jupiter.api.Test

class PlatformSummaryCacheServiceTest {
  @Test
  fun `should platformKey sort platforms alphabetically`() {
    val service = PlatformSummaryCacheService(summaryService = io.mockk.mockk())
    val key = service.platformKey(listOf(Platform.TRADING212, Platform.BINANCE, Platform.LIGHTYEAR))
    expect(key).toEqual("BINANCE,LIGHTYEAR,TRADING212")
  }

  @Test
  fun `should platformKey produce same key regardless of input order`() {
    val service = PlatformSummaryCacheService(summaryService = io.mockk.mockk())
    val key1 = service.platformKey(listOf(Platform.LIGHTYEAR, Platform.TRADING212))
    val key2 = service.platformKey(listOf(Platform.TRADING212, Platform.LIGHTYEAR))
    expect(key1).toEqual(key2)
  }

  @Test
  fun `should platformKey handle single platform`() {
    val service = PlatformSummaryCacheService(summaryService = io.mockk.mockk())
    val key = service.platformKey(listOf(Platform.LHV))
    expect(key).toEqual("LHV")
  }
}
