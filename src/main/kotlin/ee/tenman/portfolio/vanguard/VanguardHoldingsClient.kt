package ee.tenman.portfolio.vanguard

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
  name = "vanguardHoldingsClient",
  url = "\${vanguard.url}",
)
interface VanguardHoldingsClient {
  @PostMapping("/gpx/graphql")
  fun getHoldings(
    @RequestBody request: VanguardHoldingsRequest,
  ): VanguardHoldingsResponse
}
