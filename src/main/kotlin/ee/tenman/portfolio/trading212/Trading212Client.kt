package ee.tenman.portfolio.trading212

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
  name = "trading212Client",
  url = "\${cloudflare-bypass-proxy.url:http://localhost:3000}",
)
interface Trading212Client {
  @GetMapping("/prices")
  fun getPrices(
    @RequestParam tickers: String,
  ): Trading212Response
}
