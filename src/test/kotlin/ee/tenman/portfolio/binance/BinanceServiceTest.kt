package ee.tenman.portfolio.binance

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal

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
}
