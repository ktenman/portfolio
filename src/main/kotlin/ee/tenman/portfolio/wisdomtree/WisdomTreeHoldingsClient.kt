package ee.tenman.portfolio.wisdomtree

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@FeignClient(
  name = "wisdomTreeHoldingsClient",
  url = "\${trading212.proxy.url:http://localhost:3000}",
)
interface WisdomTreeHoldingsClient {
  @GetMapping("/wisdomtree/holdings/{etfId}")
  fun getHoldings(
    @PathVariable etfId: String,
  ): String
}
