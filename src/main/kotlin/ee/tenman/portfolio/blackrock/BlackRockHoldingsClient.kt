package ee.tenman.portfolio.blackrock

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
  name = "blackRockHoldingsClient",
  url = "\${blackrock.url:https://www.blackrock.com}",
)
interface BlackRockHoldingsClient {
  @GetMapping(
    value = ["/uk/individual/products/{productId}/x/1472631233320.ajax"],
    headers = [
      "User-Agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    ],
  )
  fun getHoldingsCsv(
    @PathVariable productId: String,
    @RequestParam fileName: String,
    @RequestParam fileType: String,
    @RequestParam dataType: String,
  ): String
}
