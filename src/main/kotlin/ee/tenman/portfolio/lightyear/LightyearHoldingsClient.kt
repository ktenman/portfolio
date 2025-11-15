package ee.tenman.portfolio.lightyear

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
  name = "lightyearHoldingsClient",
  url = "\${cloudflare-bypass-proxy.url:http://localhost:3000}",
)
interface LightyearHoldingsClient {
  @GetMapping("/lightyear/etf/holdings")
  fun getHoldings(
    @RequestParam path: String,
    @RequestParam page: Int,
  ): String
}
