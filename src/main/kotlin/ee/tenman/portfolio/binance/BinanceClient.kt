package ee.tenman.portfolio.binance

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
  name = BinanceClient.CLIENT_NAME,
  url = "\${binance.url}",
)
interface BinanceClient {
  companion object {
    const val CLIENT_NAME = "binanceClient"
  }

  @GetMapping("/api/v3/klines")
  fun getKlines(
    @RequestParam symbol: String,
    @RequestParam interval: String,
    @RequestParam startTime: Long? = null,
    @RequestParam endTime: Long? = null,
    @RequestParam limit: Int? = null,
  ): List<List<String>>

  @GetMapping("/api/v3/ticker/price")
  fun getTickerPrice(
    @RequestParam symbol: String,
  ): TickerPrice
}
