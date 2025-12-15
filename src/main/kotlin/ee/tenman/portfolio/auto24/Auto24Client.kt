package ee.tenman.portfolio.auto24

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
  name = "auto24Client",
  url = "\${cloudflare-bypass-proxy.url:http://localhost:3000}",
)
interface Auto24Client {
  @GetMapping("/auto24/price")
  fun getMarketPrice(
    @RequestParam regNumber: String,
  ): Auto24PriceResponse
}
