package ee.tenman.portfolio.trading212

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
  name = "trading212EtfClient",
  url = "\${cloudflare-bypass-proxy.url:http://localhost:3000}",
)
interface Trading212EtfClient {
  @GetMapping("/trading212/etf-holdings")
  fun getHoldings(
    @RequestParam ticker: String,
  ): List<Trading212EtfHolding>

  @GetMapping("/trading212/etf-summary")
  fun getSummary(
    @RequestParam ticker: String,
  ): Trading212EtfSummary
}
