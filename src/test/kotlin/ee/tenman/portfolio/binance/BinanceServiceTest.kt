package ee.tenman.portfolio.binance

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class BinanceServiceTest {
  private val binanceClient: BinanceClient = mockk()
  private val binanceService = BinanceService(binanceClient)

  @Test
  fun `should return current price from ticker endpoint`() {
    every { binanceClient.getTickerPrice("BTCEUR") } returns TickerPrice("BTCEUR", "95123.45")

    val price = binanceService.getCurrentPrice("BTCEUR")

    expect(price).toEqualNumerically(BigDecimal("95123.45"))
  }

  @Test
  fun `should handle decimal precision correctly`() {
    every { binanceClient.getTickerPrice("BNBEUR") } returns TickerPrice("BNBEUR", "747.92570000")

    val price = binanceService.getCurrentPrice("BNBEUR")

    expect(price).toEqualNumerically(BigDecimal("747.92570000"))
  }

  @Test
  fun `should fetch daily prices for date range`() {
    val klines =
      listOf(
        listOf("1703289600000", "42000.00", "43000.00", "41000.00", "42500.00", "1000.5"),
        listOf("1703376000000", "42500.00", "44000.00", "42000.00", "43500.00", "1200.7"),
      )
    every { binanceClient.getKlines(any(), any(), any(), any(), any()) } returns klines andThen emptyList()

    val prices = binanceService.getDailyPricesAsync("BTCEUR")

    expect(prices.size).toEqual(2)
  }

  @Test
  fun `should fetch hourly prices and return close prices keyed by truncated hour`() {
    val klines =
      listOf(
        listOf("1705312800000", "42000.00", "43000.00", "41000.00", "42500.00", "100.5"),
        listOf("1705316400000", "42500.00", "44000.00", "42000.00", "43500.00", "120.7"),
      )
    every { binanceClient.getKlines("BTCEUR", "1h", any(), any(), 1000) } returns klines

    val prices = binanceService.getHourlyPrices("BTCEUR", 48)

    expect(prices.size).toEqual(2)
    val entries = prices.entries.toList()
    expect(entries[0].key).toEqual(Instant.parse("2024-01-15T10:00:00Z"))
    expect(entries[0].value).toEqualNumerically(BigDecimal("42500.00"))
    expect(entries[1].key).toEqual(Instant.parse("2024-01-15T11:00:00Z"))
    expect(entries[1].value).toEqualNumerically(BigDecimal("43500.00"))
    verify { binanceClient.getKlines("BTCEUR", "1h", any(), any(), 1000) }
  }

  @Test
  fun `should return empty map when no hourly klines available`() {
    every { binanceClient.getKlines("BTCEUR", "1h", any(), any(), 1000) } returns emptyList()

    val prices = binanceService.getHourlyPrices("BTCEUR", 48)

    expect(prices.size).toEqual(0)
  }
}
